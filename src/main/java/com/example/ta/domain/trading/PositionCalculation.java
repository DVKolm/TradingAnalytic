package com.example.ta.domain.trading;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PositionCalculation {

    private BigDecimal deposit;
    private BigDecimal risk;
    private BigDecimal entryPrice;
    private BigDecimal stopPrice;

    private BigDecimal stopPercentage;
    private BigDecimal coinQuantity;
    private BigDecimal riskAmount;
    private BigDecimal positionSize;

    private String currency = "USDT";
    private String description;
}