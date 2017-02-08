#!/bin/sh
exec sudo docker run -it --rm \
    --hostname gitlab.example.com \
    --publish 127.0.0.1:8443:443 --publish 127.0.0.1:9023:9023 --publish 127.0.0.1:8022:22 \
    --name gitlab \
    --volume /srv/gitlab/config:/etc/gitlab \
    --volume /srv/gitlab/logs:/var/log/gitlab \
    --volume /srv/gitlab/data:/var/opt/gitlab \
    gitlab/gitlab-ce:8.16.4-ce.0
