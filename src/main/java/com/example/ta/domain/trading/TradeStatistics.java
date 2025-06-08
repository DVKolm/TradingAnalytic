package com.example.ta.domain.trading;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TradeStatistics {
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private PeriodType periodType;
    private int totalTrades;
    private BigDecimal totalProfit;
    private BigDecimal totalVolume;
    private int winningTrades;
    private int losingTrades;
    private BigDecimal winRate;
    private BigDecimal avgProfit;
    private BigDecimal maxProfit;
    private BigDecimal maxLoss;

}
