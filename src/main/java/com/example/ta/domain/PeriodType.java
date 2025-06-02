package com.example.ta.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Getter
@RequiredArgsConstructor
public enum PeriodType {
    TODAY("Сегодня"),
    WEEK("Неделя"),
    MONTH("Месяц"),
    QUARTER("Квартал"),
    HALF_YEAR("Полгода"),
    YEAR("Год"),
    ALL_TIME("Все время"),
    CUSTOM("Произвольный период");

    private final String displayName;

    /**
     * Получить начальную дату для периода
     */
    public LocalDate getStartDate() {
        LocalDate now = LocalDate.now();

        return switch (this) {
            case TODAY -> now;
            case WEEK -> now.minusWeeks(1);
            case MONTH -> now.minusMonths(1);
            case QUARTER -> now.minusMonths(3);
            case HALF_YEAR -> now.minusMonths(6);
            case YEAR -> now.minusYears(1);
            case ALL_TIME, CUSTOM -> null; // Для этих периодов дата устанавливается вручную
        };
    }

    /**
     * Получить конечную дату для периода
     */
    public LocalDate getEndDate() {
        LocalDate now = LocalDate.now();

        return switch (this) {
            case TODAY, WEEK, MONTH, QUARTER, HALF_YEAR, YEAR -> now;
            case ALL_TIME, CUSTOM -> null; // Для этих периодов дата устанавливается вручную
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}