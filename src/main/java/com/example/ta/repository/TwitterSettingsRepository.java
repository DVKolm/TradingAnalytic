package com.example.ta.repository;

import com.example.ta.domain.news.TwitterSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TwitterSettingsRepository extends JpaRepository<TwitterSettings, Long> {

    /**
     * Получение первой записи настроек Twitter (должна быть только одна)
     */
    @Query("SELECT t FROM TwitterSettings t ORDER BY t.id LIMIT 1")
    Optional<TwitterSettings> findFirst();

    /**
     * Альтернативный способ получения первой записи
     */
    Optional<TwitterSettings> findTopByOrderByIdAsc();
}
