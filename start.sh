#!/bin/sh
exec sudo docker run -it \
    --hostname gitlab.example.com \
    --publish 8443:443 --publish 8080:80 --publish 8022:22 \
    --name gitlab \
    --restart always \
    --volume /srv/gitlab/config:/etc/gitlab \
    --volume /srv/gitlab/logs:/var/log/gitlab \
    --volume /srv/gitlab/data:/var/opt/gitlab \
    gitlab/gitlab-ce:8.16.4-ce.0
