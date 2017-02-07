package org.jenkinsci.plugins.gitlab_branch_source;

import hudson.Extension;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMRevision;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;

/**
 * Head corresponding to a branch.
 */
public class BranchSCMHead extends SCMHead {
    /**
     * {@inheritDoc}
     */
    public BranchSCMHead(@Nonnull String name) {
        super(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPronoun() {
        return Messages.BranchSCMHead_Pronoun();
    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static class MigrationImpl extends SCMHeadMigration<GitLabSCMSource, SCMHead, SCMRevisionImpl> {
        public MigrationImpl() {
            super(GitLabSCMSource.class, SCMHead.class, AbstractGitSCMSource.SCMRevisionImpl.class);
        }

        @Override
        public SCMHead migrate(@Nonnull GitLabSCMSource source, @Nonnull SCMHead head) {
            return new BranchSCMHead(head.getName());
        }

        @Override
        public SCMRevision migrate(@Nonnull GitLabSCMSource source,
                                   @Nonnull AbstractGitSCMSource.SCMRevisionImpl revision) {
            return new AbstractGitSCMSource.SCMRevisionImpl(migrate(source, revision.getHead()), revision.getHash());
        }
    }
}
