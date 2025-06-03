package com.example.ta.controller;

import com.example.ta.domain.NewsMessage;
import com.example.ta.service.TelegramNewsService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Slf4j
public class NewsPanel extends VBox {

    @Getter
    private final TelegramNewsService telegramNewsService;
    private final VBox newsContainer;
    private final ScheduledExecutorService scheduler;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    // Красивые шрифты для новостей
    private static final Font TITLE_FONT = Font.font("Segoe UI", FontWeight.BOLD, 18);
    private static final Font CHANNEL_FONT = Font.font("Segoe UI Semibold", FontWeight.SEMI_BOLD, 13);
    private static final Font MESSAGE_FONT = Font.font("Segoe UI", FontWeight.NORMAL, 14); // 🔧 Увеличили с 12 до 14
    private static final Font TIME_FONT = Font.font("Segoe UI", FontWeight.LIGHT, 11);
    private static final Font NO_NEWS_FONT = Font.font("Segoe UI", FontPosture.ITALIC, 13);

    // Паттерн для удаления эмодзи
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
            "[\\x{1F600}-\\x{1F64F}]|" +  // эмоции
                    "[\\x{1F300}-\\x{1F5FF}]|" +  // символы и пиктограммы
                    "[\\x{1F680}-\\x{1F6FF}]|" +  // транспорт и карты
                    "[\\x{1F1E0}-\\x{1F1FF}]|" +  // флаги
                    "[\\x{2600}-\\x{26FF}]|" +    // дополнительные символы
                    "[\\x{2700}-\\x{27BF}]|" +    // дополнительные символы
                    "[\\x{1F900}-\\x{1F9FF}]|" +  // дополнительные эмодзи
                    "[\\x{1F018}-\\x{1F270}]|" +  // дополнительные символы
                    "[\\x{238C}-\\x{2454}]|" +    // дополнительные символы
                    "[\\x{20D0}-\\x{20FF}]|" +    // комбинирующие символы
                    "[\\x{FE0F}]|" +              // селектор вариантов
                    "[\\x{200D}]"                 // соединитель нулевой ширины
    );

    public NewsPanel(TelegramNewsService telegramNewsService) {
        this.telegramNewsService = telegramNewsService;
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Настройка основного контейнера с улучшенным дизайном
        this.setSpacing(15);
        this.setPadding(new Insets(15));
        this.setPrefWidth(380);
        this.setMaxWidth(380);
        // 🔧 Делаем панель растягивающейся по высоте
        this.setPrefHeight(Region.USE_COMPUTED_SIZE);
        VBox.setVgrow(this, Priority.ALWAYS);
        this.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #f8f9fa 0%, #e9ecef 100%);" +
                        "-fx-border-color: #dee2e6;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 12;" +
                        "-fx-background-radius: 12;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);"
        );

        // Стильный заголовок
        Label titleLabel = new Label("📰 Telegram Новости");
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setStyle(
                "-fx-text-fill: linear-gradient(to right, #2c3e50 0%, #3498db 100%);" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 2, 0, 0, 1);"
        );

        // Элегантный разделитель
        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: linear-gradient(to right, transparent 0%, #bdc3c7 50%, transparent 100%);");

        // Контейнер для новостей с улучшенным стилем
        newsContainer = new VBox();
        newsContainer.setSpacing(12);
        newsContainer.setPadding(new Insets(0, 0, 15, 0));

        // Стильный ScrollPane - растягивается на всю доступную высоту
        ScrollPane scrollPane = new ScrollPane(newsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // 🔧 Убираем фиксированную высоту и делаем растягивающимся
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        scrollPane.setMaxHeight(Double.MAX_VALUE);
        scrollPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
        scrollPane.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-background: transparent;" +
                        "-fx-border-color: transparent;"
        );

        // Стиль для полосы прокрутки
        scrollPane.lookupAll(".scroll-bar").forEach(node ->
                node.setStyle(
                        "-fx-background-color: #ecf0f1;" +
                                "-fx-border-radius: 6;" +
                                "-fx-background-radius: 6;"
                )
        );

        this.getChildren().addAll(titleLabel, separator, scrollPane);

        // Загружаем новости при инициализации
        loadNews();

        // Запускаем автообновление каждые 30 секунд
        startAutoRefresh();
    }

    private void loadNews() {
        try {
            List<NewsMessage> news = telegramNewsService.getLatestNews(10);

            Platform.runLater(() -> {
                newsContainer.getChildren().clear();

                if (news.isEmpty()) {
                    VBox noNewsBox = createNoNewsMessage();
                    newsContainer.getChildren().add(noNewsBox);
                } else {
                    for (NewsMessage message : news) {
                        newsContainer.getChildren().add(createNewsItem(message));
                    }
                }
            });

        } catch (Exception e) {
            log.error("Ошибка при загрузке новостей", e);
            Platform.runLater(() -> {
                VBox errorBox = createErrorMessage();
                newsContainer.getChildren().clear();
                newsContainer.getChildren().add(errorBox);
            });
        }
    }

    private VBox createNoNewsMessage() {
        VBox noNewsBox = new VBox();
        noNewsBox.setAlignment(Pos.CENTER);
        noNewsBox.setSpacing(10);
        noNewsBox.setPadding(new Insets(30));
        noNewsBox.setStyle(
                "-fx-background-color: rgba(255,255,255,0.8);" +
                        "-fx-border-color: #e9ecef;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 10;" +
                        "-fx-background-radius: 10;"
        );

        Label icon = new Label("📭");
        icon.setFont(Font.font("Segoe UI", 36));

        Label message = new Label("Новостей пока нет");
        message.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 16));
        message.setStyle("-fx-text-fill: #2c3e50;");

        Label hint = new Label("Настройте каналы в разделе Telegram");
        hint.setFont(NO_NEWS_FONT);
        hint.setStyle("-fx-text-fill: #7f8c8d;");
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        noNewsBox.getChildren().addAll(icon, message, hint);
        return noNewsBox;
    }

    private VBox createErrorMessage() {
        VBox errorBox = new VBox();
        errorBox.setAlignment(Pos.CENTER);
        errorBox.setSpacing(10);
        errorBox.setPadding(new Insets(20));
        errorBox.setStyle(
                "-fx-background-color: #ffebee;" +
                        "-fx-border-color: #ef5350;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;"
        );

        Label errorLabel = new Label("Ошибка загрузки новостей");
        errorLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
        errorLabel.setStyle("-fx-text-fill: #c62828;");

        errorBox.getChildren().add(errorLabel);
        return errorBox;
    }

    private VBox createNewsItem(NewsMessage message) {
        VBox newsItem = new VBox();
        newsItem.setSpacing(8);
        newsItem.setPadding(new Insets(16));
        newsItem.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #ffffff 0%, #f8f9fa 100%);" +
                        "-fx-border-color: #e3e6ea;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 12;" +
                        "-fx-background-radius: 12;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);"
        );

        // Заголовок канала и время
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setSpacing(8);

        // Иконка канала
        Label channelIcon = new Label("📡");
        channelIcon.setFont(Font.font("Segoe UI", 14));

        String channelTitle = message.getChannelTitle() != null ?
                message.getChannelTitle() : "@" + message.getChannelUsername();

        // 🔧 Название канала теперь черное
        Label channelLabel = new Label(removeEmojis(channelTitle));
        channelLabel.setFont(CHANNEL_FONT);
        channelLabel.setStyle("-fx-text-fill: #2c3e50;"); // 🔧 Черный цвет вместо синего градиента

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Стильная метка времени
        Label timeLabel = new Label(message.getMessageDate().format(DATE_FORMATTER));
        timeLabel.setFont(TIME_FONT);
        timeLabel.setStyle(
                "-fx-text-fill: #95a5a6;" +
                        "-fx-background-color: #ecf0f1;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 4 8 4 8;"
        );

        headerBox.getChildren().addAll(channelIcon, channelLabel, spacer, timeLabel);

        // Текст сообщения с увеличенным шрифтом
        String cleanText = removeEmojis(message.getMessageText());
        Label messageLabel = new Label(truncateText(cleanText, 220));
        messageLabel.setWrapText(true);
        messageLabel.setFont(MESSAGE_FONT); // 🔧 Теперь размер 14
        messageLabel.setStyle(
                "-fx-text-fill: #2c3e50;" +
                        "-fx-line-spacing: 2px;"
        );

        // 🔧 Убрали разделитель между заголовком и текстом
        newsItem.getChildren().addAll(headerBox, messageLabel); // Без itemSeparator

        // Анимированные эффекты при наведении
        newsItem.setOnMouseEntered(e -> {
            newsItem.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #ffffff 0%, #e8f4fd 100%);" +
                            "-fx-border-color: #3498db;" +
                            "-fx-border-width: 2;" +
                            "-fx-border-radius: 12;" +
                            "-fx-background-radius: 12;" +
                            "-fx-effect: dropshadow(gaussian, rgba(52,152,219,0.3), 12, 0, 0, 4);" +
                            "-fx-cursor: hand;" +
                            "-fx-scale-x: 1.02;" +
                            "-fx-scale-y: 1.02;"
            );
        });

        newsItem.setOnMouseExited(e -> {
            newsItem.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #ffffff 0%, #f8f9fa 100%);" +
                            "-fx-border-color: #e3e6ea;" +
                            "-fx-border-width: 1;" +
                            "-fx-border-radius: 12;" +
                            "-fx-background-radius: 12;" +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);" +
                            "-fx-scale-x: 1.0;" +
                            "-fx-scale-y: 1.0;"
            );
        });

        return newsItem;
    }

    /**
     * Удаляет все эмодзи из текста
     */
    private String removeEmojis(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Удаляем эмодзи
        String cleanText = EMOJI_PATTERN.matcher(text).replaceAll("");

        // Удаляем лишние пробелы
        cleanText = cleanText.replaceAll("\\s+", " ").trim();

        return cleanText;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private void startAutoRefresh() {
        scheduler.scheduleAtFixedRate(this::loadNews, 30, 30, TimeUnit.SECONDS);
    }

    public void refresh() {
        loadNews();
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}