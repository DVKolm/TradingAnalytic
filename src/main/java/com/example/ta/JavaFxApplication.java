
package com.example.ta;

import com.example.ta.config.SpringFXMLLoader;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;
    private TrayIcon trayIcon;
    private SystemTray systemTray;

    @Override
    public void init() {
        log.info("Инициализация Spring Boot контекста...");
        springContext = SpringApplication.run(TradingAnalyticsApplication.class);
        log.info("Spring Boot контекст успешно инициализирован");

        // 🔧 Устанавливаем implicit exit в false, чтобы приложение не завершалось при закрытии окна
        Platform.setImplicitExit(false);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        try {
            log.info("Запуск JavaFX приложения...");

            SpringFXMLLoader loader = springContext.getBean(SpringFXMLLoader.class);
            Scene scene = new Scene(loader.load("/com/example/ta/main-view.fxml"), 1400, 900);

            primaryStage.setTitle("Trading Analytics - Система анализа торговли");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(800);

            primaryStage.setMaximized(true);

            // Инициализация системного трея
            setupSystemTray();

            // 🔧 Новый обработчик закрытия - минимизация в трей
            primaryStage.setOnCloseRequest(event -> {
                event.consume(); // Отменяем стандартное закрытие
                hideToTray();
            });

            primaryStage.show();
            log.info("JavaFX приложение успешно запущено");

        } catch (Exception e) {
            log.error("Ошибка запуска JavaFX приложения", e);
            Platform.exit();
        }
    }

    /**
     * Настройка системного трея
     */
    private void setupSystemTray() {
        if (!SystemTray.isSupported()) {
            log.warn("SystemTray не поддерживается в данной системе");
            return;
        }

        systemTray = SystemTray.getSystemTray();

        // Загружаем иконку
        Image trayImage = loadTrayIcon();

        // Создаем всплывающее меню
        PopupMenu trayMenu = new PopupMenu();

        // Пункт "Закрыть полностью"
        MenuItem exitItem = new MenuItem("Закрыть приложение");
        exitItem.addActionListener(e -> exitApplication());

        trayMenu.add(exitItem);

        // Создаем иконку трея
        trayIcon = new TrayIcon(trayImage, "Trading Analytics", trayMenu);
        trayIcon.setImageAutoSize(true);

        // Обработчик двойного клика - показать приложение
        trayIcon.addActionListener(e -> showFromTray());

        try {
            systemTray.add(trayIcon);
            log.info("Системный трей успешно инициализирован");
        } catch (AWTException e) {
            log.error("Ошибка добавления иконки в системный трей", e);
        }
    }

    /**
     * Загружает иконку для системного трея
     */
    private Image loadTrayIcon() {
        try {
            // Пытаемся загрузить app.ico
            InputStream iconStream = getClass().getResourceAsStream("/app.ico");
            if (iconStream != null) {
                BufferedImage bufferedImage = ImageIO.read(iconStream);
                // 🔧 Проверяем, что изображение действительно загрузилось
                if (bufferedImage != null) {
                    return bufferedImage.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
                } else {
                    log.warn("app.ico найден, но не может быть прочитан ImageIO (возможно, неподдерживаемый формат ICO)");
                }
            } else {
                log.warn("app.ico не найден в ресурсах");
            }
        } catch (IOException e) {
            log.warn("Не удалось загрузить app.ico: {}", e.getMessage());
        }

        // Если не удалось загрузить иконку, создаем простую
        log.info("Создание дефолтной иконки для системного трея");
        return createDefaultIcon();
    }

    /**
     * Создает дефолтную иконку программно
     */
    private BufferedImage createDefaultIcon() {
        BufferedImage defaultIcon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = defaultIcon.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Рисуем фон
            g2d.setColor(new Color(52, 152, 219)); // Синий цвет
            g2d.fillRoundRect(1, 1, 14, 14, 4, 4);

            // Рисуем рамку
            g2d.setColor(new Color(41, 128, 185)); // Темнее синий
            g2d.drawRoundRect(1, 1, 14, 14, 4, 4);

            // 🔧 Правильное центрирование текста
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 8));

            String text = "TA";
            FontMetrics fm = g2d.getFontMetrics();

            // Вычисляем точные координаты для центрирования
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getAscent();

            // Центрируем относительно всей иконки (16x16)
            int x = (16 - textWidth) / 2;
            int y = (16 - textHeight) / 2 + textHeight;

            g2d.drawString(text, x, y);

        } finally {
            g2d.dispose();
        }

        return defaultIcon;
    }

    /**
     * Скрывает приложение в системный трей
     */
    private void hideToTray() {
        if (trayIcon == null) {
            log.warn("TrayIcon не инициализирован, выполняется обычное закрытие");
            exitApplication();
            return;
        }

        Platform.runLater(() -> {
            primaryStage.hide();
            log.info("Приложение свернуто в системный трей");

            // Показываем уведомление (опционально)
            try {
                trayIcon.displayMessage(
                        "Trading Analytics",
                        "Приложение свернуто в трей. Дважды кликните для восстановления.",
                        TrayIcon.MessageType.INFO
                );
            } catch (Exception e) {
                log.warn("Не удалось показать уведомление трея: {}", e.getMessage());
            }
        });
    }

    /**
     * Показывает приложение из системного трея
     */
    private void showFromTray() {
        Platform.runLater(() -> {
            if (primaryStage != null) {
                primaryStage.show();
                primaryStage.toFront();
                primaryStage.requestFocus();

                // Восстанавливаем из минимизированного состояния
                if (primaryStage.isIconified()) {
                    primaryStage.setIconified(false);
                }

                log.info("Приложение восстановлено из системного трея");
            }
        });
    }

    /**
     * Полное закрытие приложения
     */
    private void exitApplication() {
        log.info("Полное закрытие приложения...");

        // Удаляем иконку из трея
        if (systemTray != null && trayIcon != null) {
            try {
                systemTray.remove(trayIcon);
                log.info("Иконка удалена из системного трея");
            } catch (Exception e) {
                log.warn("Ошибка при удалении иконки из трея: {}", e.getMessage());
            }
        }

        // 🔧 Правильная последовательность закрытия
        Platform.runLater(() -> {
            try {
                if (springContext != null && springContext.isActive()) {
                    springContext.close();
                    log.info("Spring контекст закрыт");
                }
            } catch (Exception e) {
                log.error("Ошибка при закрытии Spring контекста", e);
            }

            Platform.exit();

            // Принудительное завершение, если что-то зависло
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    System.exit(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }

    @Override
    public void stop() {
        log.info("Вызван метод stop()...");

        if (springContext != null && springContext.isActive()) {
            try {
                springContext.close();
                log.info("Spring контекст закрыт в stop()");
            } catch (Exception e) {
                log.error("Ошибка при закрытии Spring контекста в stop()", e);
            }
        }
    }
}