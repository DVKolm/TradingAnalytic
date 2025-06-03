package com.example.ta.controller;

import com.example.ta.util.NumberFormatUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
@Component
public class AveragingCalculatorController implements Initializable {

    // Переключатель типа сделки
    @FXML private RadioButton longRadioButton;
    @FXML private RadioButton shortRadioButton;
    @FXML private ToggleGroup tradeTypeToggleGroup;

    // Входные параметры
    @FXML private TextField depositField;
    @FXML private TextField stopPercentField;
    @FXML private TextField desiredAvgPriceField;
    @FXML private TextField stopPriceField;

    @FXML private TextField entryPrice1Field;
    @FXML private TextField entryPrice2Field;
    @FXML private TextField entryPrice3Field;

    // Результаты расчетов
    @FXML private Label volume1Label;
    @FXML private Label volume2Label;
    @FXML private Label volume3Label;
    @FXML private Label totalVolumeLabel;

    @FXML private Label weight1Label;
    @FXML private Label weight2Label;
    @FXML private Label weight3Label;

    @FXML private Label calculatedAvgPriceLabel;
    @FXML private Label totalCoinsLabel;
    @FXML private Label maxLossAmountLabel;
    @FXML private Label actualRiskPercentLabel;

    @FXML private Button calculateButton;
    @FXML private Button clearButton;

