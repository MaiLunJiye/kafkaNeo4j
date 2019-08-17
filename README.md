# 简介

本项目是 通过kafka实现neo4j 同步 mysql 增量数据 的 数据同步系统实现, 大部分环境通过 docker 虚拟化实现。

本 Demo 实现以下数据同步： mysql 存储一张表，每行数据代表一个实体，当这张表插入新数据时候，会在neo4j 数据库创建相应实体。

# 注意：

1. 需要确保 `mysqldb/conf/my.cnf` 的权限为 744， 如果目录放在 ntfs 格式的磁盘下会出现权限无法修改的问题。
2. 由于 mysql 容器初始化速度很慢,导致 maxwell 连接 mysql 失败, 所以如果maxwell 因此退出,需要等待mysql正常运行后手动
执行 `docker-compose -f docker-compose.yml up maxwell`


# 测试使用

1. 看完前面的注意事项
2. 安装 依赖 `pip install -r pythonSync/requirements.txt`
3. 执行 `docker-compose -f docker-compose.yml up` 启动docker环境， 如果 maxwell 容器报错退出，需要等待mysql正常运行后手动执行 `docker-compose -f docker-compose.yml up maxwell` 重启maxwell
4. 执行 `python pythonSync/datasync.py` 运行数据同步服务
5. 打开 浏览器，访问 `http://localhost:7474` 用户名neo4j，密码 123456， 进入neo4j web管理页面
6. mysql 端口为 3306， 通过一些工具往 `test` 库 的 `testtable` 表插入数据 `insert into test.testtable values("Davi", "14")`
7. 查看 neo4j 是否创建了相应实体

# 开发过程思路

## 项目概览

```
.
├── docker-compose.yml      # docker-compose 编排容器
├── maxwell                 # mysql kafka 中间件
│   └── start.sh
├── mysqldb                 # mysql 相关配置文件
│   ├── conf
│   │   └── my.cnf
│   └── init
│       └── init.sql
├── neo4jdb                 # neo4j 插件
│   └── plugins
│       ├── apoc-3.4.0.3-all.jar
│       ├── mysql-connector-java-5.1.21.jar
│       └── mysql-connector-java-8.0.11.zip
├── pythonSync              # python 实现的数据同步服务
│   ├── datasync.py
│   └── requirements.txt
└── README.md
```

## 开发思路

maxwell 监听 mysql的增量数据， 把增量数据推送到kafka中， 数据同步服务通过读取kafka中的数据，进行处理，然后通过 neo4j API 更新到 neo4j 图数据库中。

## docker 环境开发

点击查看完整的 [docker-compose.yml](./docker-compose.yml)

### kafka 环境

docker-compose.yml 相关部分

```
  zookeeper:
    image: wurstmeister/zookeeper:latest
    container_name: "zookeeper"
    ports:
      - "2181:2181"

  kafka:
    container_name: "kafka"
    image: wurstmeister/kafka:latest
    ports: 
      - "9092:9092"
    links:
      - zookeeper:zookeeper
      - mysql:mysql
    environment:
      # 注意，迁移时候需要修改下面的地址为本机ip
      - KAFKA_ADVERTISED_HOST_NAME=192.168.0.106
      - KAFKA_ADVERTISED_PORT=9092
      - KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181
      - KAFKA_CREATE_TOPICS="mysqlsource:1,1"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - /etc/localtime:/etc/localtime
      # - ./kafka_connect/mysql.properties:/opt/kafka/config/mysql.properties

```

注意： `KAFKA_ADVERTISED_HOST_NAME=192.168.0.106` 只有被正确设置为宿主机 IP 才能被外网（相对于容器集群内部网）访问。


### mysql 环境

mysql 相关文件包括：

```
mysqldb
├── conf
│   └── my.cnf
└── init
    └── init.sql
```

maxwell 获取 mysql 增量信息需要以下两个条件：

1. mysql 开启 binlog日志
2. mysql 提供一个slave 用户

开启 binlog 需要在 `mysqldb/conf/my.cnf` 如下配置

```
[mysqld]
server_id=1
log-bin=master
binlog_format=row
default-character-set=utf8%  
```

在 `mysqldb/init/init.sql` 写入如下初始化数据

```
CREATE USER 'maxwell'@'%' IDENTIFIED BY 'XXXXXX';
GRANT ALL ON maxwell.* TO 'maxwell'@'%';
GRANT SELECT, REPLICATION CLIENT, REPLICATION SLAVE ON *.* TO 'maxwell'@'%';

CREATE DATABASE test;
USE test;
CREATE TABLE testtable (
    `id` varchar(128),
    `age` varchar(128)
);
```

