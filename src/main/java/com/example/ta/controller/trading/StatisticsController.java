package com.example.ta.controller.trading;

import com.example.ta.domain.trading.PeriodType;
import com.example.ta.domain.trading.Trade;
import com.example.ta.domain.trading.TradeStatistics;
import com.example.ta.events.TradeDataChangedEvent;
import com.example.ta.service.ExcelExportService;
import com.example.ta.service.TradeService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsController implements Initializable {

    @FXML private ComboBox<PeriodType> periodTypeComboBox;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;

    @FXML private Label totalTradesStatLabel;
    @FXML private Label totalProfitStatLabel;
    @FXML private Label winRateStatLabel;
    @FXML private Label avgProfitStatLabel;
    @FXML private Label maxProfitStatLabel;
    @FXML private Label maxLossStatLabel;
    @FXML private Label totalVolumeStatLabel;
    @FXML private Label profitableTradesStatLabel;
    @FXML private Label losingTradesStatLabel;

    @FXML private LineChart<String, Number> equityCurveChart;

    private final TradeService tradeService;
    private final ExcelExportService excelExportService;

    private TradeStatistics currentStatistics;
    private LocalDate currentStartDate;
    private LocalDate currentEndDate;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация StatisticsController");

        setupPeriodControls();
        loadCurrentStatistics();

        log.info("StatisticsController инициализирован");
    }

    private void setupPeriodControls() {
        periodTypeComboBox.setItems(FXCollections.observableArrayList(PeriodType.values()));
        periodTypeComboBox.setValue(PeriodType.ALL_TIME);

        periodTypeComboBox.setOnAction(event -> {
            PeriodType selectedPeriod = periodTypeComboBox.getValue();
            updateDatePickersForPeriod(selectedPeriod);
        });

        updateDatePickersForPeriod(PeriodType.ALL_TIME);
    }

    private void updateDatePickersForPeriod(PeriodType periodType) {
        boolean isCustomPeriod = (periodType == PeriodType.CUSTOM);

        startDatePicker.setDisable(!isCustomPeriod);
        endDatePicker.setDisable(!isCustomPeriod);

        if (!isCustomPeriod && periodType != PeriodType.ALL_TIME) {
            LocalDate startDate = periodType.getStartDate();
            LocalDate endDate = periodType.getEndDate();

            startDatePicker.setValue(startDate);
            endDatePicker.setValue(endDate);

            log.info("Установлен период {}: {} - {}", periodType.getDisplayName(), startDate, endDate);
        } else if (periodType == PeriodType.ALL_TIME) {
            startDatePicker.setValue(null);
            endDatePicker.setValue(null);
            log.info("Установлен период: Все время");
        }
    }

    @FXML
    private void calculateStatistics() {
        log.info("Запрос на расчет статистики");
        loadCurrentStatistics();
    }

    @FXML
    private void exportToExcel() {
        log.info("Запрос на экспорт статистики в Excel");

        if (currentStatistics == null) {
            showAlert("Ошибка", "Сначала рассчитайте статистику, а затем экспортируйте в Excel");
            return;
        }

        try {
            Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
            loadingAlert.setTitle("Экспорт");
            loadingAlert.setHeaderText("Создание Excel отчета...");
            loadingAlert.setContentText("Пожалуйста, подождите");
            loadingAlert.show();

            File excelFile = excelExportService.exportTradingStatistics(
                    currentStatistics, currentStartDate, currentEndDate);

            loadingAlert.close();

            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Экспорт завершен");
            successAlert.setHeaderText("Excel отчет успешно создан!");
            successAlert.setContentText("Файл сохранен: " + excelFile.getAbsolutePath());

            ButtonType openFileButton = new ButtonType("Открыть файл");
            ButtonType openFolderButton = new ButtonType("Открыть папку");
            ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);

            successAlert.getButtonTypes().setAll(openFileButton, openFolderButton, okButton);

            successAlert.showAndWait().ifPresent(buttonType -> {
                try {
                    if (buttonType == openFileButton) {
                        Desktop.getDesktop().open(excelFile);
                    } else if (buttonType == openFolderButton) {
                        Desktop.getDesktop().open(excelFile.getParentFile());
                    }
                } catch (Exception e) {
                    log.error("Ошибка при открытии файла/папки", e);
                    showAlert("Ошибка", "Не удалось открыть файл: " + e.getMessage());
                }
            });

            log.info("Excel отчет успешно создан: {}", excelFile.getAbsolutePath());

        } catch (Exception e) {
            log.error("Ошибка при экспорте в Excel", e);
            showAlert("Ошибка экспорта", "Не удалось создать Excel отчет: " + e.getMessage());
        }
    }

    private void loadCurrentStatistics() {
        try {

            if (periodTypeComboBox == null) {
                log.warn("StatisticsController еще не инициализирован, пропускаем загрузку статистики");
                return;
            }


            PeriodType selectedPeriod = periodTypeComboBox.getValue();
            LocalDate startDate = null;
            LocalDate endDate = null;

            if (selectedPeriod == null) {
                selectedPeriod = PeriodType.ALL_TIME;
                periodTypeComboBox.setValue(selectedPeriod);
            }

            if (selectedPeriod == null) {
                selectedPeriod = PeriodType.ALL_TIME;
                periodTypeComboBox.setValue(selectedPeriod);
            } else if (selectedPeriod == PeriodType.CUSTOM) {
                startDate = startDatePicker.getValue();
                endDate = endDatePicker.getValue();

                if (startDate == null || endDate == null) {
                    showAlert("Ошибка", "Для произвольного периода необходимо указать начальную и конечную даты");
                    return;
                }

                if (startDate.isAfter(endDate)) {
                    showAlert("Ошибка", "Начальная дата не может быть позже конечной");
                    return;
                }

                log.info("Загрузка статистики за произвольный период: {} - {}", startDate, endDate);
            } else {
                startDate = selectedPeriod.getStartDate();
                endDate = selectedPeriod.getEndDate();
                log.info("Загрузка статистики за период {}: {} - {}",
                        selectedPeriod.getDisplayName(), startDate, endDate);
            }

            TradeStatistics statistics;
            if (selectedPeriod == PeriodType.ALL_TIME) {
                statistics = tradeService.calculateStatistics();
            } else {
                statistics = tradeService.getStatistics(startDate, endDate);
            }

            statistics.setPeriodType(selectedPeriod);
            statistics.setPeriodStart(startDate);
            statistics.setPeriodEnd(endDate);

            currentStatistics = statistics;
            currentStartDate = startDate;
            currentEndDate = endDate;

            updateStatisticsUI(statistics);
            updateCharts();

            log.info("Статистика за {} загружена", selectedPeriod.getDisplayName());

        } catch (Exception e) {
            log.error("Ошибка при загрузке статистики", e);
            showAlert("Ошибка", "Не удалось загрузить статистику: " + e.getMessage());
        }
    }

    private void updateStatisticsUI(TradeStatistics stats) {
        try {
            totalTradesStatLabel.setText(String.valueOf(stats.getTotalTrades()));

            BigDecimal totalProfit = stats.getTotalProfit();
            totalProfitStatLabel.setText(String.format("%.2f $", totalProfit));
            if (totalProfit.compareTo(BigDecimal.ZERO) >= 0) {
                totalProfitStatLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            } else {
                totalProfitStatLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            }

            BigDecimal winRate = stats.getWinRate();
            winRateStatLabel.setText(String.format("%.1f%%", winRate));
            double winRateValue = winRate.doubleValue();
            if (winRateValue >= 60.0) {
                winRateStatLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            } else if (winRateValue >= 40.0) {
                winRateStatLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #f39c12;");
            } else {
                winRateStatLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            }

            avgProfitStatLabel.setText(String.format("%.2f $", stats.getAvgProfit()));
            maxProfitStatLabel.setText(String.format("%.2f $", stats.getMaxProfit()));
            maxLossStatLabel.setText(String.format("%.2f $", stats.getMaxLoss()));
            totalVolumeStatLabel.setText(String.format("%.2f $", stats.getTotalVolume()));
            profitableTradesStatLabel.setText(String.valueOf(stats.getWinningTrades()));
            losingTradesStatLabel.setText(String.valueOf(stats.getLosingTrades()));

            log.info("UI обновлен с новой статистикой");

        } catch (Exception e) {
            log.error("Ошибка при обновлении UI статистики", e);
        }
    }

    private void updateCharts() {
        updateEquityCurve();
    }

    private void updateEquityCurve() {
        try {
            equityCurveChart.getData().clear();

            PeriodType selectedPeriod = periodTypeComboBox.getValue();
            LocalDate startDate = null;
            LocalDate endDate = null;

            if (selectedPeriod != PeriodType.ALL_TIME) {
                if (selectedPeriod == PeriodType.CUSTOM) {
                    startDate = startDatePicker.getValue();
                    endDate = endDatePicker.getValue();
                } else {
                    startDate = selectedPeriod.getStartDate();
                    endDate = selectedPeriod.getEndDate();
                }
            }

            List<Trade> closedTrades = tradeService.getClosedTradesForPeriod(startDate, endDate);

            if (closedTrades.isEmpty()) {
                log.info("Нет закрытых сделок для построения кривой эквити за период {}",
                        selectedPeriod.getDisplayName());
                return;
            }

            XYChart.Series<String, Number> equitySeries = new XYChart.Series<>();
            equitySeries.setName("Эквити (" + selectedPeriod.getDisplayName() + ")");

            List<Trade> sortableTrades = new ArrayList<>(closedTrades);

            sortableTrades.sort((t1, t2) -> {
                if (t1.getTradeDate() == null && t2.getTradeDate() == null) return 0;
                if (t1.getTradeDate() == null) return 1;
                if (t2.getTradeDate() == null) return -1;
                return t1.getTradeDate().compareTo(t2.getTradeDate());
            });

            BigDecimal cumulativeProfit = BigDecimal.ZERO;

            if (!sortableTrades.isEmpty() && sortableTrades.getFirst().getTradeDate() != null) {
                equitySeries.getData().add(new XYChart.Data<>("Начало", 0));
            }

            int tradeIndex = 1;
            for (Trade trade : sortableTrades) {
                if (trade.getProfitLoss() != null && trade.getTradeDate() != null) {
                    cumulativeProfit = cumulativeProfit.add(trade.getProfitLoss());

                    String dateLabel = getTradeLabel(trade, selectedPeriod, tradeIndex);
                    equitySeries.getData().add(new XYChart.Data<>(dateLabel, cumulativeProfit));
                    tradeIndex++;
                }
            }

            if (!equitySeries.getData().isEmpty()) {
                equityCurveChart.getData().add(equitySeries);

                equityCurveChart.lookupAll(".chart-series-line").forEach(node ->
                        node.setStyle("-fx-stroke: #3498db; -fx-stroke-width: 2px;"));

                log.info("Кривая эквити обновлена с {} точками для периода {}",
                        equitySeries.getData().size(), selectedPeriod.getDisplayName());
            } else {
                log.info("Нет данных для отображения кривой эквити за период {}",
                        selectedPeriod.getDisplayName());
            }

        } catch (Exception e) {
            log.error("Ошибка при обновлении кривой эквити", e);
        }
    }

    /**
     * Получить подпись для сделки в зависимости от периода
     */
    private String getTradeLabel(Trade trade, PeriodType periodType, int tradeIndex) {
        LocalDate tradeDate = trade.getTradeDate();

        return switch (periodType) {
            case TODAY -> {
                if (trade.getCreatedAt() != null) {
                    LocalDateTime dateTime = trade.getCreatedAt();
                    yield dateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
                } else {
                    yield "Сделка " + tradeIndex;
                }
            }
            case WEEK, MONTH, QUARTER, HALF_YEAR, YEAR -> tradeDate.format(DateTimeFormatter.ofPattern("dd.MM"));
            default -> tradeDate.format(DateTimeFormatter.ofPattern("dd.MM.yy"));
        };
    }

    @EventListener
    public void onTradeDataChanged(TradeDataChangedEvent event) {
        log.info("Получено событие изменения данных сделок, обновляем статистику");
        loadCurrentStatistics();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}