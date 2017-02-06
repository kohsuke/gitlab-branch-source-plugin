# What's going on?
I'm trying to get GitLab Org Folder implemented by copying things from
GitLab org folder. The general approach is:

* Copy over some class from `github-branch-source` plugin and run `./replace.sh` to adjust names
* Manually fix up the code
* Repeat until no compiler error

Any help is welcome

# Known TODOs
* GL is unable to resolve a repository path name like 'OWNER/REPONAME' into a project object. This is blocking some of the current code migration
* Contributor & trusted PR build