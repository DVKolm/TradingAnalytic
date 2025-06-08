package com.example.ta.domain.trading;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Название актива не может быть пустым")
    @Size(max = 100, message = "Название актива не должно превышать 100 символов")
    @Column(name = "asset_name", nullable = false, length = 100)
    private String assetName;

    @NotNull(message = "Цена входа обязательна")
    @DecimalMin(value = "0.0", inclusive = false, message = "Цена входа должна быть больше 0")
    @Column(name = "entry_point", nullable = false, precision = 15, scale = 2)
    private BigDecimal entryPoint;

    @DecimalMin(value = "0.0", inclusive = false, message = "Цена выхода должна быть больше 0")
    @Column(name = "exit_point", precision = 15, scale = 2)
    private BigDecimal exitPoint;

    @Column(name = "take_profit_target", precision = 19, scale = 8)
    private BigDecimal takeProfitTarget;

    @Column(name = "profit", precision = 15, scale = 2)
    private BigDecimal profit;

    @Size(max = 500, message = "Причина входа не должна превышать 500 символов")
    @Column(name = "entry_reason", length = 500)
    private String entryReason;

    @Size(max = 500, message = "Причина выхода не должна превышать 500 символов")
    @Column(name = "exit_reason", length = 500)
    private String exitReason;

    @Size(max = 1000, message = "Комментарий не должен превышать 1000 символов")
    @Column(name = "comment", length = 1000)
    private String comment;

    @NotNull(message = "Количество единиц обязательно")
    @DecimalMin(value = "0.0", inclusive = false, message = "Количество должно быть больше 0")
    @Column(name = "volume", nullable = false, precision = 15, scale = 8)
    private BigDecimal volume;

    @Column(name = "volume_in_currency", precision = 15, scale = 2)
    private BigDecimal volumeInCurrency;

    @NotNull(message = "Дата сделки обязательна")
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "entry_time")
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;

    @Column(name = "profit_loss", precision = 15, scale = 2)
    private BigDecimal profitLoss;

    @Column(name = "price_movement_percent", precision = 8, scale = 4)
    private BigDecimal priceMovementPercent;

    @NotNull(message = "Статус сделки обязателен")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TradeStatus status = TradeStatus.OPEN;

    @NotNull(message = "Тип сделки обязателен")
    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false)
    private TradeType tradeType = TradeType.LONG;

    @NotNull(message = "Валюта обязательна")
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency = Currency.USD;

    @Size(max = 500, message = "Путь к изображению не должен превышать 500 символов")
    @Column(name = "chart_image_path", length = 500)
    private String chartImagePath;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        calculateProfitAndMovement();
    }

    /**
     * Рассчитывает прибыль и процентное движение цены с учетом типа сделки
     */
    public void calculateProfitAndMovement() {
        if (exitPoint != null && entryPoint != null && volume != null) {
            BigDecimal priceChange = exitPoint.subtract(entryPoint);

            // Для шорта инвертируем направление прибыли
            if (tradeType == TradeType.SHORT) {
                priceChange = priceChange.negate();
            }

            // Рассчитываем прибыль/убыток
            this.profitLoss = priceChange.multiply(volume);

            // Рассчитываем процентное движение
            BigDecimal basePrice = entryPoint;
            BigDecimal actualPriceChange = exitPoint.subtract(entryPoint);

            this.priceMovementPercent = actualPriceChange
                    .divide(basePrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // Рассчитываем объем в валюте
        if (entryPoint != null && volume != null) {
            this.volumeInCurrency = entryPoint.multiply(volume);
        }
    }

    /**
     * Получить отформатированную строку прибыли с валютой
     */
    public String getFormattedProfitLoss() {
        if (profitLoss == null) return "N/A";
        return String.format("%s%.2f", currency.getSymbol(), profitLoss);
    }

    /**
     * Получить отформатированную строку объема в валюте
     */
    public String getFormattedVolumeInCurrency() {
        if (volumeInCurrency == null) return "N/A";
        return String.format("%s%.2f", currency.getSymbol(), volumeInCurrency);
    }

    /**
     * Получить отформатированную строку процентного движения
     */
    public String getFormattedPriceMovement() {
        if (priceMovementPercent == null) return "N/A";
        return String.format("%.2f%%", priceMovementPercent);
    }

    /**
     * Проверить, есть ли изображение графика
     */
    public boolean hasChartImage() {
        return chartImagePath != null && !chartImagePath.trim().isEmpty();
    }

    public BigDecimal calculatePotentialProfitToTarget() {
        if (takeProfitTarget == null || entryPoint == null || volume == null) {
            return null;
        }

        BigDecimal priceDifference;
        if (tradeType == TradeType.LONG) {
            priceDifference = takeProfitTarget.subtract(entryPoint);
        } else {
            priceDifference = entryPoint.subtract(takeProfitTarget);
        }

        return priceDifference.multiply(volume);
    }

    /**
     * Вычисляет процент движения цены до цели тейка
     */
    public BigDecimal calculatePercentageToTarget() {
        if (takeProfitTarget == null || entryPoint == null) {
            return null;
        }

        BigDecimal priceDifference;
        if (tradeType == TradeType.LONG) {
            priceDifference = takeProfitTarget.subtract(entryPoint);
        } else {
            priceDifference = entryPoint.subtract(takeProfitTarget);
        }

        return priceDifference.divide(entryPoint, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

}