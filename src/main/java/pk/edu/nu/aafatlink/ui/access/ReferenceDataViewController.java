package pk.edu.nu.aafatlink.ui.access;

import java.util.List;
import java.util.Objects;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.access.AccessController;
import pk.edu.nu.aafatlink.domain.account.ControlledReferenceValue;
import pk.edu.nu.aafatlink.domain.account.ReferenceDataChange;
import pk.edu.nu.aafatlink.domain.account.ReferenceDataType;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;

/** JavaFX presentation for the controlled-reference part of UC12. */
public final class ReferenceDataViewController {
    private final SessionManager sessions;
    private final AccessController access;
    private ControlledReferenceValue selected;

    @FXML private ComboBox<ReferenceDataType> typeBox;
    @FXML private TableView<ControlledReferenceValue> valueTable;
    @FXML private TableColumn<ControlledReferenceValue, String> nameColumn;
    @FXML private TableColumn<ControlledReferenceValue, String> stateColumn;
    @FXML private VBox emptyState;
    @FXML private VBox editor;
    @FXML private Label editorTitle;
    @FXML private TextField nameField;
    @FXML private CheckBox activeCheck;
    @FXML private TextArea reasonArea;
    @FXML private Button refreshButton;
    @FXML private Button newButton;
    @FXML private Button saveButton;
    @FXML private Label messageLabel;

    public ReferenceDataViewController(SessionManager sessions, AccessController access) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.access = access;
    }

    @FXML
    private void initialize() {
        valueTable.setPlaceholder(new javafx.scene.layout.Region());
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        stateColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().active() ? "Active" : "Inactive"));
        typeBox.setItems(FXCollections.observableArrayList(ReferenceDataType.values()));
        typeBox.setConverter(new StringConverter<>() {
            @Override public String toString(ReferenceDataType type) { return type == null ? "" : type.displayName(); }
            @Override public ReferenceDataType fromString(String value) { return null; }
        });
        typeBox.valueProperty().addListener((observable, previous, selectedType) -> loadValues());
        valueTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, value) -> edit(value));
        typeBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void loadValues() {
        ReferenceDataType type = typeBox.getValue();
        if (type == null) return;
        newButton.setDisable(!type.allowsCreation() || demonstration());
        if (demonstration() || access == null) {
            setBusy(false);
            valueTable.getItems().clear();
            showEmpty(true);
            edit(null);
            return;
        }
        setBusy(true);
        Task<List<ControlledReferenceValue>> task = new Task<>() {
            @Override protected List<ControlledReferenceValue> call() {
                return access.listReferenceValues(session(), type);
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            valueTable.setItems(FXCollections.observableArrayList(task.getValue()));
            showEmpty(task.getValue().isEmpty());
            if (!task.getValue().isEmpty()) valueTable.getSelectionModel().selectFirst();
            else edit(null);
        });
        task.setOnFailed(event -> {
            setBusy(false); showEmpty(true); edit(null);
            showMessage(message(task.getException(), "Reference data could not be loaded."), true);
        });
        run(task, "aafatlink-load-reference-data");
    }

    @FXML
    private void createValue() {
        ReferenceDataType type = typeBox.getValue();
        if (type == null || !type.allowsCreation()) return;
        valueTable.getSelectionModel().clearSelection();
        selected = null;
        editor.setVisible(true); editor.setManaged(true);
        editorTitle.setText("Add " + type.displayName().toLowerCase());
        nameField.clear(); activeCheck.setSelected(true); reasonArea.clear();
        saveButton.setText("Add and audit value");
        saveButton.setDisable(demonstration());
        showMessage("New values become available after this audited save.", false);
    }

    private void edit(ControlledReferenceValue value) {
        selected = value;
        boolean available = value != null;
        editor.setVisible(available); editor.setManaged(available);
        saveButton.setDisable(!available || demonstration());
        if (!available) return;
        editorTitle.setText("Edit " + value.name());
        nameField.setText(value.name()); activeCheck.setSelected(value.active()); reasonArea.clear();
        saveButton.setText("Save and audit change");
        showMessage("Deactivate values instead of deleting them so historical records remain valid.", false);
    }

    @FXML
    private void saveValue() {
        ReferenceDataType type = typeBox.getValue();
        ReferenceDataChange change;
        try {
            change = new ReferenceDataChange(type, selected == null ? null : selected.id(),
                    nameField.getText(), activeCheck.isSelected(), selected == null ? 0 : selected.version(),
                    reasonArea.getText());
        } catch (RuntimeException exception) {
            showMessage(exception.getMessage(), true); return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Save this controlled reference value? The change is recorded in the audit trail.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setHeaderText(selected == null ? "Confirm new reference value" : "Confirm reference-data change");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        setBusy(true);
        Task<ControlledReferenceValue> task = new Task<>() {
            @Override protected ControlledReferenceValue call() { return access.saveReferenceValue(session(), change); }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            ControlledReferenceValue saved = task.getValue();
            int index = selected == null ? -1 : valueTable.getItems().indexOf(selected);
            if (index < 0) valueTable.getItems().add(saved); else valueTable.getItems().set(index, saved);
            valueTable.getSelectionModel().select(saved);
            showEmpty(false); showMessage("Reference value saved and audited.", false);
        });
        task.setOnFailed(event -> {
            setBusy(false); showMessage(message(task.getException(), "The reference value was not changed."), true);
            loadValues();
        });
        run(task, "aafatlink-save-reference-data");
    }

    private boolean demonstration() { return sessions.currentUser().orElseThrow().demonstrationAccount(); }
    private AuthSession session() { return sessions.authSession().orElseThrow(); }
    private void showEmpty(boolean empty) { emptyState.setVisible(empty); emptyState.setManaged(empty); }
    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy); typeBox.setDisable(busy);
        newButton.setDisable(busy || demonstration() || typeBox.getValue() == null || !typeBox.getValue().allowsCreation());
        saveButton.setDisable(busy || demonstration() || (!editor.isVisible()));
    }
    private void showMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("form-message-error", "form-message-success");
        messageLabel.getStyleClass().add(error ? "form-message-error" : "form-message-success");
    }
    private static String message(Throwable error, String fallback) {
        return error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? fallback : error.getMessage();
    }
    private static void run(Task<?> task, String name) {
        Thread thread = new Thread(task, name); thread.setDaemon(true); thread.start();
    }
}
