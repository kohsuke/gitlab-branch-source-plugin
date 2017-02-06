package org.jenkinsci.plugins.gitlab_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.AbstractGitSCMSource.SCMRevisionImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMRevision;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Head corresponding to a branch.
 */
public class BranchSCMHead extends SCMHead {
    /**
     * {@inheritDoc}
     */
    public BranchSCMHead(@NonNull String name) {
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
        public SCMHead migrate(@NonNull GitLabSCMSource source, @NonNull SCMHead head) {
            return new BranchSCMHead(head.getName());
        }

        @Override
        public SCMRevision migrate(@NonNull GitLabSCMSource source,
                                   @NonNull AbstractGitSCMSource.SCMRevisionImpl revision) {
            return new AbstractGitSCMSource.SCMRevisionImpl(migrate(source, revision.getHead()), revision.getHash());
        }
    }
}
