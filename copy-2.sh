#!/bin/bash
set -xe
sudo cp mysqld.cnf /etc/mysql/mysql.conf.d/mysqld.cnf
sudo cp redis.cnf /etc/redis/redis.cnf
sudo cp nginx-2.conf /etc/nginx/nginx.conf
sudo cp /var/lib/mysql/mysqld-slow.log ~
