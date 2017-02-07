package org.jenkinsci.plugins.gitlab_branch_source;

import com.dabsquared.gitlabjenkins.connection.GitLabConnection;
import com.dabsquared.gitlabjenkins.connection.GitLabConnectionConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.Action;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMNavigatorEvent;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSourceCategory;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.impl.UncategorizedSCMSourceCategory;
import org.apache.commons.lang.StringUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabGroup;
import org.gitlab.api.models.GitlabProject;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Kohsuke Kawaguchi
 */
public class GitLabSCMNavigator extends SCMNavigator {

    private final String repoOwner;
    /**
     * Maps to {@link GitLabConnection#getName()}
     */
    private final String endpoint;
    private String pattern = ".*";

    private final String checkoutCredentialsId;

    @CheckForNull
    private String includes;
    @CheckForNull
    private String excludes;
    /** Whether to build regular origin branches. */
    @Nonnull
    private Boolean buildOriginBranch = DescriptorImpl.defaultBuildOriginBranch;
    /** Whether to build origin branches which happen to also have a PR filed from them (but here we are naming and building as a branch). */
    @Nonnull
    private Boolean buildOriginBranchWithPR = DescriptorImpl.defaultBuildOriginBranchWithPR;
    /** Whether to build PRs filed from the origin, where the build is of the merge with the base branch. */
    @Nonnull
    private Boolean buildOriginPRMerge = DescriptorImpl.defaultBuildOriginPRMerge;
    /** Whether to build PRs filed from the origin, where the build is of the branch head. */
    @Nonnull
    private Boolean buildOriginPRHead = DescriptorImpl.defaultBuildOriginPRHead;
    /** Whether to build PRs filed from a fork, where the build is of the merge with the base branch. */
    @Nonnull
    private Boolean buildForkPRMerge = DescriptorImpl.defaultBuildForkPRMerge;
    /** Whether to build PRs filed from a fork, where the build is of the branch head. */
    @Nonnull
    private Boolean buildForkPRHead = DescriptorImpl.defaultBuildForkPRHead;

    @DataBoundConstructor
    public GitLabSCMNavigator(String endpoint, String repoOwner, String checkoutCredentialsId) {
        this.repoOwner = repoOwner;
        this.endpoint = Util.fixEmpty(endpoint);
        this.checkoutCredentialsId = checkoutCredentialsId;
    }

