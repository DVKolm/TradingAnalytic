package com.example.ta.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Добавляем только нужные модули
        mapper.registerModule(new JavaTimeModule());

        // НЕ используем findAndRegisterModules() - он автоматически подключает JAXB
        // mapper.findAndRegisterModules(); // <-- УБИРАЕМ ЭТО

        return mapper;
    }
}
