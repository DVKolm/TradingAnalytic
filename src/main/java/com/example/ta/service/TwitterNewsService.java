package com.example.ta.service;

import com.example.ta.domain.news.NewsMessage;
import com.example.ta.domain.news.SourceType;
import com.example.ta.domain.news.TwitterSettings;
import com.example.ta.domain.news.TwitterUser;
import com.example.ta.repository.NewsMessageRepository;
import com.example.ta.repository.TwitterSettingsRepository;
import com.example.ta.repository.TwitterUserRepository;
import com.twitter.clientlib.TwitterCredentialsBearer;
import com.twitter.clientlib.api.TwitterApi;
import com.twitter.clientlib.api.UsersApi;
import com.twitter.clientlib.api.TweetsApi;
import com.twitter.clientlib.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwitterNewsService {

    private final NewsMessageRepository newsMessageRepository;
    private final TwitterSettingsRepository twitterSettingsRepository;
    private final TwitterUserRepository twitterUserRepository;

    private TwitterApi twitterApi;

    // Счетчики для отслеживания лимитов Free плана
    private final AtomicInteger dailyUserInfoRequests = new AtomicInteger(0);
    private final AtomicInteger quarterHourTweetRequests = new AtomicInteger(0);
    private final AtomicInteger quarterHourUserRequests = new AtomicInteger(0);

    // Время последнего сброса счетчиков
    private LocalDateTime lastUserInfoReset = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
    private LocalDateTime lastQuarterHourReset = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
            .withMinute((LocalDateTime.now().getMinute() / 15) * 15);

    // Индекс для ротации пользователей (Free план: 1 пользователь за 15 минут)
    private int currentUserIndex = 0;

    /**
     * Проверяет лимиты перед запросом
     */
    private boolean checkRateLimit(String apiType) {
        LocalDateTime now = LocalDateTime.now();

        // Сброс дневных счетчиков
        if (now.truncatedTo(ChronoUnit.DAYS).isAfter(lastUserInfoReset)) {
            dailyUserInfoRequests.set(0);
            lastUserInfoReset = now.truncatedTo(ChronoUnit.DAYS);
            log.info("Сброс дневного лимита запросов");
        }

        // Сброс 15-минутных счетчиков
        LocalDateTime currentQuarter = now.truncatedTo(ChronoUnit.MINUTES)
                .withMinute((now.getMinute() / 15) * 15);
        if (currentQuarter.isAfter(lastQuarterHourReset)) {
            quarterHourTweetRequests.set(0);
            quarterHourUserRequests.set(0);
            lastQuarterHourReset = currentQuarter;
            log.info("Сброс 15-минутного лимита запросов");
        }

        switch (apiType) {
            case "user_tweets":
                return quarterHourTweetRequests.get() < 1; // Free: 1 запрос / 15 минут
            case "user_info":
                return dailyUserInfoRequests.get() < 1; // Free: 1 запрос / 24 часа
            case "user_lookup":
                return quarterHourUserRequests.get() < 3; // Free: 3 запроса / 15 минут
            default:
                return false;
        }
    }

    /**
     * Увеличивает счетчик использования API
     */
    private void incrementRateLimit(String apiType) {
        switch (apiType) {
            case "user_tweets":
                int tweetRequests = quarterHourTweetRequests.incrementAndGet();
                log.debug("Использовано запросов твитов: {}/1 (15 мин)", tweetRequests);
                break;
            case "user_info":
                int userInfoRequests = dailyUserInfoRequests.incrementAndGet();
                log.debug("Использовано запросов информации о пользователе: {}/1 (24 ч)", userInfoRequests);
                break;
            case "user_lookup":
                int userLookupRequests = quarterHourUserRequests.incrementAndGet();
                log.debug("Использовано запросов поиска пользователей: {}/3 (15 мин)", userLookupRequests);
                break;
        }
    }

    /**
     * Инициализирует Twitter API клиент
     */
    private void initializeTwitterApiIfNeeded() {
        if (twitterApi != null) return;

        Optional<TwitterSettings> settingsOpt = twitterSettingsRepository.findFirst();
        if (settingsOpt.isEmpty() || !settingsOpt.get().getIsEnabled()) {
            log.debug("Twitter интеграция отключена или не настроена");
            return;
        }

        TwitterSettings settings = settingsOpt.get();
        if (settings.getBearerToken() == null || settings.getBearerToken().trim().isEmpty()) {
            log.warn("Twitter Bearer Token не настроен");
            return;
        }

        try {
            TwitterCredentialsBearer credentials = new TwitterCredentialsBearer(settings.getBearerToken());
            twitterApi = new TwitterApi(credentials);
            log.info("Twitter API клиент инициализирован успешно");
        } catch (Exception e) {
            log.error("Ошибка инициализации Twitter API: {}", e.getMessage());
            twitterApi = null;
        }
    }

    /**
     * Запланированная задача для получения новых твитов (адаптировано для Free плана)
     */
    @Scheduled(fixedDelayString = "#{900000}") // 15 минут = 900000 мс (Free план лимит)
    @Transactional
    public void fetchLatestTweets() {
        Optional<TwitterSettings> settingsOpt = twitterSettingsRepository.findFirst();
        if (settingsOpt.isEmpty() || !settingsOpt.get().getIsEnabled()) {
            log.debug("Twitter интеграция отключена");
            return;
        }

        initializeTwitterApiIfNeeded();
        if (twitterApi == null) {
            log.warn("Twitter API не инициализирован");
            return;
        }

        List<TwitterUser> activeUsers = twitterUserRepository.findByIsActiveTrue();
        if (activeUsers.isEmpty()) {
            log.debug("Нет активных Twitter пользователей для отслеживания");
            return;
        }

        // Free план: обрабатываем только одного пользователя за раз
        if (!checkRateLimit("user_tweets")) {
            log.warn("Достигнут лимит запросов твитов (1/15мин). Ожидание следующего интервала");
            return;
        }

        // Ротация пользователей для равномерного обновления
        TwitterUser user = activeUsers.get(currentUserIndex % activeUsers.size());
        currentUserIndex = (currentUserIndex + 1) % activeUsers.size();

        log.info("Получение твитов для пользователя: {} ({}/{} активных)",
                user.getUsername(), (currentUserIndex == 0 ? activeUsers.size() : currentUserIndex), activeUsers.size());

        try {
            fetchTweetsForUser(user);
            incrementRateLimit("user_tweets");
        } catch (Exception e) {
            log.error("Ошибка получения твитов для пользователя {}: {}", user.getUsername(), e.getMessage());
        }
    }

    /**
     * Получает твиты для конкретного пользователя
     */
    private void fetchTweetsForUser(TwitterUser user) {
        try {
            // ИСПРАВЛЕНИЕ: Создание TweetsApi без параметров и установка клиента
            TweetsApi tweetsApi = new TweetsApi();
            tweetsApi.setClient(twitterApi.getApiClient());

            // Получаем твиты пользователя
            Get2UsersIdTweetsResponse response = tweetsApi.usersIdTweets(user.getUserId())
                    .maxResults(10)
                    .tweetFields(Set.of("created_at", "author_id", "public_metrics", "text", "lang"))
                    .expansions(Set.of("author_id"))
                    .userFields(Set.of("username", "name", "public_metrics"))
                    .execute();

            if (response.getData() == null || response.getData().isEmpty()) {
                log.debug("Новых твитов для пользователя {} не найдено", user.getUsername());
                return;
            }

            List<Tweet> tweets = response.getData();
            Map<String, User> usersMap = createUsersMap(response.getIncludes());

            int savedCount = 0;
            String latestTweetId = null;

            for (Tweet tweet : tweets) {
                if (latestTweetId == null) {
                    latestTweetId = tweet.getId();
                }

                // Проверяем, не сохранен ли уже этот твит
                if (newsMessageRepository.findByTwitterTweetId(tweet.getId()).isPresent()) {
                    continue;
                }

                // Фильтруем по языку (только английский и русский)
                if (tweet.getLang() != null &&
                        !tweet.getLang().equals("en") &&
                        !tweet.getLang().equals("ru")) {
                    continue;
                }

                User tweetAuthor = usersMap.get(tweet.getAuthorId());
                NewsMessage newsMessage = createNewsMessageFromTweet(tweet, tweetAuthor, user);
                newsMessageRepository.save(newsMessage);
                savedCount++;
            }

            // Обновляем последний ID твита
            if (latestTweetId != null) {
                user.setLastTweetId(latestTweetId);
                twitterUserRepository.save(user);
            }

            log.info("Сохранено {} новых твитов для пользователя {}", savedCount, user.getUsername());

        } catch (Exception e) {
            log.error("Ошибка при получении твитов для пользователя {}: {}", user.getUsername(), e.getMessage());
        }
    }

    /**
     * Создает карту пользователей из ответа API
     */
    private Map<String, User> createUsersMap(Expansions includes) {
        if (includes == null || includes.getUsers() == null) {
            return Collections.emptyMap();
        }

        return includes.getUsers().stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    /**
     * Создает NewsMessage из Twitter Tweet
     */
    private NewsMessage createNewsMessageFromTweet(Tweet tweet, User author, TwitterUser trackedUser) {
        NewsMessage newsMessage = new NewsMessage();

        newsMessage.setSourceType(SourceType.X);
        newsMessage.setTwitterTweetId(tweet.getId());
        newsMessage.setTwitterUsername(trackedUser.getUsername());
        newsMessage.setTwitterDisplayName(trackedUser.getDisplayName());
        newsMessage.setMessageText(tweet.getText());

        // Конвертируем время из OffsetDateTime в LocalDateTime
        if (tweet.getCreatedAt() != null) {
            newsMessage.setMessageDate(tweet.getCreatedAt().atZoneSameInstant(ZoneOffset.systemDefault()).toLocalDateTime());
        } else {
            newsMessage.setMessageDate(LocalDateTime.now());
        }

        // Метрики твита
        if (tweet.getPublicMetrics() != null) {
            TweetPublicMetrics metrics = tweet.getPublicMetrics();
            newsMessage.setRetweetCount(metrics.getRetweetCount());
            newsMessage.setLikeCount(metrics.getLikeCount());
            newsMessage.setReplyCount(metrics.getReplyCount());
        }

        newsMessage.setIsVisible(true);

        return newsMessage;
    }

    /**
     * Добавляет нового пользователя Twitter для отслеживания
     */
    @Transactional
    public void addTwitterUser(String username) {
        if (twitterUserRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Пользователь уже существует: " + username);
        }

        if (!checkRateLimit("user_lookup")) {
            throw new RuntimeException("Достигнут лимит запросов поиска пользователей (3/15мин). Попробуйте позже.");
        }

        initializeTwitterApiIfNeeded();
        if (twitterApi == null) {
            throw new RuntimeException("Twitter API не настроен");
        }

        try {
            // ИСПРАВЛЕНИЕ: Создание UsersApi без параметров и установка клиента
            UsersApi usersApi = new UsersApi();
            usersApi.setClient(twitterApi.getApiClient());

            Get2UsersByUsernameUsernameResponse response = usersApi.findUserByUsername(username)
                    .userFields(Set.of("id", "username", "name", "description", "public_metrics"))
                    .execute();

            incrementRateLimit("user_lookup");

            if (response.getData() == null) {
                throw new IllegalArgumentException("Пользователь не найден: " + username);
            }

            User twitterUserData = response.getData();

            TwitterUser user = new TwitterUser();
            user.setUsername(twitterUserData.getUsername());
            user.setDisplayName(twitterUserData.getName());
            user.setUserId(twitterUserData.getId());
            user.setDescription(twitterUserData.getDescription());
            user.setIsActive(true);

            if (twitterUserData.getPublicMetrics() != null) {
                user.setFollowersCount(twitterUserData.getPublicMetrics().getFollowersCount());
            }

            twitterUserRepository.save(user);
            log.info("Добавлен Twitter пользователь: {} ({})", username, twitterUserData.getName());

        } catch (Exception e) {
            log.error("Ошибка при добавлении Twitter пользователя {}: {}", username, e.getMessage());
            throw new RuntimeException("Не удалось добавить пользователя: " + e.getMessage());
        }
    }

    /**
     * Тестирует соединение с Twitter API
     */
    /**
     * Тестирует соединение с Twitter API
     */
    public boolean testConnection(TwitterSettings settings) {
        if (settings.getBearerToken() == null || settings.getBearerToken().trim().isEmpty()) {
            log.warn("Bearer Token не указан");
            return false;
        }

        if (!checkRateLimit("user_lookup")) {
            log.warn("Достигнут лимит запросов поиска пользователей (3/15мин)");
            return false;
        }

        try {
            log.debug("Создание Twitter API клиента для тестирования...");
            TwitterCredentialsBearer credentials = new TwitterCredentialsBearer(settings.getBearerToken());
            TwitterApi testApi = new TwitterApi(credentials);

            log.debug("Создание UsersApi...");
            UsersApi usersApi = new UsersApi();
            usersApi.setClient(testApi.getApiClient());

            log.debug("Выполнение тестового запроса к Twitter API...");

            // Попробуем несколько разных подходов для тестирования
            String[] testUsernames = {"elonmusk", "twitter", "x"};

            for (String testUsername : testUsernames) {
                try {
                    log.debug("Тестирование с пользователем: {}", testUsername);

                    Get2UsersByUsernameUsernameResponse response = usersApi.findUserByUsername(testUsername)
                            .userFields(Set.of("id", "username", "name"))
                            .execute();

                    incrementRateLimit("user_lookup");

                    if (response != null) {
                        log.debug("Получен ответ от API. Data: {}, Errors: {}",
                                response.getData(), response.getErrors());

                        if (response.getData() != null) {
                            log.info("✅ Тест Twitter API: успешно. Получен пользователь: {} (@{})",
                                    response.getData().getName(), response.getData().getUsername());
                            return true;
                        } else if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                            log.warn("Ошибки API для пользователя {}: {}", testUsername, response.getErrors());
                        }
                    } else {
                        log.warn("Получен null ответ для пользователя: {}", testUsername);
                    }

                    // Проверим лимиты перед следующей попыткой
                    if (!checkRateLimit("user_lookup")) {
                        log.warn("Достигнут лимит запросов, прерываем тестирование");
                        break;
                    }

                } catch (Exception userException) {
                    log.warn("Ошибка при тестировании с пользователем {}: {}",
                            testUsername, userException.getMessage());

                    // Если это не последний пользователь, попробуем следующего
                    if (!testUsername.equals(testUsernames[testUsernames.length - 1])) {
                        continue;
                    }
                }
            }

            log.warn("❌ Тест Twitter API: ошибка - не удалось получить данные ни одного пользователя");
            return false;

        } catch (Exception e) {
            log.error("❌ Ошибка тестирования Twitter API: {} - {}",
                    e.getClass().getSimpleName(), e.getMessage());

            // Дополнительная информация об исключении
            if (e.getCause() != null) {
                log.error("Причина ошибки: {}", e.getCause().getMessage());
            }

            // Попробуем определить тип ошибки
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("401")) {
                    log.error("🔑 Ошибка авторизации: проверьте Bearer Token");
                } else if (errorMessage.contains("403")) {
                    log.error("🚫 Доступ запрещен: возможно, токен не имеет необходимых разрешений");
                } else if (errorMessage.contains("429")) {
                    log.error("⏰ Превышен лимит запросов API");
                } else if (errorMessage.contains("timeout")) {
                    log.error("⏰ Таймаут соединения с Twitter API");
                }
            }

            return false;
        }
    }

    /**
     * Получает список активных пользователей
     */
    public List<TwitterUser> getActiveUsers() {
        return twitterUserRepository.findByIsActiveTrue();
    }

    /**
     * Переключает статус пользователя
     */
    @Transactional
    public void toggleUserStatus(Long userId) {
        TwitterUser user = twitterUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        user.setIsActive(!user.getIsActive());
        twitterUserRepository.save(user);
        log.info("Статус пользователя {} изменен на: {}", user.getUsername(), user.getIsActive());
    }

    /**
     * Удаляет пользователя
     */
    @Transactional
    public void removeUser(Long userId) {
        TwitterUser user = twitterUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        String username = user.getUsername();
        twitterUserRepository.delete(user);
        log.info("Удален Twitter пользователь: {}", username);
    }

    /**
     * Получает настройки Twitter
     */
    public TwitterSettings getSettings() {
        return twitterSettingsRepository.findFirst().orElse(new TwitterSettings());
    }

    /**
     * Сохраняет настройки Twitter
     */
    @Transactional
    public TwitterSettings saveSettings(TwitterSettings settings) {
        log.info("Сохранение настроек Twitter (включено: {})", settings.getIsEnabled());

        // Сбрасываем API клиент при изменении настроек
        twitterApi = null;

        return twitterSettingsRepository.save(settings);
    }

    /**
     * Принудительно обновляет информацию о пользователе (ограничено Free планом)
     */
    @Transactional
    public void refreshUserInfo(Long userId) {
        if (!checkRateLimit("user_info")) {
            throw new RuntimeException("Достигнут дневной лимит запросов информации о пользователе (1/24ч)");
        }

        TwitterUser user = twitterUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        initializeTwitterApiIfNeeded();
        if (twitterApi == null) {
            throw new RuntimeException("Twitter API не настроен");
        }

        try {
            // ИСПРАВЛЕНИЕ: Создание UsersApi без параметров и установка клиента
            UsersApi usersApi = new UsersApi();
            usersApi.setClient(twitterApi.getApiClient());

            Get2UsersIdResponse response = usersApi.findUserById(user.getUserId())
                    .userFields(Set.of("id", "username", "name", "description", "public_metrics"))
                    .execute();

            incrementRateLimit("user_info");

            if (response.getData() != null) {
                User twitterUserData = response.getData();
                user.setDisplayName(twitterUserData.getName());
                user.setDescription(twitterUserData.getDescription());

                if (twitterUserData.getPublicMetrics() != null) {
                    user.setFollowersCount(twitterUserData.getPublicMetrics().getFollowersCount());
                }

                twitterUserRepository.save(user);
                log.info("Обновлена информация о пользователе: {}", user.getUsername());
            }

        } catch (Exception e) {
            log.error("Ошибка обновления информации о пользователе {}: {}", user.getUsername(), e.getMessage());
            throw new RuntimeException("Не удалось обновить информацию: " + e.getMessage());
        }
    }

    /**
     * Получает статистику Twitter интеграции
     */
    public Map<String, Object> getTwitterStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("activeUsers", twitterUserRepository.countActiveUsers());
        stats.put("totalTweets", newsMessageRepository.findLatestNewsBySourceType(SourceType.X, Integer.MAX_VALUE).size());

        // ИСПРАВЛЕНИЕ: Правильное получение количества твитов за сегодня
        long todayTweetsCount = getTodayTwitterCount();
        stats.put("todayTweets", todayTweetsCount);

        stats.put("isEnabled", twitterSettingsRepository.findFirst().map(TwitterSettings::getIsEnabled).orElse(false));

        // Добавляем информацию о лимитах
        stats.put("rateLimits", getRateLimitInfo());

        return stats;
    }

    /**
     * Получает количество твитов за сегодня
     */
    private long getTodayTwitterCount() {
        try {
            LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
            List<Object[]> results = newsMessageRepository.getNewsCountBySource(startOfDay);

            // Ищем количество для TWITTER
            for (Object[] result : results) {
                if (result.length >= 2 && result[0] instanceof SourceType) {
                    SourceType sourceType = (SourceType) result[0];
                    if (sourceType == SourceType.X) {
                        return ((Number) result[1]).longValue();
                    }
                }
            }

            // Если не найдено, возвращаем 0
            return 0;

        } catch (Exception e) {
            log.error("Ошибка при получении количества твитов за сегодня: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Возвращает информацию о текущих лимитах
     */
    public Map<String, Object> getRateLimitInfo() {
        Map<String, Object> limits = new HashMap<>();

        limits.put("tweetRequestsUsed", quarterHourTweetRequests.get());
        limits.put("tweetRequestsLimit", 1);
        limits.put("userLookupRequestsUsed", quarterHourUserRequests.get());
        limits.put("userLookupRequestsLimit", 3);
        limits.put("userInfoRequestsUsed", dailyUserInfoRequests.get());
        limits.put("userInfoRequestsLimit", 1);

        limits.put("nextTweetRequestReset", lastQuarterHourReset.plusMinutes(15));
        limits.put("nextUserInfoRequestReset", lastUserInfoReset.plusDays(1));

        return limits;
    }
}