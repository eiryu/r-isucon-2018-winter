#!/bin/bash
set -xe

ip_addr=`ifconfig | sed -En 's/127.0.0.1//;s/.*inet (addr:)?(([0-9]*\.){3}[0-9]*).*/\2/p'`
if [ "${ip_addr}" = "10.0.7.70" ]; then
  sudo cp nginx-0.conf /etc/nginx/nginx.conf
  sudo cp /var/log/nginx/access_log.ltsv ~
elif [ "${ip_addr}" = "10.0.7.210" ]; then
  sudo cp nginx-1.conf /etc/nginx/nginx.conf
  sudo cp /var/log/nginx/access_log.ltsv ~
elif [ "${ip_addr}" = "10.0.7.175" ]; then
  sudo cp mysqld.cnf /etc/mysql/mysql.conf.d/mysqld.cnf
  sudo cp redis.conf /etc/redis/redis.conf
  sudo cp nginx-2.conf /etc/nginx/nginx.conf
  sudo cp /var/lib/mysql/mysqld-slow.log ~
fi
