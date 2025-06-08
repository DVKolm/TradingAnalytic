package com.example.ta.controller;

import com.example.ta.events.NavigationEvent;
import com.example.ta.domain.trading.Trade;
import com.example.ta.domain.trading.TradeStatistics;
import com.example.ta.domain.trading.TradeStatus;
import com.example.ta.domain.trading.TradeType;
import com.example.ta.service.TradeService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class WelcomeDashboardController implements Initializable {

    @FXML private Label welcomeTimeLabel;

    @FXML private Label totalTradesLabel;
    @FXML private Label totalProfitLabel;
    @FXML private Label winRateLabel;

    @FXML private VBox recentTradesContainer;

    private final TradeService tradeService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация WelcomeDashboardController");

        updateWelcomeTime();

        loadQuickStatistics();

        loadRecentTrades();

        log.info("WelcomeDashboardController инициализирован");
    }

    private void updateWelcomeTime() {
        LocalDateTime now = LocalDateTime.now();
        String greeting = "";
        int hour = now.getHour();

        if (hour >= 6 && hour < 12) {
            greeting = "Доброе утро!";
        } else if (hour >= 12 && hour < 18) {
            greeting = "Добрый день!";
        } else if (hour >= 18 && hour < 23) {
            greeting = "Добрый вечер!";
        } else {
            greeting = "Доброй ночи!";
        }

        String timeText = greeting + " Сегодня " + now.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        welcomeTimeLabel.setText(timeText);
    }

    private void loadQuickStatistics() {
        try {
            TradeStatistics stats = tradeService.getQuickStatistics();

            totalTradesLabel.setText(String.valueOf(stats.getTotalTrades()));

            BigDecimal totalProfit = stats.getTotalProfit();
            if (totalProfit.compareTo(BigDecimal.ZERO) >= 0) {
                totalProfitLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
                totalProfitLabel.setText(String.format("%.2f", totalProfit) + " $");
            } else {
                totalProfitLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
                totalProfitLabel.setText("-$ " + String.format("%.2f", totalProfit.abs()));
            }

            BigDecimal winRate = stats.getWinRate();
            winRateLabel.setText(String.format("%.1f%%", winRate));

            if (winRate.doubleValue() >= 60.0) {
                winRateLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            } else if (winRate.doubleValue() >= 40.0) {
                winRateLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
            } else {
                winRateLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            }

            log.info("Статистика загружена: {} сделок, винрейт: {}%, прибыль: {}",
                    stats.getTotalTrades(), winRate, totalProfit);

        } catch (Exception e) {
            log.error("Ошибка при загрузке статистики", e);
            totalTradesLabel.setText("Ошибка");
            totalProfitLabel.setText("Ошибка");
            winRateLabel.setText("Ошибка");
        }
    }

    private void loadRecentTrades() {
        try {
            List<Trade> recentTrades = tradeService.findRecentTrades(5);

            recentTradesContainer.getChildren().clear();

            if (recentTrades.isEmpty()) {
                Label noTradesLabel = new Label("Нет сделок для отображения");
                noTradesLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d; -fx-text-alignment: center;");
                recentTradesContainer.getChildren().add(noTradesLabel);
            } else {
                for (Trade trade : recentTrades) {
                    VBox tradeItem = createRecentTradeItem(trade);
                    recentTradesContainer.getChildren().add(tradeItem);
                }
            }

        } catch (Exception e) {
            log.error("Ошибка при загрузке последних сделок", e);
            recentTradesContainer.getChildren().clear();
            Label errorLabel = new Label("Ошибка при загрузке сделок");
            errorLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #e74c3c;");
            recentTradesContainer.getChildren().add(errorLabel);
        }
    }

    private VBox createRecentTradeItem(Trade trade) {
        VBox tradeBox = new VBox(4.0);
        tradeBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8; -fx-padding: 12; -fx-border-color: #dee2e6; -fx-border-radius: 8;");

        HBox headerBox = new HBox();
        headerBox.setSpacing(8.0);

        Label assetLabel = new Label(trade.getAssetName());
        assetLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label dateLabel = new Label(trade.getTradeDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        dateLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        headerBox.getChildren().addAll(assetLabel, spacer, dateLabel);

        HBox detailsBox = new HBox();
        detailsBox.setSpacing(8.0);

        String typeText = trade.getTradeType() == TradeType.LONG ? "Покупка" : "Продажа";
        Label typeLabel = new Label(typeText);
        typeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6c757d;");

        String statusText = trade.getStatus() == TradeStatus.OPEN ? "Открыта" : "Закрыта";
        Label statusLabel = new Label(statusText);
        if (trade.getStatus() == TradeStatus.OPEN) {
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #f39c12; -fx-font-weight: 600;");
        } else {
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #27ae60; -fx-font-weight: 600;");
        }

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, javafx.scene.layout.Priority.ALWAYS);

        detailsBox.getChildren().addAll(typeLabel, spacer2, statusLabel);

        if (trade.getProfitLoss() != null) {
            Label profitLabel = new Label();
            BigDecimal profit = trade.getProfitLoss();

            if (profit.compareTo(BigDecimal.ZERO) >= 0) {
                profitLabel.setText("+$" + String.format("%.2f", profit));
                profitLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            } else {
                profitLabel.setText("-$" + String.format("%.2f", profit.abs()));
                profitLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            }

            tradeBox.getChildren().addAll(headerBox, detailsBox, profitLabel);
        } else {
            tradeBox.getChildren().addAll(headerBox, detailsBox);
        }

        return tradeBox;
    }

    // Быстрые действия
    @FXML
    private void quickAddTrade() {
        log.info("Быстрое добавление сделки");
        eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.ADD_TRADE));
    }

    @FXML
    private void quickViewTrades() {
        log.info("Быстрый просмотр сделок");
        eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.VIEW_TRADES));
    }

    @FXML
    private void quickStatistics() {
        log.info("Быстрая статистика");
        eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.STATISTICS));
    }

    @FXML
    private void viewAllTrades() {
        log.info("Просмотр всех сделок");
        eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.VIEW_TRADES));
    }
}