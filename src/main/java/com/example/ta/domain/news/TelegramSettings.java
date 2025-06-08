package com.example.ta.domain.news;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "bot_token")
    private String botToken;
    
    @Column(name = "chat_id")
    private String chatId;
    
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = false;
    
    @Column(name = "send_on_trade_open")
    @Builder.Default
    private Boolean sendOnTradeOpen = true;
    
    @Column(name = "send_on_trade_close")
    @Builder.Default
    private Boolean sendOnTradeClose = true;
    
    @Column(name = "send_on_trade_update")
    @Builder.Default
    private Boolean sendOnTradeUpdate = false;
    
    @Column(name = "include_chart_image")
    @Builder.Default
    private Boolean includeChartImage = true;
    
    @Column(name = "message_template", length = 1000)
    private String messageTemplate;
    
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PreUpdate
    private void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}