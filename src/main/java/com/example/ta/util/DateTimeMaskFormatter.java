package com.example.ta.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class DateTimeMaskFormatter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern DATETIME_PATTERN = Pattern.compile("\\d{0,4}-?\\d{0,2}-?\\d{0,2}\\s?\\d{0,2}:?\\d{0,2}");

    public static void applyMask(TextField textField) {
        textField.setTextFormatter(new TextFormatter<>(
                new StringConverter<String>() {
                    @Override
                    public String toString(String object) {
                        return object != null ? object : "";
                    }

                    @Override
                    public String fromString(String string) {
                        return string;
                    }
                },
                "",
                createFilter()
        ));
    }

    private static UnaryOperator<TextFormatter.Change> createFilter() {
        return change -> {
            String newText = change.getControlNewText();

            // Разрешаем только цифры, дефисы, пробелы и двоеточия
            String cleaned = newText.replaceAll("[^0-9\\-: ]", "");

            // Проверяем базовый паттерн
            if (!DATETIME_PATTERN.matcher(cleaned).matches()) {
                return null;
            }

            // Ограничиваем длину до 16 символов (yyyy-MM-dd HH:mm)
            if (cleaned.length() > 16) {
                return null;
            }

            // Автоматически добавляем дефисы, пробел и двоеточие
            String formatted = formatAsYouType(cleaned);

            if (!formatted.equals(newText)) {
                change.setText(formatted);
                change.setRange(0, change.getControlText().length());

                // Устанавливаем курсор в конец
                int newCaretPosition = formatted.length();
                change.setCaretPosition(newCaretPosition);
                change.setAnchor(newCaretPosition);
            }

            return change;
        };
    }

    private static String formatAsYouType(String input) {
        StringBuilder result = new StringBuilder();
        String digitsOnly = input.replaceAll("[^0-9]", "");

        for (int i = 0; i < digitsOnly.length() && i < 12; i++) {
            char digit = digitsOnly.charAt(i);

            // Добавляем дефисы после года и месяца
            if (i == 4 || i == 6) {
                result.append('-');
            }
            // Добавляем пробел после даты
            else if (i == 8) {
                result.append(' ');
            }
            // Добавляем двоеточие после часов
            else if (i == 10) {
                result.append(':');
            }

            result.append(digit);
        }

        return result.toString();
    }

    public static boolean isValidDateTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true; // Пустое поле считается валидным
        }

        // Проверяем минимальную длину для полного формата
        if (text.length() < 16) {
            return false; // Неполный формат
        }

        try {
            LocalDateTime.parse(text, FORMATTER);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static LocalDateTime parseDateTime(String text) throws DateTimeParseException {
        return LocalDateTime.parse(text, FORMATTER);
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FORMATTER) : "";
    }

    public static DateTimeFormatter getFormatter() {
        return FORMATTER;
    }

}