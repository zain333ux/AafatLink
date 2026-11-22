package pk.edu.nu.aafatlink.ui.volunteer;

import java.io.File;
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
import javafx.stage.FileChooser;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.volunteer.VolunteerController;
import pk.edu.nu.aafatlink.domain.assignment.ClosureDecision;
import pk.edu.nu.aafatlink.domain.assignment.ProgressUpdate;
import pk.edu.nu.aafatlink.domain.assignment.TaskContext;
import pk.edu.nu.aafatlink.domain.evidence.EvidenceAttachment;
import pk.edu.nu.aafatlink.service.evidence.EvidenceFileValidator;

/** JavaFX presentation controller for UC09 progress and UC10 completion. */
public final class TaskProgressViewController {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM, HH:mm")
            .withZone(ZoneId.systemDefault());
    private final SessionManager sessions;
    private final VolunteerController volunteers;
    private final EvidenceFileValidator evidenceValidator;
    private EvidenceAttachment evidence;

    @FXML private TableView<TaskContext> taskTable;
    @FXML private TableColumn<TaskContext, String> assistanceColumn;
    @FXML private TableColumn<TaskContext, String> locationColumn;
    @FXML private TableColumn<TaskContext, String> statusColumn;
    @FXML private VBox emptyState;
    @FXML private VBox detailPlaceholder;
    @FXML private ScrollPane detailScroll;
    @FXML private Label requestIdLabel;
    @FXML private Label assistanceLabel;
    @FXML private Label locationLabel;
    @FXML private Label assignmentStatusLabel;
    @FXML private Label reworkLabel;
    @FXML private VBox reworkBox;
    @FXML private ListView<String> progressList;
    @FXML private TextArea progressNoteArea;
    @FXML private TextField percentageField;
    @FXML private TextArea completionRemarksArea;
    @FXML private Label evidenceNameLabel;
    @FXML private Label messageLabel;
    @FXML private Button refreshButton;
    @FXML private Button recordButton;
    @FXML private Button completeButton;

