package com.example.ta.repository;

import com.example.ta.domain.NewsMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsMessageRepository extends JpaRepository<NewsMessage, Long> {

    /**
     * Поиск сообщения по ID сообщения Telegram и username канала
     */
    Optional<NewsMessage> findByTelegramMessageIdAndChannelUsername(
            Integer telegramMessageId,
            String channelUsername
    );

    /**
     * Получение всех видимых новостей в порядке убывания даты
     */
    @Query("SELECT n FROM NewsMessage n WHERE n.isVisible = true ORDER BY n.messageDate DESC")
    List<NewsMessage> findAllVisibleOrderByDateDesc();

    /**
     * Получение топ видимых новостей с лимитом
     */
    @Query("SELECT n FROM NewsMessage n WHERE n.isVisible = true ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findTopVisibleNews(@Param("limit") int limit);

    /**
     * 🆕 Получение последних новостей с лимитом (основной метод для панели новостей)
     */
    @Query("SELECT n FROM NewsMessage n ORDER BY n.messageDate DESC, n.id DESC LIMIT :limit")
    List<NewsMessage> findLatestNews(@Param("limit") int limit);

    /**
     * 🆕 Получение последних новостей из конкретного канала
     */
    @Query("SELECT n FROM NewsMessage n WHERE n.channelUsername = :channelUsername " +
            "ORDER BY n.messageDate DESC, n.id DESC LIMIT :limit")
    List<NewsMessage> findLatestNewsByChannel(
            @Param("channelUsername") String channelUsername,
            @Param("limit") int limit
    );

    /**
     * 🆕 Получение новостей за последние часы
     */
    @Query("SELECT n FROM NewsMessage n WHERE n.messageDate >= :since " +
            "ORDER BY n.messageDate DESC, n.id DESC")
    List<NewsMessage> findNewsSince(@Param("since") LocalDateTime since);

    /**
     * 🆕 Получение новостей по ключевым словам
     */
    @Query("SELECT n FROM NewsMessage n WHERE " +
            "LOWER(n.messageText) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(n.channelTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findNewsByKeyword(
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );

    /**
     * 🆕 Получение статистики по каналам
     */
    @Query("SELECT n.channelUsername, n.channelTitle, COUNT(n) as messageCount " +
            "FROM NewsMessage n " +
            "GROUP BY n.channelUsername, n.channelTitle " +
            "ORDER BY COUNT(n) DESC")
    List<Object[]> getChannelStatistics();

    /**
     * 🆕 Получение новостей только из активных каналов
     */
    @Query("SELECT n FROM NewsMessage n " +
            "WHERE n.channelUsername IN (" +
            "  SELECT tc.username FROM TelegramChannel tc WHERE tc.isActive = true" +
            ") " +
            "ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findLatestNewsFromActiveChannels(@Param("limit") int limit);

    /**
     * 🔧 ИСПРАВЛЕНО: Подсчет новых сообщений за сегодня
     */
    @Query("SELECT COUNT(n) FROM NewsMessage n WHERE " +
            "n.messageDate >= :startOfDay AND n.messageDate < :endOfDay")
    long countTodayMessages(@Param("startOfDay") LocalDateTime startOfDay,
                            @Param("endOfDay") LocalDateTime endOfDay);

    /**
     * 🆕 Получение новостей за определенный период
     */
    @Query("SELECT n FROM NewsMessage n WHERE n.messageDate BETWEEN :startDate AND :endDate " +
            "ORDER BY n.messageDate DESC")
    List<NewsMessage> findNewsBetweenDates(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // Существующие методы для автоочистки
    long deleteByCreatedAtBefore(LocalDateTime cutoffDate);

    long countByCreatedAtBefore(LocalDateTime cutoffDate);

    long countByIsVisible(boolean isVisible);

    long countByCreatedAtAfter(LocalDateTime date);
}