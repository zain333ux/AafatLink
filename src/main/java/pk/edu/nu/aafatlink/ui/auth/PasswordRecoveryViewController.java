package pk.edu.nu.aafatlink.ui.auth;

import java.time.Duration;
import java.util.Objects;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import pk.edu.nu.aafatlink.application.auth.PasswordRecoveryAttempt;
import pk.edu.nu.aafatlink.application.auth.PasswordRecoveryCoordinator;
import pk.edu.nu.aafatlink.ui.ViewRouter;

/** Presentation controller for the installed-application PKCE recovery flow. */
public final class PasswordRecoveryViewController {
    private static final Duration COMPLETION_TIMEOUT = Duration.ofMinutes(10);
    private final ViewRouter router;
    private final PasswordRecoveryCoordinator recovery;

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Button recoverButton;
    @FXML
    private Button backButton;
    @FXML
    private Label recoveryMessageLabel;

    public PasswordRecoveryViewController(ViewRouter router, PasswordRecoveryCoordinator recovery) {
        this.router = Objects.requireNonNull(router, "router");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
    }

    @FXML
    private void recover() {
        String newPassword = passwordField.getText();
        if (newPassword.length() < 8) {
            showError("Use at least 8 characters for the new password.");
            return;
        }
        if (!newPassword.equals(confirmPasswordField.getText())) {
            showError("Passwords do not match.");
            return;
        }

        setBusy(true);
        showMessage("Sending the secure recovery email…", false);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try (PasswordRecoveryAttempt attempt = recovery.begin(emailField.getText())) {
                    updateMessage("Email sent. Open its recovery link within 10 minutes; AafatLink is waiting securely.");
                    recovery.complete(attempt, newPassword, COMPLETION_TIMEOUT);
                    return null;
                }
            }
        };
        recoveryMessageLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            recoveryMessageLabel.textProperty().unbind();
            passwordField.clear();
            confirmPasswordField.clear();
            showMessage("Password updated. You can now return to sign in.", true);
            backButton.setDisable(false);
        });
        task.setOnFailed(event -> {
            recoveryMessageLabel.textProperty().unbind();
            setBusy(false);
            Throwable failure = task.getException();
            showError(failure == null || failure.getMessage() == null
                    ? "Password recovery failed. Please try again." : failure.getMessage());
        });
        Thread worker = new Thread(task, "aafatlink-password-recovery");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void backToSignIn() {
        router.showLogin();
    }

    private void setBusy(boolean busy) {
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
        confirmPasswordField.setDisable(busy);
        recoverButton.setDisable(busy);
        backButton.setDisable(busy);
    }

    private void showError(String message) {
        showMessage(message, false);
        recoveryMessageLabel.getStyleClass().add("form-error");
    }

    private void showMessage(String message, boolean success) {
        recoveryMessageLabel.setText(message);
        recoveryMessageLabel.getStyleClass().removeAll("form-error", "form-success");
        if (success) {
            recoveryMessageLabel.getStyleClass().add("form-success");
        }
    }
}
