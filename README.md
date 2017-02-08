# GitLab Branch Source Plugin
This is an attempt to make Jenkins organization folder work for GitLab.
See [the documentation for GitHub org folder](https://github.com/jenkinsci/github-organization-folder-plugin) if you are new to the concept.

## Current status
This work was born in FOSDEM Hackathon. The basic functionalities work, but it probably needs some polish and more validation by people who actually use GitLab.

Here are some known TODOs:
* GitLab is unable to resolve a repository path name like 'OWNER/REPONAME' into a project object.
* Contributor & trusted PR build
* Hook support