这个文件是 mysql docker 容器运行起来后执行的sql语句， 其中包括创建 maxwell 账户，还包括了创建我们测试的数据库，数据表。

最后关于 mysql 的 docker-compose 部分如下

```
  mysql:
    image: mysql:5.7
    ports:
        - "3306:3306"
        - "33060:33060"
    volumes:
        - ./mysqldb/conf:/etc/mysql/conf.d
        - ./mysqldb/init:/docker-entrypoint-initdb.d
        
        # 如果需要数据持久化，需要把容器中的数据目录挂载到宿主机中
        # - ./mysqldb/data:/var/lib/mysql

    # 提升容器权限，确保权限允许
    privileged: true
    # 环境变量
    environment:
        # mysql密码
        - MYSQL_ROOT_PASSWORD=123456
    container_name: "Kafka_mysql"
```

### maxwell

maxwell 就只需要一个启动脚本 `maxwell/start.sh`，根据自身情况修改这个脚本。

> 参考文档 http://maxwells-daemon.io/quickstart/

```
#!/bin/bash

# 这个延时是为了等待 mysql 初始化完毕，如果失败，请手动重启maxwell
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
    --kafka.bootstrap.servers=$KAFKA_SERVER \
    --filter='exclude: *.*, include: test.*' \
    --kafka_topic=$KAFKA_TOPIC
```

maxwell docker-compose.yml 部分

```
  maxwell:
    # mysql kafka 中间件

    image: zendesk/maxwell:latest
    container_name: "maxwell"
    volumes:
        # 挂载我们的启动脚本
        - ./maxwell:/script

    # 连接 kafka 和 mysql 容器
    links:
      - kafka:kafka
      - mysql:mysql
    
    # 容器启动命令就是执行我们的脚本
    command: sh /script/start.sh
```

### neo4j 图数据库

相关文件：

```
neo4jdb
└── plugins
    ├── apoc-3.4.0.3-all.jar
    ├── mysql-connector-java-5.1.21.jar
    └── mysql-connector-java-8.0.11.zip
```

里面主要是apoc 插件，用于直接导入mysql数据，如果用不上可以不放。

docker-compose.yml 相关部分

```
  neo4j:
    image: neo4j:3.4
    links:
        - mysql:mysql
    ports:
        - 7474:7474
        - 7687:7687
    volumes:
        - ./neo4jdb/plugins:/var/lib/neo4j/plugins
        # 如果需要数据持久化，可以挂载数据目录到宿主机。
        # - ./neo4jdb/data:/data
    environment:
        # 用户名/密码
        - NEO4J_AUTH=neo4j/123456
    container_name: "neo4j"
```

## 数据同步引擎开发

整个代码逻辑是循环不断从kafka读取mysql增量数据， 然后进行格式解析，生成对应的 neo4j cypher 语句，通过相关API执行cypher语句，把数据同步到neo4j中。

不同语言有不同的neo4j驱动。这里并不使用 neo4j 驱动，而是使用适用性更加广泛的 neo4j restful API， 可以类似请求网页一样操作neo4j。

[neo4j restful API document](https://neo4j.com/docs/http-api/3.5/)

这里为了方便理解，使用 python 实现

[点击查看完整的python脚本](pythonSync/datasync.py)

### kafka 信息读取部分

使用 kafka-python 包官方案例

```
from kafka import KafkaConsumer

# value_deserializer 参数可以帮我们直接把json字符串转字典，方便后续处理
consumer = KafkaConsumer('mysqlsource',value_deserializer=lambda m: json.loads(m.decode('utf8')),bootstrap_servers=['localhost:9092'])

for msg in consumer:
    print(msg)
```

使用request库调用 [restful API](https://neo4j.com/docs/http-api/3.5/) 执行cypher语句例子

```


ua = requests.Session()
uname_pwd = uname + ":" + pwd
# 编码认证信息 
# uname_pwd ==> base64 encode str
encodeUnamePWD = str(base64.b64encode(uname_pwd.encode('utf-8')), 'utf-8')
# authStr字符串格式为 "Basic <uname:pwd>的base64编码"
authStr = 'Basic {}'.format(encodeUnamePWD)

# 认证信息写入默认请求头
ua.headers.update({
   "Authorization": authStr,
   "Accept": "application/json; charset=UTF-8"
})

# 请求实例
NEO4J_URL = 'http://localhost:7474/'
crypher = ”Create （n:Person{name="Lisa"})"

payload = {
   "statements": [{
      "statement": cypher
   }]
}
res = ua.post(NEO4J_URL + "db/data/transaction/commit", json=payload)
print(res.text)
```

剩下的就是自行补充中间部分（如解析kafka的数据，组合生成cypher语句） 就行。具体实现可以查看[完整文件](pythonSync/datasync.py)