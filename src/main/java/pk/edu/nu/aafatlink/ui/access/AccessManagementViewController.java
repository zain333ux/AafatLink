package pk.edu.nu.aafatlink.ui.access;

import pk.edu.nu.aafatlink.ui.shell.DashboardSelection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.access.AccessController;
import pk.edu.nu.aafatlink.application.access.AccessValidation;
import pk.edu.nu.aafatlink.domain.account.AccessChanges;
import pk.edu.nu.aafatlink.domain.account.AccountStatus;
import pk.edu.nu.aafatlink.domain.account.Role;
import pk.edu.nu.aafatlink.domain.account.UserAccount;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerApprovalStatus;

/** JavaFX presentation controller for UC12. */
public final class AccessManagementViewController implements DashboardSelection.Destination {
    private final DashboardSelection selection = new DashboardSelection();

    @Override
    public DashboardSelection initialSelection() { return selection; }

    private final SessionManager sessions;
    private final AccessController access;
    private final Map<UserRole, Role> roleConfigurations = new EnumMap<>(UserRole.class);
    private UserAccount selected;

    @FXML private TableView<UserAccount> userTable;
    @FXML private TableColumn<UserAccount, String> nameColumn;
    @FXML private TableColumn<UserAccount, String> roleColumn;
    @FXML private TableColumn<UserAccount, String> statusColumn;
    @FXML private TableColumn<UserAccount, String> approvalColumn;
    @FXML private VBox emptyState;
    @FXML private VBox detailPlaceholder;
    @FXML private VBox detailContent;
    @FXML private Label nameLabel;
    @FXML private Label emailLabel;
    @FXML private ComboBox<UserRole> roleBox;
    @FXML private ComboBox<AccountStatus> statusBox;
    @FXML private VBox approvalBox;
    @FXML private ComboBox<VolunteerApprovalStatus> approvalStatusBox;
    @FXML private ListView<String> permissionList;
    @FXML private TextArea reasonArea;
    @FXML private Button refreshButton;
    @FXML private Button saveButton;
    @FXML private Label messageLabel;

