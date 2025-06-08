
package com.example.ta.controller.news;

import com.example.ta.domain.news.NewsMessage;
import com.example.ta.domain.news.SourceType;
import com.example.ta.repository.NewsMessageRepository;
import com.example.ta.service.MediaDownloadService; // Добавляем новый сервис
import com.example.ta.service.TelegramNewsService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image; // Добавляем для работы с изображениями
import javafx.scene.image.ImageView; // Добавляем для отображения изображений
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    private final NewsMessageRepository newsMessageRepository;
    private final MediaDownloadService mediaDownloadService; // Добавляем новый сервис
    private final VBox newsContainer;
    private final ScheduledExecutorService scheduler;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    // Красивые шрифты для новостей
    private static final Font TITLE_FONT = Font.font("Segoe UI", FontWeight.BOLD, 18);
    private static final Font CHANNEL_FONT = Font.font("Segoe UI Semibold", FontWeight.SEMI_BOLD, 13);
    private static final Font MESSAGE_FONT = Font.font("Segoe UI", FontWeight.NORMAL, 14);
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

    // Обновляем конструктор - добавляем MediaDownloadService
    public NewsPanel(TelegramNewsService telegramNewsService,
                     NewsMessageRepository newsMessageRepository,
                     MediaDownloadService mediaDownloadService) {
        this.telegramNewsService = telegramNewsService;
        this.newsMessageRepository = newsMessageRepository;
        this.mediaDownloadService = mediaDownloadService; // Добавляем
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Настройка основного контейнера с улучшенным дизайном
        this.setSpacing(15);
        this.setPadding(new Insets(15));
        this.setPrefWidth(380);
        this.setMaxWidth(380);
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

        Label titleLabel = new Label("📰 Все Новости");
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setStyle(
                "-fx-text-fill: linear-gradient(to right, #2c3e50 0%, #3498db 100%);" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 2, 0, 0, 1);"
        );

        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: linear-gradient(to right, transparent 0%, #bdc3c7 50%, transparent 100%);");

        newsContainer = new VBox();
        newsContainer.setSpacing(12);
        newsContainer.setPadding(new Insets(0, 0, 15, 0));

        ScrollPane scrollPane = new ScrollPane(newsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        scrollPane.setMaxHeight(Double.MAX_VALUE);
        scrollPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
        scrollPane.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-background: transparent;" +
                        "-fx-border-color: transparent;"
        );

        scrollPane.lookupAll(".scroll-bar").forEach(node ->
                node.setStyle(
                        "-fx-background-color: #ecf0f1;" +
                                "-fx-border-radius: 6;" +
                                "-fx-background-radius: 6;"
                )
        );

        this.getChildren().addAll(titleLabel, separator, scrollPane);

        loadNews();
        startAutoRefresh();
    }

    private void loadNews() {
        try {
            List<NewsMessage> news = newsMessageRepository.findLatestNewsFromAllSources(15);

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

                log.debug("Загружено {} новостей из всех источников", news.size());
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

        Label hint = new Label("Настройте каналы Telegram и Twitter");
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

    // Обновляем метод создания элемента новости - добавляем поддержку изображений
    private VBox createNewsItem(NewsMessage message) {
        VBox newsItem = new VBox();
        newsItem.setSpacing(8);
        newsItem.setPadding(new Insets(14));
        newsItem.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #ffffff 0%, #f8f9fa 100%);" +
                        "-fx-border-color: #e3e6ea;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 12;" +
                        "-fx-background-radius: 12;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);"
        );

        // Заголовок канала - БЕЗ ВРЕМЕНИ
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setSpacing(8);

        String sourceIcon;
        String sourceName;
        String sourceStyle;

        if (message.getSourceType() == SourceType.X) {
            sourceIcon = "𝕏"; // Новый логотип X
            sourceName = message.getTwitterDisplayName() != null ?
                    message.getTwitterDisplayName() :
                    "@" + message.getTwitterUsername();
            sourceStyle = "-fx-text-fill: #000000;"; // Черный цвет для X
        } else {
            sourceIcon = "📱";
            sourceName = message.getChannelTitle() != null ?
                    message.getChannelTitle() :
                    "@" + message.getChannelUsername();
            sourceStyle = "-fx-text-fill: #2c3e50;";
        }

        Label channelIcon = new Label(sourceIcon);
        channelIcon.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        Label channelLabel = new Label(removeEmojis(sourceName));
        channelLabel.setFont(CHANNEL_FONT);
        channelLabel.setStyle(sourceStyle);

        headerBox.getChildren().addAll(channelIcon, channelLabel);

        // ОБЪЕДИНЕННАЯ СТРОКА: время + источник
        HBox timeAndSourceBox = new HBox();
        timeAndSourceBox.setAlignment(Pos.CENTER_LEFT);
        timeAndSourceBox.setSpacing(12);
        timeAndSourceBox.setPadding(new Insets(4, 0, 0, 0));

        // Относительное время (например: "2 часа назад")
        Label relativeTimeLabel = new Label(getRelativeTime(message.getMessageDate()));
        relativeTimeLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        relativeTimeLabel.setStyle(
                "-fx-text-fill: #6c757d;" +
                        "-fx-background-color: rgba(108, 117, 125, 0.1);" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 4 8 4 8;"
        );

        // Точка-разделитель
        Label separator = new Label("•");
        separator.setFont(Font.font("Segoe UI", 12));
        separator.setStyle("-fx-text-fill: #adb5bd;");

        // Источник сообщения (X или Telegram)
        String sourceLabelText = message.getSourceType() == SourceType.X ? "X" : "Telegram";
        Label sourceLabel = new Label(sourceLabelText);
        sourceLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        sourceLabel.setStyle(
                "-fx-text-fill: " + (message.getSourceType() == SourceType.X ? "#000000" : "#2c3e50") + ";" +
                        "-fx-background-color: " + (message.getSourceType() == SourceType.X ? "rgba(0,0,0,0.1)" : "rgba(44,62,80,0.1)") + ";" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 3 8 3 8;"
        );

        timeAndSourceBox.getChildren().addAll(relativeTimeLabel, separator, sourceLabel);

        // Добавляем заголовок и строку времени+источника
        newsItem.getChildren().add(headerBox);
        newsItem.getChildren().add(timeAndSourceBox);

        // Затем добавляем изображение (если есть)
        ImageView imageView = createImageView(message);
        if (imageView != null) {
            log.debug("Добавляем изображение в новость {}", message.getId());

            // Создаем контейнер для изображения с отступами
            VBox imageContainer = new VBox();
            imageContainer.setAlignment(Pos.CENTER);
            imageContainer.setPadding(new Insets(8, 0, 8, 0));
            imageContainer.getChildren().add(imageView);

            newsItem.getChildren().add(imageContainer);
        } else {
            log.debug("Изображение не добавлено для новости {}", message.getId());
        }

        // Текст сообщения
        String cleanText = removeEmojis(message.getMessageText());
        Label messageLabel = new Label(truncateText(cleanText, 220));
        messageLabel.setWrapText(true);
        messageLabel.setFont(MESSAGE_FONT);
        messageLabel.setStyle(
                "-fx-text-fill: #2c3e50;" +
                        "-fx-line-spacing: 2px;"
        );

        newsItem.getChildren().add(messageLabel);

        // Метрики для твитов (теперь для X)
        if (message.getSourceType() == SourceType.X &&
                (message.getLikeCount() != null || message.getRetweetCount() != null)) {

            HBox metricsBox = new HBox();
            metricsBox.setSpacing(15);
            metricsBox.setAlignment(Pos.CENTER_LEFT);
            metricsBox.setStyle("-fx-padding: 8 0 0 0;");

            if (message.getLikeCount() != null && message.getLikeCount() > 0) {
                Label likesLabel = new Label("❤️ " + formatNumber(message.getLikeCount()));
                likesLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
                likesLabel.setStyle(
                        "-fx-text-fill: #e74c3c;" +
                                "-fx-background-color: rgba(231, 76, 60, 0.1);" +
                                "-fx-background-radius: 6;" +
                                "-fx-padding: 3 6 3 6;"
                );
                metricsBox.getChildren().add(likesLabel);
            }

            if (message.getRetweetCount() != null && message.getRetweetCount() > 0) {
                Label retweetsLabel = new Label("🔄 " + formatNumber(message.getRetweetCount()));
                retweetsLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
                retweetsLabel.setStyle(
                        "-fx-text-fill: #27ae60;" +
                                "-fx-background-color: rgba(39, 174, 96, 0.1);" +
                                "-fx-background-radius: 6;" +
                                "-fx-padding: 3 6 3 6;"
                );
                metricsBox.getChildren().add(retweetsLabel);
            }

            if (!metricsBox.getChildren().isEmpty()) {
                newsItem.getChildren().add(metricsBox);
            }
        }

        // Анимированные эффекты при наведении
        newsItem.setOnMouseEntered(e -> {
            String hoverColor = message.getSourceType() == SourceType.X ?
                    "rgba(0,0,0,0.2)" : "rgba(52,152,219,0.3)";
            String borderColor = message.getSourceType() == SourceType.X ?
                    "#000000" : "#3498db";

            newsItem.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #ffffff 0%, #e8f4fd 100%);" +
                            "-fx-border-color: " + borderColor + ";" +
                            "-fx-border-width: 2;" +
                            "-fx-border-radius: 12;" +
                            "-fx-background-radius: 12;" +
                            "-fx-effect: dropshadow(gaussian, " + hoverColor + ", 12, 0, 0, 4);" +
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

    private String getRelativeTime(LocalDateTime messageDate) {
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(messageDate, now);

        if (minutes < 1) {
            return "только что";
        } else if (minutes < 60) {
            return minutes + " мин назад";
        } else if (minutes < 1440) { // меньше суток
            long hours = minutes / 60;
            return hours + " ч назад";
        } else {
            long days = minutes / 1440;
            if (days == 1) {
                return "вчера";
            } else if (days < 7) {
                return days + " дн назад";
            } else {
                return messageDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
        }
    }

    /**
     * Форматирует большие числа (например: 1.2k, 15.3k, 1.1M)
     */
    private String formatNumber(Integer number) {
        if (number == null) return "0";

        if (number < 1000) {
            return number.toString();
        } else if (number < 1000000) {
            double k = number / 1000.0;
            return String.format("%.1fk", k);
        } else {
            double m = number / 1000000.0;
            return String.format("%.1fM", m);
        }
    }



    /**
     * Создает ImageView для отображения миниатюры изображения
     */
    private ImageView createImageView(NewsMessage message) {
        log.debug("Проверяем медиа для сообщения ID: {}", message.getId());

        // Проверяем есть ли медиа в сообщении
        if (message.getHasMedia() == null || !message.getHasMedia()) {
            log.debug("Сообщение {} не содержит медиа", message.getId());
            return null;
        }

        log.debug("Сообщение {} содержит медиа. Тип: {}", message.getId(), message.getMediaType());

        // Проверяем тип медиа - отображаем только изображения
        if (!"photo".equals(message.getMediaType())) {
            log.debug("Тип медиа {} не поддерживается для отображения", message.getMediaType());
            return null;
        }

        String thumbnailPath = message.getMediaThumbnailPath();
        log.debug("Путь к миниатюре: {}", thumbnailPath);

        if (thumbnailPath == null) {
            log.warn("Путь к миниатюре не задан для сообщения {}", message.getId());
            return null;
        }

        if (!mediaDownloadService.thumbnailExists(thumbnailPath)) {
            log.warn("Файл миниатюры не существует: {}", thumbnailPath);
            return null;
        }

        try {
            File imageFile = new File(thumbnailPath);
            if (!imageFile.exists()) {
                log.error("Файл изображения не найден: {}", thumbnailPath);
                return null;
            }

            log.debug("Загружаем изображение: {}", imageFile.getAbsolutePath());

            // Проверяем, можно ли читать файл
            if (!imageFile.canRead()) {
                log.error("Нет прав на чтение файла: {}", thumbnailPath);
                return null;
            }

            // Используем разные способы загрузки изображения
            Image image;
            try {
                // Способ 1: Через URI
                image = new Image(imageFile.toURI().toString());
            } catch (Exception e) {
                log.warn("Не удалось загрузить через URI, пробуем FileInputStream: {}", e.getMessage());
                try {
                    // Способ 2: Через FileInputStream
                    image = new Image(new FileInputStream(imageFile));
                } catch (Exception e2) {
                    log.error("Не удалось загрузить изображение: {}", thumbnailPath, e2);
                    return null;
                }
            }

            if (image.isError()) {
                log.error("Ошибка при загрузке изображения: {}", image.getException().getMessage());
                return null;
            }

            log.debug("Изображение успешно загружено. Размер: {}x{}", image.getWidth(), image.getHeight());

            ImageView imageView = new ImageView(image);

            // Настраиваем размеры изображения
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(330); // Максимальная ширина
            imageView.setFitHeight(200); // Максимальная высота
            imageView.setSmooth(true);

            // Стили для изображения
            imageView.setStyle(
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 2);" +
                            "-fx-background-radius: 8;"
            );

            // Добавляем эффект при клике
            imageView.setOnMouseClicked(e -> {
                log.debug("Клик по изображению: {}", thumbnailPath);
            });

            imageView.setOnMouseEntered(e -> {
                imageView.setStyle(
                        "-fx-effect: dropshadow(gaussian, rgba(52,152,219,0.4), 8, 0, 0, 4);" +
                                "-fx-cursor: hand;" +
                                "-fx-background-radius: 8;"
                );
            });

            imageView.setOnMouseExited(e -> {
                imageView.setStyle(
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 2);" +
                                "-fx-background-radius: 8;"
                );
            });

            log.debug("ImageView успешно создан для сообщения {}", message.getId());
            return imageView;

        } catch (Exception e) {
            log.error("Ошибка при создании ImageView для: {}", thumbnailPath, e);
            return null;
        }
    }

    private String removeEmojis(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String cleanText = EMOJI_PATTERN.matcher(text).replaceAll("");
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

    public void debugMediaFiles() {
        try {
            List<NewsMessage> news = newsMessageRepository.findLatestNewsFromAllSources(5);

            log.info("=== ОТЛАДКА МЕДИАФАЙЛОВ ===");
            for (NewsMessage message : news) {
                log.info("Сообщение ID: {}", message.getId());
                log.info("  - Канал: {}", message.getChannelTitle());
                log.info("  - Есть медиа: {}", message.getHasMedia());
                log.info("  - Тип медиа: {}", message.getMediaType());
                log.info("  - URL медиа: {}", message.getMediaUrl());
                log.info("  - Путь к файлу: {}", message.getMediaFilePath());
                log.info("  - Путь к миниатюре: {}", message.getMediaThumbnailPath());

                if (message.getMediaThumbnailPath() != null) {
                    File file = new File(message.getMediaThumbnailPath());
                    log.info("  - Файл существует: {}", file.exists());
                    log.info("  - Размер файла: {} байт", file.exists() ? file.length() : 0);
                }
                log.info("  ---");
            }

        } catch (Exception e) {
            log.error("Ошибка при отладке медиафайлов", e);
        }
    }

}