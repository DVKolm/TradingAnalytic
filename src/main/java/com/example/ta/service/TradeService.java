package com.example.ta.service;

import com.example.ta.domain.Trade;
import com.example.ta.domain.TradeStatistics;
import com.example.ta.domain.TradeStatus;
import com.example.ta.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TradeService {

    private final TradeRepository tradeRepository;
    private final TelegramNotificationService telegramNotificationService;

    public Trade save(Trade trade) {
        log.info("Сохранение сделки: {}", trade.getAssetName());

        boolean isNewTrade = trade.getId() == null;
        autoSetStatusBasedOnExitTime(trade);

        Trade savedTrade = tradeRepository.save(trade);

        // Безопасная отправка уведомления (только если настроено)
        if (isNewTrade) {
            safelySendTelegramNotification(savedTrade, "open");
        }

        return savedTrade;
    }

    public Trade update(Trade trade) {
        log.info("Обновление сделки: {}", trade.getAssetName());

        TradeStatus oldStatus = null;
        if (trade.getId() != null) {
            Optional<Trade> existingTrade = tradeRepository.findById(trade.getId());
            oldStatus = existingTrade.map(Trade::getStatus).orElse(null);
        }

        autoSetStatusBasedOnExitTime(trade);
        Trade savedTrade = tradeRepository.save(trade);

        // Безопасная отправка уведомления при изменении статуса
        if (oldStatus != TradeStatus.CLOSED && savedTrade.getStatus() == TradeStatus.CLOSED) {
            safelySendTelegramNotification(savedTrade, "close");
        } else {
            safelySendTelegramNotification(savedTrade, "update");
        }

        return savedTrade;
    }


    private void safelySendTelegramNotification(Trade trade, String action) {
        try {
            if (telegramNotificationService != null) {
                telegramNotificationService.sendTradeNotification(trade, action);
            }
        } catch (Exception e) {
            log.warn("Не удалось отправить Telegram уведомление для сделки {}: {}",
                    trade.getAssetName(), e.getMessage());
        }
    }


    @Transactional(readOnly = true)
    public Optional<Trade> findById(Long id) {
        return tradeRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Trade> findAll() {
        return tradeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Trade> findAll(Pageable pageable) {
        return tradeRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Trade> search(String assetName, TradeStatus status, LocalDate startDate, LocalDate endDate) {
        // Реализация поиска с различными комбинациями параметров
        if (assetName != null && !assetName.trim().isEmpty() && status != null && startDate != null && endDate != null) {
            return tradeRepository.findByAssetNameContainingIgnoreCaseAndStatusAndTradeDateBetween(assetName, status, startDate, endDate);
        } else if (assetName != null && !assetName.trim().isEmpty() && status != null) {
            return tradeRepository.findByAssetNameContainingIgnoreCaseAndStatus(assetName, status);
        } else if (assetName != null && !assetName.trim().isEmpty() && startDate != null && endDate != null) {
            return tradeRepository.findByAssetNameContainingIgnoreCaseAndTradeDateBetween(assetName, startDate, endDate);
        } else if (status != null && startDate != null && endDate != null) {
            return tradeRepository.findByStatusAndTradeDateBetween(status, startDate, endDate);
        } else if (assetName != null && !assetName.trim().isEmpty()) {
            return tradeRepository.findByAssetNameContainingIgnoreCase(assetName);
        } else if (status != null) {
            return tradeRepository.findByStatus(status);
        } else if (startDate != null && endDate != null) {
            return tradeRepository.findByTradeDateBetween(startDate, endDate);
        } else {
            return tradeRepository.findAll();
        }
    }

    public boolean delete(Long id) {
        try {
            tradeRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при удалении сделки с ID: {}", id, e);
            return false;
        }
    }

    @Transactional(readOnly = true)
    public TradeStatistics getStatistics(LocalDate startDate, LocalDate endDate) {
        // Получаем только ЗАКРЫТЫЕ сделки за период
        List<Trade> allTradesInPeriod = tradeRepository.findByTradeDateBetween(startDate, endDate);
        List<Trade> closedTrades = allTradesInPeriod.stream()
                .filter(trade -> trade.getStatus() == TradeStatus.CLOSED)
                .toList();

        log.info("Расчет статистики за период {} - {}: найдено {} закрытых сделок из {} общих",
                startDate, endDate, closedTrades.size(), allTradesInPeriod.size());

        return calculateStatisticsFromClosedTrades(closedTrades, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<Trade> findByAsset(String assetName) {
        return tradeRepository.findByAssetNameContainingIgnoreCase(assetName);
    }

    @Transactional(readOnly = true)
    public List<Trade> findByStatus(TradeStatus status) {
        return tradeRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Trade> getProfitableTrades() {
        return tradeRepository.findProfitableTrades();
    }

    @Transactional(readOnly = true)
    public List<Trade> getLosingTrades() {
        return tradeRepository.findLosingTrades();
    }

    @Transactional(readOnly = true)
    public List<Trade> findRecentTrades(int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return tradeRepository.findAll(pageRequest).getContent();
    }

    public void deleteById(Long id) {
        log.info("Удаление сделки с ID: {}", id);
        tradeRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public TradeStatistics calculateStatistics() {
        // Получаем все сделки и фильтруем только ЗАКРЫТЫЕ
        List<Trade> allTrades = findAll();
        List<Trade> closedTrades = allTrades.stream()
                .filter(trade -> trade.getStatus() == TradeStatus.CLOSED)
                .toList();

        log.info("Расчет общей статистики: найдено {} закрытых сделок из {} общих",
                closedTrades.size(), allTrades.size());

        return calculateStatisticsFromClosedTrades(closedTrades, null, null);
    }

    @Transactional(readOnly = true)
    public TradeStatistics getQuickStatistics() {
        TradeStatistics stats = new TradeStatistics();

        // Получаем только ЗАКРЫТЫЕ сделки для статистики
        List<Trade> allTrades = tradeRepository.findAll();
        List<Trade> closedTrades = allTrades.stream()
                .filter(trade -> trade.getStatus() == TradeStatus.CLOSED)
                .filter(trade -> trade.getProfitLoss() != null)
                .toList();

        log.info("Быстрая статистика: обрабатываем {} закрытых сделок с P/L из {} общих",
                closedTrades.size(), allTrades.size());

        // Общее количество ЗАКРЫТЫХ сделок с P/L
        stats.setTotalTrades(closedTrades.size());

        // Общая прибыль только закрытых сделок
        BigDecimal totalProfit = closedTrades.stream()
                .map(Trade::getProfitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setTotalProfit(totalProfit);

        // Общий объем (можем считать из всех сделок)
        BigDecimal totalVolume = allTrades.stream()
                .filter(trade -> trade.getVolume() != null && trade.getEntryPoint() != null)
                .map(trade -> trade.getVolume().multiply(trade.getEntryPoint()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setTotalVolume(totalVolume);

        // Прибыльные и убыточные сделки (только закрытые)
        Long winningTrades = closedTrades.stream()
                .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) > 0)
                .count();

        Long losingTrades = closedTrades.stream()
                .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) < 0)
                .count();

        stats.setWinningTrades(winningTrades.intValue());
        stats.setLosingTrades(losingTrades.intValue());

        // Винрейт
        if (!closedTrades.isEmpty()) {
            BigDecimal winRate = BigDecimal.valueOf(winningTrades)
                    .divide(BigDecimal.valueOf(closedTrades.size()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            stats.setWinRate(winRate);
        } else {
            stats.setWinRate(BigDecimal.ZERO);
        }

        // Средняя прибыль
        if (!closedTrades.isEmpty()) {
            BigDecimal avgProfit = totalProfit.divide(BigDecimal.valueOf(closedTrades.size()), 2, RoundingMode.HALF_UP);
            stats.setAvgProfit(avgProfit);
        } else {
            stats.setAvgProfit(BigDecimal.ZERO);
        }

        // Максимальная прибыль и убыток (только закрытые)
        BigDecimal maxProfit = closedTrades.stream()
                .map(Trade::getProfitLoss)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        stats.setMaxProfit(maxProfit);

        BigDecimal maxLoss = closedTrades.stream()
                .map(Trade::getProfitLoss)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        stats.setMaxLoss(maxLoss);

        log.info("Быстрая статистика: {} закрытых сделок, прибыльных: {}, убыточных: {}, прибыль: {}, винрейт: {}%",
                closedTrades.size(), winningTrades, losingTrades, totalProfit, stats.getWinRate());

        return stats;
    }

    /**
     * Расчет статистики только из закрытых сделок
     */
    private TradeStatistics calculateStatisticsFromClosedTrades(List<Trade> closedTrades, LocalDate startDate, LocalDate endDate) {
        TradeStatistics stats = new TradeStatistics();

        // Устанавливаем период
        stats.setPeriodStart(startDate);
        stats.setPeriodEnd(endDate);

        if (closedTrades == null || closedTrades.isEmpty()) {
            // Возвращаем пустую статистику с нулевыми значениями
            stats.setTotalTrades(0);
            stats.setTotalProfit(BigDecimal.ZERO);
            stats.setTotalVolume(BigDecimal.ZERO);
            stats.setWinningTrades(0);
            stats.setLosingTrades(0);
            stats.setWinRate(BigDecimal.ZERO);
            stats.setAvgProfit(BigDecimal.ZERO);
            stats.setMaxProfit(BigDecimal.ZERO);
            stats.setMaxLoss(BigDecimal.ZERO);
            return stats;
        }

        // Фильтруем только сделки с рассчитанной прибылью/убытком
        List<Trade> tradesWithProfitLoss = closedTrades.stream()
                .filter(trade -> trade.getProfitLoss() != null)
                .toList();

        // Общее количество ЗАКРЫТЫХ сделок с P/L
        stats.setTotalTrades(tradesWithProfitLoss.size());

        // Общая прибыль
        BigDecimal totalProfit = tradesWithProfitLoss.stream()
                .map(Trade::getProfitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setTotalProfit(totalProfit);

        // Общий объем торгов (все закрытые сделки)
        BigDecimal totalVolume = closedTrades.stream()
                .filter(trade -> trade.getVolume() != null && trade.getEntryPoint() != null)
                .map(trade -> trade.getVolume().multiply(trade.getEntryPoint()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setTotalVolume(totalVolume);

        // Прибыльные и убыточные сделки
        int winningTrades = (int) tradesWithProfitLoss.stream()
                .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) > 0)
                .count();

        int losingTrades = (int) tradesWithProfitLoss.stream()
                .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) < 0)
                .count();

        stats.setWinningTrades(winningTrades);
        stats.setLosingTrades(losingTrades);

        // Процент успешных сделок
        if (!tradesWithProfitLoss.isEmpty()) {
            BigDecimal winRate = BigDecimal.valueOf(winningTrades)
                    .divide(BigDecimal.valueOf(tradesWithProfitLoss.size()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            stats.setWinRate(winRate);
        } else {
            stats.setWinRate(BigDecimal.ZERO);
        }

        // Средняя прибыль на сделку
        if (!tradesWithProfitLoss.isEmpty()) {
            BigDecimal avgProfit = totalProfit.divide(BigDecimal.valueOf(tradesWithProfitLoss.size()), 2, RoundingMode.HALF_UP);
            stats.setAvgProfit(avgProfit);
        } else {
            stats.setAvgProfit(BigDecimal.ZERO);
        }

        // Максимальная прибыль
        BigDecimal maxProfit = tradesWithProfitLoss.stream()
                .map(Trade::getProfitLoss)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        stats.setMaxProfit(maxProfit);

        // Максимальный убыток
        BigDecimal maxLoss = tradesWithProfitLoss.stream()
                .map(Trade::getProfitLoss)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        stats.setMaxLoss(maxLoss);

        log.info("Статистика только закрытых сделок: {} сделок с P/L, прибыльных: {}, убыточных: {}, прибыль: {}, винрейт: {}%",
                tradesWithProfitLoss.size(), winningTrades, losingTrades, totalProfit, stats.getWinRate());

        return stats;
    }

    private void autoSetStatusBasedOnExitTime(Trade trade) {
        if (trade.getExitTime() != null) {
            trade.setStatus(TradeStatus.CLOSED);
            log.debug("Автоматически установлен статус CLOSED для сделки {} из-за наличия времени выхода",
                    trade.getAssetName());
        }
    }

    /**
     * Получить только закрытые сделки для построения кривой эквити
     */
    @Transactional(readOnly = true)
    public List<Trade> getClosedTradesForPeriod(LocalDate startDate, LocalDate endDate) {
        List<Trade> allTrades;

        if (startDate != null && endDate != null) {
            allTrades = tradeRepository.findByTradeDateBetween(startDate, endDate);
            log.info("Найдено {} сделок за период {} - {}", allTrades.size(), startDate, endDate);
        } else {
            allTrades = tradeRepository.findAll();
            log.info("Найдено {} сделок за все время", allTrades.size());
        }

        List<Trade> closedTrades = allTrades.stream()
                .filter(trade -> trade.getStatus() == TradeStatus.CLOSED)
                .filter(trade -> trade.getProfitLoss() != null)
                .filter(trade -> trade.getTradeDate() != null) // Добавляем проверку даты
                .collect(Collectors.toList()); // Используем collect вместо toList()

        log.info("Из них закрытых сделок с P/L и датой: {}", closedTrades.size());

        return closedTrades;
    }

}