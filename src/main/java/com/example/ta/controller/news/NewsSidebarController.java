
package com.example.ta.controller.news;

import com.example.ta.domain.news.NewsMessage;
import com.example.ta.domain.news.SourceType;
import com.example.ta.repository.NewsMessageRepository;
import com.example.ta.service.TelegramNewsService;
import com.example.ta.service.TwitterNewsService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsSidebarController {

    private final NewsMessageRepository newsMessageRepository;
    private final TelegramNewsService telegramNewsService;
    private final TwitterNewsService twitterNewsService;

    @FXML
    private VBox mainContainer;
    @FXML
    private VBox telegramSection;
    @FXML
    private VBox twitterSection;
    @FXML
    private VBox telegramNewsContainer;
    @FXML
    private VBox twitterNewsContainer;
    @FXML
    private Label telegramHeader;
    @FXML
    private Label twitterHeader;
    @FXML
    private Button telegramToggleButton;
    @FXML
    private Button twitterToggleButton;
    @FXML
    private Button refreshButton;
    @FXML
    private ComboBox<String> newsLimitComboBox;

    private boolean telegramExpanded = true;
    private boolean twitterExpanded = true;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    @FXML
    private void initialize() {
        setupComponents();
        setupEventHandlers();
        loadNews();

        // Автообновление каждые 30 секунд
        startAutoRefresh();
    }

    private void setupComponents() {
        // Настройка ComboBox для лимита новостей
        if (newsLimitComboBox != null) {
            newsLimitComboBox.setItems(FXCollections.observableArrayList(
                    "10", "20", "50", "100"
            ));
            newsLimitComboBox.setValue("20");
        }

        // Стили для заголовков
        setupHeaderStyles();
    }

    private void setupHeaderStyles() {
        if (telegramHeader != null) {
            telegramHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
            telegramHeader.setStyle("-fx-text-fill: #0088cc;");
        }

        if (twitterHeader != null) {
            twitterHeader.setFont(Font.font("System", FontWeight.BOLD, 14));
            twitterHeader.setStyle("-fx-text-fill: #1da1f2;");
        }
    }

    private void setupEventHandlers() {
        if (telegramToggleButton != null) {
            telegramToggleButton.setOnAction(e -> toggleTelegramSection());
        }

        if (twitterToggleButton != null) {
            twitterToggleButton.setOnAction(e -> toggleTwitterSection());
        }

        if (refreshButton != null) {
            refreshButton.setOnAction(e -> refreshNews());
        }

        if (newsLimitComboBox != null) {
            newsLimitComboBox.setOnAction(e -> loadNews());
        }
    }

    private void toggleTelegramSection() {
        telegramExpanded = !telegramExpanded;
        if (telegramNewsContainer != null) {
            telegramNewsContainer.setVisible(telegramExpanded);
            telegramNewsContainer.setManaged(telegramExpanded);
        }
        if (telegramToggleButton != null) {
            telegramToggleButton.setText(telegramExpanded ? "▼" : "▶");
        }
    }

    private void toggleTwitterSection() {
        twitterExpanded = !twitterExpanded;
        if (twitterNewsContainer != null) {
            twitterNewsContainer.setVisible(twitterExpanded);
            twitterNewsContainer.setManaged(twitterExpanded);
        }
        if (twitterToggleButton != null) {
            twitterToggleButton.setText(twitterExpanded ? "▼" : "▶");
        }
    }

    private void refreshNews() {
        if (refreshButton != null) {
            refreshButton.setDisable(true);
            refreshButton.setText("Обновление...");
        }

        CompletableFuture.runAsync(this::loadNews)
                .thenRun(() -> Platform.runLater(() -> {
                    if (refreshButton != null) {
                        refreshButton.setDisable(false);
                        refreshButton.setText("🔄");
                    }
                }));
    }

    private void loadNews() {
        try {
            int limit = 20; // По умолчанию
            if (newsLimitComboBox != null && newsLimitComboBox.getValue() != null) {
                limit = Integer.parseInt(newsLimitComboBox.getValue());
            }

            // Загружаем Telegram новости
            List<NewsMessage> telegramNews = newsMessageRepository.findLatestNewsBySourceType(
                    SourceType.TELEGRAM, limit);

            // Загружаем Twitter новости
            List<NewsMessage> twitterNews = newsMessageRepository.findLatestNewsBySourceType(
                    SourceType.X, limit);

            Platform.runLater(() -> {
                populateTelegramNews(telegramNews);
                populateTwitterNews(twitterNews);
                updateHeaders(telegramNews.size(), twitterNews.size());
            });

        } catch (Exception e) {
            log.error("Ошибка загрузки новостей", e);
            Platform.runLater(() -> showError("Ошибка загрузки новостей: " + e.getMessage()));
        }
    }

    private void populateTelegramNews(List<NewsMessage> news) {
        if (telegramNewsContainer == null) return;

        telegramNewsContainer.getChildren().clear();

        if (news.isEmpty()) {
            Label noNewsLabel = new Label("Нет новостей Telegram");
            noNewsLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
            telegramNewsContainer.getChildren().add(noNewsLabel);
            return;
        }

        for (NewsMessage newsItem : news) {
            VBox newsCard = createNewsCard(newsItem);
            telegramNewsContainer.getChildren().add(newsCard);
        }
    }

    private void populateTwitterNews(List<NewsMessage> news) {
        if (twitterNewsContainer == null) return;

        twitterNewsContainer.getChildren().clear();

        if (news.isEmpty()) {
            Label noNewsLabel = new Label("Нет новостей Twitter");
            noNewsLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
            twitterNewsContainer.getChildren().add(noNewsLabel);
            return;
        }

        for (NewsMessage newsItem : news) {
            VBox newsCard = createNewsCard(newsItem);
            twitterNewsContainer.getChildren().add(newsCard);
        }
    }

    private VBox createNewsCard(NewsMessage news) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(10));
        card.setStyle(getCardStyle(news.getSourceType()));

        // Заголовок с источником и временем
        HBox header = createNewsHeader(news);

        // Текст новости
        Label textLabel = new Label(truncateText(news.getMessageText(), 200));
        textLabel.setWrapText(true);
        textLabel.setStyle("-fx-font-size: 12px;");

        // Метрики для Twitter
        HBox metrics = createMetricsBox(news);

        card.getChildren().addAll(header, textLabel);
        if (metrics != null) {
            card.getChildren().add(metrics);
        }

        // Обработчик клика для раскрытия полного текста
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                showFullNews(news);
            }
        });

        return card;
    }

    private HBox createNewsHeader(NewsMessage news) {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        // Иконка источника
        Label sourceIcon = new Label(news.getSourceType() == SourceType.TELEGRAM ? "📱" : "🐦");

        // Название канала/пользователя
        Label sourceName = new Label(getSourceName(news));
        sourceName.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        // Время
        Label timeLabel = new Label(news.getMessageDate().format(DATE_FORMATTER));
        timeLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(sourceIcon, sourceName, spacer, timeLabel);

        return header;
    }

    private HBox createMetricsBox(NewsMessage news) {
        if (news.getSourceType() != SourceType.X) {
            return null;
        }

        HBox metrics = new HBox(10);
        metrics.setAlignment(Pos.CENTER_LEFT);
        metrics.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        if (news.getLikeCount() != null && news.getLikeCount() > 0) {
            Label likes = new Label("❤️ " + formatNumber(news.getLikeCount()));
            metrics.getChildren().add(likes);
        }

        if (news.getRetweetCount() != null && news.getRetweetCount() > 0) {
            Label retweets = new Label("🔄 " + formatNumber(news.getRetweetCount()));
            metrics.getChildren().add(retweets);
        }

        if (news.getReplyCount() != null && news.getReplyCount() > 0) {
            Label replies = new Label("💬 " + formatNumber(news.getReplyCount()));
            metrics.getChildren().add(replies);
        }

        return metrics.getChildren().isEmpty() ? null : metrics;
    }

    private String getCardStyle(SourceType sourceType) {
        String baseStyle = "-fx-background-color: white; -fx-border-color: #e1e8ed; " +
                "-fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 2, 0, 0, 1);";

        if (sourceType == SourceType.TELEGRAM) {
            return baseStyle + " -fx-border-color: #0088cc;";
        } else {
            return baseStyle + " -fx-border-color: #1da1f2;";
        }
    }

    private String getSourceName(NewsMessage news) {
        if (news.getSourceType() == SourceType.TELEGRAM) {
            return news.getChannelTitle() != null ? news.getChannelTitle() : news.getChannelUsername();
        } else {
            return "@" + news.getTwitterUsername();
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private String formatNumber(Integer number) {
        if (number == null) return "0";
        if (number < 1000) return number.toString();
        if (number < 1000000) return String.format("%.1fk", number / 1000.0);
        return String.format("%.1fM", number / 1000000.0);
    }

    private void updateHeaders(int telegramCount, int twitterCount) {
        if (telegramHeader != null) {
            telegramHeader.setText(String.format("Telegram (%d)", telegramCount));
        }
        if (twitterHeader != null) {
            twitterHeader.setText(String.format("Twitter (%d)", twitterCount));
        }
    }

    private void showFullNews(NewsMessage news) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Полный текст новости");
        alert.setHeaderText(getSourceName(news) + " - " + news.getMessageDate().format(DATE_FORMATTER));

        TextArea textArea = new TextArea(news.getMessageText());
        textArea.setWrapText(true);
        textArea.setEditable(false);
        textArea.setPrefRowCount(10);
        textArea.setPrefColumnCount(50);

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void startAutoRefresh() {
        // Автообновление каждые 30 секунд
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30000); // 30 секунд
                    if (!Platform.isFxApplicationThread()) {
                        Platform.runLater(this::loadNews);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    public void forceRefresh() {
        loadNews();
    }
}