    // Информационные лейблы
    @FXML private Label tradeTypeInfoLabel;
    @FXML private Label stopInfoLabel;
    @FXML private Label profitInfoLabel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация AveragingCalculatorController (расчет объемов для ТВХ)");
        setupTradeTypeControls();
        setupInputFields();
        setupButtons();
        clearResults();
        updateTradeTypeInfo();
    }

    private void setupTradeTypeControls() {
        // Настройка переключателя типа сделки
        tradeTypeToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            updateTradeTypeInfo();
            updatePlaceholders();
            autoCalculate();
        });

        // По умолчанию Long
        longRadioButton.setSelected(true);
    }

    private void updateTradeTypeInfo() {
        boolean isLong = longRadioButton.isSelected();

        if (isLong) {
            tradeTypeInfoLabel.setText("🟢 LONG позиция — покупаем актив, ожидаем рост цены");
            stopInfoLabel.setText("🛑 Стоп-лосс должен быть НИЖЕ цен входов");
            profitInfoLabel.setText("📈 Прибыль при росте цены выше ТВХ");

            tradeTypeInfoLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        } else {
            tradeTypeInfoLabel.setText("🔴 SHORT позиция — продаем актив, ожидаем падение цены");
            stopInfoLabel.setText("🛑 Стоп-лосс должен быть выше цены входа");
            profitInfoLabel.setText("📉 Прибыль при падении цены ниже ТВХ");

            tradeTypeInfoLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        }

        stopInfoLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: 600;");
        profitInfoLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: 600;");
    }

    private void updatePlaceholders() {
        boolean isLong = longRadioButton.isSelected();

        if (isLong) {
            // Long: входы выше стопа
            desiredAvgPriceField.setPromptText("104400.00");
            stopPriceField.setPromptText("103500.00");
            entryPrice1Field.setPromptText("104586.00");
            entryPrice2Field.setPromptText("104396.00");
            entryPrice3Field.setPromptText("104058.00");
        } else {
            // Short: входы ниже стопа
            desiredAvgPriceField.setPromptText("104400.00");
            stopPriceField.setPromptText("105500.00");
            entryPrice1Field.setPromptText("104214.00");
            entryPrice2Field.setPromptText("104404.00");
            entryPrice3Field.setPromptText("104742.00");
        }
    }

    private void setupInputFields() {
        // Основные параметры
        setupNumericField(depositField, "1000.00");
        setupNumericField(stopPercentField, "2.0");
        setupNumericField(desiredAvgPriceField, "104400.00");
        setupNumericField(stopPriceField, "103500.00");

        // Точки входа
        setupNumericField(entryPrice1Field, "104586.00");
        setupNumericField(entryPrice2Field, "104396.00");
        setupNumericField(entryPrice3Field, "104058.00");

        // Автоматический расчет при изменении полей
        addAutoCalculateListeners();
    }

    private void addAutoCalculateListeners() {
        depositField.textProperty().addListener((obs, oldVal, newVal) -> autoCalculate());
        stopPercentField.textProperty().addListener((obs, oldVal, newVal) -> autoCalculate());
        desiredAvgPriceField.textProperty().addListener((obs, oldVal, newVal) -> autoCalculate());
        stopPriceField.textProperty().addListener((obs, oldVal, newVal) -> autoCalculate());

        entryPrice1Field.textProperty().addListener((obs, oldVal, newVal) -> autoCalculate());
        entryPrice2Field.textProperty().addListener((obs, oldVal, newVal) -> autoCalculate());
        entryPrice3Field.textProperty().addListener((obs, oldVal, newVal) -> autoCalculate());
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
        calculateButton.setOnAction(event -> calculateVolumes());
        clearButton.setOnAction(event -> clearAllFields());
    }

    @FXML
    private void quickCalculate() {
        try {
            log.info("Запуск быстрого расчета объемов для ТВХ");

            if (!validateInputs()) {
                return;
            }

            performVolumeCalculation();
            showSuccess("Быстрый расчет выполнен!");

        } catch (Exception e) {
            log.error("Ошибка при быстром расчете", e);
            showError("Ошибка расчета", e.getMessage());
            clearResults();
        }
    }

    @FXML
    private void calculateVolumes() {
        try {
            log.info("Запуск расчета объемов для получения заданной ТВХ");

            if (!validateInputs()) {
                return;
            }

            performVolumeCalculation();
            showSuccess("Расчет объемов выполнен успешно!");

        } catch (Exception e) {
            log.error("Ошибка при расчете объемов", e);
            showError("Ошибка расчета", e.getMessage());
            clearResults();
        }
    }

    private void autoCalculate() {
        try {
            if (allFieldsFilled()) {
                performVolumeCalculation();
            } else {
                clearResults();
            }
        } catch (Exception e) {
            clearResults();
        }
    }

    private boolean validateInputs() {
        if (!allFieldsFilled()) {
            showError("Ошибка", "Все поля должны быть заполнены");
            return false;
        }

        try {
            BigDecimal deposit = parseDecimal(depositField.getText());
            BigDecimal stopPercent = parseDecimal(stopPercentField.getText());
            BigDecimal desiredAvgPrice = parseDecimal(desiredAvgPriceField.getText());
            BigDecimal stopPrice = parseDecimal(stopPriceField.getText());

            BigDecimal entryPrice1 = parseDecimal(entryPrice1Field.getText());
            BigDecimal entryPrice2 = parseDecimal(entryPrice2Field.getText());
            BigDecimal entryPrice3 = parseDecimal(entryPrice3Field.getText());

            boolean isLong = longRadioButton.isSelected();

            // Проверки корректности
            if (deposit.compareTo(BigDecimal.ZERO) <= 0) {
                showError("Ошибка", "Депозит должен быть больше 0");
                return false;
            }

            if (stopPercent.compareTo(BigDecimal.ZERO) <= 0) {
                showError("Ошибка", "Процент стопа должен быть больше 0");
                return false;
            }

            // Проверки для Long позиций
            if (isLong) {
                if (desiredAvgPrice.compareTo(stopPrice) <= 0) {
                    showError("Ошибка", "Для Long позиции желаемая ТВХ должна быть выше цены стопа");
                    return false;
                }

                if (entryPrice1.compareTo(stopPrice) <= 0 ||
                        entryPrice2.compareTo(stopPrice) <= 0 ||
                        entryPrice3.compareTo(stopPrice) <= 0) {
                    showError("Ошибка", "Для Long позиции все цены входа должны быть выше цены стопа");
                    return false;
                }
            } else {
                // Проверки для Short позиций
                if (desiredAvgPrice.compareTo(stopPrice) >= 0) {
                    showError("Ошибка", "Для Short позиции желаемая ТВХ должна быть ниже цены стопа");
                    return false;
                }

                if (entryPrice1.compareTo(stopPrice) >= 0 ||
                        entryPrice2.compareTo(stopPrice) >= 0 ||
                        entryPrice3.compareTo(stopPrice) >= 0) {
                    showError("Ошибка", "Для Short позиции все цены входа должны быть ниже цены стопа");
                    return false;
                }
            }

            // Проверяем, что желаемая ТВХ достижима с данными ценами входа
            BigDecimal minPrice = entryPrice1.min(entryPrice2).min(entryPrice3);
            BigDecimal maxPrice = entryPrice1.max(entryPrice2).max(entryPrice3);

            if (desiredAvgPrice.compareTo(minPrice) < 0 || desiredAvgPrice.compareTo(maxPrice) > 0) {
                showError("Ошибка",
                        String.format("Желаемая ТВХ (%.2f) должна быть между минимальной (%.2f) и максимальной (%.2f) ценой входа",
                                desiredAvgPrice.doubleValue(), minPrice.doubleValue(), maxPrice.doubleValue()));
                return false;
            }

        } catch (Exception e) {
            showError("Ошибка", "Некорректные числовые значения");
            return false;
        }

        return true;
    }

    private void performVolumeCalculation() {
        // Получаем входные данные
        BigDecimal deposit = parseDecimal(depositField.getText());            // D
        BigDecimal stopPercent = parseDecimal(stopPercentField.getText());   // R
        BigDecimal desiredAvgPrice = parseDecimal(desiredAvgPriceField.getText()); // TVX
        BigDecimal stopPrice = parseDecimal(stopPriceField.getText());       // p_stop

        BigDecimal entryPrice1 = parseDecimal(entryPrice1Field.getText());   // p1
        BigDecimal entryPrice2 = parseDecimal(entryPrice2Field.getText());   // p2
        BigDecimal entryPrice3 = parseDecimal(entryPrice3Field.getText());   // p3

        boolean isLong = longRadioButton.isSelected();

        log.info("=== РАСЧЕТ ПО МАТЕМАТИЧЕСКОЙ ФОРМУЛЕ ===");
        log.info("Тип позиции: {}", isLong ? "LONG" : "SHORT");
        log.info("Депозит D = {}", deposit);
        log.info("Риск R = {}%", stopPercent);
        log.info("Желаемая ТВХ = {}", desiredAvgPrice);
        log.info("Стоп-цена = {}", stopPrice);
        log.info("Цены входа: p1={}, p2={}, p3={}", entryPrice1, entryPrice2, entryPrice3);

        // Проверяем корректность данных для типа позиции
        if (isLong) {
            // Для LONG: стоп должен быть ниже средней цены входа
            if (stopPrice.compareTo(desiredAvgPrice) >= 0) {
                log.error("Для LONG позиции стоп-цена ({}) должна быть ниже средней цены входа ({})",
                        stopPrice, desiredAvgPrice);
                showError("Ошибка", "Для LONG позиции стоп-цена должна быть ниже средней цены входа");
                return;
            }
        } else {
            // Для SHORT: стоп должен быть выше средней цены входа
            if (stopPrice.compareTo(desiredAvgPrice) <= 0) {
                log.error("Для SHORT позиции стоп-цена ({}) должна быть выше средней цены входа ({})",
                        stopPrice, desiredAvgPrice);
                showError("Ошибка", "Для SHORT позиции стоп-цена должна быть выше средней цены входа");
                return;
            }
        }

        // ШАГ 1: Рассчитываем общий объем позиции по формуле
        // Для LONG:  V = (D × R/100) / (TVX - p_stop)
        // Для SHORT: V = (D × R/100) / (p_stop - TVX)
        BigDecimal maxLossAmount = deposit.multiply(stopPercent).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        BigDecimal totalVolume; // V = v1 + v2 + v3 (в единицах актива)
        if (isLong) {
            totalVolume = maxLossAmount.divide(desiredAvgPrice.subtract(stopPrice), 8, RoundingMode.HALF_UP);
        } else {
            totalVolume = maxLossAmount.divide(stopPrice.subtract(desiredAvgPrice), 8, RoundingMode.HALF_UP);
        }

        log.info("Общий объем позиции V = {} единиц актива", totalVolume);

        // Проверяем, что общий объем положительный
        if (totalVolume.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Общий объем позиции получился отрицательным или нулевым: {}", totalVolume);
            showError("Ошибка", "Некорректные параметры для расчета объема позиции");
            return;
        }

        // ШАГ 2: Используем формулу из примера для распределения объемов
        // Выбираем v1 как базовое значение (например, 30% от общего объема)
        BigDecimal v1 = totalVolume.multiply(BigDecimal.valueOf(0.3));

        // Рассчитываем v2 по формуле:
        // v2 = ((TVX - p3) × V - (p1 - p3) × v1) / (p2 - p3)
        BigDecimal numerator = desiredAvgPrice.subtract(entryPrice3).multiply(totalVolume)
                .subtract(entryPrice1.subtract(entryPrice3).multiply(v1));
        BigDecimal denominator = entryPrice2.subtract(entryPrice3);

        BigDecimal v2;
        BigDecimal v3;

        // Проверяем корректность знаменателя
        if (denominator.abs().compareTo(BigDecimal.valueOf(0.0001)) < 0) {
            log.warn("Знаменатель (p2-p3) близок к нулю, используем равномерное распределение");
            v1 = totalVolume.divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
            v2 = totalVolume.divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
            v3 = totalVolume.subtract(v1).subtract(v2);
        } else {
            v2 = numerator.divide(denominator, 8, RoundingMode.HALF_UP);
            v3 = totalVolume.subtract(v1).subtract(v2);

            // ПРОВЕРКА: если какой-то объем отрицательный, корректируем распределение
            if (v1.compareTo(BigDecimal.ZERO) < 0 || v2.compareTo(BigDecimal.ZERO) < 0 || v3.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Обнаружены отрицательные объемы: v1={}, v2={}, v3={}", v1, v2, v3);

                // Пробуем разные значения v1 от 10% до 80%
                boolean foundPositive = false;
                for (double factor = 0.1; factor <= 0.8; factor += 0.1) {
                    v1 = totalVolume.multiply(BigDecimal.valueOf(factor));

                    numerator = desiredAvgPrice.subtract(entryPrice3).multiply(totalVolume)
                            .subtract(entryPrice1.subtract(entryPrice3).multiply(v1));
                    v2 = numerator.divide(denominator, 8, RoundingMode.HALF_UP);
                    v3 = totalVolume.subtract(v1).subtract(v2);

                    if (v1.compareTo(BigDecimal.ZERO) >= 0 &&
                            v2.compareTo(BigDecimal.ZERO) >= 0 &&
                            v3.compareTo(BigDecimal.ZERO) >= 0) {
                        log.info("Найдено подходящее распределение с v1 = {}% от общего объема", factor * 100);
                        foundPositive = true;
                        break;
                    }
                }

                // Если не удалось найти положительное распределение
                if (!foundPositive) {
                    log.warn("Не удалось найти положительное распределение, используем равномерное");
                    v1 = totalVolume.divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
                    v2 = totalVolume.divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
                    v3 = totalVolume.subtract(v1).subtract(v2);
                }
            }
        }

        log.info("Распределение объемов: v1={}, v2={}, v3={}", v1, v2, v3);

        // ШАГ 3: Рассчитываем объемы в USDT для каждой точки входа
        BigDecimal usdtVolume1 = v1.multiply(entryPrice1);
        BigDecimal usdtVolume2 = v2.multiply(entryPrice2);
        BigDecimal usdtVolume3 = v3.multiply(entryPrice3);
        BigDecimal totalUsdtVolume = usdtVolume1.add(usdtVolume2).add(usdtVolume3);

        // ШАГ 4: ПРОВЕРЯЕМ ТОЧНОСТЬ РАСЧЕТОВ

        // Фактическая средневзвешенная цена (должна равняться TVX)
        BigDecimal actualAvgPrice = entryPrice1.multiply(v1)
                .add(entryPrice2.multiply(v2))
                .add(entryPrice3.multiply(v3))
                .divide(totalVolume, 8, RoundingMode.HALF_UP);

        // Фактическая потеря при срабатывании стопа
        BigDecimal actualLoss;
        if (isLong) {
            // Для LONG: потеря = (средняя_цена_входа - стоп_цена) × объем
            actualLoss = actualAvgPrice.subtract(stopPrice).multiply(totalVolume);
        } else {
            // Для SHORT: потеря = (стоп_цена - средняя_цена_входа) × объем
            actualLoss = stopPrice.subtract(actualAvgPrice).multiply(totalVolume);
        }

        // Фактический процент риска
        BigDecimal actualRiskPercent = actualLoss.divide(deposit, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // Рассчитываем веса для отображения
        BigDecimal weight1 = v1.divide(totalVolume, 8, RoundingMode.HALF_UP);
        BigDecimal weight2 = v2.divide(totalVolume, 8, RoundingMode.HALF_UP);
        BigDecimal weight3 = v3.divide(totalVolume, 8, RoundingMode.HALF_UP);

        // ШАГ 5: Отображаем результаты
        displayResults(actualAvgPrice, totalVolume, maxLossAmount, actualRiskPercent,
                usdtVolume1, usdtVolume2, usdtVolume3, totalUsdtVolume,
                weight1, weight2, weight3, isLong);

        // ШАГ 6: Генерируем детальное описание
        generateDetailedDescription(deposit, stopPercent, desiredAvgPrice, stopPrice,
                entryPrice1, entryPrice2, entryPrice3,
                usdtVolume1, usdtVolume2, usdtVolume3, totalUsdtVolume,
                v1, v2, v3, totalVolume,
                weight1, weight2, weight3,
                actualAvgPrice, actualLoss, actualRiskPercent, isLong);

        // ШАГ 7: Проверка точности по математическим формулам
        log.info("=== ПРОВЕРКА ТОЧНОСТИ РАСЧЕТОВ ===");

        BigDecimal avgPriceDeviation = desiredAvgPrice.subtract(actualAvgPrice).abs();
        BigDecimal riskDeviation = stopPercent.subtract(actualRiskPercent).abs();

        log.info("Желаемая ТВХ: {} | Фактическая ТВХ: {} | Отклонение: {} USDT",
                desiredAvgPrice, actualAvgPrice, avgPriceDeviation);
        log.info("Заданный риск: {}% | Фактический риск: {}% | Отклонение: {}%",
                stopPercent, actualRiskPercent, riskDeviation);
        log.info("Максимальная потеря: {} USDT | Фактическая потеря: {} USDT",
                maxLossAmount, actualLoss);

        // ПРОВЕРЯЕМ МАТЕМАТИЧЕСКИЕ УСЛОВИЯ

        // Условие 1: Средневзвешенная цена должна равняться TVX
        // (p1×v1 + p2×v2 + p3×v3) / (v1+v2+v3) = TVX
        BigDecimal condition1Left = entryPrice1.multiply(v1).add(entryPrice2.multiply(v2)).add(entryPrice3.multiply(v3));
        BigDecimal condition1Right = desiredAvgPrice.multiply(totalVolume);
        boolean condition1Met = condition1Left.subtract(condition1Right).abs().compareTo(BigDecimal.valueOf(0.01)) < 0;
        log.info("Условие 1 (ТВХ): {} ≈ {} | Выполнено: {}", condition1Left, condition1Right, condition1Met);

        // Условие 2: Потеря при стопе должна равняться максимальной потере
        // Для LONG:  (TVX - p_stop) × V = D × R/100
        // Для SHORT: (p_stop - TVX) × V = D × R/100
        boolean condition2Met = actualLoss.subtract(maxLossAmount).abs().compareTo(BigDecimal.valueOf(0.01)) < 0;
        log.info("Условие 2 (риск): {} ≈ {} | Выполнено: {}", actualLoss, maxLossAmount, condition2Met);

        // Итоговая проверка
        if (condition1Met && condition2Met) {
            log.info("✅ ВСЕ МАТЕМАТИЧЕСКИЕ УСЛОВИЯ ВЫПОЛНЕНЫ");
        } else {
            log.warn("⚠️ НЕКОТОРЫЕ МАТЕМАТИЧЕСКИЕ УСЛОВИЯ НЕ ВЫПОЛНЕНЫ");
        }

        log.info("=== ИТОГОВЫЕ ОБЪЕМЫ В USDT ===");
        log.info("1-я точка входа ({} USDT): {} USDT ({} единиц)", entryPrice1, usdtVolume1, v1);
        log.info("2-я точка входа ({} USDT): {} USDT ({} единиц)", entryPrice2, usdtVolume2, v2);
        log.info("3-я точка входа ({} USDT): {} USDT ({} единиц)", entryPrice3, usdtVolume3, v3);
        log.info("ИТОГО: {} USDT ({} единиц актива)", totalUsdtVolume, totalVolume);
    }


    private void displayResults(BigDecimal actualAvgPrice, BigDecimal totalCoins, BigDecimal maxLossAmount, BigDecimal actualRiskPercent,
                                BigDecimal volume1, BigDecimal volume2, BigDecimal volume3, BigDecimal totalVolume,
                                BigDecimal weight1, BigDecimal weight2, BigDecimal weight3, boolean isLong) {

        // Основные результаты
        calculatedAvgPriceLabel.setText(NumberFormatUtil.formatNumber(actualAvgPrice, 2) + " USDT");
        totalCoinsLabel.setText(NumberFormatUtil.formatNumber(totalCoins, 6));
        maxLossAmountLabel.setText(NumberFormatUtil.formatNumber(maxLossAmount, 2) + " USDT");
        actualRiskPercentLabel.setText(NumberFormatUtil.formatPercentage(actualRiskPercent) + " от депозита");

        // Рассчитанные веса
        weight1Label.setText(NumberFormatUtil.formatPercentage(weight1.multiply(BigDecimal.valueOf(100))));
        weight2Label.setText(NumberFormatUtil.formatPercentage(weight2.multiply(BigDecimal.valueOf(100))));
        weight3Label.setText(NumberFormatUtil.formatPercentage(weight3.multiply(BigDecimal.valueOf(100))));

        // ГЛАВНОЕ: Объемы для входов
        volume1Label.setText(NumberFormatUtil.formatNumber(volume1, 2) + " USDT");
        volume2Label.setText(NumberFormatUtil.formatNumber(volume2, 2) + " USDT");
        volume3Label.setText(NumberFormatUtil.formatNumber(volume3, 2) + " USDT");
        totalVolumeLabel.setText(NumberFormatUtil.formatNumber(totalVolume, 2) + " USDT");

        // Стили с учетом типа позиции
        String typeColor = isLong ? "#27ae60" : "#e74c3c";

        calculatedAvgPriceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #3498db;");
        totalCoinsLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + typeColor + ";");
        maxLossAmountLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        actualRiskPercentLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #9b59b6;");

        volume1Label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-background-color: #e8f4f8; -fx-background-radius: 6; -fx-padding: 8;");
        volume2Label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-background-color: #fff3e0; -fx-background-radius: 6; -fx-padding: 8;");
        volume3Label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-background-color: #ffebee; -fx-background-radius: 6; -fx-padding: 8;");
        totalVolumeLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + typeColor + "; -fx-background-color: #e8f5e8; -fx-background-radius: 6; -fx-padding: 8;");
    }

    private void generateDetailedDescription(BigDecimal deposit, BigDecimal riskPercent,
                                             BigDecimal tvx, BigDecimal stopPrice,
                                             BigDecimal p1, BigDecimal p2, BigDecimal p3,
                                             BigDecimal usdt1, BigDecimal usdt2, BigDecimal usdt3, BigDecimal totalUsdt,
                                             BigDecimal v1, BigDecimal v2, BigDecimal v3, BigDecimal totalVolume,
                                             BigDecimal w1, BigDecimal w2, BigDecimal w3,
                                             BigDecimal actualTVX, BigDecimal actualLoss, BigDecimal actualRisk,
                                             boolean isLong) {

        StringBuilder description = new StringBuilder();

        description.append("🧮 РАСЧЕТ ОБЪЕМОВ ДЛЯ УСРЕДНЕНИЯ ПОЗИЦИИ\n");
        description.append("═══════════════════════════════════════════════\n\n");

        // Исходные данные
        description.append("📊 ИСХОДНЫЕ ДАННЫЕ:\n");
        description.append(String.format("• Тип позиции: %s\n", isLong ? "LONG (покупка)" : "SHORT (продажа)"));
        description.append(String.format("• Депозит: %s USDT\n", deposit));
        description.append(String.format("• Допустимый риск: %s%%\n", riskPercent));
        description.append(String.format("• Желаемая ТВХ: %s USDT\n", tvx));
        description.append(String.format("• Стоп-цена: %s USDT\n", stopPrice));
        description.append(String.format("• Цены входа: %s, %s, %s USDT\n\n", p1, p2, p3));

        // Математический расчет
        description.append("🔢 МАТЕМАТИЧЕСКИЙ РАСЧЕТ:\n");
        description.append(String.format("Максимальная потеря = %s × %s%% = %s USDT\n",
                deposit, riskPercent, deposit.multiply(riskPercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))));

        if (isLong) {
            description.append(String.format("Общий объем = %s ÷ (%s - %s) = %s единиц\n\n",
                    deposit.multiply(riskPercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)),
                    tvx, stopPrice, totalVolume));
        } else {
            description.append(String.format("Общий объем = %s ÷ (%s - %s) = %s единиц\n\n",
                    deposit.multiply(riskPercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)),
                    stopPrice, tvx, totalVolume));
        }

        // Распределение объемов
        description.append("📈 РАСПРЕДЕЛЕНИЕ ОБЪЕМОВ ПО ТОЧКАМ ВХОДА:\n");
        description.append("┌─────────────┬────────────────┬────────────────┬──────────┐\n");
        description.append("│ Точка входа │ Цена (USDT)    │ Объем (USDT)   │ Вес (%)  │\n");
        description.append("├─────────────┼────────────────┼────────────────┼──────────┤\n");
        description.append(String.format("│ 1-я точка   │ %14.2f │ %14.2f │ %7.1f%% │\n",
                p1, usdt1, w1.multiply(BigDecimal.valueOf(100))));
        description.append(String.format("│ 2-я точка   │ %14.2f │ %14.2f │ %7.1f%% │\n",
                p2, usdt2, w2.multiply(BigDecimal.valueOf(100))));
        description.append(String.format("│ 3-я точка   │ %14.2f │ %14.2f │ %7.1f%% │\n",
                p3, usdt3, w3.multiply(BigDecimal.valueOf(100))));
        description.append("└─────────────┴────────────────┴────────────────┴──────────┘\n");
        description.append(String.format("ИТОГО: %s USDT (%s единиц актива)\n\n", totalUsdt, totalVolume));

        // Проверка результатов
        description.append("✅ ПРОВЕРКА РЕЗУЛЬТАТОВ:\n");
        description.append(String.format("• Желаемая ТВХ: %s USDT\n", tvx));
        description.append(String.format("• Фактическая ТВХ: %s USDT\n", actualTVX));
        description.append(String.format("• Отклонение ТВХ: %s USDT\n", tvx.subtract(actualTVX).abs()));
        description.append(String.format("• Заданный риск: %s%%\n", riskPercent));
        description.append(String.format("• Фактический риск: %s%%\n", actualRisk));
        description.append(String.format("• Потеря при стопе: %s USDT\n\n", actualLoss));

        // Торговая стратегия
        description.append("📋 ТОРГОВАЯ СТРАТЕГИЯ:\n");
        if (isLong) {
            description.append("Стратегия усреднения LONG позиции:\n");
            description.append(String.format("1. При цене %s USDT покупаем на %s USDT\n", p1, usdt1));
            description.append(String.format("2. При цене %s USDT покупаем на %s USDT\n", p2, usdt2));
            description.append(String.format("3. При цене %s USDT покупаем на %s USDT\n", p3, usdt3));
            description.append(String.format("4. Стоп-лосс на %s USDT (потеря %s USDT)\n", stopPrice, actualLoss));
        } else {
            description.append("Стратегия усреднения SHORT позиции:\n");
            description.append(String.format("1. При цене %s USDT продаем на %s USDT\n", p1, usdt1));
            description.append(String.format("2. При цене %s USDT продаем на %s USDT\n", p2, usdt2));
            description.append(String.format("3. При цене %s USDT продаем на %s USDT\n", p3, usdt3));
            description.append(String.format("4. Стоп-лосс на %s USDT (потеря %s USDT)\n", stopPrice, actualLoss));
        }
    }

    private void clearResults() {
        calculatedAvgPriceLabel.setText("0.00 USDT");
        totalCoinsLabel.setText("0.000000");
        maxLossAmountLabel.setText("0.00 USDT");
        actualRiskPercentLabel.setText("0.0%");

        weight1Label.setText("0.0%");
        weight2Label.setText("0.0%");
        weight3Label.setText("0.0%");

        volume1Label.setText("0.00 USDT");
        volume2Label.setText("0.00 USDT");
        volume3Label.setText("0.00 USDT");
        totalVolumeLabel.setText("0.00 USDT");

        // Сброс стилей
        calculatedAvgPriceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        totalCoinsLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        maxLossAmountLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        actualRiskPercentLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");

        volume1Label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        volume2Label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        volume3Label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
        totalVolumeLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #7f8c8d;");
    }

    @FXML
    private void clearAllFields() {
        depositField.clear();
        stopPercentField.clear();
        desiredAvgPriceField.clear();
        stopPriceField.clear();

        entryPrice1Field.clear();
        entryPrice2Field.clear();
        entryPrice3Field.clear();

        longRadioButton.setSelected(true); // Возвращаем к Long по умолчанию
        updateTradeTypeInfo();
        updatePlaceholders();

        clearResults();
        log.info("Все поля очищены");
    }

    private boolean allFieldsFilled() {
        return !depositField.getText().trim().isEmpty() &&
                !stopPercentField.getText().trim().isEmpty() &&
                !desiredAvgPriceField.getText().trim().isEmpty() &&
                !stopPriceField.getText().trim().isEmpty() &&
                !entryPrice1Field.getText().trim().isEmpty() &&
                !entryPrice2Field.getText().trim().isEmpty() &&
                !entryPrice3Field.getText().trim().isEmpty();
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

