package com.example.ta.repository;

import com.example.ta.domain.TelegramSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramSettingsRepository extends JpaRepository<TelegramSettings, Long> {
    
    @Query("SELECT ts FROM TelegramSettings ts ORDER BY ts.id DESC LIMIT 1")
    Optional<TelegramSettings> findCurrentSettings();
    
    @Query("SELECT ts FROM TelegramSettings ts WHERE ts.enabled = true ORDER BY ts.id DESC LIMIT 1")
    Optional<TelegramSettings> findEnabledSettings();
}