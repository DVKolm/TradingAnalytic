package com.example.ta.service;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageViewerService {

    /**
     * Открывает изображение в полноэкранном режиме
     */
    public void openImage(String imagePath, Window parentWindow) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            log.warn("Путь к изображению пустой");
            return;
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            log.error("Файл изображения не найден: {}", imagePath);
            showErrorDialog("Файл изображения не найден", parentWindow);
            return;
        }

        try {
            log.info("Открываем изображение: {}", imagePath);
            createImageViewerWindow(imageFile, parentWindow);
        } catch (Exception e) {
            log.error("Ошибка при открытии изображения: {}", imagePath, e);
            showErrorDialog("Ошибка при открытии изображения: " + e.getMessage(), parentWindow);
        }
    }

    /**
     * Создает окно просмотра изображения
     */
    private void createImageViewerWindow(File imageFile, Window parentWindow) throws IOException {
        // Создаем новое окно
        Stage imageStage = new Stage();
        imageStage.setTitle("Просмотр изображения - " + imageFile.getName());
        imageStage.initModality(Modality.APPLICATION_MODAL);
        imageStage.initOwner(parentWindow);

        // Загружаем изображение
        Image image = new Image(new FileInputStream(imageFile));
        ImageView imageView = new ImageView(image);

        // Настраиваем масштабирование
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);

        // Создаем контейнер с прокруткой
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(imageView);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Создаем панель управления
        HBox controlPanel = createControlPanel(imageView, image, imageStage, scrollPane);

        // Создаем корневую панель
        BorderPane root = new BorderPane();
        root.setCenter(scrollPane);
        root.setBottom(controlPanel);

        // Определяем размеры окна
        double windowWidth = Math.min(image.getWidth() + 50, 1200);
        double windowHeight = Math.min(image.getHeight() + 100, 800);

        // Создаем сцену
        Scene scene = new Scene(root, windowWidth, windowHeight);

        // ВАЖНО: Устанавливаем сцену ПЕРЕД настройкой обработчиков событий
        imageStage.setScene(scene);

        // Теперь настраиваем обработчики событий
        setupEventHandlers(imageView, image, imageStage, scrollPane);

        // Центрируем окно
        if (parentWindow != null) {
            imageStage.setX(parentWindow.getX() + (parentWindow.getWidth() - windowWidth) / 2);
            imageStage.setY(parentWindow.getY() + (parentWindow.getHeight() - windowHeight) / 2);
        }

        // Устанавливаем начальный масштаб
        fitImageToWindow(imageView, image, scrollPane);

        imageStage.show();
        log.info("Окно просмотра изображения открыто");
    }

    /**
     * Создает панель управления
     */
    private HBox createControlPanel(ImageView imageView, Image image, Stage stage, ScrollPane scrollPane) {
        HBox controlPanel = new HBox(10);
        controlPanel.setPadding(new Insets(10));
        controlPanel.setAlignment(Pos.CENTER);
        controlPanel.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");

        // Кнопка "По размеру окна"
        Button fitToWindowButton = new Button("По размеру окна");
        fitToWindowButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 600; -fx-background-radius: 6; -fx-cursor: hand;");
        fitToWindowButton.setOnAction(e -> fitImageToWindow(imageView, image, scrollPane));

        // Кнопка "100%"
        Button actualSizeButton = new Button("100%");
        actualSizeButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 600; -fx-background-radius: 6; -fx-cursor: hand;");
        actualSizeButton.setOnAction(e -> setActualSize(imageView, image));

        // Кнопка "Увеличить"
        Button zoomInButton = new Button("+ Увеличить");
        zoomInButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 600; -fx-background-radius: 6; -fx-cursor: hand;");
        zoomInButton.setOnAction(e -> zoomImage(imageView, 1.2));

        // Кнопка "Уменьшить"
        Button zoomOutButton = new Button("- Уменьшить");
        zoomOutButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 600; -fx-background-radius: 6; -fx-cursor: hand;");
        zoomOutButton.setOnAction(e -> zoomImage(imageView, 0.8));

        // Информация о размере
        Label sizeLabel = new Label(String.format("Размер: %.0f × %.0f px",
                image.getWidth(), image.getHeight()));
        sizeLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        // Кнопка "Закрыть"
        Button closeButton = new Button("Закрыть");
        closeButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 600; -fx-background-radius: 6; -fx-cursor: hand;");
        closeButton.setOnAction(e -> stage.close());

        controlPanel.getChildren().addAll(
                fitToWindowButton,
                actualSizeButton,
                zoomInButton,
                zoomOutButton,
                sizeLabel,
                closeButton
        );

        return controlPanel;
    }

    /**
     * Настраивает обработчики событий
     */
    private void setupEventHandlers(ImageView imageView, Image image, Stage stage, ScrollPane scrollPane) {
        // Проверяем что сцена установлена
        Scene scene = stage.getScene();
        if (scene == null) {
            log.error("Сцена не установлена в Stage");
            return;
        }

        // Закрытие по Escape
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
            } else if (e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD) {
                zoomImage(imageView, 1.2);
            } else if (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT) {
                zoomImage(imageView, 0.8);
            } else if (e.getCode() == KeyCode.DIGIT0) {
                setActualSize(imageView, image);
            } else if (e.getCode() == KeyCode.F) {
                fitImageToWindow(imageView, image, scrollPane);
            }
        });

        // Масштабирование колесиком мыши
        scrollPane.setOnScroll(e -> {
            if (e.isControlDown()) {
                e.consume();
                double zoomFactor = e.getDeltaY() > 0 ? 1.1 : 0.9;
                zoomImage(imageView, zoomFactor);
            }
        });

        // Двойной клик для переключения между размерами
        imageView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                if (Math.abs(imageView.getFitWidth() - image.getWidth()) < 1) {
                    fitImageToWindow(imageView, image, scrollPane);
                } else {
                    setActualSize(imageView, image);
                }
            }
        });

        log.debug("Обработчики событий настроены");
    }

    /**
     * Подгоняет изображение по размеру окна
     */
    private void fitImageToWindow(ImageView imageView, Image image, ScrollPane scrollPane) {
        double scrollPaneWidth = scrollPane.getViewportBounds().getWidth();
        double scrollPaneHeight = scrollPane.getViewportBounds().getHeight();

        if (scrollPaneWidth <= 0 || scrollPaneHeight <= 0) {
            // Используем размеры сцены, если ScrollPane еще не инициализирован
            Scene scene = scrollPane.getScene();
            if (scene != null) {
                scrollPaneWidth = scene.getWidth() - 50;
                scrollPaneHeight = scene.getHeight() - 100;
            } else {
                // Fallback к размерам изображения
                scrollPaneWidth = Math.min(image.getWidth(), 1200);
                scrollPaneHeight = Math.min(image.getHeight(), 800);
            }
        }

        double widthRatio = scrollPaneWidth / image.getWidth();
        double heightRatio = scrollPaneHeight / image.getHeight();
        double ratio = Math.min(widthRatio, heightRatio);

        imageView.setFitWidth(image.getWidth() * ratio);
        imageView.setFitHeight(image.getHeight() * ratio);

        log.debug("Изображение подогнано по размеру окна, масштаб: {:.2f}", ratio);
    }

    /**
     * Устанавливает реальный размер изображения
     */
    private void setActualSize(ImageView imageView, Image image) {
        imageView.setFitWidth(image.getWidth());
        imageView.setFitHeight(image.getHeight());
        log.debug("Установлен реальный размер изображения");
    }

    /**
     * Масштабирует изображение
     */
    private void zoomImage(ImageView imageView, double factor) {
        double currentWidth = imageView.getFitWidth();
        double currentHeight = imageView.getFitHeight();

        double newWidth = currentWidth * factor;
        double newHeight = currentHeight * factor;

        // Ограничиваем минимальный и максимальный размер
        double minSize = 50;
        double maxSize = 5000;

        if (newWidth >= minSize && newWidth <= maxSize &&
                newHeight >= minSize && newHeight <= maxSize) {
            imageView.setFitWidth(newWidth);
            imageView.setFitHeight(newHeight);
            log.debug("Изображение масштабировано, новый размер: {:.0f} × {:.0f}", newWidth, newHeight);
        }
    }

    /**
     * Показывает диалог ошибки
     */
    private void showErrorDialog(String message, Window parentWindow) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);

        if (parentWindow != null) {
            alert.initOwner(parentWindow);
        }

        alert.showAndWait();
    }
}