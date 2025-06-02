package com.example.ta.controller;

import com.example.ta.events.NavigationEvent;
import com.example.ta.domain.Trade;
import com.example.ta.service.TradeService;
import com.example.ta.util.DateMaskFormatter;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeDetailsController implements Initializable {

    @FXML private Label titleLabel;
    @FXML private Label assetNameLabel;
    @FXML private Label tradeTypeLabel;
    @FXML private Label statusLabel;
    @FXML private Label tradeDateLabel;

    @FXML private Label entryPointLabel;
    @FXML private Label exitPointLabel;
    @FXML private Label volumeLabel;
    @FXML private Label volumeInCurrencyLabel;

    @FXML private Label profitLossLabel;
    @FXML private Label priceMovementLabel;

    @FXML private Label entryTimeLabel;
    @FXML private Label exitTimeLabel;

    @FXML private TextArea entryReasonArea;
    @FXML private TextArea exitReasonArea;
    @FXML private TextArea commentArea;

    @FXML private ImageView chartImageView;
    @FXML private Button uploadImageButton;
    @FXML private Button removeImageButton;
    @FXML private Label imageStatusLabel;

    @FXML private Button editButton;
    @FXML private Button closeButton;

    private Trade currentTrade;
    private final TradeService tradeService;
    private final ApplicationEventPublisher eventPublisher;

    @Setter
    private Stage dialogStage;

    private static final String IMAGES_FOLDER = "trade_images";

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация TradeDetailsController");

        createImagesFolder();

        addButtonHoverEffects();

        log.info("TradeDetailsController инициализирован");
    }

    private void addButtonHoverEffects() {
        editButton.setOnMouseEntered(e ->
                editButton.setStyle(editButton.getStyle() + "-fx-background-color: #e67e22;"));
        editButton.setOnMouseExited(e ->
                editButton.setStyle(editButton.getStyle().replace("-fx-background-color: #e67e22;", "-fx-background-color: #f39c12;")));

        closeButton.setOnMouseEntered(e ->
                closeButton.setStyle(closeButton.getStyle() + "-fx-background-color: #7f8c8d;"));
        closeButton.setOnMouseExited(e ->
                closeButton.setStyle(closeButton.getStyle().replace("-fx-background-color: #7f8c8d;", "-fx-background-color: #95a5a6;")));

        uploadImageButton.setOnMouseEntered(e ->
                uploadImageButton.setStyle(uploadImageButton.getStyle() + "-fx-background-color: #2980b9;"));
        uploadImageButton.setOnMouseExited(e ->
                uploadImageButton.setStyle(uploadImageButton.getStyle().replace("-fx-background-color: #2980b9;", "-fx-background-color: #3498db;")));

        removeImageButton.setOnMouseEntered(e ->
                removeImageButton.setStyle(removeImageButton.getStyle() + "-fx-background-color: #c0392b;"));
        removeImageButton.setOnMouseExited(e ->
                removeImageButton.setStyle(removeImageButton.getStyle().replace("-fx-background-color: #c0392b;", "-fx-background-color: #e74c3c;")));
    }

    public void setTrade(Trade trade) {
        this.currentTrade = trade;
        displayTradeDetails();
        loadChartImage();
    }

    private void displayTradeDetails() {
        if (currentTrade == null) {
            log.warn("Попытка отобразить детали null сделки");
            return;
        }

        log.info("Отображение деталей сделки: {}", currentTrade.getId());

        titleLabel.setText("Детали сделки: " + currentTrade.getAssetName());

        assetNameLabel.setText(currentTrade.getAssetName());
        tradeTypeLabel.setText(currentTrade.getTradeType().name().equals("LONG") ? "Buy (Long)" : "Sell (Short)");

        String statusText = currentTrade.getStatus().name().equals("OPEN") ? "Открыта" : "Закрыта";
        statusLabel.setText(statusText);
        if (currentTrade.getStatus().name().equals("OPEN")) {
            statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #fff3cd; -fx-text-fill: #856404; -fx-background-radius: 15; -fx-padding: 4 12 4 12;");
        } else {
            statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-background-radius: 15; -fx-padding: 4 12 4 12;");
        }

        if (currentTrade.getTradeDate() != null) {
            tradeDateLabel.setText(currentTrade.getTradeDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        } else {
            tradeDateLabel.setText("Не указана");
        }

        entryPointLabel.setText(formatPrice(currentTrade.getEntryPoint(), currentTrade.getCurrency()));
        exitPointLabel.setText(currentTrade.getExitPoint() != null ?
                formatPrice(currentTrade.getExitPoint(), currentTrade.getCurrency()) : "Не закрыта");
        volumeLabel.setText(formatVolume(currentTrade.getVolume()));

        if (currentTrade.getEntryPoint() != null && currentTrade.getVolume() != null) {
            BigDecimal volumeInCurrency = currentTrade.getEntryPoint().multiply(currentTrade.getVolume());
            volumeInCurrencyLabel.setText(formatPrice(volumeInCurrency, currentTrade.getCurrency()));
        } else {
            volumeInCurrencyLabel.setText("Не рассчитан");
        }

        if (currentTrade.getProfitLoss() != null) {
            String profitLossText = formatPrice(currentTrade.getProfitLoss(), currentTrade.getCurrency());
            profitLossLabel.setText(profitLossText);

            if (currentTrade.getProfitLoss().compareTo(BigDecimal.ZERO) > 0) {
                profitLossLabel.setStyle("-fx-text-fill: #27ae60;");
            } else if (currentTrade.getProfitLoss().compareTo(BigDecimal.ZERO) < 0) {
                profitLossLabel.setStyle("-fx-text-fill: #e74c3c;");
            } else {
                profitLossLabel.setStyle("-fx-text-fill: #6c757d;");
            }
        } else {
            profitLossLabel.setText("Не рассчитана");
            profitLossLabel.setStyle("-fx-text-fill: #6c757d;");
        }

        if (currentTrade.getEntryPoint() != null && currentTrade.getExitPoint() != null) {
            BigDecimal priceChange = currentTrade.getExitPoint().subtract(currentTrade.getEntryPoint());
            BigDecimal priceChangePercent = priceChange.divide(currentTrade.getEntryPoint(), 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            String sign = priceChange.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            String movementText = sign + String.format("%.2f", priceChange) + " (" + sign + String.format("%.2f%%)", priceChangePercent);
            priceMovementLabel.setText(movementText);

            if (priceChange.compareTo(BigDecimal.ZERO) > 0) {
                priceMovementLabel.setStyle("-fx-text-fill: #27ae60;");
            } else if (priceChange.compareTo(BigDecimal.ZERO) < 0) {
                priceMovementLabel.setStyle("-fx-text-fill: #e74c3c;");
            } else {
                priceMovementLabel.setStyle("-fx-text-fill: #6c757d;");
            }
        } else {
            priceMovementLabel.setText("Не рассчитано");
            priceMovementLabel.setStyle("-fx-text-fill: #6c757d;");
        }

        // Время
        entryTimeLabel.setText(formatDateTime(currentTrade.getEntryTime()));
        exitTimeLabel.setText(formatDateTime(currentTrade.getExitTime()));

        entryReasonArea.setText(currentTrade.getEntryReason() != null ? currentTrade.getEntryReason() : "");
        exitReasonArea.setText(currentTrade.getExitReason() != null ? currentTrade.getExitReason() : "");
        commentArea.setText(currentTrade.getComment() != null ? currentTrade.getComment() : "");
    }


    private String formatPrice(BigDecimal price, com.example.ta.domain.Currency currency) {
        if (price == null) return "Не указана";
        String symbol = currency != null ? currency.getSymbol() : "$";
        return symbol + String.format("%.2f", price);
    }

    private String formatVolume(BigDecimal volume) {
        if (volume == null) return "Не указан";
        return String.format("%.4f", volume);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "Не указано";
        return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    private void loadChartImage() {
        if (currentTrade == null || currentTrade.getChartImagePath() == null || currentTrade.getChartImagePath().trim().isEmpty()) {
            chartImageView.setImage(null);
            imageStatusLabel.setText("Изображение не загружено");
            removeImageButton.setDisable(true);
            return;
        }

        try {
            Path imagePath = Paths.get(IMAGES_FOLDER, currentTrade.getChartImagePath());
            if (Files.exists(imagePath)) {
                Image image = new Image(imagePath.toUri().toString());
                chartImageView.setImage(image);
                imageStatusLabel.setText("Изображение загружено: " + currentTrade.getChartImagePath());
                removeImageButton.setDisable(false);
                log.info("Загружено изображение: {}", imagePath);
            } else {
                chartImageView.setImage(null);
                imageStatusLabel.setText("Файл изображения не найден: " + currentTrade.getChartImagePath());
                removeImageButton.setDisable(true);
                log.warn("Файл изображения не найден: {}", imagePath);
            }
        } catch (Exception e) {
            log.error("Ошибка при загрузке изображения", e);
            chartImageView.setImage(null);
            imageStatusLabel.setText("Ошибка при загрузке изображения");
            removeImageButton.setDisable(true);
        }
    }

    @FXML
    private void uploadImage() {
        if (currentTrade == null) {
            showErrorAlert("Сделка не выбрана");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение графика");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );

        File selectedFile = fileChooser.showOpenDialog(uploadImageButton.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        try {
            String fileExtension = getFileExtension(selectedFile.getName());
            String fileName = "trade_" + currentTrade.getId() + "_" + System.currentTimeMillis() + fileExtension;

            Path targetPath = Paths.get(IMAGES_FOLDER, fileName);
            Files.copy(selectedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            currentTrade.setChartImagePath(fileName);
            tradeService.save(currentTrade);

            loadChartImage();

            showSuccessAlert("Изображение успешно загружено");
            log.info("Изображение загружено для сделки {}: {}", currentTrade.getId(), fileName);

        } catch (IOException e) {
            log.error("Ошибка при загрузке изображения", e);
            showErrorAlert("Ошибка при загрузке изображения: " + e.getMessage());
        }
    }

    @FXML
    private void removeImage() {
        if (currentTrade == null || currentTrade.getChartImagePath() == null) {
            return;
        }

        Alert alert = createStyledAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Подтверждение удаления");
        alert.setHeaderText("Удаление изображения");
        alert.setContentText("Вы действительно хотите удалить изображение графика?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    Path imagePath = Paths.get(IMAGES_FOLDER, currentTrade.getChartImagePath());
                    if (Files.exists(imagePath)) {
                        Files.delete(imagePath);
                        log.info("Удален файл изображения: {}", imagePath);
                    }

                    currentTrade.setChartImagePath(null);
                    tradeService.save(currentTrade);

                    loadChartImage();

                    showSuccessAlert("Изображение успешно удалено");

                } catch (IOException e) {
                    log.error("Ошибка при удалении изображения", e);
                    showErrorAlert("Ошибка при удалении изображения: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void editTrade() {
        if (currentTrade == null) {
            showErrorAlert("Сделка не выбрана для редактирования");
            return;
        }

        log.info("Переход к редактированию сделки: {}", currentTrade.getId());

        eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.EDIT_TRADE, currentTrade));
    }

    @FXML
    private void closeDialog() {
        log.info("Переход к списку сделок");
        eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.VIEW_TRADES));
    }

    private void createImagesFolder() {
        try {
            Path folder = Paths.get(IMAGES_FOLDER);
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
                log.info("Создана папка для изображений: {}", folder.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Ошибка при создании папки для изображений", e);
        }
    }

    private String getFileExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ".jpg";
        }
        return fileName.substring(lastIndexOf);
    }

    private Alert createStyledAlert(Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.getDialogPane().setStyle(
                "-fx-font-family: 'Segoe UI'; " +
                        "-fx-font-size: 13px; " +
                        "-fx-background-color: white;"
        );
        return alert;
    }

    private void showSuccessAlert(String message) {
        Alert alert = createStyledAlert(Alert.AlertType.INFORMATION);
        alert.setTitle("Успех");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showErrorAlert(String message) {
        Alert alert = createStyledAlert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}