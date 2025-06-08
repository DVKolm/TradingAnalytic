package com.example.ta.domain.news;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "twitter_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwitterSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "bearer_token")
    private String bearerToken;
    
    @Column(name = "api_key")
    private String apiKey;
    
    @Column(name = "api_secret")
    private String apiSecret;
    
    @Column(name = "access_token")
    private String accessToken;
    
    @Column(name = "access_token_secret")
    private String accessTokenSecret;
    
    @Column(name = "is_enabled")
    private Boolean isEnabled = false;
    
    @Column(name = "poll_interval_minutes")
    private Integer pollIntervalMinutes = 5;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}