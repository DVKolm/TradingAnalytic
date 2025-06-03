package com.example.ta.controller;

import com.example.ta.events.NavigationEvent;
import com.example.ta.events.TradeDataChangedEvent;
import com.example.ta.domain.Currency;
import com.example.ta.domain.Trade;
import com.example.ta.domain.TradeStatus;
import com.example.ta.domain.TradeType;
import com.example.ta.service.TradeService;
import com.example.ta.util.DateTimeMaskFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeFormController implements Initializable {

    @FXML private Label titleLabel;
    @FXML private TextField assetNameField;
    @FXML private ComboBox<TradeType> tradeTypeComboBox;
    @FXML private ComboBox<TradeStatus> statusComboBox;
    @FXML private ComboBox<Currency> currencyComboBox;
    @FXML private DatePicker tradeDatePicker;
    @FXML private TextField entryPointField;
    @FXML private TextField exitPointField;
    @FXML private TextField takeProfitTargetField; // НОВОЕ ПОЛЕ
    @FXML private TextField volumeField;
    @FXML private TextField profitLossField;
    @FXML private TextField entryTimeField;
    @FXML private TextField exitTimeField;
    @FXML private TextArea entryReasonArea;
    @FXML private TextArea exitReasonArea;
    @FXML private TextArea notesArea;

    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button clearButton;

    private final TradeService tradeService;
    private final ApplicationEventPublisher eventPublisher;

    private Trade editingTrade;
    private boolean isEditMode = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация TradeFormController");
        setupForm();
        setupValidation();
        setupExitTimeStatusListener();
        setCreateMode();

        log.debug("TradeFormController инициализирован");
    }

    private void setupForm() {
        tradeTypeComboBox.setItems(FXCollections.observableArrayList(TradeType.values()));
        statusComboBox.setItems(FXCollections.observableArrayList(TradeStatus.values()));
        currencyComboBox.setItems(FXCollections.observableArrayList(Currency.values()));

        DateTimeMaskFormatter.applyMask(entryTimeField);
        DateTimeMaskFormatter.applyMask(exitTimeField);

        setupDateTimeBinding();

        profitLossField.setEditable(false);

        entryPointField.textProperty().addListener((obs, oldVal, newVal) -> calculateProfitLoss());
        exitPointField.textProperty().addListener((obs, oldVal, newVal) -> calculateProfitLoss());
        volumeField.textProperty().addListener((obs, oldVal, newVal) -> calculateProfitLoss());
        tradeTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> calculateProfitLoss());

        // НОВЫЕ СЛУШАТЕЛИ для Take Profit Target
        takeProfitTargetField.textProperty().addListener((obs, oldVal, newVal) -> calculatePotentialProfit());
        entryPointField.textProperty().addListener((obs, oldVal, newVal) -> calculatePotentialProfit());
        volumeField.textProperty().addListener((obs, oldVal, newVal) -> calculatePotentialProfit());
        tradeTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> calculatePotentialProfit());
    }

    /**
     * Рассчитывает потенциальную прибыль до цели Take Profit
     */
    private void calculatePotentialProfit() {
        try {
            String entryPriceText = entryPointField.getText();
            String targetPriceText = takeProfitTargetField.getText();
            String volumeText = volumeField.getText();
            TradeType tradeType = tradeTypeComboBox.getValue();

            if (entryPriceText == null || entryPriceText.trim().isEmpty() ||
                    targetPriceText == null || targetPriceText.trim().isEmpty() ||
                    volumeText == null || volumeText.trim().isEmpty() ||
                    tradeType == null) {
                takeProfitTargetField.setTooltip(null);
                return;
            }

            BigDecimal entryPrice = parseDecimal(entryPriceText);
            BigDecimal targetPrice = parseDecimal(targetPriceText);
            BigDecimal volume = parseDecimal(volumeText);

            if (entryPrice == null || targetPrice == null || volume == null ||
                    entryPrice.compareTo(BigDecimal.ZERO) <= 0 ||
                    targetPrice.compareTo(BigDecimal.ZERO) <= 0 ||
                    volume.compareTo(BigDecimal.ZERO) <= 0) {
                takeProfitTargetField.setTooltip(null);
                return;
            }

            BigDecimal priceDifference;
            boolean isValidTarget = false;

            if (tradeType == TradeType.LONG) {
                priceDifference = targetPrice.subtract(entryPrice);
                isValidTarget = targetPrice.compareTo(entryPrice) > 0;
            } else {
                priceDifference = entryPrice.subtract(targetPrice);
                isValidTarget = targetPrice.compareTo(entryPrice) < 0;
            }

            BigDecimal potentialProfit = priceDifference.multiply(volume);
            BigDecimal percentageMove = priceDifference.divide(entryPrice, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));

            if (isValidTarget && potentialProfit.compareTo(BigDecimal.ZERO) > 0) {
                String tooltipText = String.format(
                        "💰 Потенциальная прибыль: $%.2f\n📊 Движение: %.2f%%",
                        potentialProfit, percentageMove
                );
                takeProfitTargetField.setTooltip(new Tooltip(tooltipText));

                log.debug("Рассчитана потенциальная прибыль до цели: ${} ({}%)",
                        String.format("%.2f", potentialProfit),
                        String.format("%.2f", percentageMove));
            } else {
                String errorText = tradeType == TradeType.LONG ?
                        "⚠️ Цель должна быть выше цены входа для лонга" :
                        "⚠️ Цель должна быть ниже цены входа для шорта";
                takeProfitTargetField.setTooltip(new Tooltip(errorText));
            }

        } catch (Exception e) {
            log.debug("Ошибка при расчете потенциальной прибыли: {}", e.getMessage());
            takeProfitTargetField.setTooltip(null);
        }
    }


    // Остальные методы без изменений...
    private void setupExitTimeStatusListener() {
        exitTimeField.textProperty().addListener((observable, oldValue, newValue) -> {
            log.debug("Изменение времени выхода: '{}' -> '{}'", oldValue, newValue);

            if (newValue != null && !newValue.trim().isEmpty()) {
                try {
                    LocalDateTime exitTime = parseDateTime(newValue);

                    if (exitTime != null) {
                        if (statusComboBox.getValue() != TradeStatus.CLOSED) {
                            Platform.runLater(() -> {
                                statusComboBox.setValue(TradeStatus.CLOSED);
                                log.info("Автоматически установлен статус CLOSED из-за указания времени выхода: {}", newValue);

                                showStatusChangeNotification("Статус автоматически изменен на 'ЗАКРЫТА' из-за указания времени выхода");
                            });
                        }
                    }
                } catch (Exception e) {
                    log.debug("Время выхода введено некорректно: {}", newValue);
                }
            } else {
                if (!isEditMode && statusComboBox.getValue() == TradeStatus.CLOSED) {
                    Platform.runLater(() -> {
                        statusComboBox.setValue(TradeStatus.OPEN);
                        log.info("Автоматически установлен статус OPEN из-за очистки времени выхода");

                        showStatusChangeNotification("Статус автоматически изменен на 'ОТКРЫТА' из-за очистки времени выхода");
                    });
                }
            }
        });

        exitPointField.textProperty().addListener((observable, oldValue, newValue) -> {
            calculateProfitLoss();
        });
    }

    private void showStatusChangeNotification(String message) {
        log.info("Уведомление: {}", message);
    }

    private void setupDateTimeBinding() {
        tradeDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null) {
                String currentTimeText = entryTimeField.getText();
                String timeOnly = "09:00";

                if (currentTimeText != null && currentTimeText.contains(" ")) {
                    String[] parts = currentTimeText.split(" ");
                    if (parts.length > 1) {
                        timeOnly = parts[1];
                    }
                } else if (currentTimeText != null && currentTimeText.matches("\\d{2}:\\d{2}")) {
                    timeOnly = currentTimeText;
                }

                String newDateTime = newDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " " + timeOnly;

                entryTimeField.setText(newDateTime);

                log.debug("Автоматически установлена дата и время входа: {} для даты: {}",
                        newDateTime, newDate);
            }
        });

        entryTimeField.textProperty().addListener((obs, oldText, newText) -> {
            validateDateTimeField(entryTimeField, newText);
        });

        exitTimeField.textProperty().addListener((obs, oldText, newText) -> {
            validateDateTimeField(exitTimeField, newText);
        });
    }

    private void validateDateTimeField(TextField field, String text) {
        if (text == null || text.trim().isEmpty()) {
            field.setStyle("");
            return;
        }

        if (text.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
            try {
                LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                field.setStyle("-fx-border-color: #27ae60; -fx-border-width: 1px;");
            } catch (Exception e) {
                field.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 1px;");
            }
        } else {
            field.setStyle("-fx-border-color: #f39c12; -fx-border-width: 1px;");
        }
    }

    private void setupValidation() {
        setupNumericField(entryPointField);
        setupNumericField(exitPointField);
        setupNumericField(takeProfitTargetField); // НОВОЕ ПОЛЕ
        setupNumericField(volumeField);
    }

    private void setupNumericField(TextField field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*(\\.\\d*)?")) {
                field.setText(oldValue);
            }
        });
    }

    public void setCreateMode() {
        log.info("Установка формы в режим создания новой сделки");

        this.isEditMode = false;
        this.editingTrade = null;

        titleLabel.setText("➕ Добавить новую сделку");

        clearForm();

        setDefaultValues();

        saveButton.setText("💾 Сохранить сделку");
        saveButton.setDisable(false);
    }

    private void setDefaultValues() {
        try {
            log.debug("Установка дефолтных значений для новой сделки");

            currencyComboBox.setValue(Currency.USD);

            statusComboBox.setValue(TradeStatus.OPEN);

            tradeTypeComboBox.setValue(TradeType.LONG);

            volumeField.setText("1.0");

            Platform.runLater(() -> {
                tradeDatePicker.setValue(LocalDate.now());

                assetNameField.requestFocus();
            });

            log.debug("Дефолтные значения установлены успешно");

        } catch (Exception e) {
            log.error("Ошибка при установке дефолтных значений: {}", e.getMessage(), e);
        }
    }

    public void setEditMode(Trade trade) {
        this.isEditMode = true;
        this.editingTrade = trade;
        titleLabel.setText("Редактирование: " + trade.getAssetName());
        saveButton.setText("💾 Сохранить изменения");
        fillFormWithTradeData(trade);
        log.info("Форма переведена в режим редактирования для сделки: {}", trade.getId());
    }

    private void fillFormWithTradeData(Trade trade) {
        assetNameField.setText(trade.getAssetName());
        tradeTypeComboBox.setValue(trade.getTradeType());
        statusComboBox.setValue(trade.getStatus());
        currencyComboBox.setValue(trade.getCurrency());
        tradeDatePicker.setValue(trade.getTradeDate());

        // Заполняем числовые поля
        if (trade.getEntryPoint() != null) {
            entryPointField.setText(trade.getEntryPoint().toString());
        }
        if (trade.getExitPoint() != null) {
            exitPointField.setText(trade.getExitPoint().toString());
        }
        // НОВОЕ ПОЛЕ
        if (trade.getTakeProfitTarget() != null) {
            takeProfitTargetField.setText(trade.getTakeProfitTarget().toString());
        }
        if (trade.getVolume() != null) {
            volumeField.setText(trade.getVolume().toString());
        }
        if (trade.getProfitLoss() != null) {
            profitLossField.setText(trade.getProfitLoss().toString());
        }

        // Заполняем поля времени
        if (trade.getEntryTime() != null) {
            entryTimeField.setText(trade.getEntryTime().format(DateTimeMaskFormatter.getFormatter()));
        }
        if (trade.getExitTime() != null) {
            exitTimeField.setText(trade.getExitTime().format(DateTimeMaskFormatter.getFormatter()));
        }

        // Заполняем текстовые области
        entryReasonArea.setText(trade.getEntryReason() != null ? trade.getEntryReason() : "");
        exitReasonArea.setText(trade.getExitReason() != null ? trade.getExitReason() : "");
        notesArea.setText(trade.getComment() != null ? trade.getComment() : "");
    }

    @FXML
    private void saveTrade() {
        try {
            if (!validateForm()) {
                return;
            }

            Trade trade = isEditMode ? editingTrade : new Trade();
            fillTradeFromForm(trade);

            Trade savedTrade = isEditMode ? tradeService.update(trade) : tradeService.save(trade);

            eventPublisher.publishEvent(new TradeDataChangedEvent(this, "UPDATED"));

            showInfo(isEditMode ? "Сделка успешно обновлена!" : "Сделка успешно сохранена!");

            eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.VIEW_TRADES));

        } catch (Exception e) {
            log.error("Ошибка при сохранении сделки", e);
            showError("Ошибка при сохранении сделки: " + e.getMessage());
        }
    }

    private void fillTradeFromForm(Trade trade) {
        trade.setAssetName(assetNameField.getText().trim());
        trade.setTradeType(tradeTypeComboBox.getValue());
        trade.setStatus(statusComboBox.getValue());
        trade.setCurrency(currencyComboBox.getValue());
        trade.setTradeDate(tradeDatePicker.getValue());

        trade.setEntryPoint(parseDecimal(entryPointField.getText()));
        trade.setExitPoint(parseDecimal(exitPointField.getText()));
        trade.setTakeProfitTarget(parseDecimal(takeProfitTargetField.getText())); // НОВОЕ ПОЛЕ
        trade.setVolume(parseDecimal(volumeField.getText()));
        trade.setProfitLoss(parseDecimal(profitLossField.getText()));

        trade.setEntryTime(parseDateTime(entryTimeField.getText()));
        trade.setExitTime(parseDateTime(exitTimeField.getText()));

        trade.setEntryReason(entryReasonArea.getText().trim().isEmpty() ? null : entryReasonArea.getText().trim());
        trade.setExitReason(exitReasonArea.getText().trim().isEmpty() ? null : exitReasonArea.getText().trim());
        trade.setComment(notesArea.getText().trim().isEmpty() ? null : notesArea.getText().trim());

        if (!isEditMode) {
            trade.setCreatedAt(LocalDateTime.now());
        }
        trade.setUpdatedAt(LocalDateTime.now());
    }

    private boolean validateForm() {
        StringBuilder errors = new StringBuilder();

        if (assetNameField.getText() == null || assetNameField.getText().trim().isEmpty()) {
            errors.append("• Название актива обязательно для заполнения\n");
        }

        if (tradeTypeComboBox.getValue() == null) {
            errors.append("• Тип сделки обязателен для заполнения\n");
        }

        if (statusComboBox.getValue() == null) {
            errors.append("• Статус сделки обязателен для заполнения\n");
        }

        if (currencyComboBox.getValue() == null) {
            errors.append("• Валюта обязательна для заполнения\n");
        }

        if (tradeDatePicker.getValue() == null) {
            errors.append("• Дата сделки обязательна для заполнения\n");
        }

        if (entryPointField.getText() == null || entryPointField.getText().trim().isEmpty()) {
            errors.append("• Цена входа обязательна для заполнения\n");
        }

        if (volumeField.getText() == null || volumeField.getText().trim().isEmpty()) {
            errors.append("• Объем сделки обязателен для заполнения\n");
        }

        // НОВАЯ ВАЛИДАЦИЯ для Take Profit Target
        if (!takeProfitTargetField.getText().trim().isEmpty()) {
            try {
                BigDecimal entryPrice = parseDecimal(entryPointField.getText());
                BigDecimal targetPrice = parseDecimal(takeProfitTargetField.getText());
                TradeType tradeType = tradeTypeComboBox.getValue();

                if (entryPrice != null && targetPrice != null && tradeType != null) {
                    if (tradeType == TradeType.LONG && targetPrice.compareTo(entryPrice) <= 0) {
                        errors.append("• Цель Take Profit для лонга должна быть выше цены входа\n");
                    } else if (tradeType == TradeType.SHORT && targetPrice.compareTo(entryPrice) >= 0) {
                        errors.append("• Цель Take Profit для шорта должна быть ниже цены входа\n");
                    }
                }
            } catch (Exception e) {
                errors.append("• Некорректное значение цели Take Profit\n");
            }
        }

        // Проверяем корректность времени
        if (!entryTimeField.getText().trim().isEmpty() && parseDateTime(entryTimeField.getText()) == null) {
            errors.append("• Некорректный формат времени входа (используйте ГГГГ-ММ-ДД ЧЧ:ММ)\n");
        }

        if (!exitTimeField.getText().trim().isEmpty() && parseDateTime(exitTimeField.getText()) == null) {
            errors.append("• Некорректный формат времени выхода (используйте ГГГГ-ММ-ДД ЧЧ:ММ)\n");
        }

        // Дополнительная проверка: если указано время выхода, но статус не CLOSED
        if (!exitTimeField.getText().trim().isEmpty() &&
                parseDateTime(exitTimeField.getText()) != null &&
                statusComboBox.getValue() != TradeStatus.CLOSED) {

            log.warn("Обнаружено несоответствие: время выхода указано, но статус не CLOSED");
            // Автоматически исправляем это
            statusComboBox.setValue(TradeStatus.CLOSED);
        }

        if (errors.length() > 0) {
            showError("Пожалуйста, исправьте следующие ошибки:\n\n" + errors.toString());
            return false;
        }

        return true;
    }

    private void calculateProfitLoss() {
        try {
            String entryPriceText = entryPointField.getText();
            String exitPriceText = exitPointField.getText();
            String volumeText = volumeField.getText();
            TradeType tradeType = tradeTypeComboBox.getValue();

            if (entryPriceText == null || entryPriceText.trim().isEmpty() ||
                    exitPriceText == null || exitPriceText.trim().isEmpty() ||
                    volumeText == null || volumeText.trim().isEmpty() ||
                    tradeType == null) {
                profitLossField.setText("");
                return;
            }

            BigDecimal entryPrice = parseDecimal(entryPriceText);
            BigDecimal exitPrice = parseDecimal(exitPriceText);
            BigDecimal volume = parseDecimal(volumeText);

            if (entryPrice == null || exitPrice == null || volume == null ||
                    entryPrice.compareTo(BigDecimal.ZERO) <= 0 ||
                    exitPrice.compareTo(BigDecimal.ZERO) <= 0 ||
                    volume.compareTo(BigDecimal.ZERO) <= 0) {
                profitLossField.setText("");
                return;
            }

            BigDecimal profitLoss;

            if (tradeType == TradeType.LONG) {
                profitLoss = exitPrice.subtract(entryPrice).multiply(volume);
            } else {
                profitLoss = entryPrice.subtract(exitPrice).multiply(volume);
            }

            String formattedProfitLoss = String.format("%.2f", profitLoss);
            profitLossField.setText(formattedProfitLoss);

            if (profitLoss.compareTo(BigDecimal.ZERO) >= 0) {
                profitLossField.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            } else {
                profitLossField.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            }

            log.debug("Рассчитана прибыль/убыток: {} для сделки {} с объемом {}",
                    formattedProfitLoss, tradeType, volume);

        } catch (Exception e) {
            log.warn("Ошибка при расчете прибыли/убытка: {}", e.getMessage());
            profitLossField.setText("");
            profitLossField.setStyle("");
        }
    }

    @FXML
    private void clearForm() {
        log.debug("Очистка формы");

        try {
            assetNameField.clear();
            entryPointField.clear();
            exitPointField.clear();
            takeProfitTargetField.clear(); // НОВОЕ ПОЛЕ
            volumeField.clear();
            profitLossField.clear();
            entryTimeField.clear();
            exitTimeField.clear();

            entryReasonArea.clear();
            exitReasonArea.clear();
            notesArea.clear();

            tradeTypeComboBox.setValue(null);
            statusComboBox.setValue(null);
            currencyComboBox.setValue(null);

            tradeDatePicker.setValue(null);

            profitLossField.setStyle("");
            takeProfitTargetField.setStyle(""); // Сброс стиля
            takeProfitTargetField.setTooltip(null); // Сброс подсказки

            log.debug("Форма очищена успешно");

        } catch (Exception e) {
            log.error("Ошибка при очистке формы: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void cancel() {
        log.info("Отмена " + (isEditMode ? "редактирования" : "добавления") + " сделки");
        eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.VIEW_TRADES));
    }

    // Вспомогательные методы
    private BigDecimal parseDecimal(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DateTimeMaskFormatter.getFormatter());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Информация");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}