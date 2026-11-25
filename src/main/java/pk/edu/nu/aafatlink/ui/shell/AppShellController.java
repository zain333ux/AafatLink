package pk.edu.nu.aafatlink.ui.shell;

import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.kordamp.ikonli.javafx.FontIcon;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.app.SessionUser;
import pk.edu.nu.aafatlink.app.navigation.NavigationItem;
import pk.edu.nu.aafatlink.app.navigation.NavigationPolicy;
import pk.edu.nu.aafatlink.application.auth.AuthenticationController;
import pk.edu.nu.aafatlink.application.dashboard.DashboardController;
import pk.edu.nu.aafatlink.application.request.RequestController;
import pk.edu.nu.aafatlink.application.request.RequestReviewController;
import pk.edu.nu.aafatlink.application.assignment.AssignmentController;
import pk.edu.nu.aafatlink.application.volunteer.VolunteerController;
import pk.edu.nu.aafatlink.application.reporting.ReportController;
import pk.edu.nu.aafatlink.application.access.AccessController;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.service.evidence.EvidenceFileValidator;
import pk.edu.nu.aafatlink.ui.ViewRouter;
import pk.edu.nu.aafatlink.ui.request.RequestFormViewController;
import pk.edu.nu.aafatlink.ui.request.RequestListViewController;
import pk.edu.nu.aafatlink.ui.request.RequestReviewViewController;
import pk.edu.nu.aafatlink.ui.request.ClosureReviewViewController;
import pk.edu.nu.aafatlink.ui.assignment.VolunteerAssignmentViewController;
import pk.edu.nu.aafatlink.ui.volunteer.VolunteerAssignmentsViewController;
import pk.edu.nu.aafatlink.ui.volunteer.VolunteerReadinessViewController;
import pk.edu.nu.aafatlink.ui.volunteer.TaskProgressViewController;
import pk.edu.nu.aafatlink.ui.reporting.ReportViewController;
import pk.edu.nu.aafatlink.ui.access.AccessManagementViewController;
import pk.edu.nu.aafatlink.ui.access.ReferenceDataViewController;

/** Presentation controller for the reusable role-aware application frame. */
public final class AppShellController {
    private final ViewRouter router;
    private final SessionManager sessions;
    private final NavigationPolicy navigationPolicy;
    private final AuthenticationController authentication;
    private final DashboardController dashboard;
    private final RequestController requests;
    private final RequestReviewController reviews;
    private final VolunteerController volunteers;
    private final AssignmentController assignments;
    private final ReportController reports;
    private final AccessController access;
    private final Map<NavigationItem, Button> navigationButtons = new EnumMap<>(NavigationItem.class);

    @FXML
    private VBox navigationBox;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label avatarLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private Label pageTitleLabel;
    @FXML
    private Button logoutButton;
    @FXML
    private StackPane contentHost;
    @FXML
    private Label demoNoticeLabel;

    public AppShellController(ViewRouter router, SessionManager sessions, NavigationPolicy navigationPolicy,
            AuthenticationController authentication, RequestController requests, RequestReviewController reviews,
            VolunteerController volunteers, AssignmentController assignments, ReportController reports,
            AccessController access, DashboardController dashboard) {
        this.router = Objects.requireNonNull(router, "router");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.navigationPolicy = Objects.requireNonNull(navigationPolicy, "navigationPolicy");
        this.authentication = authentication;
        this.requests = requests;
        this.reviews = reviews;
        this.volunteers = volunteers;
        this.assignments = assignments;
        this.reports = reports;
        this.access = access;
        this.dashboard = dashboard;
    }

