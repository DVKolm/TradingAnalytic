package com.example.ta.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class DateMaskFormatter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{0,4}-?\\d{0,2}-?\\d{0,2}");

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

            // Удаляем все что не цифры и дефисы
            String cleaned = newText.replaceAll("[^0-9\\-]", "");

            // Проверяем базовый паттерн
            if (!DATE_PATTERN.matcher(cleaned).matches()) {
                return null;
            }

            // Ограничиваем длину до 10 символов
            if (cleaned.length() > 10) {
                return null;
            }

            // Автоматически добавляем дефисы
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
        String digits = input.replaceAll("[^0-9]", "");

        for (int i = 0; i < digits.length() && i < 8; i++) {
            char digit = digits.charAt(i);

            if (i == 4 || i == 6) {
                result.append('-');
            }

            result.append(digit);
        }

        return result.toString();
    }

    public static boolean isValidDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true; // Пустое поле считается валидным
        }

        if (text.length() < 10) {
            return false; // Неполная дата
        }

        try {
            LocalDate.parse(text, FORMATTER);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static LocalDate parseDate(String text) throws DateTimeParseException {
        return LocalDate.parse(text, FORMATTER);
    }

    public static String formatDate(LocalDate date) {
        return date != null ? date.format(FORMATTER) : "";
    }
}