    public AccessManagementViewController(SessionManager sessions, AccessController access) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.access = access;
    }

    @FXML
    private void initialize() {
        userTable.setPlaceholder(new javafx.scene.layout.Region());
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayName()));
        roleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().role().displayName()));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(displayStatus(data.getValue().status())));
        approvalColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().volunteerApprovalStatus().map(Enum::name).orElse("—")));
        roleBox.setItems(FXCollections.observableArrayList(UserRole.values()));
        statusBox.setItems(FXCollections.observableArrayList(AccountStatus.ACTIVE, AccountStatus.DISABLED));
        approvalStatusBox.setItems(FXCollections.observableArrayList(VolunteerApprovalStatus.values()));
        roleBox.valueProperty().addListener((observable, oldValue, role) -> {
            showApproval(role == UserRole.VOLUNTEER);
            showPermissions(role);
        });
        userTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, user) -> showUser(user));
        loadUsers();
    }

    @FXML
    private void loadUsers() {
        if (sessions.currentUser().orElseThrow().demonstrationAccount() || access == null) {
            setBusy(true); showEmpty(true); return;
        }
        setBusy(true);
        Task<AccessAdministrationData> task = new Task<>() {
            @Override protected AccessAdministrationData call() {
                return new AccessAdministrationData(access.listUsers(session()), access.listRoles(session()));
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            AccessAdministrationData data = task.getValue();
            roleConfigurations.clear();
            data.roles().forEach(role -> roleConfigurations.put(role.roleId(), role));
            userTable.setItems(FXCollections.observableArrayList(data.users()));
            showEmpty(data.users().isEmpty());
            if (!data.users().isEmpty()) selection.select(userTable, UserAccount::id);
        });
        task.setOnFailed(event -> {
            setBusy(false); showEmpty(true); showMessage(message(task.getException(), "Users could not be loaded."), true);
        });
        run(task, "aafatlink-load-users");
    }

    private void showUser(UserAccount user) {
        selected = user;
        boolean available = user != null;
        saveButton.setDisable(!available || sessions.currentUser().orElseThrow().demonstrationAccount());
        detailPlaceholder.setVisible(!available); detailPlaceholder.setManaged(!available);
        detailContent.setVisible(available); detailContent.setManaged(available);
        if (!available) return;
        nameLabel.setText(user.displayName()); emailLabel.setText(user.email()); roleBox.setValue(user.role());
        statusBox.setValue(user.status() == AccountStatus.ACTIVE ? AccountStatus.ACTIVE : AccountStatus.DISABLED);
        approvalStatusBox.setValue(user.volunteerApprovalStatus().orElse(VolunteerApprovalStatus.PENDING));
        showApproval(user.role() == UserRole.VOLUNTEER);
        roleConfigurations.putIfAbsent(user.role(), user.accessRole());
        showPermissions(user.role());
        reasonArea.clear(); showMessage("Changes take effect at the user's next login.", false);
    }

    @FXML
    private void saveAccess() {
        if (selected == null) return;
        AccessChanges changes;
        try {
            changes = new AccessChanges(roleBox.getValue(), statusBox.getValue(),
                    roleBox.getValue() == UserRole.VOLUNTEER ? approvalStatusBox.getValue() : null,
                    reasonArea.getText());
        } catch (RuntimeException exception) {
            showMessage(exception.getMessage(), true); return;
        }
        AccessValidation validation = access.validateAccessChange(session(), selected, changes);
        if (!validation.allowed()) { showMessage(validation.reason(), true); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Apply the selected role, account status and volunteer approval? The change is audited and takes effect at next login.",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.setHeaderText("Confirm access change for " + selected.displayName());
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        setBusy(true);
        Task<UserAccount> task = new Task<>() {
            @Override protected UserAccount call() { return access.updateAccess(session(), selected, changes); }
        };
        task.setOnSucceeded(event -> {
            setBusy(false); UserAccount updated = task.getValue();
            int index = userTable.getItems().indexOf(selected); userTable.getItems().set(index, updated);
            userTable.getSelectionModel().select(updated); showMessage("Access configuration saved and audited.", false);
        });
        task.setOnFailed(event -> {
            setBusy(false); showMessage(message(task.getException(), "The existing access configuration was unchanged."), true);
            loadUsers();
        });
        run(task, "aafatlink-update-access");
    }

    private void showApproval(boolean visible) { approvalBox.setVisible(visible); approvalBox.setManaged(visible); }
    private void showPermissions(UserRole roleId) {
        Role role = roleConfigurations.get(roleId);
        List<String> labels = role == null ? List.of() : role.permissions().stream()
                .sorted(java.util.Comparator.comparing(permission -> permission.permissionId()))
                .map(permission -> permission.permissionId() + " — " + permission.permissionName())
                .toList();
        permissionList.setItems(FXCollections.observableArrayList(labels));
    }
    private void showEmpty(boolean empty) { emptyState.setVisible(empty); emptyState.setManaged(empty); }
    private void setBusy(boolean busy) { refreshButton.setDisable(busy); saveButton.setDisable(busy || selected == null); }
    private AuthSession session() { return sessions.authSession().orElseThrow(); }
    private void showMessage(String text, boolean error) {
        messageLabel.setText(text); messageLabel.getStyleClass().removeAll("form-message-error", "form-message-success");
        messageLabel.getStyleClass().add(error ? "form-message-error" : "form-message-success");
    }
    private static String displayStatus(AccountStatus status) { return status == AccountStatus.ACTIVE ? "Active" : "Deactivated"; }
    private static String message(Throwable error, String fallback) {
        return error == null || error.getMessage() == null || error.getMessage().isBlank() ? fallback : error.getMessage();
    }
    private static void run(Task<?> task, String name) { Thread thread = new Thread(task, name); thread.setDaemon(true); thread.start(); }

    private record AccessAdministrationData(List<UserAccount> users, List<Role> roles) { }
}
