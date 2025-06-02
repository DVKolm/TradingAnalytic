package com.example.ta.controller;

import com.example.ta.events.NavigationEvent;
import com.example.ta.domain.Trade;
import com.example.ta.domain.TradeStatistics;
import com.example.ta.domain.TradeStatus;
import com.example.ta.domain.TradeType;
import com.example.ta.service.TradeService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
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

    @FXML private Label tipTitleLabel;
    @FXML private Label tipContentLabel;

    private final TradeService tradeService;
    private final ApplicationEventPublisher eventPublisher;

    private Timeline tipRotationTimer;
    private int currentTipIndex = 0;

    private final List<TradingTip> tradingTips = Arrays.asList(
            new TradingTip(
                    "Ведите подробные записи",
                    "Записывайте не только цены входа и выхода, но и причины принятия решений. Это поможет вам анализировать свои ошибки и улучшать торговую стратегию."
            ),
            new TradingTip(
                    "Управляйте рисками",
                    "Никогда не рискуйте более чем 2-3% от вашего капитала в одной сделке. Хорошее управление рисками важнее прибыльности отдельных сделок."
            ),
            new TradingTip(
                    "Анализируйте свою статистику",
                    "Регулярно просматривайте свою торговую статистику. Выявляйте закономерности в успешных и неуспешных сделках."
            ),
            new TradingTip(
                    "Не торгуйте на эмоциях",
                    "Эмоциональные решения часто приводят к убыткам. Всегда следуйте своему торговому плану и стратегии."
            ),
            new TradingTip(
                    "Изучайте рынок постоянно",
                    "Финансовые рынки постоянно меняются. Уделяйте время изучению новых инструментов и стратегий."
            ),
            new TradingTip(
                    "Диверсифицируйте портфель",
                    "Не концентрируйте все средства в одном активе или секторе. Распределение рисков поможет сохранить капитал."
            )
    );

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация WelcomeDashboardController");

        updateWelcomeTime();

        loadQuickStatistics();

        loadRecentTrades();

        setupTipRotation();

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

    private void setupTipRotation() {
        showCurrentTip();

        tipRotationTimer = new Timeline(new KeyFrame(Duration.seconds(10), e -> {
            currentTipIndex = (currentTipIndex + 1) % tradingTips.size();
            showCurrentTip();
        }));
        tipRotationTimer.setCycleCount(Timeline.INDEFINITE);
        tipRotationTimer.play();
    }

    private void showCurrentTip() {
        TradingTip currentTip = tradingTips.get(currentTipIndex);
        tipTitleLabel.setText(currentTip.getTitle());
        tipContentLabel.setText(currentTip.getContent());
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

    public void shutdown() {
        if (tipRotationTimer != null) {
            tipRotationTimer.stop();
        }
        log.info("WelcomeDashboardController завершен");
    }

    private static class TradingTip {
        private final String title;
        private final String content;

        public TradingTip(String title, String content) {
            this.title = title;
            this.content = content;
        }

        public String getTitle() {
            return title;
        }

        public String getContent() {
            return content;
        }
    }
}