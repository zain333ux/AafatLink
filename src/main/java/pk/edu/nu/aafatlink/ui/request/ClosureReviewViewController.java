package pk.edu.nu.aafatlink.ui.request;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.request.RequestReviewController;
import pk.edu.nu.aafatlink.domain.assignment.CompletionEvidence;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdate;
import pk.edu.nu.aafatlink.domain.assignment.TaskContext;

/** JavaFX presentation controller for UC06 closure approval and rework. */
public final class ClosureReviewViewController {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM, HH:mm")
            .withZone(ZoneId.systemDefault());
    private final SessionManager sessions;
    private final RequestReviewController reviews;

    @FXML private TableView<TaskContext> requestTable;
    @FXML private TableColumn<TaskContext, String> assistanceColumn;
    @FXML private TableColumn<TaskContext, String> locationColumn;
    @FXML private TableColumn<TaskContext, String> priorityColumn;
    @FXML private VBox emptyState;
    @FXML private VBox detailPlaceholder;
    @FXML private ScrollPane detailScroll;
    @FXML private Label requestIdLabel;
    @FXML private Label assistanceLabel;
    @FXML private Label locationLabel;
    @FXML private Label completionRemarksLabel;
    @FXML private ListView<String> progressList;
    @FXML private ListView<String> evidenceList;
    @FXML private TextArea remarksArea;
    @FXML private Label messageLabel;
    @FXML private Button refreshButton;
    @FXML private Button approveButton;
    @FXML private Button reworkButton;

    public ClosureReviewViewController(SessionManager sessions, RequestReviewController reviews) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.reviews = reviews;
    }

    @FXML
    private void initialize() {
        requestTable.setPlaceholder(new Region());
        assistanceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().assignmentContext().request().details().requiredAssistance()));
        locationColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().assignmentContext().request().details().locationAddress()));
        priorityColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().assignmentContext().request().priority() == null ? "—"
                        : data.getValue().assignmentContext().request().priority().name()));
        requestTable.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (selected != null) showDetails(selected);
        });
        loadRequests();
    }

    @FXML
    private void loadRequests() {
        if (sessions.authSession().isEmpty() || reviews == null) {
            requestTable.setItems(FXCollections.observableArrayList());
            showEmpty("Demonstration mode does not load operational closure records.");
            setBusy(true);
            return;
        }
        setBusy(true);
        showMessage("Loading closure-review queue…", false);
        Task<List<TaskContext>> task = new Task<>() {
            @Override protected List<TaskContext> call() {
                return reviews.loadClosureQueue(sessions.authSession().orElseThrow());
            }
        };
        task.setOnSucceeded(event -> {
            requestTable.setItems(FXCollections.observableArrayList(task.getValue()));
            setBusy(false);
            if (task.getValue().isEmpty()) showEmpty("No request is awaiting closure review.");
            else {
                emptyState.setVisible(false);
                emptyState.setManaged(false);
                requestTable.getSelectionModel().selectFirst();
                showMessage("Review completion details before recording a decision.", false);
            }
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showEmpty("Closure-review requests could not be loaded. Use Refresh to retry.");
            showMessage(message(task.getException(), "Closure-review requests could not be loaded."), true);
        });
        start(task, "aafatlink-closure-load");
    }

    @FXML
    private void approveClosure() { submitDecision(true); }

    @FXML
    private void returnForRework() { submitDecision(false); }

    private void submitDecision(boolean approve) {
        TaskContext selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null || sessions.authSession().isEmpty()) return;
        setBusy(true);
        showMessage(approve ? "Approving closure…" : "Returning task for rework…", false);
        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                if (approve) reviews.approveClosure(sessions.authSession().orElseThrow(), selected,
                        remarksArea.getText());
                else reviews.returnForRework(sessions.authSession().orElseThrow(), selected,
                        remarksArea.getText());
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            remarksArea.clear();
            showMessage(approve ? "Request closed and assignment completed."
                    : "Task returned to In Progress. The reason is visible to the volunteer.", false);
            loadRequests();
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showMessage(message(task.getException(), "The closure decision was not saved."), true);
        });
        start(task, "aafatlink-closure-save");
    }

    private void showDetails(TaskContext context) {
        detailPlaceholder.setVisible(false);
        detailPlaceholder.setManaged(false);
        detailScroll.setVisible(true);
        detailScroll.setManaged(true);
        requestIdLabel.setText(context.assignmentContext().request().id().toString());
        assistanceLabel.setText(context.assignmentContext().request().details().requiredAssistance());
        locationLabel.setText(context.assignmentContext().request().details().locationAddress());
        completionRemarksLabel.setText(context.completionDetails()
                .map(completion -> completion.remarks()).orElse("Completion details are unavailable."));
        progressList.setItems(FXCollections.observableArrayList(context.progressHistory().stream()
                .map(this::progressLabel).toList()));
        progressList.setPlaceholder(new Label("No progress history was recorded."));
        evidenceList.setItems(FXCollections.observableArrayList(context.completionEvidence().stream()
                .map(this::evidenceLabel).toList()));
        evidenceList.setPlaceholder(new Label("No completion evidence was attached."));
    }

    private String progressLabel(ProgressUpdate update) {
        return TIME.format(update.recordedAt()) + "  "
                + update.percentage().map(value -> value + "% · ").orElse("") + update.progressNote();
    }

    private String evidenceLabel(CompletionEvidence evidence) {
        return evidence.fileName() + " · " + evidence.contentType() + " · "
                + String.format("%.1f MB", evidence.sizeBytes() / 1048576.0);
    }

    private void showEmpty(String text) {
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        ((Label) emptyState.getChildren().get(1)).setText(text);
        detailScroll.setVisible(false);
        detailScroll.setManaged(false);
        detailPlaceholder.setVisible(true);
        detailPlaceholder.setManaged(true);
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        approveButton.setDisable(busy || sessions.authSession().isEmpty());
        reworkButton.setDisable(busy || sessions.authSession().isEmpty());
    }

    private void showMessage(String message, boolean error) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().removeAll("form-error", "form-success");
        messageLabel.getStyleClass().add(error ? "form-error" : "form-success");
    }

    private static String message(Throwable failure, String fallback) {
        return failure == null || failure.getMessage() == null ? fallback : failure.getMessage();
    }

    private static void start(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }
}
