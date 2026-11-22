package pk.edu.nu.aafatlink.ui.request;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.request.RequestReviewController;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestReviewDetails;

/** Coordinator presentation controller for UC03 and UC04. */
public final class RequestReviewViewController {
    public enum Mode { VERIFICATION, PRIORITY }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());
    private final SessionManager sessions;
    private final RequestReviewController reviews;
    private final Mode mode;

    @FXML private Label headingLabel;
    @FXML private Label introLabel;
    @FXML private TableView<AssistanceRequest> requestTable;
    @FXML private TableColumn<AssistanceRequest, String> requestIdColumn;
    @FXML private TableColumn<AssistanceRequest, String> submittedColumn;
    @FXML private TableColumn<AssistanceRequest, String> statusColumn;
    @FXML private TableColumn<AssistanceRequest, String> priorityColumn;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private VBox emptyState;
    @FXML private Label emptyStateLabel;
    @FXML private VBox errorState;
    @FXML private Label errorLabel;
    @FXML private VBox detailPlaceholder;
    @FXML private VBox detailContent;
    @FXML private Label detailIdLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailLocationLabel;
    @FXML private Label detailDescriptionLabel;
    @FXML private Label detailAssistanceLabel;
    @FXML private Label detailAffectedLabel;
    @FXML private Label duplicateLabel;
    @FXML private VBox verificationContextBox;
    @FXML private Label requesterNameLabel;
    @FXML private Label requesterContactLabel;
    @FXML private javafx.scene.control.ListView<String> evidenceList;
    @FXML private javafx.scene.control.ListView<String> duplicateList;
    @FXML private VBox verificationActions;
    @FXML private TextArea decisionReasonArea;
    @FXML private VBox priorityActions;
    @FXML private Label suggestedPriorityLabel;
    @FXML private ComboBox<RequestPriority> selectedPriorityBox;
    @FXML private TextArea overrideJustificationArea;
    @FXML private Label actionMessageLabel;
    @FXML private Button refreshButton;

    public RequestReviewViewController(SessionManager sessions, RequestReviewController reviews, Mode mode) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.reviews = reviews;
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @FXML
    private void initialize() {
        boolean verification = mode == Mode.VERIFICATION;
        headingLabel.setText(verification ? "Pending verification" : "Priority assessment");
        introLabel.setText(verification
                ? "Review request facts, duplicate indicators and the documented decision alternatives."
                : "Apply the consistent suggestion rules, then record the coordinator's final priority.");
        verificationActions.setVisible(verification);
        verificationActions.setManaged(verification);
        verificationContextBox.setVisible(verification);
        verificationContextBox.setManaged(verification);
        priorityActions.setVisible(!verification);
        priorityActions.setManaged(!verification);
        selectedPriorityBox.setItems(FXCollections.observableArrayList(RequestPriority.values()));
        requestIdColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().id().toString()));
        submittedColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(DATE_TIME.format(cell.getValue().submittedAt())));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().status().displayName()));
        priorityColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().priority() == null ? "Not set" : cell.getValue().priority().name()));
        requestTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> showDetail(selected));
        loadRequests();
    }

    @FXML
    private void loadRequests() {
        if (sessions.authSession().isEmpty() || reviews == null) {
            refreshButton.setDisable(true);
            showEmpty("Demonstration mode contains no operational coordinator queue.");
            return;
        }
        setLoading(true);
        AuthSession session = sessions.authSession().orElseThrow();
        Task<List<AssistanceRequest>> task = new Task<>() {
            @Override protected List<AssistanceRequest> call() {
                return mode == Mode.VERIFICATION
                        ? reviews.loadVerificationQueue(session) : reviews.loadPriorityQueue(session);
            }
        };
        task.setOnSucceeded(event -> {
            setLoading(false);
            requestTable.getItems().setAll(task.getValue());
            if (task.getValue().isEmpty()) showEmpty("No requests currently require this decision.");
            else { showOnly(requestTable); requestTable.getSelectionModel().selectFirst(); }
        });
        task.setOnFailed(event -> {
            setLoading(false);
            errorLabel.setText(message(task.getException(), "The coordinator queue could not be loaded."));
            showOnly(errorState);
        });
        start(task, "aafatlink-review-queue");
    }

    @FXML private void verifySelected() { runVerification(Action.VERIFY); }
    @FXML private void returnSelected() { runVerification(Action.RETURN); }
    @FXML private void rejectSelected() { runVerification(Action.REJECT); }

    private void runVerification(Action action) {
        AssistanceRequest request = selected();
        if (request == null || sessions.authSession().isEmpty()) return;
        String reason = decisionReasonArea.getText();
        runDecision(new Task<>() {
            @Override protected Object call() {
                return switch (action) {
                    case VERIFY -> reviews.verify(sessions.authSession().orElseThrow(), request);
                    case RETURN -> reviews.requestAdditionalInformation(sessions.authSession().orElseThrow(), request, reason);
                    case REJECT -> reviews.reject(sessions.authSession().orElseThrow(), request, reason);
                };
            }
        }, "Verification decision recorded.");
    }

    @FXML
    private void setPriority() {
        AssistanceRequest request = selected();
        RequestPriority selected = selectedPriorityBox.getValue();
        if (request == null || sessions.authSession().isEmpty() || selected == null) {
            showActionMessage("Select a priority level.", true);
            return;
        }
        String justification = overrideJustificationArea.getText();
        runDecision(new Task<>() {
            @Override protected Object call() {
                return reviews.setPriority(sessions.authSession().orElseThrow(), request, selected, justification);
            }
        }, "Priority decision recorded.");
    }

    private void runDecision(Task<Object> task, String successMessage) {
        verificationActions.setDisable(true);
        priorityActions.setDisable(true);
        showActionMessage("Saving decision…", false);
        task.setOnSucceeded(event -> { showActionMessage(successMessage, false); loadRequests(); });
        task.setOnFailed(event -> {
            verificationActions.setDisable(false);
            priorityActions.setDisable(false);
            showActionMessage(message(task.getException(), "The decision was not saved. Refresh and retry."), true);
        });
        start(task, "aafatlink-review-decision");
    }

    private void showDetail(AssistanceRequest request) {
        boolean available = request != null;
        detailPlaceholder.setVisible(!available); detailPlaceholder.setManaged(!available);
        detailContent.setVisible(available); detailContent.setManaged(available);
        if (!available) return;
        detailIdLabel.setText(request.id().toString());
        detailStatusLabel.setText(request.status().displayName());
        detailLocationLabel.setText(request.details().locationAddress());
        detailDescriptionLabel.setText(request.details().description());
        detailAssistanceLabel.setText(request.details().requiredAssistance());
        detailAffectedLabel.setText(request.details().affectedPersonDetails());
        duplicateLabel.setText(request.possibleDuplicate() ? "Possible duplicate - loading related records…" : "No duplicate flag");
        if (mode == Mode.VERIFICATION) loadVerificationContext(request);
        decisionReasonArea.clear(); overrideJustificationArea.clear();
        if (mode == Mode.PRIORITY) {
            RequestPriority suggestion = reviews.suggestPriority(request);
            suggestedPriorityLabel.setText(suggestion.name());
            selectedPriorityBox.setValue(suggestion);
        }
        verificationActions.setDisable(false); priorityActions.setDisable(false);
        showActionMessage("", false);
    }

    private void loadVerificationContext(AssistanceRequest request) {
        requesterNameLabel.setText("Loading…");
        requesterContactLabel.setText("Loading…");
        evidenceList.setItems(FXCollections.observableArrayList("Loading committed evidence…"));
        duplicateList.setItems(FXCollections.observableArrayList("Loading related requests…"));
        if (sessions.authSession().isEmpty() || reviews == null) {
            requesterNameLabel.setText("Demonstration data unavailable");
            requesterContactLabel.setText("—");
            evidenceList.setItems(FXCollections.observableArrayList("No operational evidence loaded"));
            duplicateList.setItems(FXCollections.observableArrayList("No operational records loaded"));
            return;
        }
        Task<java.util.Optional<RequestReviewDetails>> task = new Task<>() {
            @Override protected java.util.Optional<RequestReviewDetails> call() {
                return reviews.getRequestReviewDetails(sessions.authSession().orElseThrow(), request.id());
            }
        };
        task.setOnSucceeded(event -> {
            if (selected() == null || !selected().id().equals(request.id())) return;
            RequestReviewDetails details = task.getValue().orElse(null);
            if (details == null) {
                showActionMessage("The request changed or is no longer available.", true);
                return;
            }
            requesterNameLabel.setText(details.requesterName());
            requesterContactLabel.setText(details.requesterContact());
            var evidenceRows = details.evidence().stream()
                    .map(item -> item.fileName() + " · " + item.contentType() + " · " + formatBytes(item.sizeBytes()))
                    .toList();
            evidenceList.setItems(FXCollections.observableArrayList(evidenceRows.isEmpty()
                    ? List.of("No evidence attached") : evidenceRows));
            var duplicateRows = details.possibleDuplicates().stream()
                    .map(match -> match.requestId() + " · " + match.status().displayName()
                            + match.optionalPriority().map(priority -> " · " + priority.name()).orElse(""))
                    .toList();
            duplicateList.setItems(FXCollections.observableArrayList(duplicateRows.isEmpty()
                    ? List.of("No related open requests") : duplicateRows));
            duplicateLabel.setText(duplicateRows.isEmpty()
                    ? "No related open request matched the duplicate rule"
                    : "Possible duplicate - compare " + duplicateRows.size() + " related request(s) before deciding");
        });
        task.setOnFailed(event -> {
            if (selected() == null || !selected().id().equals(request.id())) return;
            requesterNameLabel.setText("Unavailable"); requesterContactLabel.setText("Unavailable");
            evidenceList.setItems(FXCollections.observableArrayList("Evidence could not be loaded"));
            duplicateList.setItems(FXCollections.observableArrayList("Related requests could not be loaded"));
            showActionMessage(message(task.getException(), "Review details could not be loaded."), true);
        });
        start(task, "aafatlink-verification-details");
    }

    private AssistanceRequest selected() { return requestTable.getSelectionModel().getSelectedItem(); }
    private void setLoading(boolean value) { refreshButton.setDisable(value); if (value) showOnly(loadingIndicator); }
    private void showEmpty(String text) { emptyStateLabel.setText(text); showOnly(emptyState); }
    private void showOnly(javafx.scene.Node visible) {
        for (javafx.scene.Node node : List.of(requestTable, loadingIndicator, emptyState, errorState)) {
            boolean selected = node == visible; node.setVisible(selected); node.setManaged(selected);
        }
    }
    private void showActionMessage(String text, boolean error) {
        actionMessageLabel.setText(text);
        actionMessageLabel.getStyleClass().removeAll("form-error", "form-success");
        actionMessageLabel.getStyleClass().add(error ? "form-error" : "form-success");
    }
    private static String message(Throwable error, String fallback) {
        return error == null || error.getMessage() == null ? fallback : error.getMessage();
    }
    private static void start(Task<?> task, String name) {
        Thread worker = new Thread(task, name); worker.setDaemon(true); worker.start();
    }
    private static String formatBytes(long bytes) {
        return bytes < 1024 * 1024 ? Math.max(1, bytes / 1024) + " KB"
                : String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
    private enum Action { VERIFY, RETURN, REJECT }
}
