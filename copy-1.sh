#!/bin/bash
set -xe
sudo cp nginx-1.conf /etc/nginx/nginx.conf
sudo cp /var/log/nginx/access_log.ltsv ~
