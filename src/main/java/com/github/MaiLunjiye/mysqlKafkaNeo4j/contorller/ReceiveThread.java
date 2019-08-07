package com.github.MaiLunjiye.mysqlKafkaNeo4j.contorller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.network.KafkaChannel;
import org.apache.kafka.common.protocol.types.Field;
import org.neo4j.driver.v1.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ReceiveThread implements Runnable {

    private KafkaConsumer<String,String> consumer;
    private Driver neo4jDriver;

    public ReceiveThread(Properties kafkaProperties, String topic, String Neo4jUri, String Neo4jUname, String Neo4jPwd) {
        consumer = new KafkaConsumer<String, String>(kafkaProperties);
        consumer.subscribe(Arrays.asList(topic));
        log.debug(Neo4jUri + " " + Neo4jUname + " " + Neo4jPwd);
        neo4jDriver = GraphDatabase.driver(
                Neo4jUri, AuthTokens.basic(Neo4jUname, Neo4jPwd),
                Config.build()
                        .withMaxConnectionPoolSize(50)
                        .withConnectionAcquisitionTimeout(5, TimeUnit.SECONDS)
                        .toConfig()
        );
    }

    public void run() {
        try (Session session = neo4jDriver.session()){
            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord record : records) {
                    if (record != null) {
                        handing(record, session);
                    }
                }
            }
        } finally {
            consumer.close();
            neo4jDriver.close();
        }



    }

    private void handing(ConsumerRecord record, Session session) {
        System.out.println(Thread.currentThread().getName() + record.value());
        /*
        {
            "database": "test",
            "table": "testtable",
            "type": "insert",
            "ts": 1564979340,
            "xid": 8237,
            "commit": true,
            "data": {
                "A": "A3",
                "B": "cB",
                "rel": "re1"
            }
        }
        */

        // 接下来解析json，判断一些条件，然后开neo4j链接构建对应数据。
        // 接下来解析json，判断一些条件，然后开neo4j链接构建对应数据。
        String opCypher="";
        Map valueMap = new HashMap();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree((String) record.value());
            if (root.path("type").asText("None").equals("insert") ) {
                opCypher = "merge(n:Node{name:$NAME1})-[:rel{rel:$rel}]->(n2:Node{name:$NAME2})";

                JsonNode dataNode = root.path("data");

                valueMap.put("NAME1", dataNode.path("A").asText());
                valueMap.put("NAME2", dataNode.path("B").asText());
                valueMap.put("rel", dataNode.path("rel").asText());
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            return;
        }


        try (Transaction tx = session.beginTransaction()) {
            tx.run(opCypher, valueMap);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
