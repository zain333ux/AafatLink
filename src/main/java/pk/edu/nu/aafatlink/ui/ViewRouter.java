package pk.edu.nu.aafatlink.ui;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import pk.edu.nu.aafatlink.application.auth.AuthenticationController;
import pk.edu.nu.aafatlink.application.auth.PasswordRecoveryCoordinator;
import pk.edu.nu.aafatlink.app.AppConfiguration;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.app.navigation.NavigationPolicy;
import pk.edu.nu.aafatlink.ui.auth.LoginViewController;
import pk.edu.nu.aafatlink.ui.auth.PasswordRecoveryViewController;
import pk.edu.nu.aafatlink.ui.auth.RegistrationViewController;
import pk.edu.nu.aafatlink.ui.shell.AppShellController;

/** Loads top-level FXML views and supplies their presentation dependencies. */
public final class ViewRouter {
    private static final String THEME = "/pk/edu/nu/aafatlink/css/aafatlink.css";
    private final Stage stage;
    private final AppConfiguration configuration;
    private final SessionManager sessions;
    private final NavigationPolicy navigationPolicy;
    private final AuthenticationController authentication;
    private final PasswordRecoveryCoordinator passwordRecovery;

    public ViewRouter(Stage stage, AppConfiguration configuration, SessionManager sessions,
            NavigationPolicy navigationPolicy, AuthenticationController authentication,
            PasswordRecoveryCoordinator passwordRecovery) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.navigationPolicy = Objects.requireNonNull(navigationPolicy, "navigationPolicy");
        this.authentication = authentication;
        this.passwordRecovery = passwordRecovery;
    }

    public void showLogin() {
        sessions.logout();
        show("/pk/edu/nu/aafatlink/fxml/login-view.fxml", 1_180, 760);
    }

    public void showShell() {
        if (sessions.currentUser().isEmpty()) {
            showLogin();
            return;
        }
        show("/pk/edu/nu/aafatlink/fxml/app-shell.fxml", 1_280, 800);
    }

    public void showRegistration() {
        if (authentication == null) {
            showLogin();
            return;
        }
        show("/pk/edu/nu/aafatlink/fxml/registration-view.fxml", 1_180, 780);
    }

    public void showPasswordRecovery() {
        if (passwordRecovery == null) {
            showLogin();
            return;
        }
        show("/pk/edu/nu/aafatlink/fxml/password-recovery-view.fxml", 1_180, 780);
    }

    private void show(String resourcePath, double width, double height) {
        URL resource = ViewRouter.class.getResource(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Missing view resource: " + resourcePath);
        }
        FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(this::createController);
        try {
            Parent root = loader.load();
            Scene scene = new Scene(root, width, height);
            URL stylesheet = ViewRouter.class.getResource(THEME);
            if (stylesheet == null) {
                throw new IllegalStateException("Missing theme resource: " + THEME);
            }
            scene.getStylesheets().add(stylesheet.toExternalForm());
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load view: " + resourcePath, exception);
        }
    }

    private Object createController(Class<?> type) {
        if (type == LoginViewController.class) {
            return new LoginViewController(this, configuration, sessions, authentication);
        }
        if (type == AppShellController.class) {
            return new AppShellController(this, sessions, navigationPolicy, authentication);
        }
        if (type == RegistrationViewController.class) {
            return new RegistrationViewController(this, authentication);
        }
        if (type == PasswordRecoveryViewController.class) {
            return new PasswordRecoveryViewController(this, passwordRecovery);
        }
        throw new IllegalArgumentException("Unsupported FXML controller: " + type.getName());
    }
}
