#!/bin/bash
set -ex

if [ -f /var/lib/mysql/mysqld-slow.log ]; then
    sudo mv /var/lib/mysql/mysqld-slow.log /var/lib/mysql/mysqld-slow.log.$(date "+%Y%m%d_%H%M%S")
fi
if [ -f /var/log/nginx/access_log.ltsv ]; then
    sudo mv /var/log/nginx/access_log.ltsv /var/log/nginx/access_log.ltsv.$(date "+%Y%m%d_%H%M%S")
fi

ip_addr=`ifconfig | sed -En 's/127.0.0.1//;s/.*inet (addr:)?(([0-9]*\.){3}[0-9]*).*/\2/p'`
if [ "${ip_addr}" = "10.0.7.175" ]; then
  sudo systemctl restart mysql
  sudo systemctl restart nginx
else
  #sudo systemctl restart mysql
  cd /home/isucon/r-isucon/webapps/java
  ./gradlew build -x test
  cd /home/isucon/r-isucon/webapps
  sudo systemctl restart nginx
  sudo systemctl restart r-isucon-java
fi