    @FXML
    private void initialize() {
        SessionUser user = sessions.currentUser()
                .orElseThrow(() -> new IllegalStateException("The application shell requires an active session."));
        userNameLabel.setText(user.displayName());
        avatarLabel.setText(java.util.Arrays.stream(user.displayName().strip().split("\\s+"))
                .filter(part -> !part.isEmpty()).limit(2)
                .map(part -> part.substring(0, part.offsetByCodePoints(0, 1)))
                .collect(java.util.stream.Collectors.joining()).toUpperCase(java.util.Locale.ROOT));
        roleLabel.setText(user.role().displayName());
        demoNoticeLabel.setVisible(user.demonstrationAccount());
        demoNoticeLabel.managedProperty().bind(demoNoticeLabel.visibleProperty());

        for (NavigationItem item : navigationPolicy.itemsFor(user.role())) {
            Button button = createNavigationButton(item);
            navigationButtons.put(item, button);
            navigationBox.getChildren().add(button);
        }
        navigate(NavigationItem.DASHBOARD);
    }

    private Button createNavigationButton(NavigationItem item) {
        FontIcon icon = new FontIcon(item.iconLiteral());
        icon.setIconSize(17);
        Label text = new Label(item.label());
        HBox content = new HBox(12, icon, text);
        content.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("navigation-button");
        button.setOnAction(event -> navigate(item));
        return button;
    }

    private java.util.UUID dashboardRecord;

    private void navigate(NavigationItem item, java.util.UUID record) {
        dashboardRecord = record;
        try {
            navigate(item);
        } finally {
            dashboardRecord = null;
        }
    }

    private void navigate(NavigationItem item) {
        Button selectedButton = navigationButtons.get(item);
        if (selectedButton == null) {
            return;
        }
        navigationBox.getChildren().forEach(node -> node.getStyleClass().remove("navigation-button-selected"));
        selectedButton.getStyleClass().add("navigation-button-selected");
        pageTitleLabel.setText(item.label());
        switch (item) {
            case DASHBOARD -> setContent(load("dashboard-view.fxml", type ->
                    new DashboardViewController(sessions, dashboard, this::navigate)));
            case SUBMIT_REQUEST -> showRequestForm(null);
            case RECORD_REQUEST -> showRequestForm(null);
            case MY_REQUESTS -> showRequestList();
            case VERIFICATION_QUEUE -> showRequestReview(RequestReviewViewController.Mode.VERIFICATION);
            case PRIORITY_QUEUE -> showRequestReview(RequestReviewViewController.Mode.PRIORITY);
            case READINESS_PROFILE -> showVolunteerReadiness();
            case ASSIGNMENTS -> showVolunteerAssignments();
            case TASK_PROGRESS -> showTaskProgress();
            case VOLUNTEER_ASSIGNMENT -> showVolunteerAssignment();
            case CLOSURE_REVIEW -> showClosureReview();
            case REPORTS -> showReports();
            case ACCESS_MANAGEMENT -> showAccessManagement();
            case REFERENCE_DATA -> showReferenceData();
        }
    }

