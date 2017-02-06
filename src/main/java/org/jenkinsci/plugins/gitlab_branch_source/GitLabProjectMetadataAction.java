package org.jenkinsci.plugins.gitlab_branch_source;

import jenkins.scm.api.metadata.AvatarMetadataAction;

/**
 * Invisible property that retains information about GitLab repository.
 *
 * @author Kohsuke Kawaguchi
 */
public class GitLabProjectMetadataAction extends AvatarMetadataAction {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarIconClassName() {
        return "icon-github-repo";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAvatarDescription() {
        return Messages.GitLabProjectMetadataAction_IconDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return true;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "GitLabProjectMetadataAction{}";
    }
}