    public TaskProgressViewController(SessionManager sessions, VolunteerController volunteers,
            EvidenceFileValidator evidenceValidator) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.volunteers = volunteers;
        this.evidenceValidator = Objects.requireNonNull(evidenceValidator, "evidenceValidator");
    }

    @FXML
    private void initialize() {
        taskTable.setPlaceholder(new Region());
        assistanceColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().assignmentContext().request().details().requiredAssistance()));
        locationColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().assignmentContext().request().details().locationAddress()));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().assignmentContext().assignment().status().name().replace('_', ' ')));
        taskTable.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (selected != null) showDetails(selected);
        });
        updateEvidenceDisplay();
        loadTasks();
    }

    @FXML
    private void loadTasks() {
        loadTasks(null, false);
    }

    private void loadTasks(String notice, boolean noticeIsError) {
        if (sessions.authSession().isEmpty() || volunteers == null) {
            taskTable.setItems(FXCollections.observableArrayList());
            showEmpty("Demonstration mode does not load operational tasks.");
            setBusy(true);
            return;
        }
        setBusy(true);
        showMessage("Loading active tasks…", false);
        Task<List<TaskContext>> task = new Task<>() {
            @Override protected List<TaskContext> call() {
                return volunteers.getActiveTasks(sessions.authSession().orElseThrow());
            }
        };
        task.setOnSucceeded(event -> {
            taskTable.setItems(FXCollections.observableArrayList(task.getValue()));
            setBusy(false);
            if (task.getValue().isEmpty()) showEmpty(notice == null
                    ? "No accepted or in-progress task needs an update." : notice + " The latest task list is empty.");
            else {
                emptyState.setVisible(false);
                emptyState.setManaged(false);
                taskTable.getSelectionModel().selectFirst();
                showMessage(notice == null ? "Select a task to record progress or submit completion." : notice,
                        notice != null && noticeIsError);
            }
        });
        task.setOnFailed(event -> {
            setBusy(false);
            showEmpty("Active tasks could not be loaded. Use Refresh to retry.");
            showMessage(message(task.getException(), "Active tasks could not be loaded."), true);
        });
        start(task, "aafatlink-task-load");
    }

    @FXML
    private void recordProgress() {
        TaskContext selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || sessions.authSession().isEmpty()) return;
        Integer percentage;
        try {
            percentage = percentageField.getText().isBlank()
                    ? null : Integer.valueOf(percentageField.getText().trim());
        } catch (NumberFormatException exception) {
            showMessage("Estimated completion must be a whole number from 0 to 99.", true);
            return;
        }
        String progressNote = progressNoteArea.getText();
        setBusy(true);
        showMessage("Recording progress…", false);
        Integer submittedPercentage = percentage;
        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                volunteers.recordProgress(sessions.authSession().orElseThrow(),
                        selected.assignmentContext().assignment().id(), progressNote,
                        submittedPercentage);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            progressNoteArea.clear();
            percentageField.clear();
            showMessage("Progress recorded. The task and request are now In Progress.", false);
            loadTasks();
        });
        task.setOnFailed(event -> {
            String failure = message(task.getException(), "Progress was not recorded.");
            loadTasks(failure + " The task list was refreshed.", true);
        });
        start(task, "aafatlink-progress-save");
    }

    @FXML
    private void chooseEvidence() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose completion evidence");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Supported evidence (JPG, PNG, PDF)", "*.jpg", "*.jpeg", "*.png", "*.pdf"));
        File selected = chooser.showOpenDialog(completeButton.getScene().getWindow());
        if (selected == null) return;
        try {
            evidence = evidenceValidator.validate(selected.toPath());
            showMessage("Completion evidence is ready for secure upload.", false);
        } catch (IllegalArgumentException exception) {
            evidence = null;
            showMessage(exception.getMessage(), true);
        }
        updateEvidenceDisplay();
    }

    @FXML
    private void removeEvidence() {
        evidence = null;
        updateEvidenceDisplay();
    }

    @FXML
    private void completeTask() {
        TaskContext selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || sessions.authSession().isEmpty()) return;
        String completionRemarks = completionRemarksArea.getText();
        setBusy(true);
        showMessage("Submitting completion for coordinator review…", false);
        EvidenceAttachment submittedEvidence = evidence;
        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                if (submittedEvidence == null) {
                    volunteers.completeTask(sessions.authSession().orElseThrow(),
                            selected.assignmentContext().assignment().id(), completionRemarks);
                } else {
                    volunteers.completeTask(sessions.authSession().orElseThrow(),
                            selected.assignmentContext().assignment().id(), completionRemarks,
                            submittedEvidence);
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            evidence = null;
            completionRemarksArea.clear();
            updateEvidenceDisplay();
            showMessage("Completion submitted. The request is Awaiting Closure Review.", false);
            loadTasks();
        });
        task.setOnFailed(event -> {
            String failure = message(task.getException(), "Completion was not submitted.");
            loadTasks(failure + " The task list was refreshed.", true);
        });
        start(task, "aafatlink-task-complete");
    }

    private void showDetails(TaskContext context) {
        detailPlaceholder.setVisible(false);
        detailPlaceholder.setManaged(false);
        detailScroll.setVisible(true);
        detailScroll.setManaged(true);
        requestIdLabel.setText(context.assignmentContext().request().id().toString());
        assistanceLabel.setText(context.assignmentContext().request().details().requiredAssistance());
        locationLabel.setText(context.assignmentContext().request().details().locationAddress());
        assignmentStatusLabel.setText(context.assignmentContext().assignment().status().name().replace('_', ' '));
        List<String> entries = context.progressHistory().stream().map(this::progressLabel).toList();
        progressList.setItems(FXCollections.observableArrayList(entries));
        if (entries.isEmpty()) progressList.setPlaceholder(new Label("No progress update has been recorded yet."));
        boolean rework = context.latestReview().filter(review ->
                review.decision() == ClosureDecision.RETURNED_FOR_REWORK).isPresent();
        reworkBox.setVisible(rework);
        reworkBox.setManaged(rework);
        reworkLabel.setText(rework ? context.latestReview().orElseThrow().remarks().orElse("Rework requested.") : "");
    }

    private String progressLabel(ProgressUpdate update) {
        String percent = update.percentage().map(value -> value + "% · ").orElse("");
        return TIME.format(update.recordedAt()) + "  " + percent + update.progressNote();
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

    private void updateEvidenceDisplay() {
        evidenceNameLabel.setText(evidence == null ? "No evidence selected (optional)"
                : evidence.fileName() + " · " + String.format("%.1f MB", evidence.sizeBytes() / 1048576.0));
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        recordButton.setDisable(busy || sessions.authSession().isEmpty());
        completeButton.setDisable(busy || sessions.authSession().isEmpty());
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
