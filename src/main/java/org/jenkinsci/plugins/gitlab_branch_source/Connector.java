package org.jenkinsci.plugins.gitlab_branch_source;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.AbortException;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang.StringUtils;
import org.gitlab.api.GitlabAPI;
import org.jenkinsci.plugins.gitclient.GitClient;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class Connector {
    private static final Logger LOGGER = Logger.getLogger(Connector.class.getName());

    private Connector() {
        throw new IllegalAccessError("Utility class");
    }

    public static ListBoxModel listScanCredentials(SCMSourceOwner context, String apiUri) {
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                        context instanceof Queue.Task
                                ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                                : ACL.SYSTEM,
                        context,
                        StandardUsernameCredentials.class,
                        githubDomainRequirements(apiUri),
                        githubScanCredentialsMatcher()
                );
    }

    public static FormValidation checkScanCredentials(SCMSourceOwner context, String apiUri, String scanCredentialsId) {
        if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                context != null && !context.hasPermission(Item.EXTENDED_READ)) {
            return FormValidation.ok();
        }
        if (!scanCredentialsId.isEmpty()) {
            ListBoxModel options = listScanCredentials(context, apiUri);
            boolean found = false;
            for (ListBoxModel.Option b: options) {
                if (scanCredentialsId.equals(b.value)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return FormValidation.error("Credentials not found");
            }
            if (!(context.hasPermission(Item.CONFIGURE)
                    || context.hasPermission(Item.BUILD)
                    || context.hasPermission(CredentialsProvider.USE_ITEM))) {
                return FormValidation.ok("Credentials found");
            }
            StandardCredentials credentials = null;
            try {
                credentials = Connector.lookupScanCredentials(context, apiUri, scanCredentialsId);
            } catch (AbortException e) {
                return FormValidation.error(e.getMessage());
            }
            try {
                GitlabAPI connector = Connector.connect(apiUri, credentials);
                connector.getVersion();
                return FormValidation.ok();
            } catch (IOException e) {
                // ignore, never thrown
                LOGGER.log(Level.WARNING, "Exception validating credentials {0} on {1}", new Object[]{
                        CredentialsNameProvider.name(credentials), apiUri
                });
                return FormValidation.error("Exception validating credentials",e);
            }
        } else {
            return FormValidation.warning("Credentials are recommended");
        }
    }

    @CheckForNull
    public static StandardCredentials lookupScanCredentials(@CheckForNull SCMSourceOwner context,
                                                                          @CheckForNull String apiUri,
                                                                          @CheckForNull String scanCredentialsId) throws AbortException {
        if (Util.fixEmpty(scanCredentialsId) == null) {
            return null;
        } else {
            StandardUsernameCredentials c = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            StandardUsernameCredentials.class,
                            context,
                            context instanceof Task
                                    ? Tasks.getDefaultAuthenticationOf((Task) context)
                                    : ACL.SYSTEM,
                            githubDomainRequirements(apiUri)
                    ),
                    CredentialsMatchers.allOf(CredentialsMatchers.withId(scanCredentialsId), githubScanCredentialsMatcher())
            );
            if (c!=null)    return c;

            String message = String.format("Invalid scan credentials %s to connect to %s, skipping",
                    scanCredentialsId, apiUri == null ? Connector.GITLAB_URL : apiUri);
            throw new AbortException(message);
        }
    }

    public static ListBoxModel listCheckoutCredentials(SCMSourceOwner context, String apiUri) {
        StandardListBoxModel result = new StandardListBoxModel();
        result.includeEmptyValue();
        result.add("- same as scan credentials -", GitLabSCMSource.DescriptorImpl.SAME);
        result.add("- anonymous -", GitLabSCMSource.DescriptorImpl.ANONYMOUS);
        return result.includeMatchingAs(
                context instanceof Queue.Task
                        ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                        : ACL.SYSTEM,
                context,
                StandardUsernameCredentials.class,
                githubDomainRequirements(apiUri),
                GitClient.CREDENTIALS_MATCHER
        );
    }

    public static boolean isCredentialsValid(GitlabAPI api) {
        try {
            api.getVersion();
            return true;
        } catch (IOException e) {
            LOGGER.log(FINE, "Credentials appear invalid on "+api, e);
            return false;
        }
    }

    public static @Nonnull
    GitlabAPI connect(@CheckForNull String apiUri, @CheckForNull StandardCredentials credentials) throws IOException {
        String apiUrl = Util.fixEmptyAndTrim(apiUri);
        String host;
        try {
            apiUrl = apiUrl != null ? apiUrl : Connector.GITLAB_URL;
            host = new URL(apiUrl).getHost();
        } catch (MalformedURLException e) {
            throw new IOException("Invalid GitLab API URL: " + apiUrl, e);
        }

        if (credentials == null) {
            // nothing further to configure
            throw new UnsupportedOperationException();
//            return GitlabAPI.connect(apiUrl,null);
        } else if (credentials instanceof StandardUsernamePasswordCredentials) {
            StandardUsernamePasswordCredentials c = (StandardUsernamePasswordCredentials) credentials;
            GitlabAPI con = GitlabAPI.connect(apiUrl, c.getPassword().getPlainText());
            try {
                con.getVersion();
            } catch (IOException e) {
                throw new IOException("Invalid credentials for "+apiUri,e);
            }
            return con;
        } else {
            // TODO OAuth support
            throw new IOException("Unsupported credential type: " + credentials.getClass().getName());
        }
    }

    private static CredentialsMatcher githubScanCredentialsMatcher() {
        // TODO OAuth credentials
        return CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
    }

    private static List<DomainRequirement> githubDomainRequirements(String apiUri) {
        return URIRequirementBuilder.fromUri(StringUtils.defaultIfEmpty(apiUri, "https://github.com")).build();
    }

    /**
     * Uses proxy if configured on pluginManager/advanced page
     *
     * @param host GitLab's hostname to build proxy to
     *
     * @return proxy to use it in connector. Should not be null as it can lead to unexpected behaviour
     */
    @Nonnull
    private static Proxy getProxy(@Nonnull String host) {
        Jenkins jenkins = Jenkins.getActiveInstance();

        if (jenkins.proxy == null) {
            return Proxy.NO_PROXY;
        } else {
            return jenkins.proxy.createProxy(host);
        }
    }

    public static final String GITLAB_URL = "https://gitlab.com/";
}