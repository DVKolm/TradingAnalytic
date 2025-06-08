package com.example.ta.service;

import com.example.ta.domain.trading.PositionCalculation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
public class PositionCalculatorService {

    /**
     * Выполняет полный расчет позиции на основе входных данных
     */
    public PositionCalculation calculatePosition(PositionCalculation input) {
        log.info("Начинаем расчет позиции: депозит={}, риск={}%, вход={}, стоп={}",
                input.getDeposit(), input.getRisk(), input.getEntryPrice(), input.getStopPrice());

        validateInput(input);

        PositionCalculation result = new PositionCalculation();

        result.setDeposit(input.getDeposit());
        result.setRisk(input.getRisk());
        result.setEntryPrice(input.getEntryPrice());
        result.setStopPrice(input.getStopPrice());
        result.setCurrency(input.getCurrency());

        BigDecimal stopPercentage = calculateStopPercentage(input.getEntryPrice(), input.getStopPrice());
        result.setStopPercentage(stopPercentage);

        BigDecimal riskAmount = calculateRiskAmount(input.getDeposit(), input.getRisk());
        result.setRiskAmount(riskAmount);

        BigDecimal coinQuantity = calculateCoinQuantity(input.getDeposit(), input.getRisk(),
                stopPercentage, input.getEntryPrice());
        result.setCoinQuantity(coinQuantity);

        BigDecimal positionSize = calculatePositionSize(coinQuantity, input.getEntryPrice());
        result.setPositionSize(positionSize);

        result.setDescription(generateDescription(result));

        log.info("Расчет завершен: стоп {}%, монет {}, размер позиции {} USDT",
                stopPercentage, coinQuantity, positionSize);

        return result;
    }

    /**
     * Рассчитывает процент стопа по формуле из Excel:
     * =ЕСЛИ(B3>B4; ОКРУГЛ(100-(B4/B3*100);1); ОКРУГЛ(100-(B3/B4*100);1))
     */
    public BigDecimal calculateStopPercentage(BigDecimal entryPrice, BigDecimal stopPrice) {
        if (entryPrice == null || stopPrice == null) {
            throw new IllegalArgumentException("Цена входа и цена стопа не могут быть null");
        }

        BigDecimal percentage;

        if (entryPrice.compareTo(stopPrice) > 0) {
            percentage = BigDecimal.valueOf(100).subtract(
                    stopPrice.divide(entryPrice, 10, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
            );
        } else {
            percentage = BigDecimal.valueOf(100).subtract(
                    entryPrice.divide(stopPrice, 10, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
            );
        }

        return percentage.setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * ИСПРАВЛЕННАЯ формула для расчета количества монет:
     * Количество = (Депозит * Риск% / 100) / (Цена_входа * Процент_стопа / 100)
     */
    public BigDecimal calculateCoinQuantity(BigDecimal deposit, BigDecimal riskPercent,
                                            BigDecimal stopPercentage, BigDecimal entryPrice) {
        if (deposit == null || riskPercent == null || stopPercentage == null || entryPrice == null) {
            throw new IllegalArgumentException("Все параметры для расчета количества монет должны быть заданы");
        }

        if (stopPercentage.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Процент стопа не может быть равен нулю");
        }

        BigDecimal riskAmountUSDT = deposit
                .multiply(riskPercent)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        BigDecimal stopLossPerCoin = entryPrice
                .multiply(stopPercentage)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        BigDecimal coinQuantity = riskAmountUSDT
                .divide(stopLossPerCoin, 6, RoundingMode.HALF_UP);

        log.debug("Расчет количества монет: рискUSDT={}, стопНаМонету={}, количество={}",
                riskAmountUSDT, stopLossPerCoin, coinQuantity);

        return coinQuantity.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Рассчитывает размер позиции в USDT
     */
    public BigDecimal calculatePositionSize(BigDecimal coinQuantity, BigDecimal entryPrice) {
        if (coinQuantity == null || entryPrice == null) {
            return BigDecimal.ZERO;
        }

        return coinQuantity.multiply(entryPrice).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Быстрый расчет только процента стопа
     */
    public BigDecimal quickStopPercentageCalculation(BigDecimal entryPrice, BigDecimal stopPrice) {
        return calculateStopPercentage(entryPrice, stopPrice);
    }

    /**
     * Быстрый расчет риска в валюте
     */
    public BigDecimal calculateRiskAmount(BigDecimal deposit, BigDecimal riskPercent) {
        if (deposit == null || riskPercent == null) {
            return BigDecimal.ZERO;
        }
        return deposit.multiply(riskPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Проверка валидности входных данных
     */
    private void validateInput(PositionCalculation input) {
        if (input.getDeposit() == null || input.getDeposit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Депозит должен быть больше нуля");
        }

        if (input.getRisk() == null || input.getRisk().compareTo(BigDecimal.ZERO) <= 0 ||
                input.getRisk().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Риск должен быть от 0.1% до 100%");
        }

        if (input.getEntryPrice() == null || input.getEntryPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Цена входа должна быть больше нуля");
        }

        if (input.getStopPrice() == null || input.getStopPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Цена стопа должна быть больше нуля");
        }

        if (input.getEntryPrice().compareTo(input.getStopPrice()) == 0) {
            throw new IllegalArgumentException("Цена входа и цена стопа не могут быть одинаковыми");
        }
    }

    /**
     * Генерирует описание для расчета
     */
    private String generateDescription(PositionCalculation calc) {
        String positionType = calc.getEntryPrice().compareTo(calc.getStopPrice()) > 0 ? "LONG" : "SHORT";

        return String.format(
                "📊 РАСЧЕТ %s ПОЗИЦИИ\n\n" +
                        "Депозит: %.2f %s\n" +
                        "Риск: %.1f%% (%.2f %s)\n" +
                        "Цена входа: %.2f\n" +
                        "Стоп-лосс: %.2f\n\n" +
                        "Процент стопа: %.1f%%\n" +
                        "Количество монет: %.6f\n" +
                        "Размер позиции: %.2f %s\n\n" +
                        "При достижении стоп-лосса вы потеряете %.2f %s (%.1f%% от депозита)",
                positionType,
                calc.getDeposit(), calc.getCurrency(),
                calc.getRisk(), calc.getRiskAmount(), calc.getCurrency(),
                calc.getEntryPrice(),
                calc.getStopPrice(),
                calc.getStopPercentage(),
                calc.getCoinQuantity(),
                calc.getPositionSize(), calc.getCurrency(),
                calc.getRiskAmount(), calc.getCurrency(), calc.getRisk()
        );
    }
}