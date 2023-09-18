#!/bin/bash

set -e

./mvnw install
./mvnw install -Dsecure=all
./mvnw install -Dsecure=ui
./mvnw install -Ddb=mysql
./mvnw install -Ddb=mysql -Dsecure=all
./mvnw install -Ddb=mysql -Dsecure=ui
./mvnw install -Ddb=mariadb
./mvnw install -Ddb=mariadb -Dsecure=all
./mvnw install -Ddb=mariadb -Dsecure=ui
./mvnw install -Ddb=oracle
./mvnw install -Ddb=oracle -Dsecure=all
./mvnw install -Ddb=oracle -Dsecure=ui
./mvnw install -Ddb=sqlserver
./mvnw install -Ddb=sqlserver -Dsecure=all
./mvnw install -Ddb=sqlserver -Dsecure=ui
./mvnw install -Ddb=db2
./mvnw install -Ddb=firebird
./mvnw install -Ddb=h2
