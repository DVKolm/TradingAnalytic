package com.example.ta.service;

import com.example.ta.domain.news.TelegramSettings;
import com.example.ta.domain.trading.Trade;
import com.example.ta.repository.TelegramSettingsRepository;
import com.example.ta.util.NumberFormatUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final TelegramSettingsRepository settingsRepository;
    private TelegramLongPollingBot bot;

    /**
     * Инициализирует бота только при наличии валидных настроек
     */
    private void initializeBotIfNeeded() {
        if (bot != null) return;

        Optional<TelegramSettings> settingsOpt = getEnabledSettings();
        if (settingsOpt.isEmpty()) {
            log.debug("Telegram настройки отсутствуют или отключены");
            return;
        }

        TelegramSettings settings = settingsOpt.get();
        if (isValidSettings(settings)) {
            try {
                bot = new TradingAnalyticsBot(settings.getBotToken());
                log.info("Telegram бот инициализирован успешно");
            } catch (Exception e) {
                log.error("Ошибка инициализации Telegram бота", e);
                bot = null;
            }
        }
    }

    /**
     * Отправляет уведомление о сделке (только если настройки корректны)
     */
    public void sendTradeNotification(Trade trade, String action) {
        try {
            Optional<TelegramSettings> settingsOpt = getEnabledSettings();

            if (settingsOpt.isEmpty()) {
                log.debug("Telegram уведомления отключены или не настроены");
                return;
            }

            TelegramSettings settings = settingsOpt.get();

            if (!isValidSettings(settings)) {
                log.warn("Некорректные настройки Telegram, уведомление не отправлено");
                return;
            }

            if (!shouldSendNotification(settings, action)) {
                log.debug("Уведомление для действия '{}' отключено", action);
                return;
            }

            initializeBotIfNeeded();
            if (bot == null) {
                log.warn("Telegram бот не инициализирован, уведомление не отправлено");
                return;
            }

            String message = formatTradeMessage(trade, action, settings);

            if (settings.getIncludeChartImage() && trade.hasChartImage()) {
                sendPhotoWithMessage(settings.getChatId(), message, trade.getChartImagePath());
            } else {
                sendTextMessage(settings.getChatId(), message);
            }

            log.info("✅ Отправлено Telegram уведомление: {} ({})", trade.getAssetName(), action);

        } catch (Exception e) {
            log.error("❌ Ошибка при отправке Telegram уведомления", e);
        }
    }

    /**
     * Тестирует соединение с указанными настройками
     */
    public boolean testConnection(TelegramSettings settings) {
        if (!isValidSettings(settings)) {
            log.warn("Некорректные настройки для тестирования");
            return false;
        }

        try {
            TelegramLongPollingBot testBot = new TradingAnalyticsBot(settings.getBotToken());

            SendMessage message = new SendMessage();
            message.setChatId(settings.getChatId());
            message.setText("🤖 Тестовое сообщение от Trading Analytics!\n\n✅ Настройки работают корректно!");
            message.setParseMode("HTML");

            testBot.execute(message);
            log.info("✅ Тест соединения с Telegram успешен");
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка тестирования Telegram соединения: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверяет валидность настроек
     */
    private boolean isValidSettings(TelegramSettings settings) {
        return settings != null
                && settings.getEnabled()
                && settings.getBotToken() != null
                && !settings.getBotToken().trim().isEmpty()
                && settings.getChatId() != null
                && !settings.getChatId().trim().isEmpty();
    }

    private void sendTextMessage(String chatId, String text) {
        if (bot == null) return;

        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(text);
            message.setParseMode("HTML");

            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки текстового сообщения: {}", e.getMessage());
        }
    }

    private void sendPhotoWithMessage(String chatId, String caption, String imagePath) {
        if (bot == null) return;

        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                log.warn("Изображение не найдено: {}, отправляем только текст", imagePath);
                sendTextMessage(chatId, caption);
                return;
            }

            SendPhoto photo = new SendPhoto();
            photo.setChatId(chatId);
            photo.setPhoto(new InputFile(imageFile));
            photo.setCaption(caption);
            photo.setParseMode("HTML");

            bot.execute(photo);
        } catch (Exception e) {
            log.error("Ошибка отправки фото: {}, отправляем только текст", e.getMessage());
            sendTextMessage(chatId, caption);
        }
    }

    private String formatTradeMessage(Trade trade, String action, TelegramSettings settings) {
        if (settings.getMessageTemplate() != null && !settings.getMessageTemplate().trim().isEmpty()) {
            return formatCustomTemplate(trade, action, settings.getMessageTemplate());
        }
        return formatDefaultMessage(trade, action);
    }

    private String formatDefaultMessage(Trade trade, String action) {
        StringBuilder message = new StringBuilder();

        String actionEmoji = switch (action.toLowerCase()) {
            case "open", "create" -> "🟢";
            case "close" -> "🔴";
            case "update" -> "🟡";
            default -> "📊";
        };

        String actionText = switch (action.toLowerCase()) {
            case "open", "create" -> "ОТКРЫТА ПОЗИЦИЯ";
            case "close" -> "ЗАКРЫТА ПОЗИЦИЯ";
            case "update" -> "ОБНОВЛЕНА ПОЗИЦИЯ";
            default -> "ИНФОРМАЦИЯ О СДЕЛКЕ";
        };

        message.append(actionEmoji).append(" <b>").append(actionText).append("</b>\n\n");
        message.append("💰 <b>Актив:</b> ").append(trade.getAssetName()).append("\n");
        message.append("📈 <b>Тип:</b> ").append(trade.getTradeType().getDisplayName()).append("\n");
        message.append("💵 <b>Цена входа:</b> ").append(formatPrice(trade.getEntryPoint())).append("\n");

        if (trade.getTakeProfitTarget() != null) {
            message.append("🎯 <b>Цель (Take Profit):</b> ").append(formatPrice(trade.getTakeProfitTarget())).append("\n");

            // Показываем потенциальную прибыль до цели
            BigDecimal potentialProfitToTarget = trade.calculatePotentialProfitToTarget();
            if (potentialProfitToTarget != null) {
                message.append("💎 <b>Потенциальная прибыль:</b> ").append(formatProfitLoss(potentialProfitToTarget)).append("\n");
            }

            // Показываем процент до цели
            BigDecimal percentageToTarget = trade.calculatePercentageToTarget();
            if (percentageToTarget != null) {
                message.append("📊 <b>% до цели:</b> ").append(formatPercentage(percentageToTarget)).append("\n");
            }
        }


        if (trade.getExitPoint() != null) {
            message.append("🏁 <b>Цена выхода:</b> ").append(formatPrice(trade.getExitPoint())).append("\n");
        }

        message.append("📊 <b>Объем:</b> ").append(formatVolume(trade.getVolume())).append(trade.getAssetName()).append("\n");
        message.append("💎 <b>Объем в USD:</b> ").append(formatVolumeInCurrency(trade.getVolumeInCurrency())).append("\n");

        if (trade.getProfitLoss() != null) {
            String profitEmoji = trade.getProfitLoss().compareTo(BigDecimal.ZERO) >= 0 ? "💚" : "❤️";
            message.append(profitEmoji).append(" <b>Потенциальная прибыль:</b> ").append(formatProfitLoss(trade.getProfitLoss())).append("\n");
        }

        if (trade.getPriceMovementPercent() != null) {
            message.append("📊 <b>Движение:</b> ").append(formatPercentage(trade.getPriceMovementPercent())).append("\n");
        }

        message.append("📅 <b>Дата:</b> ").append(trade.getTradeDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append("\n");
        message.append("🔷 <b>Статус:</b> ").append(trade.getStatus().getDisplayName()).append("\n");

        if (trade.getEntryReason() != null && !trade.getEntryReason().trim().isEmpty()) {
            message.append("\n💭 <b>Причина входа:</b>\n").append(trade.getEntryReason()).append("\n");
        }

        message.append("\n⏰ ").append(java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

        return message.toString();
    }

    private String formatCustomTemplate(Trade trade, String action, String template) {
        return template
                .replace("{asset}", trade.getAssetName())
                .replace("{type}", trade.getTradeType().getDisplayName())
                .replace("{entryPrice}", formatPrice(trade.getEntryPoint()))
                .replace("{exitPrice}", trade.getExitPoint() != null ? formatPrice(trade.getExitPoint()) : "N/A")
                .replace("{volume}", formatVolume(trade.getVolume()))
                .replace("{profitLoss}", formatProfitLoss(trade.getProfitLoss()))
                .replace("{status}", trade.getStatus().getDisplayName())
                .replace("{action}", action.toUpperCase());
    }

    /**
     * Форматирует цену с использованием NumberFormatUtil
     * Результат: $156,00
     */
    private String formatPrice(BigDecimal price) {
        if (price == null) return "N/A";
        return "$" + NumberFormatUtil.formatNumberWithSpaces(price);
    }

    /**
     * Форматирует объем с двумя знаками после запятой
     * Результат: 15,00
     */
    private String formatVolume(BigDecimal volume) {
        if (volume == null) return "N/A";
        return NumberFormatUtil.formatNumber(volume, 2);
    }

    /**
     * Форматирует объем в валюте
     * Результат: $2 340,00
     */
    private String formatVolumeInCurrency(BigDecimal volumeInCurrency) {
        if (volumeInCurrency == null) return "N/A";
        return "$" + NumberFormatUtil.formatNumberWithSpaces(volumeInCurrency);
    }

    /**
     * Форматирует прибыль/убыток
     * Результат: $285,00
     */
    private String formatProfitLoss(BigDecimal profitLoss) {
        if (profitLoss == null) return "N/A";
        return "$" + NumberFormatUtil.formatNumberWithSpaces(profitLoss);
    }

    /**
     * Форматирует процентное движение
     * Результат: 12,18%
     */
    private String formatPercentage(BigDecimal percentage) {
        if (percentage == null) return "N/A";
        return NumberFormatUtil.formatPercentage(percentage);
    }

    private boolean shouldSendNotification(TelegramSettings settings, String action) {
        return switch (action.toLowerCase()) {
            case "open", "create" -> settings.getSendOnTradeOpen();
            case "close" -> settings.getSendOnTradeClose();
            case "update" -> settings.getSendOnTradeUpdate();
            default -> false;
        };
    }

    private Optional<TelegramSettings> getEnabledSettings() {
        return settingsRepository.findEnabledSettings();
    }

    /**
     * Внутренний класс бота
     */
    private static class TradingAnalyticsBot extends TelegramLongPollingBot {
        private final String token;

        public TradingAnalyticsBot(String token) {
            this.token = token;
        }

        @Override
        public String getBotUsername() {
            return "TradingAnalyticsBot";
        }

        @Override
        public String getBotToken() {
            return token;
        }

        @Override
        public void onUpdateReceived(Update update) {
            // Обработка команд
            if (update.hasMessage() && update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();
                String chatId = update.getMessage().getChatId().toString();

                if ("/start".equals(messageText)) {
                    sendWelcomeMessage(chatId);
                } else if ("/chatid".equals(messageText)) {
                    sendChatId(chatId);
                }
            }
        }

        private void sendWelcomeMessage(String chatId) {
            String welcomeText = """
                    🤖 <b>Trading Analytics Bot</b>
                    
                    Добро пожаловать! Этот бот будет отправлять уведомления о ваших торговых сделках.
                    
                    📋 <b>Команды:</b>
                    /start - показать это сообщение
                    /chatid - получить ID этого чата
                    
                    🆔 <b>Chat ID:</b> <code>%s</code>
                    
                    Скопируйте Chat ID и используйте его в настройках приложения.
                    """.formatted(chatId);

            sendText(chatId, welcomeText);
        }

        private void sendChatId(String chatId) {
            String text = """
                    🆔 <b>Chat ID этого чата:</b>
                    
                    <code>%s</code>
                    
                    Скопируйте этот ID и вставьте в настройки Telegram в приложении Trading Analytics.
                    """.formatted(chatId);

            sendText(chatId, text);
        }

        private void sendText(String chatId, String text) {
            try {
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText(text);
                message.setParseMode("HTML");
                execute(message);
            } catch (TelegramApiException e) {
                // Игнорируем ошибки в базовых командах
            }
        }
    }
}