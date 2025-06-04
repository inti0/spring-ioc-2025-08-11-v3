package com.ll.global.testJackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Configuration;

@Configuration
public class TestJacksonConfig {
    @Bean
    public JavaTimeModule testBaseJavaTimeModule() {
        return new JavaTimeModule();
    }

    @Bean
    public ObjectMapper testBaseObjectMapper(JavaTimeModule testBaseJavaTimeModule) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(testBaseJavaTimeModule);
        return objectMapper;
    }
}