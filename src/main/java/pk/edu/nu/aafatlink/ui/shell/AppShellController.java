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

/** Presentation controller for the reusable role-aware application frame. */
public final class AppShellController {
    private static final Map<NavigationItem, String> DESCRIPTIONS = descriptions();
    private final ViewRouter router;
    private final SessionManager sessions;
    private final NavigationPolicy navigationPolicy;
    private final AuthenticationController authentication;
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
    private Label roleLabel;
    @FXML
    private Label pageTitleLabel;
    @FXML
    private Label pageDescriptionLabel;
    @FXML
    private Button logoutButton;
    @FXML
    private StackPane contentHost;
    @FXML
    private Label demoNoticeLabel;
    @FXML
    private Label sessionNoticeTitle;

    public AppShellController(ViewRouter router, SessionManager sessions, NavigationPolicy navigationPolicy,
            AuthenticationController authentication, RequestController requests, RequestReviewController reviews,
            VolunteerController volunteers, AssignmentController assignments, ReportController reports,
            AccessController access) {
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
    }

    @FXML
    private void initialize() {
        SessionUser user = sessions.currentUser()
                .orElseThrow(() -> new IllegalStateException("The application shell requires an active session."));
        userNameLabel.setText(user.displayName());
        roleLabel.setText(user.role().displayName());
        sessionNoticeTitle.setText(user.demonstrationAccount() ? "DEMONSTRATION" : "SECURE SESSION");
        demoNoticeLabel.setText(user.demonstrationAccount()
                ? "Read-only preview. No operational data is loaded or changed."
                : "Connected session. Access remains enforced by database policies.");

        for (NavigationItem item : navigationPolicy.itemsFor(user.role())) {
            Button button = createNavigationButton(item);
            navigationButtons.put(item, button);
            navigationBox.getChildren().add(button);
        }
        navigate(NavigationItem.DASHBOARD);
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
        button.setOnAction(event -> navigate(item));
        return button;
    }

    private void navigate(NavigationItem item) {
        Button selectedButton = navigationButtons.get(item);
        if (selectedButton == null) {
            return;
        }
        navigationBox.getChildren().forEach(node -> node.getStyleClass().remove("navigation-button-selected"));
        selectedButton.getStyleClass().add("navigation-button-selected");
        pageTitleLabel.setText(item.label());
        pageDescriptionLabel.setText(DESCRIPTIONS.getOrDefault(item,
                "This workflow will be connected during its scheduled implementation phase."));
        switch (item) {
            case DASHBOARD -> showStaticView("dashboard-view.fxml");
            case SUBMIT_REQUEST -> showRequestForm(null);
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
            default -> showUnavailable(item);
        }
    }

    private void showStaticView(String fileName) {
        setContent(load(fileName, null));
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
            pageDescriptionLabel.setText("Amend the returned request while keeping its original request ID.");
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

    private void showUnavailable(NavigationItem item) {
        Label message = new Label(item.label() + " is not implemented yet. Its mandatory behavior remains tracked in the use-case traceability document.");
        message.setWrapText(true);
        message.getStyleClass().add("empty-state-copy");
        VBox box = new VBox(10, new Label("Scheduled workflow"), message);
        box.getStyleClass().addAll("content-area", "empty-state");
        setContent(box);
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
            return loader.load();
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

    private static Map<NavigationItem, String> descriptions() {
        Map<NavigationItem, String> descriptions = new EnumMap<>(NavigationItem.class);
        descriptions.put(NavigationItem.DASHBOARD,
                "Your role-aware command centre for the documented response workflow.");
        descriptions.put(NavigationItem.SUBMIT_REQUEST, "Create a validated assistance request or resubmit one returned for more information.");
        descriptions.put(NavigationItem.MY_REQUESTS, "Review your requests, current status, priority and latest available progress.");
        descriptions.put(NavigationItem.READINESS_PROFILE, "Maintain UC07 availability, skills, service areas and contact details.");
        descriptions.put(NavigationItem.ASSIGNMENTS, "Review and respond to UC08 assignments awaiting your decision.");
        descriptions.put(NavigationItem.TASK_PROGRESS, "Record UC09 progress or submit UC10 completion evidence for review.");
        descriptions.put(NavigationItem.VERIFICATION_QUEUE, "Review pending requests and record an auditable UC03 decision.");
        descriptions.put(NavigationItem.PRIORITY_QUEUE, "Apply UC04 priority rules and record the coordinator's final decision.");
        descriptions.put(NavigationItem.VOLUNTEER_ASSIGNMENT, "Match and reassign volunteers using UC05 eligibility and conflict rules.");
        descriptions.put(NavigationItem.CLOSURE_REVIEW, "Approve UC06 closure or return incomplete assistance for rework.");
        descriptions.put(NavigationItem.REPORTS, "Generate and export the four committed-data reports required by UC11.");
        descriptions.put(NavigationItem.ACCESS_MANAGEMENT, "Apply audited UC12 role, status and volunteer-approval changes.");
        return Map.copyOf(descriptions);
    }
}
