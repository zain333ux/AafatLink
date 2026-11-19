package pk.edu.nu.aafatlink;

import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import pk.edu.nu.aafatlink.application.auth.AuthenticationController;
import pk.edu.nu.aafatlink.application.auth.PasswordRecoveryCoordinator;
import pk.edu.nu.aafatlink.application.request.RequestController;
import pk.edu.nu.aafatlink.app.AppConfiguration;
import pk.edu.nu.aafatlink.app.ConfigurationException;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.app.navigation.NavigationPolicy;
import pk.edu.nu.aafatlink.infrastructure.supabase.SupabaseAccountRepository;
import pk.edu.nu.aafatlink.infrastructure.supabase.SupabaseAuthClient;
import pk.edu.nu.aafatlink.infrastructure.supabase.SupabaseWorkflowRepository;
import pk.edu.nu.aafatlink.ui.ViewRouter;

/** JavaFX lifecycle entry point for the AafatLink desktop application. */
public final class AafatLinkApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(AafatLinkApplication.class.getName());

    @Override
    public void start(Stage primaryStage) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                LOGGER.log(Level.SEVERE, "Unhandled error on " + thread.getName(), error));
        try {
            AppConfiguration configuration = AppConfiguration.fromEnvironment();
            SessionManager sessions = new SessionManager();
            SupabaseAuthClient authClient = configuration.isRemoteConfigured()
                    ? new SupabaseAuthClient(configuration.supabaseUrl().orElseThrow(),
                            configuration.publishableKey().orElseThrow())
                    : null;
            AuthenticationController authentication = authClient == null
                    ? null
                    : new AuthenticationController(
                            authClient,
                            new SupabaseAccountRepository(configuration.supabaseUrl().orElseThrow(),
                                    configuration.publishableKey().orElseThrow()),
                            sessions);
            PasswordRecoveryCoordinator passwordRecovery = authClient == null
                    ? null : new PasswordRecoveryCoordinator(authClient);
            RequestController requests = authClient == null
                    ? null
                    : new RequestController(new SupabaseWorkflowRepository(
                            configuration.supabaseUrl().orElseThrow(),
                            configuration.publishableKey().orElseThrow()));
            ViewRouter router = new ViewRouter(primaryStage, configuration, sessions,
                    new NavigationPolicy(), authentication, passwordRecovery, requests);
            primaryStage.setTitle("AafatLink");
            primaryStage.setMinWidth(1_020);
            primaryStage.setMinHeight(680);
            router.showLogin();
        } catch (ConfigurationException exception) {
            LOGGER.log(Level.SEVERE, "AafatLink configuration is invalid", exception);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("AafatLink could not start safely");
            alert.setContentText(exception.getMessage());
            alert.showAndWait();
        }
    }
}
