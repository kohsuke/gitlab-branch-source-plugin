package org.jenkinsci.plugins.gitlab_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabProject;

import java.util.logging.Logger;

/**
 * Head corresponding to a pull request.
 * Named like {@code PR-123} or {@code PR-123-merged} or {@code PR-123-unmerged}.
 */
public final class MergeRequestSCMHead extends SCMHead implements ChangeRequestSCMHead {

    private static final Logger LOGGER = Logger.getLogger(MergeRequestSCMHead.class.getName());

    private static final long serialVersionUID = 1;

    private Boolean merge;
    private final int number;
    private final BranchSCMHead target;
    private final int sourceProject;
    private final String sourceOwner, sourceRepo;
    private final String sourceBranch;
    /**
     * Only populated if de-serializing instances.
     */
    private transient Metadata metadata;

    /**
     * @param repo
     *      Repository that the MR is in.
     */
    MergeRequestSCMHead(GitlabProject repo, GitlabMergeRequest pr, String name, boolean merge) {
        super(name);
        // the merge flag is encoded into the name, so safe to store here
        this.merge = merge;
        this.number = pr.getIid();
        this.target = new BranchSCMHead(pr.getTargetBranch());
        this.sourceProject = pr.getSourceProjectId();
        this.sourceOwner = repo.getNamespace().getPath();
        this.sourceRepo = repo.getPath();
        this.sourceBranch = pr.getSha();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPronoun() {
        return Messages.MergeRequestSCMHead_Pronoun();
    }

    public int getNumber() {
        return number;
    }

    /**
     * Default for old settings.
     */
    @SuppressFBWarnings("SE_PRIVATE_READ_RESOLVE_NOT_INHERITED") // because JENKINS-41453
    private Object readResolve() {
        if (merge == null) {
            merge = true;
        }
        return this;
    }

    /**
     * Whether we intend to build the merge of the PR head with the base branch.
     */
    public boolean isMerge() {
        return merge;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getId() {
        return Integer.toString(number);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public SCMHead getTarget() {
        return target;
    }

    public String getSourceOwner() {
        return sourceOwner;
    }

    public String getSourceRepo() {
        return sourceRepo;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public int getSourceProject() {
        return sourceProject;
    }

    /**
     * Holds legacy data so we can recover the details.
     */
    private static class Metadata {
        private final int number;
        private final String url;
        private final String userLogin;
        private final String baseRef;

        public Metadata(int number, String url, String userLogin, String baseRef) {
            this.number = number;
            this.url = url;
            this.userLogin = userLogin;
            this.baseRef = baseRef;
        }

        public int getNumber() {
            return number;
        }

        public String getUrl() {
            return url;
        }

        public String getUserLogin() {
            return userLogin;
        }

        public String getBaseRef() {
            return baseRef;
        }
    }
}
