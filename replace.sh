#!/bin/zsh
for f in PullRequest/MergeRequest GHBranch/GitlabBranch GitHubSCM/GitLabSCM GHRepository/GitlabProject \
         GHMergeRequest/GitlabMergeRequest \
         GHCommit/GitlabCommit GHOrganization/GitlabGroup .getOrganization/.getGroup GHUser/GitlabUser \
         GitLabRepoMetadata/GitLabProjectMetadata getLogin/getUsername \
         getWebUrl\(\).toString\(\)/getWebUrl\(\) \
         GitHub/GitLab;
do
  perl -pi -w -e "s/$f/g;" src/**/*.java src/**/*.properties src/**/*.jelly
done
