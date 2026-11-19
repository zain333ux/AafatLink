package pk.edu.nu.aafatlink.ui.request;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.request.RequestController;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;

/** JavaFX presentation controller for the owner-scoped UC02 list and detail view. */
public final class RequestListViewController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final List<RequestStatus> MAIN_LIFECYCLE = List.of(
            RequestStatus.PENDING_VERIFICATION, RequestStatus.VERIFIED, RequestStatus.ASSIGNED,
            RequestStatus.IN_PROGRESS, RequestStatus.AWAITING_CLOSURE_REVIEW, RequestStatus.CLOSED);

    private final SessionManager sessions;
    private final RequestController requests;
    private final Consumer<AssistanceRequest> openResubmission;

    @FXML private TableView<AssistanceRequest> requestTable;
    @FXML private TableColumn<AssistanceRequest, String> requestIdColumn;
    @FXML private TableColumn<AssistanceRequest, String> submittedColumn;
    @FXML private TableColumn<AssistanceRequest, String> disasterColumn;
    @FXML private TableColumn<AssistanceRequest, String> statusColumn;
    @FXML private VBox emptyState;
    @FXML private Label emptyStateLabel;
    @FXML private VBox errorState;
    @FXML private Label errorLabel;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private VBox detailPlaceholder;
    @FXML private VBox detailContent;
    @FXML private Label detailIdLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailPriorityLabel;
    @FXML private Label detailSubmittedLabel;
    @FXML private Label detailLocationLabel;
    @FXML private Label detailDescriptionLabel;
    @FXML private Label detailAssistanceLabel;
    @FXML private Label detailAffectedLabel;
    @FXML private Label detailAssignmentLabel;
    @FXML private Label detailProgressLabel;
    @FXML private VBox coordinatorReasonBox;
    @FXML private Label coordinatorReasonLabel;
    @FXML private FlowPane lifecyclePane;
    @FXML private Button resubmitButton;
    @FXML private Button refreshButton;

    public RequestListViewController(SessionManager sessions, RequestController requests,
            Consumer<AssistanceRequest> openResubmission) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.requests = requests;
        this.openResubmission = Objects.requireNonNull(openResubmission, "openResubmission");
    }

    @FXML
    private void initialize() {
        requestIdColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().id().toString()));
        submittedColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(DATE_TIME.format(cell.getValue().submittedAt())));
        disasterColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().details().disasterType().displayName()));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().status().displayName()));
        requestTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> showDetail(selected));
        loadRequests();
    }

    @FXML
    private void loadRequests() {
        if (sessions.authSession().isEmpty() || requests == null) {
            showEmpty("Demonstration mode has no operational requests. Sign in with a connected Requester account to load your own data.");
            refreshButton.setDisable(true);
            return;
        }
        setLoading(true);
        AuthSession session = sessions.authSession().orElseThrow();
        Task<List<AssistanceRequest>> task = new Task<>() {
            @Override protected List<AssistanceRequest> call() {
                return requests.getMyRequests(session);
            }
        };
        task.setOnSucceeded(event -> {
            setLoading(false);
            List<AssistanceRequest> result = task.getValue();
            requestTable.getItems().setAll(result);
            if (result.isEmpty()) {
                showEmpty("No assistance requests found. Use Submit request to create your first request.");
            } else {
                showTable();
                requestTable.getSelectionModel().selectFirst();
            }
        });
        task.setOnFailed(event -> {
            setLoading(false);
            Throwable failure = task.getException();
            errorLabel.setText(failure == null || failure.getMessage() == null
                    ? "AafatLink could not load your requests. Check the connection and retry."
                    : failure.getMessage());
            showOnly(errorState);
        });
        Thread worker = new Thread(task, "aafatlink-request-list");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void resubmitSelected() {
        AssistanceRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected != null && selected.status().canRequesterResubmit()) {
            openResubmission.accept(selected);
        }
    }

    private void showDetail(AssistanceRequest request) {
        boolean available = request != null;
        detailPlaceholder.setVisible(!available);
        detailPlaceholder.setManaged(!available);
        detailContent.setVisible(available);
        detailContent.setManaged(available);
        if (!available) {
            return;
        }
        detailIdLabel.setText(request.id().toString());
        detailStatusLabel.setText(request.status().displayName());
        detailPriorityLabel.setText(priorityLabel(request.priority()));
        detailSubmittedLabel.setText(DATE_TIME.format(request.submittedAt()));
        detailLocationLabel.setText(request.details().locationAddress());
        detailDescriptionLabel.setText(request.details().description());
        detailAssistanceLabel.setText(request.details().requiredAssistance());
        detailAffectedLabel.setText(request.details().affectedPersonDetails());
        detailAssignmentLabel.setText(request.assignmentState() == null ? "Not assigned yet" : request.assignmentState());
        detailProgressLabel.setText(request.latestProgress() == null ? "No progress update yet" : request.latestProgress());
        boolean hasReason = request.coordinatorReason() != null;
        coordinatorReasonBox.setVisible(hasReason);
        coordinatorReasonBox.setManaged(hasReason);
        coordinatorReasonLabel.setText(hasReason ? request.coordinatorReason() : "");
        resubmitButton.setVisible(request.status().canRequesterResubmit());
        resubmitButton.setManaged(request.status().canRequesterResubmit());
        renderLifecycle(request.status());
    }

    private void renderLifecycle(RequestStatus current) {
        lifecyclePane.getChildren().clear();
        int currentIndex = MAIN_LIFECYCLE.indexOf(current);
        if (currentIndex < 0) {
            Label alternative = new Label(current.displayName());
            alternative.getStyleClass().addAll("lifecycle-step", "lifecycle-step-alternative");
            lifecyclePane.getChildren().add(alternative);
        }
        for (int index = 0; index < MAIN_LIFECYCLE.size(); index++) {
            RequestStatus status = MAIN_LIFECYCLE.get(index);
            Label step = new Label(status.displayName());
            step.getStyleClass().add("lifecycle-step");
            if (currentIndex >= 0 && index < currentIndex) {
                step.getStyleClass().add("lifecycle-step-complete");
            } else if (index == currentIndex) {
                step.getStyleClass().add("lifecycle-step-active");
            }
            lifecyclePane.getChildren().add(step);
        }
    }

    private void setLoading(boolean loading) {
        refreshButton.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        if (loading) {
            showOnly(loadingIndicator);
        }
    }

    private void showEmpty(String message) {
        emptyStateLabel.setText(message);
        showOnly(emptyState);
    }

    private void showTable() {
        showOnly(requestTable);
    }

    private void showOnly(javafx.scene.Node visibleNode) {
        for (javafx.scene.Node node : List.of(requestTable, emptyState, errorState, loadingIndicator)) {
            boolean visible = node == visibleNode;
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private static String priorityLabel(RequestPriority priority) {
        if (priority == null) {
            return "Not assessed yet";
        }
        String value = priority.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
