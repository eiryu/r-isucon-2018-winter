#!/bin/bash
set -ex

if [ -f /var/lib/mysql/mysqld-slow.log ]; then
    sudo mv /var/lib/mysql/mysqld-slow.log /var/lib/mysql/mysqld-slow.log.$(date "+%Y%m%d_%H%M%S")
fi
if [ -f /var/log/nginx/access_log.ltsv ]; then
    sudo mv /var/log/nginx/access_log.ltsv /var/log/nginx/access_log.ltsv.$(date "+%Y%m%d_%H%M%S")
fi
#sudo systemctl restart mysql
cd /home/isucon/r-isucon/webapps/java
./gradlew build -x test
cd /home/isucon/r-isucon/webapps
sudo systemctl restart nginx
sudo systemctl restart r-isucon-java
