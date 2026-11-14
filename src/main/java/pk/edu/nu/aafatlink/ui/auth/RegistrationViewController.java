package pk.edu.nu.aafatlink.ui.auth;

import java.util.List;
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
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.RegistrationResult;
import pk.edu.nu.aafatlink.ui.ViewRouter;

/** Presentation controller for public Requester and Volunteer registration. */
public final class RegistrationViewController {
    private final ViewRouter router;
    private final AuthenticationController authentication;

    @FXML
    private TextField displayNameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private ComboBox<UserRole> accountTypeBox;
    @FXML
    private Button registerButton;
    @FXML
    private Label registrationMessageLabel;

    public RegistrationViewController(ViewRouter router, AuthenticationController authentication) {
        this.router = Objects.requireNonNull(router, "router");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
    }

    @FXML
    private void initialize() {
        accountTypeBox.setItems(FXCollections.observableArrayList(List.of(UserRole.REQUESTER, UserRole.VOLUNTEER)));
        accountTypeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(UserRole role) {
                return role == null ? "" : role.displayName();
            }

            @Override
            public UserRole fromString(String value) {
                return null;
            }
        });
        accountTypeBox.getSelectionModel().select(UserRole.REQUESTER);
    }

    @FXML
    private void register() {
        if (!passwordField.getText().equals(confirmPasswordField.getText())) {
            showError("Passwords do not match.");
            return;
        }
        setBusy(true);
        registrationMessageLabel.setText("Creating your secure account…");
        registrationMessageLabel.getStyleClass().removeAll("form-error", "form-success");
        UserRole selectedType = accountTypeBox.getValue();
        Task<RegistrationResult> task = new Task<>() {
            @Override
            protected RegistrationResult call() {
                return authentication.register(displayNameField.getText(), emailField.getText(), passwordField.getText());
            }
        };
        task.setOnSucceeded(event -> {
            RegistrationResult result = task.getValue();
            passwordField.clear();
            confirmPasswordField.clear();
            String next = result.emailConfirmationRequired()
                    ? "Check your email to verify the account, then return to sign in."
                    : "Your account is ready. Return to sign in.";
            if (selectedType == UserRole.VOLUNTEER) {
                next += " After sign-in, complete the readiness profile to request volunteer approval.";
            }
            registrationMessageLabel.setText(next);
            registrationMessageLabel.getStyleClass().add("form-success");
            registerButton.setText("Account created");
        });
        task.setOnFailed(event -> {
            setBusy(false);
            Throwable failure = task.getException();
            showError(failure == null || failure.getMessage() == null
                    ? "Registration failed. Please try again." : failure.getMessage());
        });
        Thread worker = new Thread(task, "aafatlink-auth-registration");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void backToSignIn() {
        router.showLogin();
    }

    private void setBusy(boolean busy) {
        displayNameField.setDisable(busy);
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        confirmPasswordField.setDisable(busy);
        accountTypeBox.setDisable(busy);
        registerButton.setDisable(busy);
    }

    private void showError(String message) {
        registrationMessageLabel.setText(message);
        registrationMessageLabel.getStyleClass().removeAll("form-error", "form-success");
        registrationMessageLabel.getStyleClass().add("form-error");
    }
}
