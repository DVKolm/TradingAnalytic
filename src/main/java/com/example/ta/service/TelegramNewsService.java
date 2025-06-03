package com.example.ta.service;

import com.example.ta.domain.NewsMessage;
import com.example.ta.domain.TelegramChannel;
import com.example.ta.repository.NewsMessageRepository;
import com.example.ta.repository.TelegramChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramNewsService {

    private final NewsMessageRepository newsMessageRepository;
    private final TelegramChannelRepository channelRepository;

    // Паттерны для извлечения времени из разных форматов
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})");
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile("(\\d{1,2}\\.\\d{1,2}\\.\\d{4})\\s+(\\d{1,2}:\\d{2})");

    // Ваш часовой пояс (настройте под себя)
    private static final ZoneId LOCAL_ZONE = ZoneId.systemDefault(); // Автоматически определяет системный часовой пояс

    /**
     * 🕐 ШЕДУЛЕР: Получение новостей из всех активных каналов через веб-скрапинг
     */
    @Scheduled(fixedRate = 100000) // каждые 5 минут
    @Transactional
    public void fetchAllChannelNews() {
        List<TelegramChannel> activeChannels = channelRepository.findAllActive();

        if (activeChannels.isEmpty()) {
            log.debug("Нет активных каналов для мониторинга");
            return;
        }

        log.info("🔍 Начинаем мониторинг {} каналов", activeChannels.size());

        for (TelegramChannel channel : activeChannels) {
            try {
                fetchChannelNews(channel);
                Thread.sleep(2000); // Пауза между запросами
            } catch (Exception e) {
                log.error("❌ Ошибка при получении новостей из {}: {}",
                        channel.getUsername(), e.getMessage());
            }
        }
    }

    /**
     * 🕷️ Получение новостей из конкретного канала через веб-скрапинг
     */
    private void fetchChannelNews(TelegramChannel channel) {
        try {
            String url = "https://t.me/s/" + channel.getUsername();
            log.debug("📡 Парсинг канала: {} ({})", channel.getUsername(), url);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();

            Elements messages = doc.select(".tgme_widget_message");
            log.debug("Найдено {} сообщений в канале {}", messages.size(), channel.getUsername());

            int newMessages = 0;

            // Обрабатываем последние 20 сообщений
            for (Element messageElement : messages.subList(0, Math.min(20, messages.size()))) {
                try {
                    if (parseAndSaveMessage(messageElement, channel)) {
                        newMessages++;
                    }
                } catch (Exception e) {
                    log.debug("Ошибка парсинга сообщения: {}", e.getMessage());
                }
            }

            if (newMessages > 0) {
                log.info("✅ Канал {}: добавлено {} новых сообщений",
                        channel.getUsername(), newMessages);
            } else {
                log.debug("📭 Канал {}: новых сообщений нет", channel.getUsername());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка подключения к каналу {}: {}",
                    channel.getUsername(), e.getMessage());
        }
    }

    /**
     * 🔍 Парсинг и сохранение сообщения
     */
    private boolean parseAndSaveMessage(Element messageElement, TelegramChannel channel) {
        try {
            // Извлекаем ID сообщения
            String dataPost = messageElement.attr("data-post");
            if (dataPost.isEmpty()) return false;

            String[] parts = dataPost.split("/");
            if (parts.length < 2) return false;

            int messageId = Integer.parseInt(parts[1]);

            // Проверяем, есть ли уже такое сообщение
            Optional<NewsMessage> existing = newsMessageRepository
                    .findByTelegramMessageIdAndChannelUsername(messageId, channel.getUsername());

            if (existing.isPresent()) {
                return false; // Сообщение уже существует
            }

            // Извлекаем текст сообщения
            Element textElement = messageElement.select(".tgme_widget_message_text").first();
            if (textElement == null) return false;

            String messageText = textElement.text();
            if (messageText.trim().isEmpty()) return false;

            // Извлекаем дату с правильным часовым поясом
            LocalDateTime messageDate = extractMessageDateWithTimezone(messageElement);

            // Создаем новое сообщение
            NewsMessage newsMessage = new NewsMessage();
            newsMessage.setTelegramMessageId(messageId);
            newsMessage.setChannelUsername(channel.getUsername());
            newsMessage.setChannelTitle(channel.getTitle());
            newsMessage.setMessageText(messageText);
            newsMessage.setMessageDate(messageDate);

            newsMessageRepository.save(newsMessage);

            log.debug("💾 Сохранено: {} - {} (время: {})",
                    channel.getUsername(),
                    messageText.substring(0, Math.min(50, messageText.length())),
                    messageDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

            return true;

        } catch (Exception e) {
            log.debug("Ошибка обработки сообщения: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 🕐 Улучшенное извлечение даты сообщения с учетом часовых поясов
     */
    private LocalDateTime extractMessageDateWithTimezone(Element messageElement) {
        try {
            // Сначала пытаемся найти элемент времени
            Element timeElement = messageElement.select(".tgme_widget_message_date time").first();

            if (timeElement != null) {
                String datetime = timeElement.attr("datetime");
                if (!datetime.isEmpty()) {
                    // Парсим ISO datetime и конвертируем в локальное время
                    ZonedDateTime utcTime = ZonedDateTime.parse(datetime);
                    ZonedDateTime localTime = utcTime.withZoneSameInstant(LOCAL_ZONE);

                    log.debug("Время сообщения: UTC={}, Local={}",
                            utcTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            localTime.format(DateTimeFormatter.ofPattern("HH:mm")));

                    return localTime.toLocalDateTime();
                }

                // Пробуем извлечь из текста элемента
                String timeText = timeElement.text().trim();
                if (!timeText.isEmpty()) {
                    return parseTimeText(timeText);
                }
            }

            // Если не нашли time элемент, ищем в тексте
            Element dateElement = messageElement.select(".tgme_widget_message_date").first();
            if (dateElement != null) {
                String dateText = dateElement.text().trim();
                return parseTimeText(dateText);
            }

        } catch (Exception e) {
            log.debug("Ошибка извлечения даты: {}", e.getMessage());
        }

        log.debug("Не удалось извлечь дату сообщения, используем текущее время");
        return LocalDateTime.now();
    }

    /**
     * 🕰️ Парсинг текста времени с учетом различных форматов
     */
    private LocalDateTime parseTimeText(String timeText) {
        try {
            LocalDateTime now = LocalDateTime.now();

            // Ищем время в формате "HH:mm"
            Matcher timeMatcher = TIME_PATTERN.matcher(timeText);
            if (timeMatcher.find()) {
                String timeStr = timeMatcher.group(1);
                String[] timeParts = timeStr.split(":");
                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1]);

                // Создаем дату с сегодняшним днем и найденным временем
                LocalDateTime messageTime = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

                // Если время больше текущего, возможно это вчерашнее сообщение
                if (messageTime.isAfter(now.plusMinutes(10))) {
                    messageTime = messageTime.minusDays(1);
                }

                return messageTime;
            }

            // Ищем полную дату в формате "dd.MM.yyyy HH:mm"
            Matcher dateTimeMatcher = DATE_TIME_PATTERN.matcher(timeText);
            if (dateTimeMatcher.find()) {
                String dateStr = dateTimeMatcher.group(1);
                String timeStr = dateTimeMatcher.group(2);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
                return LocalDateTime.parse(dateStr + " " + timeStr, formatter);
            }

            // Если содержит "сегодня"
            if (timeText.toLowerCase().contains("сегодня") || timeText.toLowerCase().contains("today")) {
                Matcher timeMatcher2 = TIME_PATTERN.matcher(timeText);
                if (timeMatcher2.find()) {
                    String timeStr = timeMatcher2.group(1);
                    String[] timeParts = timeStr.split(":");
                    int hour = Integer.parseInt(timeParts[0]);
                    int minute = Integer.parseInt(timeParts[1]);

                    return now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
                }
            }

            // Если содержит "вчера"
            if (timeText.toLowerCase().contains("вчера") || timeText.toLowerCase().contains("yesterday")) {
                Matcher timeMatcher2 = TIME_PATTERN.matcher(timeText);
                if (timeMatcher2.find()) {
                    String timeStr = timeMatcher2.group(1);
                    String[] timeParts = timeStr.split(":");
                    int hour = Integer.parseInt(timeParts[0]);
                    int minute = Integer.parseInt(timeParts[1]);

                    return now.minusDays(1).withHour(hour).withMinute(minute).withSecond(0).withNano(0);
                }
            }

        } catch (Exception e) {
            log.debug("Ошибка парсинга времени '{}': {}", timeText, e.getMessage());
        }

        return LocalDateTime.now();
    }

    /**
     * 📰 Получение последних новостей (улучшенная версия)
     */
    public List<NewsMessage> getLatestNews(int limit) {
        try {
            List<NewsMessage> news = newsMessageRepository.findLatestNewsFromActiveChannels(limit);
            log.debug("📰 Получено {} последних новостей", news.size());
            return news;
        } catch (Exception e) {
            log.error("❌ Ошибка при получении последних новостей", e);
            return newsMessageRepository.findLatestNews(limit);
        }
    }

    /**
     * 📡 Получение новостей из конкретного канала
     */
    public List<NewsMessage> getChannelNews(String channelUsername, int limit) {
        return newsMessageRepository.findLatestNewsByChannel(channelUsername, limit);
    }

    /**
     * 🔍 Поиск новостей по ключевому слову
     */
    public List<NewsMessage> searchNews(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getLatestNews(limit);
        }
        return newsMessageRepository.findNewsByKeyword(keyword.trim(), limit);
    }

    /**
     * ⏰ Получение новых новостей за последние часы
     */
    public List<NewsMessage> getRecentNews(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return newsMessageRepository.findNewsSince(since);
    }

    /**
     * 📊 Получение статистики по каналам
     */
    public List<Object[]> getChannelStatistics() {
        return newsMessageRepository.getChannelStatistics();
    }

    /**
     * 📅 Получение количества новых сообщений за сегодня
     */
    public long getTodayMessagesCount() {
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.now().with(LocalTime.MAX);
        return newsMessageRepository.countTodayMessages(startOfDay, endOfDay);
    }

    /**
     * 📆 Получение новостей за период
     */
    public List<NewsMessage> getNewsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return newsMessageRepository.findNewsBetweenDates(startDate, endDate);
    }

    /**
     * 🔄 Ручное обновление новостей
     */
    public void refreshNews() {
        log.info("🔄 Ручное обновление новостей");
        fetchAllChannelNews();
        log.info("📊 Сегодня получено {} новых сообщений", getTodayMessagesCount());
    }

    /**
     * 📋 Получение активных каналов
     */
    public List<TelegramChannel> getActiveChannels() {
        return channelRepository.findAllActive();
    }

    /**
     * ➕ Добавление канала для мониторинга
     */
    @Transactional
    public void addChannel(String username, String title, String description) {
        // Убираем @ если есть
        if (username.startsWith("@")) {
            username = username.substring(1);
        }

        // Проверяем, не существует ли уже такой канал
        Optional<TelegramChannel> existing = channelRepository.findByUsername(username);
        if (existing.isPresent()) {
            if (existing.get().getIsActive()) {
                throw new IllegalArgumentException("Канал уже добавлен и активен");
            } else {
                // Реактивируем существующий канал
                existing.get().setIsActive(true);
                channelRepository.save(existing.get());
                log.info("✅ Реактивирован канал: @{}", username);
                return;
            }
        }

        TelegramChannel channel = new TelegramChannel();
        channel.setUsername(username);
        channel.setTitle(title.isEmpty() ? username : title);
        channel.setDescription(description);
        channel.setIsActive(true);
        channel.setCreatedAt(LocalDateTime.now());

        channelRepository.save(channel);
        log.info("✅ Добавлен канал для мониторинга: @{}", username);
    }

    /**
     * ➖ Удаление/деактивация канала
     */
    @Transactional
    public void removeChannel(String username) {
        Optional<TelegramChannel> channel = channelRepository.findByUsername(username);
        if (channel.isPresent()) {
            channel.get().setIsActive(false);
            channelRepository.save(channel.get());
            log.info("⏸️ Канал деактивирован: @{}", username);
        }
    }
}