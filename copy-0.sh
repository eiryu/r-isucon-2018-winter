#!/bin/bash
set -xe
sudo cp nginx-0.conf /etc/nginx/nginx.conf
sudo cp /var/log/nginx/access_log.ltsv ~
