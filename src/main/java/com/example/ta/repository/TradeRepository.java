package com.example.ta.repository;

import com.example.ta.domain.trading.Trade;
import com.example.ta.domain.trading.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

    /**
     * Найти сделки по названию актива (с игнорированием регистра)
     */
    List<Trade> findByAssetNameContainingIgnoreCase(String assetName);

    /**
     * Найти сделки по статусу
     */
    List<Trade> findByStatus(TradeStatus status);

    /**
     * Найти сделки за период
     */
    List<Trade> findByTradeDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Найти сделки по активу и статусу
     */
    List<Trade> findByAssetNameContainingIgnoreCaseAndStatus(String assetName, TradeStatus status);

    /**
     * Найти сделки по активу, статусу и периоду
     */
    List<Trade> findByAssetNameContainingIgnoreCaseAndStatusAndTradeDateBetween(
            String assetName, TradeStatus status, LocalDate startDate, LocalDate endDate);

    /**
     * Найти сделки по активу и периоду
     */
    List<Trade> findByAssetNameContainingIgnoreCaseAndTradeDateBetween(
            String assetName, LocalDate startDate, LocalDate endDate);

    /**
     * Найти сделки по статусу и периоду
     */
    List<Trade> findByStatusAndTradeDateBetween(
            TradeStatus status, LocalDate startDate, LocalDate endDate);

    /**
     * Найти прибыльные сделки
     */
    List<Trade> findByProfitLossGreaterThan(BigDecimal value);

    /**
     * Найти убыточные сделки
     */
    List<Trade> findByProfitLossLessThan(BigDecimal value);

    /**
     * Найти прибыльные сделки (альтернативный метод через @Query)
     */
    @Query("SELECT t FROM Trade t WHERE t.profitLoss > 0")
    List<Trade> findProfitableTrades();

    /**
     * Найти убыточные сделки (альтернативный метод через @Query)
     */
    @Query("SELECT t FROM Trade t WHERE t.profitLoss < 0")
    List<Trade> findLosingTrades();

    /**
     * Получить общую прибыль за период
     */
    @Query("SELECT COALESCE(SUM(t.profitLoss), 0) FROM Trade t WHERE t.tradeDate BETWEEN :startDate AND :endDate AND t.status = 'CLOSED'")
    BigDecimal getTotalProfitForPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Получить количество прибыльных сделок за период
     */
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.tradeDate BETWEEN :startDate AND :endDate AND t.profitLoss > 0 AND t.status = 'CLOSED'")
    Long getWinningTradesCountForPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Получить общее количество закрытых сделок за период
     */
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.tradeDate BETWEEN :startDate AND :endDate AND t.status = 'CLOSED'")
    Long getClosedTradesCountForPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Получить максимальную прибыль
     */
    @Query("SELECT MAX(t.profitLoss) FROM Trade t WHERE t.status = 'CLOSED'")
    BigDecimal getMaxProfit();

    /**
     * Получить максимальный убыток
     */
    @Query("SELECT MIN(t.profitLoss) FROM Trade t WHERE t.status = 'CLOSED'")
    BigDecimal getMaxLoss();

    /**
     * Получить общий объем торгов за период
     */
    @Query("SELECT COALESCE(SUM(t.volumeInCurrency), 0) FROM Trade t WHERE t.tradeDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalVolumeForPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Найти сделки с сортировкой по дате (новые сначала)
     */
    List<Trade> findAllByOrderByTradeDateDescCreatedAtDesc();

    /**
     * Получить общую прибыль за все время
     */
    @Query("SELECT COALESCE(SUM(t.profitLoss), 0) FROM Trade t WHERE t.profitLoss IS NOT NULL")
    BigDecimal getTotalProfit();

    /**
     * Получить общий объем за все время
     */
    @Query("SELECT COALESCE(SUM(t.volumeInCurrency), 0) FROM Trade t WHERE t.volumeInCurrency IS NOT NULL")
    BigDecimal getTotalVolume();

    /**
     * Получить количество прибыльных сделок за все время
     */
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.profitLoss > 0")
    Long getWinningTradesCount();

    /**
     * Получить количество убыточных сделок за все время
     */
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.profitLoss < 0")
    Long getLosingTradesCount();

    /**
     * Получить общее количество сделок с рассчитанной прибылью
     */
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.profitLoss IS NOT NULL")
    Long getTradesWithProfitLossCount();

    /**
     * Получить максимальную прибыль за все время
     */
    @Query("SELECT COALESCE(MAX(t.profitLoss), 0) FROM Trade t WHERE t.profitLoss IS NOT NULL")
    BigDecimal getMaxProfitAllTime();

    /**
     * Получить максимальный убыток за все время
     */
    @Query("SELECT COALESCE(MIN(t.profitLoss), 0) FROM Trade t WHERE t.profitLoss IS NOT NULL")
    BigDecimal getMaxLossAllTime();
}