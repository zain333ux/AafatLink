package pk.edu.nu.aafatlink;

import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import pk.edu.nu.aafatlink.application.auth.AuthenticationController;
import pk.edu.nu.aafatlink.app.AppConfiguration;
import pk.edu.nu.aafatlink.app.ConfigurationException;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.app.navigation.NavigationPolicy;
import pk.edu.nu.aafatlink.infrastructure.supabase.SupabaseAccountRepository;
import pk.edu.nu.aafatlink.infrastructure.supabase.SupabaseAuthClient;
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
            AuthenticationController authentication = configuration.isRemoteConfigured()
                    ? new AuthenticationController(
                            new SupabaseAuthClient(configuration.supabaseUrl().orElseThrow(),
                                    configuration.publishableKey().orElseThrow()),
                            new SupabaseAccountRepository(configuration.supabaseUrl().orElseThrow(),
                                    configuration.publishableKey().orElseThrow()),
                            sessions)
                    : null;
            ViewRouter router = new ViewRouter(primaryStage, configuration, sessions,
                    new NavigationPolicy(), authentication);
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
