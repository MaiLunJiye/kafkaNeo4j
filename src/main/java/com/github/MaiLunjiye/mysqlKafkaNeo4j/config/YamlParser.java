package com.github.MaiLunjiye.mysqlKafkaNeo4j.config;




import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;

import static org.apache.kafka.common.requests.FetchMetadata.log;

@Slf4j
public class YamlParser implements Serializable {
    private static ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public static <T> T readValue(String yamlFilePath, Class<T> clazz) {
        URL resource = ClassLoader.getSystemClassLoader().getResource(yamlFilePath);

        if (resource == null) {
            log.error("yaml config file not found path is {}", yamlFilePath);
            throw new RuntimeException("config file not found");
        }

        try {
            return mapper.readValue(resource, clazz);
        } catch (IOException e) {
            log.error("exception is {}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }
}
