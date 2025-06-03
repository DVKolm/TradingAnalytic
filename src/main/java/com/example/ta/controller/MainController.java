package com.example.ta.controller;

import com.example.ta.config.SpringFXMLLoader;
import com.example.ta.domain.Trade;
import com.example.ta.events.NavigationEvent;
import com.example.ta.service.TelegramSettingsService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class MainController implements Initializable {

    @FXML
    private Button homeButton;
    @FXML
    private Button tradesListButton;
    @FXML
    private Button addTradeButton;
    @FXML
    private Button statisticsButton;
    @FXML
    private Button positionCalculatorButton;
    @FXML
    private Button averagingCalculatorButton;
    @FXML
    private Button refreshButton;

    @FXML
    private StackPane contentArea;

    @FXML
    private Label statusLabel;
    @FXML
    private Label currentTimeLabel;
    @FXML
    private Label versionLabel;
    @FXML
    private Label userInfoLabel;
    @FXML
    private Label telegramStatusLabel;

    private final TelegramSettingsService telegramSettingsService;
    private final SpringFXMLLoader springFXMLLoader;
    private final NewsPanel newsPanel; // Добавляем зависимость

    private Button currentActiveButton;
    private List<Button> navigationButtons;
    private Timeline timelineTimer;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация MainController");

        navigationButtons = List.of(homeButton, tradesListButton, addTradeButton,
                statisticsButton, positionCalculatorButton, averagingCalculatorButton);

        setupButtonHoverEffects();
        setupTimeUpdater();
        showHome();
        updateStatus("Приложение запущено");

        log.info("MainController инициализирован");

        updateTelegramStatus();
        Timeline telegramStatusUpdater = new Timeline(new KeyFrame(Duration.seconds(30), e -> updateTelegramStatus()));
        telegramStatusUpdater.setCycleCount(Timeline.INDEFINITE);
        telegramStatusUpdater.play();
    }

    private void updateTelegramStatus() {
        if (telegramStatusLabel != null && telegramSettingsService != null) {
            try {
                String status = telegramSettingsService.getTelegramStatus();
                String displayText = getTelegramDisplayText(status);
                String style = getTelegramStatusStyle(status);

                telegramStatusLabel.setText(displayText);
                telegramStatusLabel.setStyle(style);

            } catch (Exception e) {
                telegramStatusLabel.setText("📱 Telegram: ❌ Ошибка");
                telegramStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                log.warn("Ошибка при обновлении статуса Telegram", e);
            }
        }
    }

    private String getTelegramDisplayText(String status) {
        return switch (status) {
            case "Активно" -> "📱 Telegram: ✅ Активно";
            case "Отключено" -> "📱 Telegram: ⏸️ Отключено";
            case "Не настроено" -> "📱 Telegram: ⚙️ Не настроено";
            case "Неполные настройки" -> "📱 Telegram: ⚠️ Неполные настройки";
            default -> "📱 Telegram: ❓ " + status;
        };
    }

    private String getTelegramStatusStyle(String status) {
        return switch (status) {
            case "Активно" -> "-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 12px;";
            case "Отключено" -> "-fx-text-fill: #f39c12; -fx-font-weight: bold; -fx-font-size: 12px;";
            case "Не настроено" -> "-fx-text-fill: #95a5a6; -fx-font-weight: normal; -fx-font-size: 12px;";
            case "Неполные настройки" -> "-fx-text-fill: #e74c3c; -fx-font-weight: normal; -fx-font-size: 12px;";
            default -> "-fx-text-fill: #e74c3c; -fx-font-weight: normal; -fx-font-size: 12px;";
        };
    }

    @FXML
    private void showTelegramSettings() {
        loadContent("/com/example/ta/telegram-settings.fxml");
        setActiveButton(null);
        updateStatus("Настройки Telegram");
    }

    @EventListener
    public void onNavigationEvent(NavigationEvent event) {
        log.info("Получено событие навигации: {}", event.getNavigationType());

        switch (event.getNavigationType()) {
            case ADD_TRADE -> showAddTrade();
            case VIEW_TRADES -> showTradesList();
            case STATISTICS -> showStatistics();
            case HOME -> showHome();
            case POSITION_CALCULATOR -> showPositionCalculator();
            case VIEW_TRADE_DETAILS -> showTradeDetails(event.getTrade());
            case EDIT_TRADE -> showEditTrade(event.getTrade());
        }
    }

    private void showEditTrade(Trade trade) {
        try {
            log.info("Показываем форму редактирования сделки: {}", trade.getId());

            FXMLLoader loader = springFXMLLoader.getLoader("/com/example/ta/trade-form-view.fxml");
            Node content = loader.load();

            TradeFormController controller = loader.getController();
            controller.setEditMode(trade);

            // Создаем контейнер с новостной панелью справа
            HBox mainContainer = createContentWithNews(content);

            contentArea.getChildren().clear();
            contentArea.getChildren().add(mainContainer);

            setActiveButton(addTradeButton);
            updateStatus("Редактирование сделки: " + trade.getAssetName());
            log.info("Загружен контент для редактирования: /com/example/ta/trade-form-view.fxml");

        } catch (Exception e) {
            log.error("Ошибка при загрузке формы редактирования", e);
            showError("Ошибка при загрузке формы редактирования: " + e.getMessage());
        }
    }

    private void showTradeDetails(Trade trade) {
        try {
            log.info("Показываем детали сделки: {}", trade.getId());

            FXMLLoader loader = springFXMLLoader.getLoader("/com/example/ta/trade-details.fxml");
            Node content = loader.load();

            TradeDetailsController controller = loader.getController();
            controller.setTrade(trade);

            // Создаем контейнер с новостной панелью справа
            HBox mainContainer = createContentWithNews(content);

            contentArea.getChildren().clear();
            contentArea.getChildren().add(mainContainer);

            updateStatus("Отображаются детали сделки: " + trade.getAssetName());
            log.info("Загружен контент: /com/example/ta/trade-details.fxml");

        } catch (Exception e) {
            log.error("Ошибка при загрузке деталей сделки", e);
            showError("Ошибка при загрузке деталей сделки: " + e.getMessage());
        }
    }

    private void setupButtonHoverEffects() {
        for (Button button : navigationButtons) {
            if (button == homeButton) continue;

            String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #6c757d; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-radius: 12; -fx-border-color: transparent; -fx-cursor: hand; -fx-padding: 12 20 12 20;";
            String hoverStyle = "-fx-background-color: #e9ecef; -fx-text-fill: #495057; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-radius: 12; -fx-border-color: transparent; -fx-cursor: hand; -fx-padding: 12 20 12 20;";

            button.setOnMouseEntered(e -> {
                if (button != currentActiveButton) {
                    button.setStyle(hoverStyle);
                }
            });

            button.setOnMouseExited(e -> {
                if (button != currentActiveButton) {
                    button.setStyle(inactiveStyle);
                }
            });
        }

        homeButton.setOnMouseEntered(e -> {
            if (homeButton != currentActiveButton) {
                homeButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-radius: 12; -fx-border-color: transparent; -fx-cursor: hand; -fx-padding: 12 20 12 20; -fx-effect: dropshadow(gaussian, rgba(52,152,219,0.2), 4, 0, 0, 1);");
            }
        });
        homeButton.setOnMouseExited(e -> {
            if (homeButton != currentActiveButton) {
                homeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #6c757d; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-radius: 12; -fx-border-color: transparent; -fx-cursor: hand; -fx-padding: 12 20 12 20;");
            }
        });

        refreshButton.setOnMouseEntered(e ->
                refreshButton.setStyle("-fx-background-color: #e9ecef; -fx-text-fill: #495057; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-radius: 12; -fx-border-color: transparent; -fx-cursor: hand; -fx-padding: 12 20 12 20;"));
        refreshButton.setOnMouseExited(e ->
                refreshButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #6c757d; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-radius: 12; -fx-border-color: transparent; -fx-cursor: hand; -fx-padding: 12 20 12 20;"));
    }

    private void setupTimeUpdater() {
        timelineTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateCurrentTime()));
        timelineTimer.setCycleCount(Timeline.INDEFINITE);
        timelineTimer.play();

        updateCurrentTime();
    }

    private void updateCurrentTime() {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        currentTimeLabel.setText(currentTime);
    }

    private void setActiveButton(Button activeButton) {
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #6c757d; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-radius: 12; -fx-border-color: transparent; -fx-cursor: hand; -fx-padding: 12 20 12 20;";
        String activeStyle = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-radius: 12; -fx-border-color: transparent; -fx-cursor: hand; -fx-padding: 12 20 12 20; -fx-effect: dropshadow(gaussian, rgba(52,152,219,0.2), 4, 0, 0, 1);";

        for (Button button : navigationButtons) {
            if (button == activeButton) {
                button.setStyle(activeStyle);
            } else {
                button.setStyle(inactiveStyle);
            }
        }

        currentActiveButton = activeButton;
    }

    private void loadContent(String fxmlPath) {
        try {
            Node content = springFXMLLoader.load(fxmlPath);

            // Создаем контейнер с новостной панелью справа
            HBox mainContainer = createContentWithNews(content);

            contentArea.getChildren().clear();
            contentArea.getChildren().add(mainContainer);
            log.info("Загружен контент: {}", fxmlPath);
        } catch (Exception e) {
            log.error("Ошибка при загрузке контента: {}", fxmlPath, e);
            updateStatus("Ошибка при загрузке: " + fxmlPath);
        }
    }

    /**
     * Создает контейнер с основным контентом слева и новостной панелью справа
     */
    private HBox createContentWithNews(Node mainContent) {
        HBox container = new HBox();
        container.setSpacing(16);
        container.setStyle("-fx-padding: 16;");

        // Основной контент занимает всё доступное пространство
        HBox.setHgrow(mainContent, Priority.ALWAYS);

        // Клонируем новостную панель для безопасности
        NewsPanel newsClone = new NewsPanel(newsPanel.getTelegramNewsService());

        container.getChildren().addAll(mainContent, newsClone);

        return container;
    }

    @FXML
    private void showHome() {
        log.info("Показываем главную страницу");
        setActiveButton(homeButton);
        loadContent("/com/example/ta/welcome-page.fxml");
        updateStatus("Главная страница");
    }

    @FXML
    private void showTradesList() {
        log.info("Показываем список сделок");
        setActiveButton(tradesListButton);
        loadContent("/com/example/ta/trades-list-view.fxml");
        updateStatus("Список сделок");
    }

    @FXML
    private void showAddTrade() {
        log.info("Показываем форму добавления сделки");
        setActiveButton(addTradeButton);
        loadContent("/com/example/ta/trade-form-view.fxml");
        updateStatus("Форма добавления новой сделки");
    }

    @FXML
    private void showStatistics() {
        log.info("Показываем статистику");
        setActiveButton(statisticsButton);
        loadContent("/com/example/ta/statistics-dashboard.fxml");
        updateStatus("Статистика торгов");
    }

    @FXML
    private void showPositionCalculator() {
        log.info("Показываем калькулятор позиции");
        setActiveButton(positionCalculatorButton);
        loadContent("/com/example/ta/position-calculator.fxml");
        updateStatus("Калькулятор позиции");
    }

    @FXML
    private void showAveragingCalculator() {
        log.info("Показываем калькулятор усреднения");
        setActiveButton(averagingCalculatorButton);
        loadContent("/com/example/ta/averaging-calculator.fxml");
        updateStatus("Калькулятор усреднения позиции");
    }

    @FXML
    private void refreshCurrentView() {
        log.info("Обновляем текущий вид");

        // Обновляем новостную панель
        if (newsPanel != null) {
            newsPanel.refresh();
        }

        if (currentActiveButton == homeButton) {
            showHome();
        } else if (currentActiveButton == tradesListButton) {
            showTradesList();
        } else if (currentActiveButton == addTradeButton) {
            showAddTrade();
        } else if (currentActiveButton == statisticsButton) {
            showStatistics();
        } else if (currentActiveButton == positionCalculatorButton) {
            showPositionCalculator();
        } else if (currentActiveButton == averagingCalculatorButton) {
            showAveragingCalculator();
        }

        updateStatus("Контент обновлен");
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
        log.debug("Статус обновлен: {}", message);
    }

    public void navigateToAddTrade() {
        showAddTrade();
    }

    public void navigateToTradesList() {
        showTradesList();
    }

    public void navigateToStatistics() {
        showStatistics();
    }

    public void navigateToPositionCalculator() {
        showPositionCalculator();
    }

    public void navigateToAveragingCalculator() {
        showAveragingCalculator();
    }

    public void updateStatusFromExternal(String message) {
        updateStatus(message);
    }

    public void shutdown() {
        if (timelineTimer != null) {
            timelineTimer.stop();
        }
        if (newsPanel != null) {
            newsPanel.shutdown();
        }
        log.info("MainController завершен");
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}