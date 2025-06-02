package com.example.ta.controller;

import com.example.ta.events.NavigationEvent;
import com.example.ta.domain.Trade;
import com.example.ta.service.ImageViewerService;
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
import java.io.FileInputStream;
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
    @FXML private Button viewFullImageButton; // НОВАЯ КНОПКА
    @FXML private Label imageStatusLabel;

    @FXML private Button editButton;
    @FXML private Button closeButton;

    private Trade currentTrade;
    private final TradeService tradeService;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageViewerService imageViewerService; // НОВЫЙ СЕРВИС

    @Setter
    private Stage dialogStage;

    private static final String IMAGES_FOLDER = "trade_images";

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        log.info("Инициализация TradeDetailsController");

        // Делаем текстовые области только для чтения
        entryReasonArea.setEditable(false);
        exitReasonArea.setEditable(false);
        commentArea.setEditable(false);

        // Настраиваем ImageView
        chartImageView.setPreserveRatio(true);
        chartImageView.setSmooth(true);
        chartImageView.setCache(true);

        // Настраиваем кнопки
        addButtonHoverEffects();

        // Изначально скрываем кнопку просмотра
        viewFullImageButton.setVisible(false);

        log.info("TradeDetailsController инициализирован");
    }

    private void addButtonHoverEffects() {
        String buttonStyle = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand; -fx-padding: 8 16 8 16;";
        String hoverStyle = "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand; -fx-padding: 8 16 8 16;";

        for (Button button : new Button[]{uploadImageButton, removeImageButton, viewFullImageButton, editButton, closeButton}) {
            if (button != null) {
                button.setStyle(buttonStyle);
                button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
                button.setOnMouseExited(e -> button.setStyle(buttonStyle));
            }
        }
    }

    public void setTrade(Trade trade) {
        this.currentTrade = trade;
        displayTradeDetails();
        loadChartImage();
    }

    private void displayTradeDetails() {
        if (currentTrade == null) return;

        titleLabel.setText("Детали сделки #" + currentTrade.getId());
        assetNameLabel.setText(currentTrade.getAssetName());
        tradeTypeLabel.setText(currentTrade.getTradeType().getDisplayName());
        statusLabel.setText(currentTrade.getStatus().getDisplayName());
        tradeDateLabel.setText(currentTrade.getTradeDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));

        entryPointLabel.setText(formatPrice(currentTrade.getEntryPoint(), currentTrade.getCurrency()));
        exitPointLabel.setText(currentTrade.getExitPoint() != null ?
                formatPrice(currentTrade.getExitPoint(), currentTrade.getCurrency()) : "Не закрыта");
        volumeLabel.setText(formatVolume(currentTrade.getVolume()));
        volumeInCurrencyLabel.setText(currentTrade.getFormattedVolumeInCurrency());

        profitLossLabel.setText(currentTrade.getFormattedProfitLoss());
        priceMovementLabel.setText(currentTrade.getFormattedPriceMovement());

        entryTimeLabel.setText(formatDateTime(currentTrade.getEntryTime()));
        exitTimeLabel.setText(formatDateTime(currentTrade.getExitTime()));

        entryReasonArea.setText(currentTrade.getEntryReason() != null ? currentTrade.getEntryReason() : "");
        exitReasonArea.setText(currentTrade.getExitReason() != null ? currentTrade.getExitReason() : "");
        commentArea.setText(currentTrade.getComment() != null ? currentTrade.getComment() : "");

        // Настраиваем цвет для прибыли/убытка
        if (currentTrade.getProfitLoss() != null) {
            String color = currentTrade.getProfitLoss().compareTo(BigDecimal.ZERO) >= 0 ? "#27ae60" : "#e74c3c";
            profitLossLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        }

        log.info("Отображены детали сделки: {}", currentTrade.getId());
    }

    private String formatPrice(BigDecimal price, com.example.ta.domain.Currency currency) {
        if (price == null) return "N/A";
        return String.format("%s%.2f", currency.getSymbol(), price);
    }

    private String formatVolume(BigDecimal volume) {
        if (volume == null) return "N/A";
        return String.format("%.8f", volume);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "Не указано";
        return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    }

    private void loadChartImage() {
        if (currentTrade == null || !currentTrade.hasChartImage()) {
            chartImageView.setImage(null);
            imageStatusLabel.setText("Изображение не загружено");
            viewFullImageButton.setVisible(false);
            return;
        }

        try {
            File imageFile = new File(currentTrade.getChartImagePath());
            if (imageFile.exists()) {
                Image image = new Image(new FileInputStream(imageFile));
                chartImageView.setImage(image);
                imageStatusLabel.setText("Изображение: " + imageFile.getName());
                viewFullImageButton.setVisible(true); // ПОКАЗЫВАЕМ КНОПКУ
                log.info("Загружено изображение: {}", currentTrade.getChartImagePath());
            } else {
                chartImageView.setImage(null);
                imageStatusLabel.setText("Файл изображения не найден");
                viewFullImageButton.setVisible(false);
                log.warn("Файл изображения не найден: {}", currentTrade.getChartImagePath());
            }
        } catch (Exception e) {
            chartImageView.setImage(null);
            imageStatusLabel.setText("Ошибка загрузки изображения");
            viewFullImageButton.setVisible(false);
            log.error("Ошибка при загрузке изображения", e);
        }
    }

    @FXML
    private void uploadImage() {
        log.info("Загрузка изображения для сделки: {}", currentTrade.getId());

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение графика");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("PNG файлы", "*.png"),
                new FileChooser.ExtensionFilter("JPG файлы", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(uploadImageButton.getScene().getWindow());

        if (selectedFile != null) {
            try {
                createImagesFolder();

                String fileName = "trade_" + currentTrade.getId() + "_" +
                        System.currentTimeMillis() + getFileExtension(selectedFile.getName());
                Path destinationPath = Paths.get(IMAGES_FOLDER, fileName);

                Files.copy(selectedFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

                currentTrade.setChartImagePath(destinationPath.toString());
                tradeService.save(currentTrade);

                loadChartImage();
                showSuccessAlert("Изображение успешно загружено");
                log.info("Изображение сохранено: {}", destinationPath);

            } catch (IOException e) {
                log.error("Ошибка при загрузке изображения", e);
                showErrorAlert("Ошибка при загрузке изображения: " + e.getMessage());
            }
        }
    }

    @FXML
    private void removeImage() {
        if (currentTrade == null || !currentTrade.hasChartImage()) {
            return;
        }

        Alert confirmAlert = createStyledAlert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Подтверждение");
        confirmAlert.setHeaderText("Удаление изображения");
        confirmAlert.setContentText("Вы уверены, что хотите удалить изображение?");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Удаляем файл
                    File imageFile = new File(currentTrade.getChartImagePath());
                    if (imageFile.exists()) {
                        Files.delete(imageFile.toPath());
                        log.info("Файл изображения удален: {}", currentTrade.getChartImagePath());
                    }

                    // Обновляем запись в БД
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

    /**
     * НОВЫЙ МЕТОД - Открывает изображение в полноэкранном режиме
     */
    @FXML
    private void viewFullImage() {
        if (currentTrade == null || !currentTrade.hasChartImage()) {
            showErrorAlert("Изображение не загружено");
            return;
        }

        log.info("Открываем изображение в полноэкранном режиме: {}", currentTrade.getChartImagePath());
        imageViewerService.openImage(currentTrade.getChartImagePath(),
                chartImageView.getScene().getWindow());
    }

    @FXML
    private void editTrade() {
        log.info("Переход к редактированию сделки: {}", currentTrade.getId());
        eventPublisher.publishEvent(new NavigationEvent(NavigationEvent.NavigationType.EDIT_TRADE, currentTrade));
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    @FXML
    private void closeDialog() {
        log.info("Закрытие диалога деталей сделки");
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void createImagesFolder() throws IOException {
        Path imagesPath = Paths.get(IMAGES_FOLDER);
        if (!Files.exists(imagesPath)) {
            Files.createDirectories(imagesPath);
            log.info("Создана папка для изображений: {}", IMAGES_FOLDER);
        }
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex) : "";
    }

    private Alert createStyledAlert(Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.initOwner(uploadImageButton.getScene().getWindow());
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