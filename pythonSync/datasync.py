from kafka import KafkaConsumer
import json
import requests
import base64

KAFKA_TOPIC = 'mysqlsource'
KAFKA_BOOTSTRAP_SERVERS = ['localhost:9092']

NEO4J_URL = 'http://localhost:7474/'
NEO4J_UNAME = 'neo4j'
NEO4J_PWD = '123456'


def neo4jInit(uname, pwd, testUrl='http://localhost:7474/'):
    """
    初始化连接neo4j的会话对象
    API 文档 https://neo4j.com/docs/http-api/3.5/security/
    uname str 用户名
    pwd str 密码
    """

    ua = requests.Session()
    uname_pwd = uname + ":" + pwd
    # 编码认证信息
    # uname_pwd ==> base64 encode str
    encodeUnamePWD = str(base64.b64encode(uname_pwd.encode('utf-8')), 'utf-8')
    authStr = 'Basic {}'.format(encodeUnamePWD)
    ua.headers.update({
        "Authorization": authStr,
        "Accept": "application/json; charset=UTF-8"
    })

    # 测试
    res = ua.get(testUrl + "user/neo4j")
    if res.status_code != 200:
        print(res.text)
        exit()
    else:
        print("neo4j ready")
    
    return ua

def kafkaInit(topic, bootstrap_servers):
    """
    topic str: kafka Topic
    bootstrap_servers []string : kafka server url
    """
    # 初始化 Kafka 消费者
    consumer = KafkaConsumer(
        topic,
        value_deserializer=lambda m: json.loads(m.decode('utf8')),
        bootstrap_servers=bootstrap_servers)
    
    return consumer

def opInsertTestTable(dataDict):
    """
    同步 testtabel 表新插入的数据
    dataDict dict: 数据值
    """
    # 通过 restfull API 发送cypher 语句,把数据同步到neo4j
    dataDict = valueDict['data']
    cypher = "Merge (n:Person{id:\"%s\", age:\"%s\"})" % (dataDict['id'], dataDict['age'])
    payload = {
        "statements": [{
            "statement": cypher
        }]
    }
    res = ua.post(NEO4J_URL + "db/data/transaction/commit", json=payload)
    print(res.text)


if __name__ == "__main__":
    ua = neo4jInit(NEO4J_UNAME, NEO4J_PWD)
    consumer = kafkaInit(KAFKA_TOPIC, KAFKA_BOOTSTRAP_SERVERS)

    for message in consumer:
        # 进行一些简单判断
        valueDict = message.value
        # print(valueDict)
        '''
        {
        'database': 'test', 
        'table': 'testtable',
        'type': 'insert',
        'ts': 1566014699,
        'xid': 9695,
        'commit': True,
        'data': {'id': 'A45', 'age': '1744'}
        }
        '''

        if valueDict['table'] == "testtable" and valueDict['type']=='insert':
            opInsertTestTable(valueDict['data'])
        # else if .....
