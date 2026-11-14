package pk.edu.nu.aafatlink.ui.shell;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.kordamp.ikonli.javafx.FontIcon;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.app.SessionUser;
import pk.edu.nu.aafatlink.app.navigation.NavigationItem;
import pk.edu.nu.aafatlink.app.navigation.NavigationPolicy;
import pk.edu.nu.aafatlink.ui.ViewRouter;

/** Presentation controller for the reusable role-aware application frame. */
public final class AppShellController {
    private static final Map<NavigationItem, String> DESCRIPTIONS = descriptions();
    private final ViewRouter router;
    private final SessionManager sessions;
    private final NavigationPolicy navigationPolicy;

    @FXML
    private VBox navigationBox;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private Label pageTitleLabel;
    @FXML
    private Label pageDescriptionLabel;
    @FXML
    private Label welcomeLabel;

    public AppShellController(ViewRouter router, SessionManager sessions, NavigationPolicy navigationPolicy) {
        this.router = Objects.requireNonNull(router, "router");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.navigationPolicy = Objects.requireNonNull(navigationPolicy, "navigationPolicy");
    }

    @FXML
    private void initialize() {
        SessionUser user = sessions.currentUser()
                .orElseThrow(() -> new IllegalStateException("The application shell requires an active session."));
        userNameLabel.setText(user.displayName());
        roleLabel.setText(user.role().displayName());
        welcomeLabel.setText("Good to see you, " + user.role().displayName().toLowerCase() + ".");

        for (NavigationItem item : navigationPolicy.itemsFor(user.role())) {
            navigationBox.getChildren().add(createNavigationButton(item));
        }
        select(NavigationItem.DASHBOARD, (Button) navigationBox.getChildren().get(0));
    }

    private Button createNavigationButton(NavigationItem item) {
        FontIcon icon = new FontIcon(item.iconLiteral());
        icon.setIconSize(20);
        Label text = new Label(item.label());
        HBox content = new HBox(12, icon, text);
        content.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("navigation-button");
        button.setOnAction(event -> select(item, button));
        return button;
    }

    private void select(NavigationItem item, Button selectedButton) {
        navigationBox.getChildren().forEach(node -> node.getStyleClass().remove("navigation-button-selected"));
        selectedButton.getStyleClass().add("navigation-button-selected");
        pageTitleLabel.setText(item.label());
        pageDescriptionLabel.setText(DESCRIPTIONS.getOrDefault(item,
                "This workflow will be connected during its scheduled implementation phase."));
    }

    @FXML
    private void logout() {
        sessions.logout();
        router.showLogin();
    }

    private static Map<NavigationItem, String> descriptions() {
        Map<NavigationItem, String> descriptions = new EnumMap<>(NavigationItem.class);
        descriptions.put(NavigationItem.DASHBOARD,
                "Your role-aware command centre. Operational figures shown in Phase 1 are clearly marked as demonstration data.");
        descriptions.put(NavigationItem.SUBMIT_REQUEST, "UC01 request submission is scheduled for the requester workflow phase.");
        descriptions.put(NavigationItem.MY_REQUESTS, "UC02 request tracking is scheduled for the requester workflow phase.");
        descriptions.put(NavigationItem.READINESS_PROFILE, "UC07 readiness management is scheduled for volunteer coordination.");
        descriptions.put(NavigationItem.ASSIGNMENTS, "UC08 assignment response is scheduled for volunteer coordination.");
        descriptions.put(NavigationItem.TASK_PROGRESS, "UC09 and UC10 task execution workflows are scheduled for Phase 7.");
        descriptions.put(NavigationItem.VERIFICATION_QUEUE, "UC03 verification is scheduled for the coordinator workflow phase.");
        descriptions.put(NavigationItem.PRIORITY_QUEUE, "UC04 priority assessment is scheduled for the coordinator workflow phase.");
        descriptions.put(NavigationItem.VOLUNTEER_ASSIGNMENT, "UC05 assignment is scheduled for volunteer coordination.");
        descriptions.put(NavigationItem.CLOSURE_REVIEW, "UC06 closure review is scheduled for Phase 7.");
        descriptions.put(NavigationItem.REPORTS, "UC11 reporting is scheduled for the reporting and administration phase.");
        descriptions.put(NavigationItem.ACCESS_MANAGEMENT, "UC12 access control is scheduled for the administration phase.");
        return Map.copyOf(descriptions);
    }
}
