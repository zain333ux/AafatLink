package pk.edu.nu.aafatlink.ui.volunteer;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.volunteer.VolunteerController;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;

/** JavaFX presentation controller for UC08. */
public final class VolunteerAssignmentsViewController {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM, HH:mm")
            .withZone(ZoneId.systemDefault());
    private final SessionManager sessions;
    private final VolunteerController volunteers;

    @FXML private TableView<AssignmentContext> assignmentTable;
    @FXML private TableColumn<AssignmentContext, String> assistanceColumn;
    @FXML private TableColumn<AssignmentContext, String> locationColumn;
    @FXML private TableColumn<AssignmentContext, String> priorityColumn;
    @FXML private TableColumn<AssignmentContext, String> assignedColumn;
    @FXML private VBox emptyState;
    @FXML private Label emptyMessageLabel;
    @FXML private VBox detailPlaceholder;
    @FXML private VBox detailContent;
    @FXML private Label requestIdLabel;
    @FXML private Label assistanceLabel;
    @FXML private Label locationLabel;
    @FXML private Label priorityLabel;
    @FXML private Label messageLabel;
    @FXML private TextArea declineReasonArea;
    @FXML private ComboBox<String> standardReasonBox;
    @FXML private Button acceptButton;
    @FXML private Button declineButton;
    @FXML private Button refreshButton;

    public VolunteerAssignmentsViewController(SessionManager sessions, VolunteerController volunteers) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.volunteers = volunteers;
    }

    @FXML
    private void initialize() {
        assistanceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().request().details().requiredAssistance()));
        locationColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().request().details().locationAddress()));
        priorityColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().request().priority().name()));
        assignedColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                DATE_TIME.format(cell.getValue().assignment().assignedAt())));
        assignmentTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> showDetail(selected));
        standardReasonBox.valueProperty().addListener((observable, previous, reason) -> {
            if (reason != null) declineReasonArea.setText(reason);
        });
        loadStandardReasons();
        loadAssignments();
    }

    private void loadStandardReasons() {
        if (sessions.authSession().isEmpty() || volunteers == null) {
            standardReasonBox.setDisable(true);
            return;
        }
        Task<List<String>> task = new Task<>() {
            @Override protected List<String> call() {
                return volunteers.getStandardDeclineReasons(sessions.authSession().orElseThrow());
            }
        };
        task.setOnSucceeded(event -> standardReasonBox.setItems(
                javafx.collections.FXCollections.observableArrayList(task.getValue())));
        start(task, "aafatlink-decline-reasons");
    }

    @FXML
    private void loadAssignments() {
        if (sessions.authSession().isEmpty() || volunteers == null) {
            refreshButton.setDisable(true);
            showEmpty("Demonstration mode contains no operational assignments.");
            return;
        }
        refreshButton.setDisable(true);
        showMessage("Loading assignments…", false);
        Task<List<AssignmentContext>> task = new Task<>() {
            @Override protected List<AssignmentContext> call() {
                return volunteers.getPendingAssignments(sessions.authSession().orElseThrow());
            }
        };
        task.setOnSucceeded(event -> {
            refreshButton.setDisable(false);
            assignmentTable.getItems().setAll(task.getValue());
            if (task.getValue().isEmpty()) showEmpty("No assignment currently needs your response.");
            else {
                showTable();
                assignmentTable.getSelectionModel().selectFirst();
                showMessage("", false);
            }
        });
        task.setOnFailed(event -> {
            refreshButton.setDisable(false);
            showEmpty(message(task.getException(), "Assignments could not be loaded."));
        });
        start(task, "aafatlink-assignment-list");
    }

    @FXML private void acceptSelected() { respond(true); }
    @FXML private void declineSelected() { respond(false); }

    private void respond(boolean accept) {
        AssignmentContext selected = assignmentTable.getSelectionModel().getSelectedItem();
        if (selected == null || sessions.authSession().isEmpty()) return;
        setDecisionDisabled(true);
        showMessage(accept ? "Accepting assignment…" : "Declining assignment…", false);
        Task<Object> task = new Task<>() {
            @Override protected Object call() {
                return volunteers.respondToAssignment(sessions.authSession().orElseThrow(),
                        selected.assignment().id(), accept, declineReasonArea.getText());
            }
        };
        task.setOnSucceeded(event -> {
            showMessage(accept ? "Assignment accepted." : "Assignment declined and returned for reassignment.", false);
            loadAssignments();
        });
        task.setOnFailed(event -> {
            setDecisionDisabled(false);
            showMessage(message(task.getException(), "Assignment response was not saved."), true);
        });
        start(task, "aafatlink-assignment-response");
    }

    private void showDetail(AssignmentContext context) {
        boolean present = context != null;
        detailPlaceholder.setVisible(!present); detailPlaceholder.setManaged(!present);
        detailContent.setVisible(present); detailContent.setManaged(present);
        if (!present) return;
        requestIdLabel.setText(context.request().id().toString());
        assistanceLabel.setText(context.request().details().requiredAssistance());
        locationLabel.setText(context.request().details().locationAddress());
        priorityLabel.setText(context.request().priority().name());
        declineReasonArea.clear();
        standardReasonBox.getSelectionModel().clearSelection();
        setDecisionDisabled(false);
    }

    private void setDecisionDisabled(boolean disabled) {
        acceptButton.setDisable(disabled); declineButton.setDisable(disabled);
    }
    private void showEmpty(String text) {
        emptyMessageLabel.setText(text);
        emptyState.setVisible(true); emptyState.setManaged(true);
        assignmentTable.setVisible(false); assignmentTable.setManaged(false);
        showDetail(null);
    }
    private void showTable() {
        emptyState.setVisible(false); emptyState.setManaged(false);
        assignmentTable.setVisible(true); assignmentTable.setManaged(true);
    }
    private void showMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.getStyleClass().removeAll("form-error", "form-success");
        messageLabel.getStyleClass().add(error ? "form-error" : "form-success");
    }
    private static String message(Throwable error, String fallback) {
        return error == null || error.getMessage() == null ? fallback : error.getMessage();
    }
    private static void start(Task<?> task, String name) {
        Thread worker = new Thread(task, name); worker.setDaemon(true); worker.start();
    }
}
