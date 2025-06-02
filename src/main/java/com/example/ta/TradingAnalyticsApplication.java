package com.example.ta;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(proxyBeanMethods = false)
public class TradingAnalyticsApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        System.setProperty("spring.aop.proxy-target-class", "true");

        Application.launch(JavaFxApplication.class, args);
    }
}
