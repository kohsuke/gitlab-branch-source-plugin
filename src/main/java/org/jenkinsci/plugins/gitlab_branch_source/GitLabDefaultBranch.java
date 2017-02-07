package org.jenkinsci.plugins.gitlab_branch_source;

import hudson.model.InvisibleAction;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * @author Stephen Connolly
 */
public class GitLabDefaultBranch extends InvisibleAction implements Serializable {
    private static final long serialVersionUID = 1L;
    @Nonnull
    private final String repoOwner;
    @Nonnull
    private final String repository;
    @Nonnull
    private final String defaultBranch;

    public GitLabDefaultBranch(@Nonnull String repoOwner, @Nonnull String repository, @Nonnull String defaultBranch) {
        this.repoOwner = repoOwner;
        this.repository = repository;
        this.defaultBranch = defaultBranch;
    }

    @Nonnull
    public String getRepoOwner() {
        return repoOwner;
    }

    @Nonnull
    public String getRepository() {
        return repository;
    }

    @Nonnull
    public String getDefaultBranch() {
        return defaultBranch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GitLabDefaultBranch that = (GitLabDefaultBranch) o;

        if (!repoOwner.equals(that.repoOwner)) {
            return false;
        }
        if (!repository.equals(that.repository)) {
            return false;
        }
        return defaultBranch.equals(that.defaultBranch);
    }

    @Override
    public int hashCode() {
        int result = repoOwner.hashCode();
        result = 31 * result + repository.hashCode();
        result = 31 * result + defaultBranch.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "GitLabDefaultBranch{" +
                "repoOwner='" + repoOwner + '\'' +
                ", repository='" + repository + '\'' +
                ", defaultBranch='" + defaultBranch + '\'' +
                '}';
    }


}