    private void showRequestForm(AssistanceRequest existingRequest) {
        FXMLLoader loader = loader("request-form-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == RequestFormViewController.class) {
                return new RequestFormViewController(sessions, requests, new EvidenceFileValidator(),
                        () -> navigate(NavigationItem.MY_REQUESTS));
            }
            throw new IllegalArgumentException("Unsupported shell content controller: " + type.getName());
        });
        Parent content = load(loader);
        RequestFormViewController controller = loader.getController();
        if (existingRequest != null) {
            controller.prepareResubmission(existingRequest);
            pageTitleLabel.setText("Update and resubmit");
        }
        setContent(content);
    }

    private void showRequestList() {
        FXMLLoader loader = loader("request-list-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == RequestListViewController.class) {
                return new RequestListViewController(sessions, requests, this::showRequestForm);
            }
            throw new IllegalArgumentException("Unsupported shell content controller: " + type.getName());
        });
        setContent(load(loader));
    }

    private void showRequestReview(RequestReviewViewController.Mode mode) {
        FXMLLoader loader = loader("request-review-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == RequestReviewViewController.class) {
                return new RequestReviewViewController(sessions, reviews, mode);
            }
            throw new IllegalArgumentException("Unsupported shell content controller: " + type.getName());
        });
        setContent(load(loader));
    }

    private void showVolunteerReadiness() {
        FXMLLoader loader = loader("volunteer-readiness-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == VolunteerReadinessViewController.class) {
                return new VolunteerReadinessViewController(sessions, volunteers);
            }
            throw new IllegalArgumentException("Unsupported readiness controller: " + type.getName());
        });
        setContent(load(loader));
    }

    private void showVolunteerAssignments() {
        FXMLLoader loader = loader("volunteer-assignments-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == VolunteerAssignmentsViewController.class) {
                return new VolunteerAssignmentsViewController(sessions, volunteers);
            }
            throw new IllegalArgumentException("Unsupported volunteer assignment controller: " + type.getName());
        });
        setContent(load(loader));
    }

    private void showVolunteerAssignment() {
        FXMLLoader loader = loader("volunteer-assignment-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == VolunteerAssignmentViewController.class) {
                return new VolunteerAssignmentViewController(sessions, assignments);
            }
            throw new IllegalArgumentException("Unsupported coordinator assignment controller: " + type.getName());
        });
        setContent(load(loader));
    }

    private void showTaskProgress() {
        FXMLLoader loader = loader("task-progress-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == TaskProgressViewController.class) {
                return new TaskProgressViewController(sessions, volunteers, new EvidenceFileValidator());
            }
            throw new IllegalArgumentException("Unsupported task-progress controller: " + type.getName());
        });
        setContent(load(loader));
    }

    private void showClosureReview() {
        FXMLLoader loader = loader("closure-review-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == ClosureReviewViewController.class) {
                return new ClosureReviewViewController(sessions, reviews);
            }
            throw new IllegalArgumentException("Unsupported closure-review controller: " + type.getName());
        });
        setContent(load(loader));
    }

    private void showReports() {
        FXMLLoader loader = loader("report-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == ReportViewController.class) {
                return new ReportViewController(sessions, reports, assignments);
            }
            throw new IllegalArgumentException("Unsupported report controller: " + type.getName());
        });
        setContent(load(loader));
    }

    private void showAccessManagement() {
        FXMLLoader loader = loader("access-management-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == AccessManagementViewController.class) {
                return new AccessManagementViewController(sessions, access);
            }
            throw new IllegalArgumentException("Unsupported access controller: " + type.getName());
        });
        setContent(load(loader));
    }

    private void showReferenceData() {
        FXMLLoader loader = loader("reference-data-view.fxml");
        loader.setControllerFactory(type -> {
            if (type == ReferenceDataViewController.class) {
                return new ReferenceDataViewController(sessions, access);
            }
            throw new IllegalArgumentException("Unsupported reference-data controller: " + type.getName());
        });
        setContent(load(loader));
    }

    private void setContent(Parent content) {
        contentHost.getChildren().setAll(content);
    }

    private Parent load(String fileName, javafx.util.Callback<Class<?>, Object> factory) {
        FXMLLoader loader = loader(fileName);
        if (factory != null) {
            loader.setControllerFactory(factory);
        }
        return load(loader);
    }

    private FXMLLoader loader(String fileName) {
        String path = "/pk/edu/nu/aafatlink/fxml/" + fileName;
        URL resource = AppShellController.class.getResource(path);
        if (resource == null) {
            throw new IllegalStateException("Missing shell content resource: " + path);
        }
        return new FXMLLoader(resource);
    }

    private Parent load(FXMLLoader loader) {
        try {
            Parent content = loader.load();
            if (dashboardRecord != null && loader.getController() instanceof DashboardSelection.Destination destination) {
                destination.initialSelection().request(dashboardRecord);
            }
            return content;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load shell content.", exception);
        }
    }

    @FXML
    private void logout() {
        SessionUser user = sessions.currentUser().orElse(null);
        if (user == null || user.demonstrationAccount() || authentication == null) {
            sessions.logout();
            router.showLogin();
            return;
        }
        logoutButton.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                authentication.logout();
                return null;
            }
        };
        task.setOnSucceeded(event -> router.showLogin());
        task.setOnFailed(event -> router.showLogin());
        Thread worker = new Thread(task, "aafatlink-auth-logout");
        worker.setDaemon(true);
        worker.start();
    }
}
