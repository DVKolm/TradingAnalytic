
package com.example.ta.controller.news;

import com.example.ta.domain.news.TelegramChannel;
import com.example.ta.domain.news.TelegramSettings;
import com.example.ta.service.TelegramNewsService;
import com.example.ta.service.TelegramSettingsService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramSettingsController implements Initializable {

    private final TelegramSettingsService telegramSettingsService;
    private final TelegramNewsService telegramNewsService;

    // Существующие поля для уведомлений
    @FXML private TextField botTokenField;
    @FXML private TextField chatIdField;
    @FXML private CheckBox enabledCheckBox;
    @FXML private CheckBox sendOnOpenCheckBox;
    @FXML private CheckBox sendOnCloseCheckBox;
    @FXML private CheckBox sendOnUpdateCheckBox;
    @FXML private CheckBox includeImageCheckBox;
    @FXML private TextArea messageTemplateArea;
    @FXML private Button saveButton;
    @FXML private Button testButton;
    @FXML private Label statusLabel;

    // Новые поля для управления каналами
    @FXML private TableView<TelegramChannel> channelsTable;
    @FXML private TableColumn<TelegramChannel, String> usernameColumn;
    @FXML private TableColumn<TelegramChannel, String> titleColumn;
    @FXML private TableColumn<TelegramChannel, Boolean> activeColumn;
    @FXML private TableColumn<TelegramChannel, String> createdColumn;

    @FXML private TextField newChannelUsernameField;
    @FXML private TextField newChannelTitleField;
    @FXML private TextArea newChannelDescriptionArea;
    @FXML private Button addChannelButton;
    @FXML private Button removeChannelButton;
    @FXML private Button toggleChannelButton;

    private ObservableList<TelegramChannel> channelsData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.info("Инициализация TelegramSettingsController");

        setupForm();
        setupChannelsTable();
        loadCurrentSettings();
        loadChannels();
        setupEventHandlers();

        log.info("TelegramSettingsController инициализирован");
    }

    private void setupForm() {
        // Подсказки для полей уведомлений
        botTokenField.setPromptText("Например: 123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PA");
        chatIdField.setPromptText("Например: -1001237890 или 98321");

        // Подсказки для каналов
        newChannelUsernameField.setPromptText("Например: crypto_news");
        newChannelTitleField.setPromptText("Например: Крипто новости");
        newChannelDescriptionArea.setPromptText("Описание канала (необязательно)");

        // Подсказка для шаблона
        messageTemplateArea.setPromptText("""
                Доступные плейсхолдеры:
                {asset} - название актива
                {type} - тип сделки (LONG/SHORT)
                {entryPrice} - цена входа
                {exitPrice} - цена выхода
                {volume} - объем
                {profitLoss} - прибыль/убыток
                {status} - статус сделки
                {action} - действие (OPEN/CLOSE/UPDATE)
                
                Пример:
                🚀 {action} {type} по {asset}
                💰 Цена: {entryPrice}
                📊 Объем: {volume}
                """);

        // Устанавливаем начальные значения по умолчанию
        sendOnOpenCheckBox.setSelected(true);
        sendOnCloseCheckBox.setSelected(true);
        sendOnUpdateCheckBox.setSelected(false);
        includeImageCheckBox.setSelected(true);
    }

    private void setupChannelsTable() {
        // Настройка колонок таблицы
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        activeColumn.setCellValueFactory(new PropertyValueFactory<>("isActive"));
        createdColumn.setCellValueFactory(cellData -> {
            if (cellData.getValue().getCreatedAt() != null) {
                String formatted = cellData.getValue().getCreatedAt()
                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                return new javafx.beans.property.SimpleStringProperty(formatted);
            }
            return new javafx.beans.property.SimpleStringProperty("");
        });

        // Настройка отображения активности
        activeColumn.setCellFactory(column -> new TableCell<TelegramChannel, Boolean>() {
            @Override
            protected void updateItem(Boolean active, boolean empty) {
                super.updateItem(active, empty);
                if (empty || active == null) {
                    setText("");
                } else {
                    setText(active ? "✅ Активен" : "❌ Отключен");
                    setStyle(active ? "-fx-text-fill: #27ae60;" : "-fx-text-fill: #e74c3c;");
                }
            }
        });

        // Привязываем данные к таблице
        channelsTable.setItems(channelsData);

        // Обработчик выбора строки
        channelsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean hasSelection = newSelection != null;
            removeChannelButton.setDisable(!hasSelection);
            toggleChannelButton.setDisable(!hasSelection);

            if (hasSelection) {
                toggleChannelButton.setText(newSelection.getIsActive() ? "Отключить" : "Включить");
            }
        });
    }

    private void setupEventHandlers() {
        botTokenField.textProperty().addListener((obs, old, newValue) -> {
            if (newValue == null || newValue.trim().isEmpty()) {
                enabledCheckBox.setSelected(false);
            }
        });

        chatIdField.textProperty().addListener((obs, old, newValue) -> {
            if (newValue == null || newValue.trim().isEmpty()) {
                enabledCheckBox.setSelected(false);
            }
        });

        // Предупреждение при включении без заполненных полей
        enabledCheckBox.selectedProperty().addListener((obs, old, newValue) -> {
            if (newValue && !validateRequiredFields()) {
                Platform.runLater(() -> {
                    enabledCheckBox.setSelected(false);
                    showWarning("Заполните токен бота и ID чата перед включением уведомлений");
                });
            }
        });

        // Валидация поля username канала
        newChannelUsernameField.textProperty().addListener((obs, old, newValue) -> {
            boolean valid = newValue != null && !newValue.trim().isEmpty();
            addChannelButton.setDisable(!valid);
        });
    }

    @FXML
    private void handleAddChannel() {
        String username = newChannelUsernameField.getText().trim();
        String title = newChannelTitleField.getText().trim();
        String description = newChannelDescriptionArea.getText().trim();

        if (username.isEmpty()) {
            showError("Введите username канала");
            return;
        }

        // Убираем @ если пользователь его добавил
        if (username.startsWith("@")) {
            username = username.substring(1);
        }

        try {
            telegramNewsService.addChannel(username, title.isEmpty() ? username : title, description);

            // Очищаем поля
            newChannelUsernameField.clear();
            newChannelTitleField.clear();
            newChannelDescriptionArea.clear();

            // Обновляем таблицу
            loadChannels();

            setStatus("✅ Канал добавлен: @" + username, "#27ae60");
            showInfo("Канал @" + username + " успешно добавлен для отслеживания новостей!");

        } catch (IllegalArgumentException e) {
            showError("Канал с таким username уже существует");
        } catch (Exception e) {
            log.error("Ошибка при добавлении канала", e);
            showError("Ошибка при добавлении канала: " + e.getMessage());
        }
    }

    @FXML
    private void handleRemoveChannel() {
        TelegramChannel selected = channelsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Выберите канал для удаления");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Подтверждение");
        confirmAlert.setHeaderText("Удаление канала");
        confirmAlert.setContentText("Вы действительно хотите удалить канал @" + selected.getUsername() + "?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                telegramNewsService.removeChannel(selected.getUsername());
                loadChannels();
                setStatus("✅ Канал удален", "#27ae60");
            } catch (Exception e) {
                log.error("Ошибка при удалении канала", e);
                showError("Ошибка при удалении канала: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleToggleChannel() {
        TelegramChannel selected = channelsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Выберите канал");
            return;
        }

        try {
            if (selected.getIsActive()) {
                telegramNewsService.removeChannel(selected.getUsername());
                setStatus("⏸️ Канал отключен", "#f39c12");
            } else {
                // Для включения канала добавляем его заново
                telegramNewsService.addChannel(selected.getUsername(), selected.getTitle(), selected.getDescription());
                setStatus("▶️ Канал включен", "#27ae60");
            }
            loadChannels();
        } catch (Exception e) {
            log.error("Ошибка при переключении канала", e);
            showError("Ошибка при переключении канала: " + e.getMessage());
        }
    }

    private void loadChannels() {
        try {
            var channels = telegramNewsService.getActiveChannels();
            channelsData.clear();
            channelsData.addAll(channels);
        } catch (Exception e) {
            log.error("Ошибка при загрузке каналов", e);
            showError("Ошибка при загрузке списка каналов: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        try {
            if (enabledCheckBox.isSelected() && !validateRequiredFields()) {
                showError("Заполните все обязательные поля перед включением уведомлений");
                return;
            }

            // Создаем объект настроек
            TelegramSettings settings = TelegramSettings.builder()
                    .botToken(botTokenField.getText().trim())
                    .chatId(chatIdField.getText().trim())
                    .enabled(enabledCheckBox.isSelected())
                    .sendOnTradeOpen(sendOnOpenCheckBox.isSelected())
                    .sendOnTradeClose(sendOnCloseCheckBox.isSelected())
                    .sendOnTradeUpdate(sendOnUpdateCheckBox.isSelected())
                    .includeChartImage(includeImageCheckBox.isSelected())
                    .messageTemplate(messageTemplateArea.getText().trim())
                    .build();

            // Сохраняем настройки
            telegramSettingsService.saveSettings(settings);

            // Показываем успех
            setStatus("✅ Настройки сохранены!", "#27ae60");

            // Показываем информационное сообщение
            if (settings.getEnabled()) {
                showInfo("Telegram уведомления включены! Теперь вы будете получать сообщения о ваших сделках.");
            } else {
                showInfo("Настройки сохранены. Уведомления отключены.");
            }

            log.info("Настройки Telegram сохранены (включено: {})", settings.getEnabled());

        } catch (Exception e) {
            log.error("Ошибка при сохранении настроек Telegram", e);
            setStatus("❌ Ошибка сохранения: " + e.getMessage(), "#e74c3c");
            showError("Ошибка при сохранении настроек: " + e.getMessage());
        }
    }

    @FXML
    private void handleTest() {
        if (!validateRequiredFields()) {
            showError("Заполните токен бота и ID чата для тестирования");
            return;
        }

        // Блокируем кнопку во время тестирования
        testButton.setDisable(true);
        setStatus("🔍 Тестирование соединения...", "#f39c12");

        // Создаем задачу для тестирования в фоновом потоке
        Task<Boolean> testTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                TelegramSettings testSettings = TelegramSettings.builder()
                        .botToken(botTokenField.getText().trim())
                        .chatId(chatIdField.getText().trim())
                        .enabled(true)
                        .build();

                return telegramSettingsService.testConnection(testSettings);
            }
        };

        testTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                testButton.setDisable(false);
                boolean success = testTask.getValue();

                if (success) {
                    setStatus("✅ Соединение успешно!", "#27ae60");
                    showInfo("Тестовое сообщение отправлено! Проверьте ваш Telegram.");
                } else {
                    setStatus("❌ Ошибка соединения!", "#e74c3c");
                    showError("Не удалось отправить тестовое сообщение. Проверьте настройки:\n" +
                            "• Корректность токена бота\n" +
                            "• Правильность ID чата\n" +
                            "• Добавлен ли бот в группу/чат");
                }
            });
        });

        testTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                testButton.setDisable(false);
                Throwable exception = testTask.getException();
                log.error("Ошибка при тестировании Telegram", exception);
                setStatus("❌ Ошибка тестирования!", "#e74c3c");
                showError("Ошибка тестирования: " + exception.getMessage());
            });
        });

        // Запускаем тестирование в отдельном потоке
        Thread testThread = new Thread(testTask);
        testThread.setDaemon(true);
        testThread.start();
    }

    @FXML
    private void loadCurrentSettings() {
        telegramSettingsService.getCurrentSettings().ifPresentOrElse(
                this::populateForm,
                () -> {
                    log.info("Настройки Telegram не найдены, используем значения по умолчанию");
                    populateForm(telegramSettingsService.getDefaultSettings());
                }
        );
    }

    private void populateForm(TelegramSettings settings) {
        botTokenField.setText(settings.getBotToken() != null ? settings.getBotToken() : "");
        chatIdField.setText(settings.getChatId() != null ? settings.getChatId() : "");
        enabledCheckBox.setSelected(settings.getEnabled() != null ? settings.getEnabled() : false);
        sendOnOpenCheckBox.setSelected(settings.getSendOnTradeOpen() != null ? settings.getSendOnTradeOpen() : true);
        sendOnCloseCheckBox.setSelected(settings.getSendOnTradeClose() != null ? settings.getSendOnTradeClose() : true);
        sendOnUpdateCheckBox.setSelected(settings.getSendOnTradeUpdate() != null ? settings.getSendOnTradeUpdate() : false);
        includeImageCheckBox.setSelected(settings.getIncludeChartImage() != null ? settings.getIncludeChartImage() : true);
        messageTemplateArea.setText(settings.getMessageTemplate() != null ? settings.getMessageTemplate() : "");

        log.debug("Форма заполнена настройками: включено={}", settings.getEnabled());
    }

    private boolean validateRequiredFields() {
        String token = botTokenField.getText();
        String chatId = chatIdField.getText();

        return token != null && !token.trim().isEmpty()
                && chatId != null && !chatId.trim().isEmpty();
    }

    private void setStatus(String message, String color) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: 500;");
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Информация");
        alert.setHeaderText("Telegram настройки");
        alert.setContentText(message);

        // Улучшаем внешний вид диалога
        alert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px;");

        alert.showAndWait();
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Предупреждение");
        alert.setHeaderText("Telegram настройки");
        alert.setContentText(message);

        alert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px;");

        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText("Ошибка настройки Telegram");
        alert.setContentText(message);

        alert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 13px;");

        alert.showAndWait();
    }

    /**
     * Публичный метод для обновления статуса из других контроллеров
     */
    public void refreshStatus() {
        loadCurrentSettings();
        loadChannels();

        String status = telegramSettingsService.getTelegramStatus();
        String color = switch (status) {
            case "Активно" -> "#27ae60";
            case "Отключено" -> "#f39c12";
            default -> "#e74c3c";
        };

        setStatus("📱 Статус: " + status, color);
    }
}