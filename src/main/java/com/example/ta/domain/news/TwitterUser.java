package com.example.ta.domain.news;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "twitter_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwitterUser {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "username", unique = true)
    private String username;
    
    @Column(name = "display_name")
    private String displayName;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "followers_count")
    private Integer followersCount;
    
    @Column(name = "last_tweet_id")
    private String lastTweetId;
    
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