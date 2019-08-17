#!/bin/bash

echo "wating for mysql"
sleep 40
echo "now begin"

DB_UNAME='maxwell'
DB_PWD='XXXXXX'
DB_HOST='mysql'
KAFKA_SERVER='kafka:9092'
KAFKA_TOPIC='mysqlsource'

# DB_FILTER='exclude:\ *.*,\ include:\ test.*'
# filter 参数用于过滤,目前不知道为什么没法通过环境变量传入,
# 只能直接写入到执行命令中

bin/maxwell --user=$DB_UNAME \
    --password=$DB_PWD \
    --host=$DB_HOST \
    --producer=kafka \
    --filter='exclude: *.*, include: test.*' \
    --kafka.bootstrap.servers=$KAFKA_SERVER \
    --kafka_topic=$KAFKA_TOPIC
