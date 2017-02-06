package org.jenkinsci.plugins.gitlab_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMRevision;

/**
 * Revision of a pull request.
 */
public class MergeRequestSCMRevision extends SCMRevision {

    private static final long serialVersionUID = 1L;

    private final @NonNull
    String baseHash;
    private final @NonNull String pullHash;

    MergeRequestSCMRevision(@NonNull MergeRequestSCMHead head, @NonNull String baseHash, @NonNull String pullHash) {
        super(head);
        this.baseHash = baseHash;
        this.pullHash = pullHash;
    }

    /**
     * The commit hash of the base branch we are tracking.
     * If {@link MergeRequestSCMHead#isMerge}, this would be the current head of the base branch.
     * Otherwise it would be the PR’s {@code .base.sha}, the common ancestor of the PR branch and the base branch.
     */
    public @NonNull String getBaseHash() {
        return baseHash;
    }

    /**
     * The commit hash of the head of the pull request branch.
     */
    public @NonNull String getPullHash() {
        return pullHash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MergeRequestSCMRevision)) {
            return false;
        }
        MergeRequestSCMRevision other = (MergeRequestSCMRevision) o;
        return getHead().equals(other.getHead()) && baseHash.equals(other.baseHash) && pullHash.equals(other.pullHash);
    }

    @Override
    public int hashCode() {
        return pullHash.hashCode();
    }

    @Override
    public String toString() {
        return getHead() instanceof MergeRequestSCMHead && ((MergeRequestSCMHead) getHead()).isMerge() ? pullHash + "+" + baseHash : pullHash;
    }

}
