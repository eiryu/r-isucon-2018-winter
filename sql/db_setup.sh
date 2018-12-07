#!/bin/bash

HOST=${RISUCON_DB_HOST:-localhost}
PORT=${RISUCON_DB_PORT:-3306}
USERNAME=${RISUCON_DB_USER:-isucon}
PASSWORD=${RISUCON_DB_PASSWORD:-isucon}

cd `dirname $0`
mysql -h $HOST --port $PORT -u $USERNAME -p$PASSWORD < ./01_table.sql
mysql -h $HOST --port $PORT -u $USERNAME -p$PASSWORD rine < ./02_data.sql
