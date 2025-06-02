package com.example.ta.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
@EnableTransactionManagement(proxyTargetClass = true)
public class ApplicationConfig {
}

