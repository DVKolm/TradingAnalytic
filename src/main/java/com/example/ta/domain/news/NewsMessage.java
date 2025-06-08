package com.example.ta.domain.news;

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

    // Новые поля для Twitter интеграции
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private SourceType sourceType = SourceType.TELEGRAM;

    @Column(name = "twitter_tweet_id")
    private String twitterTweetId;

    @Column(name = "twitter_username")
    private String twitterUsername;

    @Column(name = "twitter_display_name")
    private String twitterDisplayName;

    @Column(name = "retweet_count")
    private Integer retweetCount;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(name = "reply_count")
    private Integer replyCount;

    @Column(name = "has_media")
    private Boolean hasMedia = false;

    @Column(name = "media_type")
    private String mediaType;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "media_file_path")
    private String mediaFilePath;

    @Column(name = "media_thumbnail_path")
    private String mediaThumbnailPath;


    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (sourceType == null) {
            sourceType = SourceType.TELEGRAM;
        }
    }
}
