package com.github.MaiLunjiye.mysqlKafkaNeo4j;

import com.github.MaiLunjiye.mysqlKafkaNeo4j.config.YamlConfig;
import com.github.MaiLunjiye.mysqlKafkaNeo4j.config.YamlParser;
import com.github.MaiLunjiye.mysqlKafkaNeo4j.contorller.ReceiveThread;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Application {

    public static void main(String[] args) {
        YamlConfig config = YamlParser.readValue("application.yml", YamlConfig.class);
        System.out.println(config.getTopic());
        System.out.println(config.getKafkaPartitions());

        // 多线程来进行消费者启动， partition来控制线程数
        int kafkaPartitions = config.getKafkaPartitions();
        ExecutorService executorService = Executors.newFixedThreadPool(kafkaPartitions);


        Properties properties = config.getKafkaConsumerProperties();

        Map<String, String > neo4jConfig = config.getNeo4jDatasource();
        ReceiveThread thread = new ReceiveThread(
                properties,
                config.getTopic(),
                neo4jConfig.get("uri"),
                neo4jConfig.get("username"),
                neo4jConfig.get("password")
        );
        for (int i = 0; i < kafkaPartitions; i++) {
            executorService.execute(thread);
        }

    }

}
