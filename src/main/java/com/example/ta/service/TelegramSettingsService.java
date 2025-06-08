package com.example.ta.service;

import com.example.ta.domain.news.TelegramSettings;
import com.example.ta.repository.TelegramSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TelegramSettingsService {

    private final TelegramSettingsRepository settingsRepository;
    private final TelegramNotificationService telegramService;

    public Optional<TelegramSettings> getCurrentSettings() {
        return settingsRepository.findCurrentSettings();
    }

    public TelegramSettings saveSettings(TelegramSettings settings) {
        log.info("Сохранение настроек Telegram (включено: {})", settings.getEnabled());

        // Отключаем, если обязательные поля пустые
        if (settings.getEnabled() && !hasRequiredFields(settings)) {
            log.warn("Отключение Telegram: отсутствуют обязательные настройки");
            settings.setEnabled(false);
        }

        return settingsRepository.save(settings);
    }

    public boolean testConnection(TelegramSettings settings) {
        if (!hasRequiredFields(settings)) {
            log.warn("Невозможно протестировать: отсутствуют обязательные поля");
            return false;
        }

        try {
            boolean success = telegramService.testConnection(settings);
            log.info("Результат тестирования Telegram: {}", success ? "успех" : "ошибка");
            return success;
        } catch (Exception e) {
            log.error("Ошибка при тестировании Telegram соединения", e);
            return false;
        }
    }

    public void enableNotifications() {
        Optional<TelegramSettings> settingsOpt = getCurrentSettings();
        if (settingsOpt.isPresent()) {
            TelegramSettings settings = settingsOpt.get();

            if (hasRequiredFields(settings)) {
                settings.setEnabled(true);
                saveSettings(settings);
                log.info("✅ Telegram уведомления включены");
            } else {
                log.warn("❌ Невозможно включить Telegram: заполните настройки");
            }
        } else {
            log.warn("❌ Настройки Telegram не найдены");
        }
    }

    public void disableNotifications() {
        Optional<TelegramSettings> settingsOpt = getCurrentSettings();
        if (settingsOpt.isPresent()) {
            TelegramSettings settings = settingsOpt.get();
            settings.setEnabled(false);
            saveSettings(settings);
            log.info("❌ Telegram уведомления отключены");
        }
    }

    public TelegramSettings getDefaultSettings() {
        return TelegramSettings.builder()
                .enabled(false)
                .sendOnTradeOpen(true)
                .sendOnTradeClose(true)
                .sendOnTradeUpdate(false)
                .includeChartImage(true)
                .messageTemplate("")
                .build();
    }

    /**
     * Проверяет наличие обязательных полей
     */
    public boolean hasRequiredFields(TelegramSettings settings) {
        return settings != null
                && settings.getBotToken() != null
                && !settings.getBotToken().trim().isEmpty()
                && settings.getChatId() != null
                && !settings.getChatId().trim().isEmpty();
    }

    /**
     * Проверяет, настроен ли и включен ли Telegram
     */
    public boolean isTelegramEnabled() {
        return settingsRepository.findEnabledSettings().isPresent();
    }

    /**
     * Возвращает статус интеграции для отображения в UI
     */
    public String getTelegramStatus() {
        Optional<TelegramSettings> settings = getCurrentSettings();

        if (settings.isEmpty()) {
            return "Не настроено";
        }

        TelegramSettings s = settings.get();

        if (!hasRequiredFields(s)) {
            return "Неполные настройки";
        }

        if (!s.getEnabled()) {
            return "Отключено";
        }

        return "Активно";
    }
}