package com.example.ta;

import com.example.ta.config.SpringFXMLLoader;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        log.info("Инициализация Spring Boot контекста...");
        springContext = SpringApplication.run(TradingAnalyticsApplication.class);
        log.info("Spring Boot контекст успешно инициализирован");
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            log.info("Запуск JavaFX приложения...");

            SpringFXMLLoader loader = springContext.getBean(SpringFXMLLoader.class);
            Scene scene = new Scene(loader.load("/com/example/ta/main-view.fxml"), 1400, 900);

            primaryStage.setTitle("Trading Analytics - Система анализа торговли");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(800);

            // Обработка закрытия приложения
            primaryStage.setOnCloseRequest(event -> {
                log.info("Закрытие приложения...");
                Platform.exit();
                springContext.close();
                System.exit(0);
            });

            primaryStage.show();
            log.info("JavaFX приложение успешно запущено");

        } catch (Exception e) {
            log.error("Ошибка запуска JavaFX приложения", e);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        log.info("Остановка приложения...");
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }
}