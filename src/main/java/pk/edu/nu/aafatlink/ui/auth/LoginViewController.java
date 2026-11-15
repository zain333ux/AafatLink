package pk.edu.nu.aafatlink.ui.auth;

import java.util.Objects;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import pk.edu.nu.aafatlink.application.auth.AuthenticationController;
import pk.edu.nu.aafatlink.app.AppConfiguration;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.ui.ViewRouter;

/** Presentation controller for the Phase 1 authentication foundation. */
public final class LoginViewController {
    private final ViewRouter router;
    private final AppConfiguration configuration;
    private final SessionManager sessions;
    private final AuthenticationController authentication;

    @FXML
    private ComboBox<UserRole> demoRoleBox;
    @FXML
    private Label configurationModeLabel;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button signInButton;
    @FXML
    private Label loginMessageLabel;
    @FXML
    private Button registrationButton;
    @FXML
    private Button recoveryButton;

    public LoginViewController(ViewRouter router, AppConfiguration configuration, SessionManager sessions,
            AuthenticationController authentication) {
        this.router = Objects.requireNonNull(router, "router");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.authentication = authentication;
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
        boolean connected = configuration.isRemoteConfigured() && authentication != null;
        emailField.setDisable(!connected);
        passwordField.setDisable(!connected);
        signInButton.setDisable(!connected);
        registrationButton.setDisable(!connected);
        recoveryButton.setDisable(!connected);
        signInButton.setText(connected ? "Sign in securely" : "Sign in requires Supabase configuration");
    }

    @FXML
    private void openDemonstrationWorkspace() {
        sessions.startDemonstrationSession(demoRoleBox.getValue());
        router.showShell();
    }

    @FXML
    private void openRegistration() {
        router.showRegistration();
    }

    @FXML
    private void openPasswordRecovery() {
        router.showPasswordRecovery();
    }

    @FXML
    private void signIn() {
        if (authentication == null) {
            return;
        }
        setBusy(true);
        loginMessageLabel.setText("Signing in securely…");
        loginMessageLabel.getStyleClass().removeAll("form-error", "form-success");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                authentication.signIn(emailField.getText(), passwordField.getText());
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            passwordField.clear();
            router.showShell();
        });
        task.setOnFailed(event -> {
            setBusy(false);
            Throwable failure = task.getException();
            loginMessageLabel.setText(failure == null || failure.getMessage() == null
                    ? "Sign in failed. Please try again." : failure.getMessage());
            loginMessageLabel.getStyleClass().add("form-error");
        });
        Thread worker = new Thread(task, "aafatlink-auth-sign-in");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean busy) {
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        signInButton.setDisable(busy);
        recoveryButton.setDisable(busy);
        demoRoleBox.setDisable(busy);
    }
}
