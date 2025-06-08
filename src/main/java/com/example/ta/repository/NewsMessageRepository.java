package com.example.ta.repository;

import com.example.ta.domain.news.NewsMessage;
import com.example.ta.domain.news.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsMessageRepository extends JpaRepository<NewsMessage, Long> {

    // Существующие методы для Telegram
    Optional<NewsMessage> findByTelegramMessageIdAndChannelUsername(Integer telegramMessageId, String channelUsername);

    @Query("SELECT n FROM NewsMessage n WHERE n.isVisible = true ORDER BY n.messageDate DESC")
    List<NewsMessage> findAllVisibleOrderByDateDesc();

    @Query("SELECT n FROM NewsMessage n WHERE n.isVisible = true ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findTopVisibleNews(@Param("limit") int limit);

    @Query("SELECT n FROM NewsMessage n ORDER BY n.messageDate DESC, n.id DESC LIMIT :limit")
    List<NewsMessage> findLatestNews(@Param("limit") int limit);

    @Query("SELECT n FROM NewsMessage n WHERE n.channelUsername = :channelUsername " +
            "ORDER BY n.messageDate DESC, n.id DESC LIMIT :limit")
    List<NewsMessage> findLatestNewsByChannel(@Param("channelUsername") String channelUsername, @Param("limit") int limit);

    @Query("SELECT n FROM NewsMessage n WHERE n.messageDate >= :since " +
            "ORDER BY n.messageDate DESC, n.id DESC")
    List<NewsMessage> findNewsSince(@Param("since") LocalDateTime since);

    @Query("SELECT n FROM NewsMessage n WHERE " +
            "LOWER(n.messageText) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(n.channelTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findNewsByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    @Query("SELECT n.channelUsername, n.channelTitle, COUNT(n) as messageCount " +
            "FROM NewsMessage n " +
            "GROUP BY n.channelUsername, n.channelTitle " +
            "ORDER BY COUNT(n) DESC")
    List<Object[]> getChannelStatistics();

    @Query("SELECT n FROM NewsMessage n " +
            "WHERE n.channelUsername IN (" +
            "  SELECT tc.username FROM TelegramChannel tc WHERE tc.isActive = true" +
            ") " +
            "ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findLatestNewsFromActiveChannels(@Param("limit") int limit);

    @Query("SELECT COUNT(n) FROM NewsMessage n WHERE " +
            "n.messageDate >= :startOfDay AND n.messageDate < :endOfDay")
    long countTodayMessages(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT n FROM NewsMessage n WHERE n.messageDate BETWEEN :startDate AND :endDate " +
            "ORDER BY n.messageDate DESC")
    List<NewsMessage> findNewsBetweenDates(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    long deleteByCreatedAtBefore(LocalDateTime cutoffDate);
    long countByCreatedAtBefore(LocalDateTime cutoffDate);
    long countByIsVisible(boolean isVisible);
    long countByCreatedAtAfter(LocalDateTime date);

    // Новые методы для Twitter
    Optional<NewsMessage> findByTwitterTweetId(String twitterTweetId);

    @Query("SELECT n FROM NewsMessage n WHERE n.sourceType = :sourceType AND n.isVisible = true ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findLatestNewsBySourceType(@Param("sourceType") SourceType sourceType, @Param("limit") int limit);

    @Query("SELECT n FROM NewsMessage n WHERE n.sourceType = 'TWITTER' AND n.twitterUsername = :username " +
            "ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findLatestTwitterNewsByUser(@Param("username") String username, @Param("limit") int limit);

    @Query("SELECT n FROM NewsMessage n WHERE n.sourceType = 'TWITTER' AND n.twitterUsername IN (" +
            "  SELECT tu.username FROM TwitterUser tu WHERE tu.isActive = true" +
            ") ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findLatestTwitterNewsFromActiveUsers(@Param("limit") int limit);

    @Query("SELECT n.twitterUsername, n.twitterDisplayName, COUNT(n) as tweetCount, " +
            "AVG(n.retweetCount) as avgRetweets, AVG(n.likeCount) as avgLikes " +
            "FROM NewsMessage n WHERE n.sourceType = 'TWITTER' " +
            "GROUP BY n.twitterUsername, n.twitterDisplayName " +
            "ORDER BY COUNT(n) DESC")
    List<Object[]> getTwitterUserStatistics();

    @Query("SELECT n FROM NewsMessage n WHERE n.sourceType = 'TWITTER' AND " +
            "(LOWER(n.messageText) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(n.twitterDisplayName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findTwitterNewsByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    // Комбинированные методы для обеих платформ
    @Query("SELECT n FROM NewsMessage n WHERE n.isVisible = true " +
            "ORDER BY n.messageDate DESC LIMIT :limit")
    List<NewsMessage> findLatestNewsFromAllSources(@Param("limit") int limit);

    @Query("SELECT n.sourceType, COUNT(n) as count FROM NewsMessage n " +
            "WHERE n.createdAt >= :since GROUP BY n.sourceType")
    List<Object[]> getNewsCountBySource(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(n) FROM NewsMessage n WHERE n.sourceType = :sourceType AND n.messageDate >= :since")
    long countBySourceTypeAndMessageDateAfter(@Param("sourceType") SourceType sourceType, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(n) FROM NewsMessage n WHERE n.sourceType = 'TWITTER' AND n.messageDate >= :since")
    long countTwitterMessagesSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(n) FROM NewsMessage n WHERE n.sourceType = 'TELEGRAM' AND n.messageDate >= :since")
    long countTelegramMessagesSince(@Param("since") LocalDateTime since);

}