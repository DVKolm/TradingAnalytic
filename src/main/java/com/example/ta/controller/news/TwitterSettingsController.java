package com.example.ta.controller.news;

import com.example.ta.domain.news.TwitterSettings;
import com.example.ta.domain.news.TwitterUser;
import com.example.ta.service.TwitterNewsService;
import com.example.ta.service.TwitterSettingsService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TwitterSettingsController {

    private final TwitterSettingsService twitterSettingsService;
    private final TwitterNewsService twitterNewsService;

    @FXML private TextField bearerTokenField;
    @FXML private TextField apiKeyField;
    @FXML private TextField apiSecretField;
    @FXML private TextField accessTokenField;
    @FXML private TextField accessTokenSecretField;
    @FXML private CheckBox enabledCheckBox;
    @FXML private Spinner<Integer> pollIntervalSpinner;
    @FXML private Button testConnectionButton;
    @FXML private Button saveButton;
    @FXML private Label statusLabel;
    
    // Управление пользователями
    @FXML private TextField usernameField;
    @FXML private Button addUserButton;
    @FXML private TableView<TwitterUser> usersTable;
    @FXML private TableColumn<TwitterUser, String> usernameColumn;
    @FXML private TableColumn<TwitterUser, String> displayNameColumn;
    @FXML private TableColumn<TwitterUser, Integer> followersColumn;
    @FXML private TableColumn<TwitterUser, Boolean> activeColumn;
    @FXML private TableColumn<TwitterUser, Void> actionsColumn;
    
    // Статистика
    @FXML private Label activeUsersLabel;
    @FXML private Label totalTweetsLabel;
    @FXML private Label todayTweetsLabel;
    
    private ObservableList<TwitterUser> usersList = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        setupComponents();
        setupTable();
        loadSettings();
        loadUsers();
        loadStatistics();
        updateStatus();
    }

    private void setupComponents() {
        // Настройка спиннера интервала
        pollIntervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 5));
        
        // Обработчики событий
        testConnectionButton.setOnAction(e -> testConnection());
        saveButton.setOnAction(e -> saveSettings());
        addUserButton.setOnAction(e -> addUser());
        
        // Валидация полей
        bearerTokenField.textProperty().addListener((obs, oldVal, newVal) -> validateForm());
        enabledCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> validateForm());
    }

    private void setupTable() {
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        displayNameColumn.setCellValueFactory(new PropertyValueFactory<>("displayName"));
        followersColumn.setCellValueFactory(new PropertyValueFactory<>("followersCount"));
        
        // Колонка активности с чекбоксом
        activeColumn.setCellFactory(column -> new TableCell<TwitterUser, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    TwitterUser user = getTableRow().getItem();
                    checkBox.setSelected(user.getIsActive());
                    checkBox.setOnAction(e -> {
                        twitterNewsService.toggleUserStatus(user.getId());
                        loadUsers();
                    });
                    setGraphic(checkBox);
                }
            }
        });
        
        // Колонка действий
        actionsColumn.setCellFactory(column -> new TableCell<TwitterUser, Void>() {
            private final Button refreshButton = new Button("🔄");
            private final Button deleteButton = new Button("🗑️");
            
            {
                refreshButton.setOnAction(e -> {
                    TwitterUser user = getTableRow().getItem();
                    if (user != null) {
                        try {
                            twitterNewsService.refreshUserInfo(user.getId());
                            loadUsers();
                            showSuccess("Информация о пользователе обновлена");
                        } catch (Exception ex) {
                            showError("Ошибка обновления: " + ex.getMessage());
                        }
                    }
                });
                
                deleteButton.setOnAction(e -> {
                    TwitterUser user = getTableRow().getItem();
                    if (user != null && confirmDelete(user.getUsername())) {
                        try {
                            twitterNewsService.removeUser(user.getId());
                            loadUsers();
                            showSuccess("Пользователь удален");
                        } catch (Exception ex) {
                            showError("Ошибка удаления: " + ex.getMessage());
                        }
                    }
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    var hbox = new javafx.scene.layout.HBox(5);
                    hbox.getChildren().addAll(refreshButton, deleteButton);
                    setGraphic(hbox);
                }
            }
        });
        
        usersTable.setItems(usersList);
    }

    private void loadSettings() {
        Optional<TwitterSettings> settingsOpt = twitterSettingsService.getCurrentSettings();
        if (settingsOpt.isPresent()) {
            TwitterSettings settings = settingsOpt.get();
            
            Platform.runLater(() -> {
                bearerTokenField.setText(settings.getBearerToken() != null ? settings.getBearerToken() : "");
                apiKeyField.setText(settings.getApiKey() != null ? settings.getApiKey() : "");
                apiSecretField.setText(settings.getApiSecret() != null ? settings.getApiSecret() : "");
                accessTokenField.setText(settings.getAccessToken() != null ? settings.getAccessToken() : "");
                accessTokenSecretField.setText(settings.getAccessTokenSecret() != null ? settings.getAccessTokenSecret() : "");
                enabledCheckBox.setSelected(settings.getIsEnabled());
                pollIntervalSpinner.getValueFactory().setValue(settings.getPollIntervalMinutes());
            });
        }
    }

    private void loadUsers() {
        try {
            var users = twitterNewsService.getActiveUsers();
            Platform.runLater(() -> {
                usersList.clear();
                usersList.addAll(users);
            });
        } catch (Exception e) {
            log.error("Ошибка загрузки пользователей", e);
        }
    }

    private void loadStatistics() {
        try {
            Map<String, Object> stats = twitterNewsService.getTwitterStatistics();
            Platform.runLater(() -> {
                activeUsersLabel.setText(String.valueOf(stats.get("activeUsers")));
                totalTweetsLabel.setText(String.valueOf(stats.get("totalTweets")));
                todayTweetsLabel.setText(String.valueOf(stats.get("todayTweets")));
            });
        } catch (Exception e) {
            log.error("Ошибка загрузки статистики", e);
        }
    }

    @FXML
    public void testConnection() {
        TwitterSettings settings = createSettingsFromForm();
        
        testConnectionButton.setDisable(true);
        testConnectionButton.setText("Тестирование...");
        
        // Выполняем в отдельном потоке
        new Thread(() -> {
            try {
                boolean success = twitterSettingsService.testConnection(settings);
                
                Platform.runLater(() -> {
                    if (success) {
                        showSuccess("✅ Соединение с Twitter API успешно!");
                    } else {
                        showError("❌ Ошибка соединения с Twitter API");
                    }
                    
                    testConnectionButton.setDisable(false);
                    testConnectionButton.setText("Тест соединения");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("❌ Ошибка: " + e.getMessage());
                    testConnectionButton.setDisable(false);
                    testConnectionButton.setText("Тест соединения");
                });
            }
        }).start();
    }

    @FXML
    public void saveSettings() {
        try {
            TwitterSettings settings = createSettingsFromForm();
            twitterSettingsService.saveSettings(settings);
            
            showSuccess("✅ Настройки Twitter сохранены!");
            updateStatus();
            
        } catch (Exception e) {
            showError("❌ Ошибка сохранения: " + e.getMessage());
        }
    }

    @FXML
    public void addUser() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            showError("Введите имя пользователя");
            return;
        }
        
        // Убираем @ если есть
        if (username.startsWith("@")) {
            username = username.substring(1);
        }
        
        addUserButton.setDisable(true);
        addUserButton.setText("Добавление...");
        
        String finalUsername = username;
        new Thread(() -> {
            try {
                twitterNewsService.addTwitterUser(finalUsername);
                
                Platform.runLater(() -> {
                    usernameField.clear();
                    loadUsers();
                    loadStatistics();
                    showSuccess("Пользователь @" + finalUsername + " добавлен!");
                    
                    addUserButton.setDisable(false);
                    addUserButton.setText("Добавить");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Ошибка добавления: " + e.getMessage());
                    addUserButton.setDisable(false);
                    addUserButton.setText("Добавить");
                });
            }
        }).start();
    }

    private TwitterSettings createSettingsFromForm() {
        TwitterSettings settings = new TwitterSettings();
        settings.setBearerToken(bearerTokenField.getText().trim());
        settings.setApiKey(apiKeyField.getText().trim());
        settings.setApiSecret(apiSecretField.getText().trim());
        settings.setAccessToken(accessTokenField.getText().trim());
        settings.setAccessTokenSecret(accessTokenSecretField.getText().trim());
        settings.setIsEnabled(enabledCheckBox.isSelected());
        settings.setPollIntervalMinutes(pollIntervalSpinner.getValue());
        return settings;
    }

    private void validateForm() {
        boolean hasRequiredFields = !bearerTokenField.getText().trim().isEmpty();
        boolean enabled = enabledCheckBox.isSelected();
        
        testConnectionButton.setDisable(!hasRequiredFields);
        saveButton.setDisable(false); // Сохранение всегда доступно
        
        if (enabled && !hasRequiredFields) {
            statusLabel.setText("⚠️ Заполните Bearer Token");
            statusLabel.setStyle("-fx-text-fill: orange;");
        }
    }

    private void updateStatus() {
        String status = twitterSettingsService.getTwitterStatus();
        Platform.runLater(() -> {
            statusLabel.setText("Статус: " + status);
            statusLabel.setStyle(getStatusStyle(status));
        });
    }

    private String getStatusStyle(String status) {
        return switch (status) {
            case "Активно" -> "-fx-text-fill: green;";
            case "Отключено" -> "-fx-text-fill: gray;";
            case "Не настроено", "Неполные настройки" -> "-fx-text-fill: orange;";
            default -> "-fx-text-fill: black;";
        };
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Успех");
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

    private boolean confirmDelete(String username) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Подтверждение");
        alert.setHeaderText("Удалить пользователя?");
        alert.setContentText("Вы уверены, что хотите удалить пользователя @" + username + "?");
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}