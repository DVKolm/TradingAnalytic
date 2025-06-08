
package com.example.ta.controller.trading;

import com.example.ta.events.NavigationEvent;
import com.example.ta.events.TradeDataChangedEvent;
import com.example.ta.domain.trading.Trade;
import com.example.ta.domain.trading.TradeStatus;
import com.example.ta.domain.trading.TradeType;
import com.example.ta.service.TradeService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradesListController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ComboBox<TradeStatus> statusFilterComboBox;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Button searchButton;
    @FXML private Button clearFiltersButton;
    @FXML private Button refreshButton;

    @FXML private Label totalTradesLabel;
    @FXML private Label totalProfitLabel;
    @FXML private Label winRateLabel;
    @FXML private Label resultCountLabel;

    @FXML private TableView<Trade> tradesTable;
    @FXML private TableColumn<Trade, String> assetColumn;
    @FXML private TableColumn<Trade, String> typeColumn;
    @FXML private TableColumn<Trade, String> statusColumn;
    @FXML private TableColumn<Trade, String> dateColumn;
    @FXML private TableColumn<Trade, String> entryPriceColumn;
    @FXML private TableColumn<Trade, String> exitPriceColumn;
    @FXML private TableColumn<Trade, String> volumeColumn;
    @FXML private TableColumn<Trade, String> profitLossColumn;
    @FXML private TableColumn<Trade, Void> actionsColumn;

    private final TradeService tradeService;
    private final ApplicationEventPublisher eventPublisher;

    private ObservableList<Trade> tradesList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация TradesListController");
        setupFilters();
        setupTable();
        loadTrades();
    }

    private void setupFilters() {
        statusFilterComboBox.setItems(FXCollections.observableArrayList(TradeStatus.values()));
        statusFilterComboBox.setPromptText("Все статусы");

        searchField.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                searchTrades();
            }
        });
    }

    private void setupTable() {
        assetColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getAssetName()));

        typeColumn.setCellValueFactory(data -> {
            TradeType type = data.getValue().getTradeType();
            return new SimpleStringProperty(type == TradeType.LONG ? "LONG" : "SHORT");
        });

        statusColumn.setCellValueFactory(data -> {
            TradeStatus status = data.getValue().getStatus();
            return new SimpleStringProperty(status == TradeStatus.OPEN ? "Открыта" : "Закрыта");
        });

        dateColumn.setCellValueFactory(data -> {
            LocalDate date = data.getValue().getTradeDate();
            String dateStr = date != null ? date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "";
            return new SimpleStringProperty(dateStr);
        });

        entryPriceColumn.setCellValueFactory(data -> {
            BigDecimal price = data.getValue().getEntryPoint();
            String currency = data.getValue().getCurrency() != null ? data.getValue().getCurrency().getSymbol() : "";
            return new SimpleStringProperty(price != null ? currency + String.format("%.2f", price) : "");
        });

        exitPriceColumn.setCellValueFactory(data -> {
            BigDecimal price = data.getValue().getExitPoint();
            String currency = data.getValue().getCurrency() != null ? data.getValue().getCurrency().getSymbol() : "";
            return new SimpleStringProperty(price != null ? currency + String.format("%.2f", price) : "-");
        });

        volumeColumn.setCellValueFactory(data -> {
            BigDecimal volume = data.getValue().getVolume();
            return new SimpleStringProperty(volume != null ? String.format("%.4f", volume) : "");
        });

        profitLossColumn.setCellValueFactory(data -> {
            BigDecimal profitLoss = data.getValue().getProfitLoss();
            String currency = data.getValue().getCurrency() != null ? data.getValue().getCurrency().getSymbol() : "";
            if (profitLoss != null) {
                String sign = profitLoss.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                return new SimpleStringProperty(sign + currency + String.format("%.2f", profitLoss));
            }
            return new SimpleStringProperty("-");
        });

        setupCenteredTextCellFactory(assetColumn);
        setupCenteredTextCellFactory(typeColumn);
        setupCenteredTextCellFactory(dateColumn);
        setupCenteredTextCellFactory(entryPriceColumn);
        setupCenteredTextCellFactory(exitPriceColumn);
        setupCenteredTextCellFactory(volumeColumn);

        statusColumn.setCellFactory(column -> new TableCell<Trade, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER); // Центрирование
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("Открыта")) {
                        setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold; -fx-alignment: center;");
                    } else {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-alignment: center;");
                    }
                }
            }
        });

        profitLossColumn.setCellFactory(column -> new TableCell<Trade, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER); // Центрирование
                if (empty || item == null || item.equals("-")) {
                    setText(item);
                    setStyle("-fx-alignment: center;");
                } else {
                    setText(item);
                    if (item.startsWith("+")) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-alignment: center;");
                    } else if (item.startsWith("-")) {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-alignment: center;");
                    } else {
                        setStyle("-fx-text-fill: #6c757d; -fx-font-weight: bold; -fx-alignment: center;");
                    }
                }
            }
        });

        actionsColumn.setCellFactory(column -> new TableCell<Trade, Void>() {
            private final Button viewButton = new Button("Детали");
            private final Button editButton = new Button("Изменить");
            private final Button deleteButton = new Button("Удалить");
            private final HBox actionBox = new HBox(8);

            {
                viewButton.setStyle(
                        "-fx-background-color: #3498db; " +
                                "-fx-text-fill: white; " +
                                "-fx-font-size: 11px; " +
                                "-fx-font-weight: 600; " +
                                "-fx-background-radius: 4; " +
                                "-fx-cursor: hand; " +
                                "-fx-padding: 4 8 4 8; " +
                                "-fx-min-width: 60px; " +
                                "-fx-pref-width: 60px;"
                );

                editButton.setStyle(
                        "-fx-background-color: #f39c12; " +
                                "-fx-text-fill: white; " +
                                "-fx-font-size: 11px; " +
                                "-fx-font-weight: 600; " +
                                "-fx-background-radius: 4; " +
                                "-fx-cursor: hand; " +
                                "-fx-padding: 4 8 4 8; " +
                                "-fx-min-width: 70px; " +
                                "-fx-pref-width: 70px;"
                );

                deleteButton.setStyle(
                        "-fx-background-color: #e74c3c; " +
                                "-fx-text-fill: white; " +
                                "-fx-font-size: 11px; " +
                                "-fx-font-weight: 600; " +
                                "-fx-background-radius: 4; " +
                                "-fx-cursor: hand; " +
                                "-fx-padding: 4 8 4 8; " +
                                "-fx-min-width: 65px; " +
                                "-fx-pref-width: 65px;"
                );

                viewButton.setOnMouseEntered(e ->
                        viewButton.setStyle(viewButton.getStyle() + "-fx-background-color: #2980b9;"));
                viewButton.setOnMouseExited(e ->
                        viewButton.setStyle(viewButton.getStyle().replace("-fx-background-color: #2980b9;", "-fx-background-color: #3498db;")));

                editButton.setOnMouseEntered(e ->
                        editButton.setStyle(editButton.getStyle() + "-fx-background-color: #e67e22;"));
                editButton.setOnMouseExited(e ->
                        editButton.setStyle(editButton.getStyle().replace("-fx-background-color: #e67e22;", "-fx-background-color: #f39c12;")));

                deleteButton.setOnMouseEntered(e ->
                        deleteButton.setStyle(deleteButton.getStyle() + "-fx-background-color: #c0392b;"));
                deleteButton.setOnMouseExited(e ->
                        deleteButton.setStyle(deleteButton.getStyle().replace("-fx-background-color: #c0392b;", "-fx-background-color: #e74c3c;")));

                viewButton.setTooltip(new Tooltip("Посмотреть детали сделки"));
                editButton.setTooltip(new Tooltip("Редактировать сделку"));
                deleteButton.setTooltip(new Tooltip("Удалить сделку"));

                actionBox.getChildren().addAll(viewButton, editButton, deleteButton);
                actionBox.setAlignment(Pos.CENTER);

                viewButton.setOnAction(event -> {
                    Trade trade = getTableView().getItems().get(getIndex());
                    viewTradeDetails(trade);
                });

                editButton.setOnAction(event -> {
                    Trade trade = getTableView().getItems().get(getIndex());
                    editTrade(trade);
                });

                deleteButton.setOnAction(event -> {
                    Trade trade = getTableView().getItems().get(getIndex());
                    deleteTrade(trade);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(actionBox);
                }
            }
        });

        tradesTable.setItems(tradesList);

        tradesTable.setRowFactory(tv -> {
            TableRow<Trade> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    viewTradeDetails(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * Настройка центрирования текста в ячейках для обычных колонок
     */
    private void setupCenteredTextCellFactory(TableColumn<Trade, String> column) {
        column.setCellFactory(col -> new TableCell<Trade, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: center;");
                } else {
                    setText(item);
                    setStyle("-fx-alignment: center;");
                }
            }
        });
    }

    private void viewTradeDetails(Trade trade) {
        log.info("Переход к деталям сделки: {}", trade.getId());
        eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.VIEW_TRADE_DETAILS, trade));
    }

    private void editTrade(Trade trade) {
        log.info("Переход к редактированию сделки: {}", trade.getId());
        eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.EDIT_TRADE, trade));
    }

    private void deleteTrade(Trade trade) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Подтверждение удаления");
        alert.setHeaderText("Удаление сделки");
        alert.setContentText("Вы действительно хотите удалить сделку \"" + trade.getAssetName() + "\"?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                tradeService.deleteById(trade.getId());
                eventPublisher.publishEvent(new TradeDataChangedEvent(this, "UPDATED"));
                showInfo("Сделка успешно удалена");
                loadTrades();
            } catch (Exception e) {
                log.error("Ошибка при удалении сделки", e);
                showError("Ошибка при удалении сделки: " + e.getMessage());
            }
        }
    }

    @FXML
    private void searchTrades() {
        log.info("Поиск сделок");

        String assetName = searchField.getText();
        TradeStatus status = statusFilterComboBox.getValue();
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        List<Trade> searchResults = tradeService.search(assetName, status, startDate, endDate);

        tradesList.clear();
        tradesList.addAll(searchResults);

        updateStatistics();
        resultCountLabel.setText("Найдено: " + searchResults.size() + " сделок");

        log.info("Найдено {} сделок", searchResults.size());
    }

    @FXML
    private void clearFilters() {
        log.info("Очистка фильтров");

        searchField.clear();
        statusFilterComboBox.setValue(null);
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);

        loadTrades();
    }

    @FXML
    private void refreshTrades() {
        log.info("Обновление списка сделок");
        loadTrades();
    }

    private void loadTrades() {
        try {
            List<Trade> trades = tradeService.findAll();
            tradesList.clear();
            tradesList.addAll(trades);

            updateStatistics();
            resultCountLabel.setText("Всего: " + trades.size() + " сделок");

            log.info("Загружено {} сделок", trades.size());
        } catch (Exception e) {
            log.error("Ошибка при загрузке сделок", e);
            showError("Ошибка при загрузке сделок: " + e.getMessage());
        }
    }

    private void updateStatistics() {
        try {
            List<Trade> currentTrades = tradesList;

            totalTradesLabel.setText(String.valueOf(currentTrades.size()));

            BigDecimal totalProfit = currentTrades.stream()
                    .filter(trade -> trade.getProfitLoss() != null)
                    .map(Trade::getProfitLoss)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalProfitLabel.setText("$" + String.format("%.2f", totalProfit));
            if (totalProfit.compareTo(BigDecimal.ZERO) >= 0) {
                totalProfitLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            } else {
                totalProfitLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            }

            long profitableTrades = currentTrades.stream()
                    .filter(trade -> trade.getProfitLoss() != null)
                    .filter(trade -> trade.getProfitLoss().compareTo(BigDecimal.ZERO) > 0)
                    .count();

            long totalWithPL = currentTrades.stream()
                    .filter(trade -> trade.getProfitLoss() != null)
                    .count();

            if (totalWithPL > 0) {
                double winRate = ((double) profitableTrades / totalWithPL) * 100;
                winRateLabel.setText(String.format("%.1f%%", winRate));
            } else {
                winRateLabel.setText("0%");
            }

        } catch (Exception e) {
            log.error("Ошибка при обновлении статистики", e);
        }
    }

    @EventListener
    public void onTradeDataChanged(TradeDataChangedEvent event) {
        log.info("Получено событие об изменении данных сделок");
        loadTrades();
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