package com.github.MaiLunjiye.mysqlKafkaNeo4j.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Slf4j
@Data
public class YamlConfig {
    private Map<String ,String > neo4jDatasource;
    private Map<String,String> kafkaConsumer;
    private String topic;
    private int kafkaPartitions;

    private Properties kafkaConsumerProperties = new Properties();

    public Properties getKafkaConsumerProperties() {
        Set kafkaConsumerSet =  kafkaConsumer.keySet();
        Iterator<String> kafkaConsumerSetIterrator = kafkaConsumerSet.iterator();

        while(kafkaConsumerSetIterrator.hasNext()) {
            String key = kafkaConsumerSetIterrator.next();
            String value = kafkaConsumer.get(key);
            kafkaConsumerProperties.put(key, value);
        }
        return kafkaConsumerProperties;
    }
}
