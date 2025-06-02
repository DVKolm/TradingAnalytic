package com.example.ta.controller;

import com.example.ta.events.NavigationEvent;
import com.example.ta.config.SpringFXMLLoader;
import com.example.ta.domain.Trade;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

    @FXML private Button homeButton;
    @FXML private Button tradesListButton;
    @FXML private Button addTradeButton;
    @FXML private Button statisticsButton;
    @FXML private Button positionCalculatorButton;
    @FXML private Button refreshButton;

    @FXML private StackPane contentArea;

    @FXML private Label statusLabel;
    @FXML private Label currentTimeLabel;
    @FXML private Label versionLabel;
    @FXML private Label userInfoLabel;

    private final SpringFXMLLoader springFXMLLoader;

    private Button currentActiveButton;
    private List<Button> navigationButtons;

    private Timeline timelineTimer;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация MainController");

        navigationButtons = List.of(homeButton, tradesListButton, addTradeButton, statisticsButton, positionCalculatorButton);

        setupButtonHoverEffects();

        setupTimeUpdater();

        showHome();

        updateStatus("Приложение запущено");

        log.info("MainController инициализирован");
    }

    /**
     * Обработчик событий навигации от других контроллеров
     */
    @EventListener
    public void onNavigationEvent(NavigationEvent event) {
        log.info("Получено событие навигации: {}", event.getNavigationType());

        switch (event.getNavigationType()) {
            case ADD_TRADE -> showAddTrade();
            case VIEW_TRADES -> showTradesList();
            case STATISTICS -> showStatistics();
            case HOME -> showHome();
            case POSITION_CALCULATOR -> showPositionCalculator(); // НОВЫЙ СЛУЧАЙ
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

            contentArea.getChildren().clear();
            contentArea.getChildren().add(content);

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

            contentArea.getChildren().clear();
            contentArea.getChildren().add(content);

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

            String inactiveStyle = "-fx-background-color: #f8f9fa; -fx-text-fill: #6c757d; -fx-font-size: 14px; -fx-font-weight: 600; -fx-background-radius: 0; -fx-border-color: #dee2e6; -fx-border-width: 0 1 0 0; -fx-cursor: hand;";
            String hoverStyle = "-fx-background-color: #e9ecef; -fx-text-fill: #495057; -fx-font-size: 14px; -fx-font-weight: 600; -fx-background-radius: 0; -fx-border-color: #dee2e6; -fx-border-width: 0 1 0 0; -fx-cursor: hand;";

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
                homeButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: 600; -fx-background-radius: 0; -fx-border-color: transparent; -fx-cursor: hand;");
            }
        });
        homeButton.setOnMouseExited(e -> {
            if (homeButton != currentActiveButton) {
                homeButton.setStyle("-fx-background-color: #f8f9fa; -fx-text-fill: #6c757d; -fx-font-size: 14px; -fx-font-weight: 600; -fx-background-radius: 0; -fx-border-color: #dee2e6; -fx-border-width: 0 1 0 0; -fx-cursor: hand;");
            }
        });

        refreshButton.setOnMouseEntered(e ->
                refreshButton.setStyle("-fx-background-color: #e9ecef; -fx-text-fill: #495057; -fx-font-size: 14px; -fx-font-weight: 600; -fx-background-radius: 0; -fx-border-color: #dee2e6; -fx-border-width: 0 0 0 1; -fx-cursor: hand;"));
        refreshButton.setOnMouseExited(e ->
                refreshButton.setStyle("-fx-background-color: #f8f9fa; -fx-text-fill: #6c757d; -fx-font-size: 14px; -fx-font-weight: 600; -fx-background-radius: 0; -fx-border-color: #dee2e6; -fx-border-width: 0 0 0 1; -fx-cursor: hand;"));
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
        String inactiveStyle = "-fx-background-color: #f8f9fa; -fx-text-fill: #6c757d; -fx-font-size: 14px; -fx-font-weight: 600; -fx-background-radius: 0; -fx-border-color: #dee2e6; -fx-border-width: 0 1 0 0; -fx-cursor: hand;";
        String activeStyle = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: 600; -fx-background-radius: 0; -fx-border-color: transparent; -fx-cursor: hand;";

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
            contentArea.getChildren().clear();
            contentArea.getChildren().add(content);
            log.info("Загружен контент: {}", fxmlPath);
        } catch (Exception e) {
            log.error("Ошибка при загрузке контента: {}", fxmlPath, e);
            updateStatus("Ошибка при загрузке: " + fxmlPath);
        }
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
    private void refreshCurrentView() {
        log.info("Обновляем текущий вид");

        // Определяем какая кнопка активна и перезагружаем соответствующий контент
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

    public void updateStatusFromExternal(String message) {
        updateStatus(message);
    }

    // Освобождаем ресурсы при закрытии
    public void shutdown() {
        if (timelineTimer != null) {
            timelineTimer.stop();
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