package org.jenkinsci.plugins.gitlab_branch_source;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMRevision;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommit;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabRepositoryTree;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

class GitLabSCMProbe extends SCMProbe {
    private static final long serialVersionUID = 1L;
    private final SCMRevision revision;
    private final transient GitlabAPI api;
    private final transient GitlabProject repo;
    private final String ref;
    private final String name;

    public GitLabSCMProbe(GitlabAPI api, GitlabProject repo, SCMHead head, SCMRevision revision) {
        this.revision = revision;
        this.api = api;
        this.repo = repo;
        this.name = head.getName();
        if (head instanceof MergeRequestSCMHead) {
            MergeRequestSCMHead pr = (MergeRequestSCMHead) head;
            this.ref = "refs/pull/" + pr.getNumber() + (pr.isMerge() ? "/merge" : "/head");
        } else {
            this.ref = "refs/heads/" + head.getName();
        }
    }

    @Override
    public void close() throws IOException {
        // no-op as the GitlabProject does not keep a persistent connection
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public long lastModified() {
        if (repo == null) {
            return 0L;
        }
        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
            try {
                GitlabCommit commit = api.getCommit(repo.getId(),((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash());
                return commit.getCommittedDate().getTime();
            } catch (IOException e) {
                // ignore
            }
        } else if (revision == null) {
            try {
                return api.getBranch(repo, this.ref).getCommit().getCommittedDate().getTime();
            } catch (IOException e) {
                // ignore
            }
        }
        return 0;
    }

    @NonNull
    @Override
    public SCMProbeStat stat(@NonNull String path) throws IOException {
        if (repo == null) {
            throw new IOException("No connection available");
        }
        try {

            int index = path.lastIndexOf('/') + 1;
            List<GitlabRepositoryTree> directoryContent = api.getRepositoryTree(repo,path.substring(0, index), ref, false);
            for (GitlabRepositoryTree content : directoryContent) {
                if (content.getPath().equals(path)) {
                    String t = content.getType();
                    if (t.equals("blob")) {
                        return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
                    } else if (t.equals("tree")) {
                        return SCMProbeStat.fromType(SCMFile.Type.DIRECTORY);
                    } else if (content.getMode().equals("120000")) {
                        return SCMProbeStat.fromType(SCMFile.Type.LINK);
                    } else {
                        return SCMProbeStat.fromType(SCMFile.Type.OTHER);
                    }
                }
                if (content.getPath().equalsIgnoreCase(path)) {
                    return SCMProbeStat.fromAlternativePath(content.getPath());
                }
            }
        } catch (FileNotFoundException fnf) {
            // means that does not exist and this is handled below this try/catch block.
        }
        return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
    }

    @Override
    public SCMFile getRoot() {
        if (repo == null) {
            return null;
        }
        String ref;
        if (revision != null) {
            if (revision.getHead() instanceof MergeRequestSCMHead) {
                ref = this.ref;
            } else if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl){
                ref = ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash();
            } else {
                ref = this.ref;
            }
        } else {
            ref = this.ref;
        }
        return new GitLabSCMFile(repo, ref);
    }

}