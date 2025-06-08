package com.example.ta.service;

import com.example.ta.domain.news.TwitterSettings;
import com.example.ta.repository.TwitterSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TwitterSettingsService {

    private final TwitterSettingsRepository settingsRepository;
    private final TwitterNewsService twitterNewsService;

    public Optional<TwitterSettings> getCurrentSettings() {
        return settingsRepository.findFirst();
    }

    public TwitterSettings saveSettings(TwitterSettings settings) {
        log.info("Сохранение настроек Twitter (включено: {})", settings.getIsEnabled());

        // Отключаем, если обязательные поля пустые
        if (settings.getIsEnabled() && !hasRequiredFields(settings)) {
            log.warn("Отключение Twitter: отсутствуют обязательные настройки");
            settings.setIsEnabled(false);
        }

        return settingsRepository.save(settings);
    }

    public boolean testConnection(TwitterSettings settings) {
        if (!hasRequiredFields(settings)) {
            log.warn("Невозможно протестировать: отсутствуют обязательные поля");
            return false;
        }

        try {
            boolean success = twitterNewsService.testConnection(settings);
            log.info("Результат тестирования Twitter: {}", success ? "успех" : "ошибка");
            return success;
        } catch (Exception e) {
            log.error("Ошибка при тестировании Twitter соединения", e);
            return false;
        }
    }

    public void enableTwitter() {
        Optional<TwitterSettings> settingsOpt = getCurrentSettings();
        if (settingsOpt.isPresent()) {
            TwitterSettings settings = settingsOpt.get();

            if (hasRequiredFields(settings)) {
                settings.setIsEnabled(true);
                saveSettings(settings);
                log.info("✅ Twitter интеграция включена");
            } else {
                log.warn("❌ Невозможно включить Twitter: заполните настройки");
            }
        } else {
            log.warn("❌ Настройки Twitter не найдены");
        }
    }

    public void disableTwitter() {
        Optional<TwitterSettings> settingsOpt = getCurrentSettings();
        if (settingsOpt.isPresent()) {
            TwitterSettings settings = settingsOpt.get();
            settings.setIsEnabled(false);
            saveSettings(settings);
            log.info("❌ Twitter интеграция отключена");
        }
    }

    public TwitterSettings getDefaultSettings() {
        TwitterSettings settings = new TwitterSettings();
        settings.setIsEnabled(false);
        settings.setPollIntervalMinutes(5);
        return settings;
    }

    /**
     * Проверяет наличие обязательных полей
     */
    public boolean hasRequiredFields(TwitterSettings settings) {
        return settings != null
                && settings.getBearerToken() != null
                && !settings.getBearerToken().trim().isEmpty();
    }

    /**
     * Проверяет, настроен ли и включен ли Twitter
     */
    public boolean isTwitterEnabled() {
        Optional<TwitterSettings> settings = getCurrentSettings();
        return settings.isPresent() 
                && settings.get().getIsEnabled() 
                && hasRequiredFields(settings.get());
    }

    /**
     * Возвращает статус интеграции для отображения в UI
     */
    public String getTwitterStatus() {
        Optional<TwitterSettings> settings = getCurrentSettings();

        if (settings.isEmpty()) {
            return "Не настроено";
        }

        TwitterSettings s = settings.get();

        if (!hasRequiredFields(s)) {
            return "Неполные настройки";
        }

        if (!s.getIsEnabled()) {
            return "Отключено";
        }

        return "Активно";
    }
}