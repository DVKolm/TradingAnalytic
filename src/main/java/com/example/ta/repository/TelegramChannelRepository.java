package com.example.ta.repository;

import com.example.ta.domain.news.TelegramChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramChannelRepository extends JpaRepository<TelegramChannel, Long> {
    
    Optional<TelegramChannel> findByUsername(String username);
    
    @Query("SELECT t FROM TelegramChannel t WHERE t.isActive = true")
    List<TelegramChannel> findAllActive();
}