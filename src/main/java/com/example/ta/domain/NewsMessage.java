package com.example.ta.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "telegram_message_id")
    private Integer telegramMessageId;
    
    @Column(name = "channel_username")
    private String channelUsername;
    
    @Column(name = "channel_title")
    private String channelTitle;
    
    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageText;
    
    @Column(name = "message_date")
    private LocalDateTime messageDate;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "is_visible")
    private Boolean isVisible = true;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}