    /** Use defaults for old settings. */
    @SuppressFBWarnings(value="RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification="Only non-null after we set them here!")
    private Object readResolve() {
        if (buildOriginBranch == null) {
            buildOriginBranch = DescriptorImpl.defaultBuildOriginBranch;
        }
        if (buildOriginBranchWithPR == null) {
            buildOriginBranchWithPR = DescriptorImpl.defaultBuildOriginBranchWithPR;
        }
        if (buildOriginPRMerge == null) {
            buildOriginPRMerge = DescriptorImpl.defaultBuildOriginPRMerge;
        }
        if (buildOriginPRHead == null) {
            buildOriginPRHead = DescriptorImpl.defaultBuildOriginPRHead;
        }
        if (buildForkPRMerge == null) {
            buildForkPRMerge = DescriptorImpl.defaultBuildForkPRMerge;
        }
        if (buildForkPRHead == null) {
            buildForkPRHead = DescriptorImpl.defaultBuildForkPRHead;
        }
        return this;
    }

    @Nonnull
    public String getIncludes() {
        return includes != null ? includes : DescriptorImpl.defaultIncludes;
    }

    @DataBoundSetter
    public void setIncludes(@Nonnull String includes) {
        this.includes = includes.equals(DescriptorImpl.defaultIncludes) ? null : includes;
    }

    @Nonnull
    public String getExcludes() {
        return excludes != null ? excludes : DescriptorImpl.defaultExcludes;
    }

    @DataBoundSetter
    public void setExcludes(@Nonnull String excludes) {
        this.excludes = excludes.equals(DescriptorImpl.defaultExcludes) ? null : excludes;
    }

    public boolean getBuildOriginBranch() {
        return buildOriginBranch;
    }

    @DataBoundSetter
    public void setBuildOriginBranch(boolean buildOriginBranch) {
        this.buildOriginBranch = buildOriginBranch;
    }

    public boolean getBuildOriginBranchWithPR() {
        return buildOriginBranchWithPR;
    }

    @DataBoundSetter
    public void setBuildOriginBranchWithPR(boolean buildOriginBranchWithPR) {
        this.buildOriginBranchWithPR = buildOriginBranchWithPR;
    }

    public boolean getBuildOriginPRMerge() {
        return buildOriginPRMerge;
    }

    @DataBoundSetter
    public void setBuildOriginPRMerge(boolean buildOriginPRMerge) {
        this.buildOriginPRMerge = buildOriginPRMerge;
    }

    public boolean getBuildOriginPRHead() {
        return buildOriginPRHead;
    }

    @DataBoundSetter
    public void setBuildOriginPRHead(boolean buildOriginPRHead) {
        this.buildOriginPRHead = buildOriginPRHead;
    }

    public boolean getBuildForkPRMerge() {
        return buildForkPRMerge;
    }

    @DataBoundSetter
    public void setBuildForkPRMerge(boolean buildForkPRMerge) {
        this.buildForkPRMerge = buildForkPRMerge;
    }

    public boolean getBuildForkPRHead() {
        return buildForkPRHead;
    }

    @DataBoundSetter
    public void setBuildForkPRHead(boolean buildForkPRHead) {
        this.buildForkPRHead = buildForkPRHead;
    }

    public String getRepoOwner() {
        return repoOwner;
    }

    @CheckForNull
    public String getCheckoutCredentialsId() {
        return checkoutCredentialsId;
    }

    public String getPattern() {
        return pattern;
    }

    @CheckForNull
    public String getEndpoint() {
        return endpoint;
    }

    @DataBoundSetter
    public void setPattern(String pattern) {
        Pattern.compile(pattern);
        this.pattern = pattern;
    }

    @Nonnull
    @Override
    protected String id() {
        return endpoint + "::" + repoOwner;
    }

    @Override
    public void visitSources(SCMSourceObserver observer) throws IOException, InterruptedException {
        TaskListener listener = observer.getListener();
        GitlabAPI gitlab = connect(observer);

        GitlabGroup org = null;
        try {
            org = gitlab.getGroup(repoOwner);
        } catch (FileNotFoundException fnf) {
            // may be an user... ok to ignore
        }
        if (org != null) {
            listener.getLogger().format("Looking up repositories of organization %s%n%n", repoOwner);
            for (GitlabProject repo : gitlab.getGroupProjects(org)) {
//                if (!observer.isObserving()) {
//                    return;
//                }
                checkInterrupt();
                add(listener, observer, gitlab, repo);
            }
            return;
        }

        throw new AbortException(repoOwner + " does not correspond to a known GitLab User Account or Organization");
    }

    @Override
    public void visitSource(String sourceName, SCMSourceObserver observer)
            throws IOException, InterruptedException {
        TaskListener listener = observer.getListener();

        GitlabAPI gitlab = connect(observer);
        GitlabProject p = gitlab.getProject(repoOwner + "/" + sourceName);
        add(listener, observer, gitlab, p);
    }

    private GitlabAPI connect(SCMSourceObserver observer) throws IOException {
        return connect(observer.getContext());
    }

    private GitlabAPI connect(SCMSourceOwner observer) throws IOException {
        return Connector.connect(observer,endpoint);
    }

    private void add(TaskListener listener, SCMSourceObserver observer, GitlabAPI gitlab, GitlabProject repo) throws InterruptedException {
        String name = repo.getName();
        if (!Pattern.compile(pattern).matcher(name).matches()) {
            listener.getLogger().format("Ignoring %s%n", name);
            return;
        }
        listener.getLogger().format("Proposing %s%n", name);
        checkInterrupt();
        SCMSourceObserver.ProjectObserver projectObserver = observer.observe(name);

        GitLabSCMSource glSCMSource = new GitLabSCMSource(getId()+ "::" + name, endpoint, checkoutCredentialsId, repoOwner, name);
        glSCMSource.setExcludes(getExcludes());
        glSCMSource.setIncludes(getIncludes());
        glSCMSource.setBuildOriginBranch(getBuildOriginBranch());
        glSCMSource.setBuildOriginBranchWithPR(getBuildOriginBranchWithPR());
        glSCMSource.setBuildOriginPRMerge(getBuildOriginPRMerge());
        glSCMSource.setBuildOriginPRHead(getBuildOriginPRHead());
        glSCMSource.setBuildForkPRMerge(getBuildForkPRMerge());
        glSCMSource.setBuildForkPRHead(getBuildForkPRHead());

        projectObserver.addSource(glSCMSource);
        projectObserver.complete();
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public List<Action> retrieveActions(@Nonnull SCMNavigatorOwner owner,
                                        @CheckForNull SCMNavigatorEvent event,
                                        @Nonnull TaskListener listener) throws IOException {
        // TODO when we have support for trusted events, use the details from event if event was from trusted source
        listener.getLogger().printf("Looking up details of %s...%n", getRepoOwner());
        List<Action> result = new ArrayList<>();
        GitlabAPI api = connect(owner);

        GitlabGroup g = api.getGroup(getRepoOwner());
        String objectUrl = g.getWebUrl();
        result.add(new ObjectMetadataAction(
                Util.fixEmpty(g.getName()),
                null,
                objectUrl)
        );
//        result.add(new GitLabOrgMetadataAction(g));
        result.add(new GitLabLink("icon-gitlab-logo", g.getWebUrl()));
        if (objectUrl == null) {
            listener.getLogger().println("Organization URL: unspecified");
        } else {
            listener.getLogger().printf("Organization URL: %s%n",
                    HyperlinkNote.encodeTo(objectUrl, StringUtils.defaultIfBlank(g.getName(), objectUrl)));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterSave(@Nonnull SCMNavigatorOwner owner) {
        // TODO
//        GitLabWebHook.get().registerHookFor(owner);
//        try {
//            // FIXME MINOR HACK ALERT
//            StandardCredentials credentials =
//                    Connector.lookupScanCredentials(owner, getApiUri(), getScanCredentialsId());
//            GitLab hub = Connector.connect(getApiUri(), credentials);
//            GitLabOrgWebHook.register(hub, repoOwner);
//        } catch (IOException e) {
//            DescriptorImpl.LOGGER.log(Level.WARNING, e.getMessage(), e);
//        }
    }

    @Extension
    public static class DescriptorImpl extends SCMNavigatorDescriptor implements IconSpec {

        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        public static final String defaultIncludes = GitLabSCMSource.DescriptorImpl.defaultIncludes;
        public static final String defaultExcludes = GitLabSCMSource.DescriptorImpl.defaultExcludes;
        public static final String SAME = GitLabSCMSource.DescriptorImpl.SAME;
        public static final boolean defaultBuildOriginBranch = GitLabSCMSource.DescriptorImpl.defaultBuildOriginBranch;
        public static final boolean defaultBuildOriginBranchWithPR = GitLabSCMSource.DescriptorImpl.defaultBuildOriginBranchWithPR;
        public static final boolean defaultBuildOriginPRMerge = GitLabSCMSource.DescriptorImpl.defaultBuildOriginPRMerge;
        public static final boolean defaultBuildOriginPRHead = GitLabSCMSource.DescriptorImpl.defaultBuildOriginPRHead;
        public static final boolean defaultBuildForkPRMerge = GitLabSCMSource.DescriptorImpl.defaultBuildForkPRMerge;
        public static final boolean defaultBuildForkPRHead = GitLabSCMSource.DescriptorImpl.defaultBuildForkPRHead;

        @Inject
        private GitLabSCMSource.DescriptorImpl delegate;

        @Inject
        private GitLabConnectionConfig gitLabConfig;

        /**
         * {@inheritDoc}
         */
        @Override
        public String getPronoun() {
            return Messages.GitLabSCMNavigator_Pronoun();
        }

        @Override
        public String getDisplayName() {
            return Messages.GitLabSCMNavigator_DisplayName();
        }

        @Override
        public String getDescription() {
            return Messages.GitLabSCMNavigator_Description();
        }

        @Override
        public String getIconFilePathPattern() {
            return "plugin/github-branch-source/images/:size/github-scmnavigator.png";
        }

        @Override
        public String getIconClassName() {
            return "icon-github-scm-navigator";
        }

        @Override
        public SCMNavigator newInstance(String name) {
            return new GitLabSCMNavigator("", name, GitLabSCMSource.DescriptorImpl.SAME);
        }

        @Nonnull
        @Override
        protected SCMSourceCategory[] createCategories() {
            return new SCMSourceCategory[]{
                    new UncategorizedSCMSourceCategory(Messages._GitLabSCMNavigator_UncategorizedCategory())
                    // TODO add support for forks
            };
        }

        public ListBoxModel doFillCheckoutCredentialsIdItems(@AncestorInPath SCMSourceOwner context, @QueryParameter String apiUri) {
             return Connector.listCheckoutCredentials(context, apiUri);
         }

        public ListBoxModel doFillEndpointItems() {
            ListBoxModel result = new ListBoxModel();
            for (GitLabConnection con : gitLabConfig.getConnections()) {
                result.add(String.format("%s (%s)", con.getName(), con.getUrl()),con.getName());
            }
            return result;
        }

        public boolean isApiUriSelectable() {
            return gitLabConfig.getConnections().size()>1;
        }

        // TODO repeating configuration blocks like this is clumsy; better to factor shared config into a Describable and use f:property

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckIncludes(@QueryParameter String value) {
            return delegate.doCheckIncludes(value);
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckBuildOriginBranchWithPR(
            @QueryParameter boolean buildOriginBranch,
            @QueryParameter boolean buildOriginBranchWithPR,
            @QueryParameter boolean buildOriginPRMerge,
            @QueryParameter boolean buildOriginPRHead,
            @QueryParameter boolean buildForkPRMerge,
            @QueryParameter boolean buildForkPRHead
        ) {
            return delegate.doCheckBuildOriginBranchWithPR(buildOriginBranch, buildOriginBranchWithPR, buildOriginPRMerge, buildOriginPRHead, buildForkPRMerge, buildForkPRHead);
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckBuildOriginPRHead(@QueryParameter boolean buildOriginBranchWithPR, @QueryParameter boolean buildOriginPRMerge, @QueryParameter boolean buildOriginPRHead) {
            return delegate.doCheckBuildOriginPRHead(buildOriginBranchWithPR, buildOriginPRMerge, buildOriginPRHead);
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckBuildForkPRHead/* web method name controls UI position of message; we want this at the bottom */(
            @QueryParameter boolean buildOriginBranch,
            @QueryParameter boolean buildOriginBranchWithPR,
            @QueryParameter boolean buildOriginPRMerge,
            @QueryParameter boolean buildOriginPRHead,
            @QueryParameter boolean buildForkPRMerge,
            @QueryParameter boolean buildForkPRHead
        ) {
            return delegate.doCheckBuildForkPRHead(buildOriginBranch, buildOriginBranchWithPR, buildOriginPRMerge, buildOriginPRHead, buildForkPRMerge, buildForkPRHead);
        }

        static {
            IconSet.icons.addIcon(
                    new Icon("icon-github-scm-navigator icon-sm",
                            "plugin/github-branch-source/images/16x16/github-scmnavigator.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-scm-navigator icon-md",
                            "plugin/github-branch-source/images/24x24/github-scmnavigator.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-scm-navigator icon-lg",
                            "plugin/github-branch-source/images/32x32/github-scmnavigator.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-scm-navigator icon-xlg",
                            "plugin/github-branch-source/images/48x48/github-scmnavigator.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-github-logo icon-sm",
                            "plugin/github-branch-source/images/16x16/github-logo.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-logo icon-md",
                            "plugin/github-branch-source/images/24x24/github-logo.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-logo icon-lg",
                            "plugin/github-branch-source/images/32x32/github-logo.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-logo icon-xlg",
                            "plugin/github-branch-source/images/48x48/github-logo.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-github-repo icon-sm",
                            "plugin/github-branch-source/images/16x16/github-repo.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-repo icon-md",
                            "plugin/github-branch-source/images/24x24/github-repo.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-repo icon-lg",
                            "plugin/github-branch-source/images/32x32/github-repo.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-repo icon-xlg",
                            "plugin/github-branch-source/images/48x48/github-repo.png",
                            Icon.ICON_XLARGE_STYLE));

            IconSet.icons.addIcon(
                    new Icon("icon-github-branch icon-sm",
                            "plugin/github-branch-source/images/16x16/github-branch.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-branch icon-md",
                            "plugin/github-branch-source/images/24x24/github-branch.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-branch icon-lg",
                            "plugin/github-branch-source/images/32x32/github-branch.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-github-branch icon-xlg",
                            "plugin/github-branch-source/images/48x48/github-branch.png",
                            Icon.ICON_XLARGE_STYLE));
        }
    }

}