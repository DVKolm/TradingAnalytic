package com.example.ta.config;

import com.example.ta.domain.news.TwitterSettings;
import com.example.ta.repository.TwitterSettingsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
@EnableTransactionManagement(proxyTargetClass = true)
public class ApplicationConfig {

    @Bean
    @ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = false)
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        return new TelegramBotsApi(DefaultBotSession.class);
    }

    @Bean
    public TwitterSettings twitterSettings(TwitterSettingsRepository repository) {
        return repository.findFirst()
                .orElse(createDefaultTwitterSettings());
    }

    private TwitterSettings createDefaultTwitterSettings() {
        TwitterSettings settings = new TwitterSettings();
        settings.setIsEnabled(false);
        settings.setPollIntervalMinutes(5);
        return settings;
    }

}

