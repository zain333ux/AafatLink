package pk.edu.nu.aafatlink.ui.auth;

import java.util.Objects;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.util.StringConverter;
import pk.edu.nu.aafatlink.app.AppConfiguration;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.ui.ViewRouter;

/** Presentation controller for the Phase 1 authentication foundation. */
public final class LoginViewController {
    private final ViewRouter router;
    private final AppConfiguration configuration;
    private final SessionManager sessions;

    @FXML
    private ComboBox<UserRole> demoRoleBox;
    @FXML
    private Label configurationModeLabel;

    public LoginViewController(ViewRouter router, AppConfiguration configuration, SessionManager sessions) {
        this.router = Objects.requireNonNull(router, "router");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @FXML
    private void initialize() {
        demoRoleBox.setItems(FXCollections.observableArrayList(UserRole.values()));
        demoRoleBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(UserRole role) {
                return role == null ? "" : role.displayName();
            }

            @Override
            public UserRole fromString(String value) {
                return null;
            }
        });
        demoRoleBox.getSelectionModel().select(UserRole.REQUESTER);
        configurationModeLabel.setText(configuration.modeLabel());
    }

    @FXML
    private void openDemonstrationWorkspace() {
        sessions.startDemonstrationSession(demoRoleBox.getValue());
        router.showShell();
    }
}
