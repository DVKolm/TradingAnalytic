
package com.example.ta.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Утилиты для форматирования чисел
 */
public class NumberFormatUtil {

    /**
     * Форматирует число с разделителем тысяч (пробелы) и символом валюты
     * Например: 1 234 567,89 $
     */
    public static String formatCurrencyWithSpaces(BigDecimal value) {
        if (value == null) {
            return "0,00 $";
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator(' '); // Используем пробел как разделитель тысяч
        symbols.setDecimalSeparator(',');   // Запятая как разделитель дробной части

        DecimalFormat formatter = new DecimalFormat("#,##0.00", symbols);
        return formatter.format(value) + " $";
    }

    /**
     * Форматирует число с разделителем тысяч (пробелы) без символа валюты
     * Например: 1 234 567,89
     */
    public static String formatNumberWithSpaces(BigDecimal value) {
        if (value == null) {
            return "0,00";
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator(' '); // Используем пробел как разделитель тысяч
        symbols.setDecimalSeparator(',');   // Запятая как разделитель дробной части

        DecimalFormat formatter = new DecimalFormat("#,##0.00", symbols);
        return formatter.format(value);
    }

    /**
     * Форматирует число с указанным количеством знаков после запятой
     * Например: formatNumber(123456.789, 2) = "123 456,79"
     *
     * @param value число для форматирования
     * @param decimalPlaces количество знаков после запятой
     * @return отформатированное число с разделителями тысяч
     */
    public static String formatNumber(BigDecimal value, int decimalPlaces) {
        if (value == null) {
            return "0," + "0".repeat(Math.max(0, decimalPlaces));
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator(' '); // Используем пробел как разделитель тысяч
        symbols.setDecimalSeparator(',');   // Запятая как разделитель дробной части

        // Создаем шаблон с нужным количеством знаков после запятой
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimalPlaces > 0) {
            pattern.append(".");
            pattern.append("0".repeat(decimalPlaces));
        }

        DecimalFormat formatter = new DecimalFormat(pattern.toString(), symbols);
        return formatter.format(value);
    }

    /**
     * Форматирует целое число с разделителем тысяч (пробелы)
     * Например: 1 234 567
     */
    public static String formatIntegerWithSpaces(int value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator(' '); // Используем пробел как разделитель тысяч

        DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
        return formatter.format(value);
    }

    /**
     * Форматирует процент с двумя знаками после запятой
     * Например: 75,18%
     */
    public static String formatPercentage(BigDecimal value) {
        if (value == null) {
            return "0,00%";
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setDecimalSeparator(',');   // Запятая как разделитель дробной части

        DecimalFormat formatter = new DecimalFormat("0.00", symbols);
        return formatter.format(value) + "%";
    }

    /**
     * Форматирует валюту для Telegram сообщений (без пробела перед $)
     * Например: $1 234,56
     */
    public static String formatTelegramCurrency(BigDecimal value) {
        if (value == null) {
            return "$0,00";
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator(' '); // Используем пробел как разделитель тысяч
        symbols.setDecimalSeparator(',');   // Запятая как разделитель дробной части

        DecimalFormat formatter = new DecimalFormat("#,##0.00", symbols);
        return "$" + formatter.format(value);
    }
}