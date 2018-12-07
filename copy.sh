#!/bin/bash
set -xe
sudo cp mysqld.cnf /etc/mysql/mysql.conf.d/mysqld.cnf
sudo cp nginx.conf /etc/nginx/nginx.conf
sudo cp /var/lib/mysql/mysqld-slow.log ~
sudo cp /var/log/nginx/access_log.ltsv ~
