package org.jenkinsci.plugins.gitlab_branch_source;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.dabsquared.gitlabjenkins.connection.GitLabApiToken;
import com.dabsquared.gitlabjenkins.connection.GitLabConnection;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionConfig;
import hudson.AbortException;
import hudson.Util;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang.StringUtils;
import org.gitlab.api.GitlabAPI;
import org.jenkinsci.plugins.gitclient.GitClient;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.Proxy;
import java.util.List;
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

    @CheckForNull
    public static StandardCredentials lookupScanCredentials(@CheckForNull SCMSourceOwner context,
                                                                          @CheckForNull String apiUri,
                                                                          @CheckForNull String scanCredentialsId) throws AbortException {
        if (Util.fixEmpty(scanCredentialsId) == null) {
            return null;
        } else {
            StandardCredentials c = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            StandardCredentials.class,
                            context,
                            context instanceof Task
                                    ? Tasks.getDefaultAuthenticationOf((Task) context)
                                    : ACL.SYSTEM,
                            gitlabDomainRequirements(apiUri)
                    ),
                    CredentialsMatchers.allOf(CredentialsMatchers.withId(scanCredentialsId), gitlabScanCredentialsMatcher())
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
                gitlabDomainRequirements(apiUri),
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

    public static GitLabConnection getEndpoint(String endpoint) {
        GitLabConnectionConfig config = Jenkins.getInstance().getInjector().getInstance(GitLabConnectionConfig.class);
        for (GitLabConnection con : config.getConnections()) {
            if (con.getName().equals(endpoint)) {
                return con;
            }
        }
        throw new IllegalArgumentException("No such GitLab endpoint defined in global config: " + endpoint);
    }


    private static String getApiToken(SCMSourceOwner context, GitLabConnection con) throws IOException {
        String id = con.getApiTokenId();
        StandardCredentials credentials = Connector.lookupScanCredentials(context, con.getUrl(), id);
        if (credentials == null) {
            throw new AbortException("No credentials found for credentialsId: " + id);
        }

        if (credentials instanceof GitLabApiToken) {
            return ((GitLabApiToken) credentials).getApiToken().getPlainText();
        }
//      if (credentials instanceof StringCredentials) {
//          return ((StringCredentials) credentials).getSecret().getPlainText();
//      }
        throw new AbortException("Unsupported credential type: " + credentials.getClass().getName());
    }

    public static @Nonnull
    GitlabAPI connect(SCMSourceOwner context, String endpoint) throws IOException {
        GitLabConnection con = getEndpoint(endpoint);
        String apiToken = getApiToken(context, con);

        try {
            GitlabAPI api = GitlabAPI.connect(con.getUrl(), apiToken);
            api.getVersion();
            return api;
        } catch (IOException e) {
            throw new IOException("Wrong URL or invalid credentials: "+con.getUrl()+" with "+con.getApiTokenId(),e);
        }
    }

    private static CredentialsMatcher gitlabScanCredentialsMatcher() {
        return CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
    }

    private static List<DomainRequirement> gitlabDomainRequirements(String apiUri) {
        return URIRequirementBuilder.fromUri(StringUtils.defaultIfEmpty(apiUri, "https://gitlab.com")).build();
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