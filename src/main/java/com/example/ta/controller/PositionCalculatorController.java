package com.example.ta.controller;

import com.example.ta.domain.PositionCalculation;
import com.example.ta.service.PositionCalculatorService;
import com.example.ta.util.NumberFormatUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class PositionCalculatorController implements Initializable {

    @FXML private TextField depositField;
    @FXML private TextField riskField;
    @FXML private TextField entryPriceField;
    @FXML private TextField stopPriceField;

    @FXML private Label stopLoss15Label;
    @FXML private Label stopLoss25Label;
    @FXML private Label stopLoss5Label;
    @FXML private Button useStopLoss15Button;
    @FXML private Button useStopLoss25Button;
    @FXML private Button useStopLoss5Button;

    @FXML private Label stopPercentageLabel;
    @FXML private Label coinQuantityLabel;
    @FXML private Label riskAmountLabel;
    @FXML private Label positionSizeLabel;

    @FXML private Button calculateButton;
    @FXML private Button clearButton;
    @FXML private Button copyResultButton;

    @FXML private TextArea descriptionArea;

    private final PositionCalculatorService calculatorService;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация PositionCalculatorController");
        setupInputFields();
        setupButtons();
        clearResults();
        setupAutoStopLoss();
    }

    private void setupInputFields() {
        setupNumericField(depositField, "250.00");
        setupNumericField(riskField, "2.0");
        setupNumericField(entryPriceField, "109800.00");
        setupNumericField(stopPriceField, "111500.00");

        depositField.textProperty().addListener((obs, oldVal, newVal) -> autoCalculate());
        riskField.textProperty().addListener((obs, oldVal, newVal) -> autoCalculate());
        entryPriceField.textProperty().addListener((obs, oldVal, newVal) -> {
            calculateAutoStopLosses();
            autoCalculate();
        });
        stopPriceField.textProperty().addListener((obs, oldVal, newVal) -> autoCalculate());
    }

    private void setupNumericField(TextField field, String placeholder) {
        field.setPromptText(placeholder);

        field.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*[.,]?\\d*")) {
                field.setText(oldValue);
            }
        });
    }

    private void setupButtons() {
        calculateButton.setOnAction(event -> calculatePosition());
        clearButton.setOnAction(event -> clearAllFields());
        copyResultButton.setOnAction(event -> copyResultsToClipboard());
    }

    private void setupAutoStopLoss() {
        if (useStopLoss15Button != null && useStopLoss25Button != null && useStopLoss5Button != null) {
            useStopLoss15Button.setOnAction(event -> useAutoStopLoss(1.5));
            useStopLoss25Button.setOnAction(event -> useAutoStopLoss(2.5));
            useStopLoss5Button.setOnAction(event -> useAutoStopLoss(5.0));
            log.info("Кнопки автоматических стоп-лоссов настроены");
        } else {
            log.warn("Кнопки автоматических стоп-лоссов не найдены в FXML файле");
        }
    }

    /**
     * Расчет автоматических стоп-лоссов при изменении цены входа
     */
    private void calculateAutoStopLosses() {
        if (stopLoss15Label == null || stopLoss25Label == null || stopLoss5Label == null) {
            log.warn("Лейблы автоматических стоп-лоссов не найдены в FXML файле");
            return;
        }

        try {
            String entryPriceText = entryPriceField.getText();
            if (entryPriceText == null || entryPriceText.trim().isEmpty()) {
                clearAutoStopLosses();
                return;
            }

            BigDecimal entryPrice = parseDecimal(entryPriceText);

            BigDecimal stopLoss1_5 = entryPrice.multiply(BigDecimal.valueOf(0.985));
            BigDecimal stopLoss2_5 = entryPrice.multiply(BigDecimal.valueOf(0.975));
            BigDecimal stopLoss5_0 = entryPrice.multiply(BigDecimal.valueOf(0.95));

            stopLoss15Label.setText(NumberFormatUtil.formatNumber(stopLoss1_5, 2));
            stopLoss25Label.setText(NumberFormatUtil.formatNumber(stopLoss2_5, 2));
            stopLoss5Label.setText(NumberFormatUtil.formatNumber(stopLoss5_0, 2));

            if (useStopLoss15Button != null) useStopLoss15Button.setDisable(false);
            if (useStopLoss25Button != null) useStopLoss25Button.setDisable(false);
            if (useStopLoss5Button != null) useStopLoss5Button.setDisable(false);

            stopLoss15Label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            stopLoss25Label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
            stopLoss5Label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");

            log.debug("Рассчитаны автоматические стоп-лоссы для цены входа: {}", entryPrice);

        } catch (Exception e) {
            log.debug("Ошибка при расчете автоматических стоп-лоссов: {}", e.getMessage());
            clearAutoStopLosses();
        }
    }

    /**
     * Использование выбранного автоматического стоп-лосса
     */
    private void useAutoStopLoss(double percentage) {
        try {
            String entryPriceText = entryPriceField.getText();
            if (entryPriceText == null || entryPriceText.trim().isEmpty()) {
                return;
            }

            BigDecimal entryPrice = parseDecimal(entryPriceText);
            BigDecimal multiplier = BigDecimal.valueOf((100 - percentage) / 100);
            BigDecimal stopLoss = entryPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

            stopPriceField.setText(stopLoss.toString());

            log.info("Установлен автоматический стоп-лосс {}%: {}", percentage, stopLoss);

            showQuickInfo(String.format("✅ Установлен стоп-лосс %.1f%%", percentage));

        } catch (Exception e) {
            log.error("Ошибка при установке автоматического стоп-лосса", e);
            showError("Ошибка", "Не удалось установить стоп-лосс");
        }
    }

    /**
     * Очистка автоматических стоп-лоссов
     */
    private void clearAutoStopLosses() {
        if (stopLoss15Label != null) {
            stopLoss15Label.setText("—");
            stopLoss15Label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        }
        if (stopLoss25Label != null) {
            stopLoss25Label.setText("—");
            stopLoss25Label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        }
        if (stopLoss5Label != null) {
            stopLoss5Label.setText("—");
            stopLoss5Label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        }

        if (useStopLoss15Button != null) useStopLoss15Button.setDisable(true);
        if (useStopLoss25Button != null) useStopLoss25Button.setDisable(true);
        if (useStopLoss5Button != null) useStopLoss5Button.setDisable(true);
    }

    @FXML
    private void calculatePosition() {
        try {
            log.info("Запуск расчета позиции");

            PositionCalculation input = createInputFromFields();
            PositionCalculation result = calculatorService.calculatePosition(input);

            displayResults(result);

            showSuccess("Расчет выполнен успешно!");

        } catch (Exception e) {
            log.error("Ошибка при расчете позиции", e);
            showError("Ошибка расчета", e.getMessage());
            clearResults();
        }
    }

    private void autoCalculate() {
        try {
            if (allFieldsFilled()) {
                PositionCalculation input = createInputFromFields();
                PositionCalculation result = calculatorService.calculatePosition(input);
                displayResults(result);
            } else {
                clearResults();
            }
        } catch (Exception e) {
            clearResults();
        }
    }

    private PositionCalculation createInputFromFields() {
        PositionCalculation input = new PositionCalculation();

        input.setDeposit(parseDecimal(depositField.getText()));
        input.setRisk(parseDecimal(riskField.getText()));
        input.setEntryPrice(parseDecimal(entryPriceField.getText()));
        input.setStopPrice(parseDecimal(stopPriceField.getText()));
        input.setCurrency("USDT");

        return input;
    }

    private void displayResults(PositionCalculation result) {
        stopPercentageLabel.setText(NumberFormatUtil.formatPercentage(result.getStopPercentage()));
        coinQuantityLabel.setText(NumberFormatUtil.formatNumberWithSpaces(result.getCoinQuantity()) + " монет");
        riskAmountLabel.setText(NumberFormatUtil.formatCurrencyWithSpaces(result.getRiskAmount()));
        positionSizeLabel.setText(NumberFormatUtil.formatCurrencyWithSpaces(result.getPositionSize()));

        stopPercentageLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        coinQuantityLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #3498db;");
        riskAmountLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e67e22;");
        positionSizeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");

        descriptionArea.setText(result.getDescription());
    }

    private void clearResults() {
        stopPercentageLabel.setText("0.0%");
        coinQuantityLabel.setText("0.00 монет");
        riskAmountLabel.setText("0.00 $");
        positionSizeLabel.setText("0.00 $");

        stopPercentageLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        coinQuantityLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        riskAmountLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        positionSizeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");

        descriptionArea.clear();
    }

    @FXML
    private void clearAllFields() {
        depositField.clear();
        riskField.clear();
        entryPriceField.clear();
        stopPriceField.clear();
        clearResults();
        clearAutoStopLosses();

        log.info("Все поля очищены");
    }

    @FXML
    private void copyResultsToClipboard() {
        try {
            String results = String.format(
                    "📊 РЕЗУЛЬТАТЫ РАСЧЕТА ПОЗИЦИИ\n\n" +
                            "Депозит: %s\n" +
                            "Риск: %s\n" +
                            "Вход: %s USDT\n" +
                            "Стоп: %s USDT\n\n" +
                            "Процент стопа: %s\n" +
                            "Количество монет: %s\n" +
                            "Сумма риска: %s\n" +
                            "Размер позиции: %s\n\n" +
                            "%s",
                    depositField.getText() + " USDT",
                    riskField.getText() + "%",
                    entryPriceField.getText(),
                    stopPriceField.getText(),
                    stopPercentageLabel.getText(),
                    coinQuantityLabel.getText(),
                    riskAmountLabel.getText(),
                    positionSizeLabel.getText(),
                    descriptionArea.getText()
            );

            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(results);
            clipboard.setContent(content);

            showSuccess("Результаты скопированы в буфер обмена!");

        } catch (Exception e) {
            log.error("Ошибка при копировании в буфер обмена", e);
            showError("Ошибка", "Не удалось скопировать результаты");
        }
    }

    private boolean allFieldsFilled() {
        return !depositField.getText().trim().isEmpty() &&
                !riskField.getText().trim().isEmpty() &&
                !entryPriceField.getText().trim().isEmpty() &&
                !stopPriceField.getText().trim().isEmpty();
    }

    private BigDecimal parseDecimal(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Поле не может быть пустым");
        }

        try {
            String normalizedText = text.trim().replace(",", ".");
            return new BigDecimal(normalizedText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректное числовое значение: " + text);
        }
    }

    private void showQuickInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Информация");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();

        javafx.util.Duration delay = javafx.util.Duration.seconds(2);
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(delay, e -> alert.close())
        );
        timeline.play();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Успех");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}