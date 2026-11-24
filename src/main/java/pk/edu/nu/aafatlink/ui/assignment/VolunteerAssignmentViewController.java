package pk.edu.nu.aafatlink.ui.assignment;

import pk.edu.nu.aafatlink.ui.shell.DashboardSelection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.assignment.AssignmentController;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentContext;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter;
import pk.edu.nu.aafatlink.domain.assignment.VolunteerCandidate;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.volunteer.ServiceArea;
import pk.edu.nu.aafatlink.domain.volunteer.Skill;

/** Coordinator JavaFX presentation controller for UC05. */
public final class VolunteerAssignmentViewController implements DashboardSelection.Destination {
    private final DashboardSelection selection = new DashboardSelection();

    @Override
    public DashboardSelection initialSelection() { return selection; }

    private final SessionManager sessions;
    private final AssignmentController assignments;

    @FXML private TableView<AssistanceRequest> requestTable;
    @FXML private TableColumn<AssistanceRequest, String> requestIdColumn;
    @FXML private TableColumn<AssistanceRequest, String> requestPriorityColumn;
    @FXML private TableColumn<AssistanceRequest, String> requestLocationColumn;
    @FXML private ComboBox<Skill> skillBox;
    @FXML private ComboBox<ServiceArea> serviceAreaBox;
    @FXML private Label assistanceLabel;
    @FXML private Label situationLabel;
    @FXML private TableView<VolunteerCandidate> candidateTable;
    @FXML private TableColumn<VolunteerCandidate, String> candidateNameColumn;
    @FXML private TableColumn<VolunteerCandidate, String> candidateSkillsColumn;
    @FXML private TableColumn<VolunteerCandidate, String> candidateAreasColumn;
    @FXML private TableColumn<VolunteerCandidate, String> candidateWorkloadColumn;
    @FXML private Label assignmentMessageLabel;
    @FXML private Button findButton;
    @FXML private Button assignButton;
    @FXML private Button refreshButton;
    @FXML private TableView<AssignmentContext> activeTable;
    @FXML private TableColumn<AssignmentContext, String> activeRequestColumn;
    @FXML private TableColumn<AssignmentContext, String> activeVolunteerColumn;
    @FXML private TableColumn<AssignmentContext, String> activeStatusColumn;
    @FXML private TextArea reassignmentReasonArea;
    @FXML private Label reassignmentMessageLabel;
    @FXML private Button reassignButton;

    public VolunteerAssignmentViewController(SessionManager sessions, AssignmentController assignments) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.assignments = assignments;
    }

    @FXML
    private void initialize() {
        requestIdColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(shortId(cell.getValue().id().toString())));
        requestPriorityColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().priority().name()));
        requestLocationColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().details().locationAddress()));
        candidateNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().displayName()));
        candidateSkillsColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().profile().skills()
                .stream().map(Skill::name).collect(Collectors.joining(", "))));
        candidateAreasColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().profile().serviceAreas()
                .stream().map(ServiceArea::name).collect(Collectors.joining(", "))));
        candidateWorkloadColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                Long.toString(cell.getValue().activeAssignmentCount())));
        activeRequestColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(shortId(cell.getValue().request().id().toString())));
        activeVolunteerColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                shortId(cell.getValue().assignment().volunteerAccountId().toString())));
        activeStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().assignment().status().name()));
        requestTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> showRequest(selected));
        candidateTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> assignButton.setDisable(selected == null));
        activeTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> reassignButton.setDisable(selected == null));
        loadData();
    }

    @FXML
    private void loadData() {
        loadData(null, false);
    }

    private void loadData(String notice, boolean noticeIsError) {
        if (sessions.authSession().isEmpty() || assignments == null) {
            setMainDisabled(true);
            showAssignmentMessage("Demonstration mode contains no operational assignment data.", false);
            return;
        }
        setMainDisabled(true);
        candidateTable.getItems().clear();
        assignButton.setDisable(true);
        Task<AssignmentData> task = new Task<>() {
            @Override protected AssignmentData call() {
                var session = sessions.authSession().orElseThrow();
                return new AssignmentData(assignments.getAssignableRequests(session), assignments.getSkills(session),
                        assignments.getServiceAreas(session), assignments.getActiveAssignments(session));
            }
        };
        task.setOnSucceeded(event -> {
            AssignmentData data = task.getValue();
            requestTable.getItems().setAll(data.requests());
            skillBox.setItems(FXCollections.observableArrayList(data.skills()));
            serviceAreaBox.setItems(FXCollections.observableArrayList(data.areas()));
            activeTable.getItems().setAll(data.activeAssignments());
            if (!data.requests().isEmpty()) selection.select(requestTable, AssistanceRequest::id);
            if (!data.skills().isEmpty()) skillBox.getSelectionModel().selectFirst();
            if (!data.areas().isEmpty()) serviceAreaBox.getSelectionModel().selectFirst();
            setMainDisabled(false);
            assignButton.setDisable(candidateTable.getSelectionModel().getSelectedItem() == null);
            reassignButton.setDisable(activeTable.getSelectionModel().getSelectedItem() == null);
            String defaultMessage = data.requests().isEmpty()
                    ? "No verified prioritized request currently needs assignment."
                    : "Select requirements to find eligible volunteers.";
            showAssignmentMessage(notice == null ? defaultMessage : notice, notice != null && noticeIsError);
        });
        task.setOnFailed(event -> {
            setMainDisabled(false);
            showAssignmentMessage(message(task.getException(), "Assignment data could not be loaded."), true);
        });
        start(task, "aafatlink-assignment-data");
    }

    @FXML
    private void findCandidates() {
        AssistanceRequest request = requestTable.getSelectionModel().getSelectedItem();
        if (request == null || skillBox.getValue() == null || serviceAreaBox.getValue() == null
                || sessions.authSession().isEmpty()) {
            showAssignmentMessage("Select a request, required skill and service area.", true);
            return;
        }
        AssignmentFilter filter = filter();
        findButton.setDisable(true);
        Task<List<VolunteerCandidate>> task = new Task<>() {
            @Override protected List<VolunteerCandidate> call() {
                return assignments.findEligibleVolunteers(sessions.authSession().orElseThrow(), request, filter);
            }
        };
        task.setOnSucceeded(event -> {
            findButton.setDisable(false);
            candidateTable.getItems().setAll(task.getValue());
            if (!task.getValue().isEmpty()) candidateTable.getSelectionModel().selectFirst();
            showAssignmentMessage(task.getValue().isEmpty()
                    ? "No approved, available and nonconflicting volunteer matches these requirements."
                    : task.getValue().size() + " eligible volunteer(s) found.", false);
        });
        task.setOnFailed(event -> {
            findButton.setDisable(false);
            showAssignmentMessage(message(task.getException(), "Eligible volunteers could not be loaded."), true);
        });
        start(task, "aafatlink-candidate-search");
    }

    @FXML
    private void assignSelected() {
        AssistanceRequest request = requestTable.getSelectionModel().getSelectedItem();
        VolunteerCandidate candidate = candidateTable.getSelectionModel().getSelectedItem();
        if (request == null || candidate == null || sessions.authSession().isEmpty()) return;
        AssignmentFilter submittedFilter = filter();
        assignButton.setDisable(true);
        Task<Object> task = new Task<>() {
            @Override protected Object call() {
                return assignments.assignVolunteer(sessions.authSession().orElseThrow(), request, candidate,
                        submittedFilter);
            }
        };
        task.setOnSucceeded(event -> {
            showAssignmentMessage("Volunteer assigned. The assignment is pending acceptance.", false);
            candidateTable.getItems().clear();
            loadData();
        });
        task.setOnFailed(event -> {
            String failure = message(task.getException(), "Volunteer was not assigned.");
            loadData(failure + " Candidate and request data were refreshed.", true);
        });
        start(task, "aafatlink-assign-volunteer");
    }

    @FXML
    private void reassignSelected() {
        AssignmentContext selected = activeTable.getSelectionModel().getSelectedItem();
        if (selected == null || sessions.authSession().isEmpty()) return;
        String reason = reassignmentReasonArea.getText();
        reassignButton.setDisable(true);
        Task<Object> task = new Task<>() {
            @Override protected Object call() {
                return assignments.reassignVolunteer(sessions.authSession().orElseThrow(),
                        selected.assignment().id(), reason);
            }
        };
        task.setOnSucceeded(event -> {
            reassignmentReasonArea.clear();
            showReassignmentMessage("Assignment released. The request is back in the verified queue with its priority preserved.", false);
            loadData();
        });
        task.setOnFailed(event -> {
            String failure = message(task.getException(), "Assignment could not be released.");
            showReassignmentMessage(failure + " Current assignment data was refreshed.", true);
            loadData();
        });
        start(task, "aafatlink-reassign-volunteer");
    }

    private AssignmentFilter filter() {
        return new AssignmentFilter(skillBox.getValue().id(), serviceAreaBox.getValue().id(), null, null);
    }
    private void showRequest(AssistanceRequest request) {
        assistanceLabel.setText(request == null ? "Select a request" : request.details().requiredAssistance());
        situationLabel.setText(request == null ? "" : request.details().description());
        candidateTable.getItems().clear();
        assignButton.setDisable(true);
    }
    private void setMainDisabled(boolean disabled) {
        refreshButton.setDisable(disabled); findButton.setDisable(disabled);
    }
    private void showAssignmentMessage(String text, boolean error) { showMessage(assignmentMessageLabel, text, error); }
    private void showReassignmentMessage(String text, boolean error) { showMessage(reassignmentMessageLabel, text, error); }
    private static void showMessage(Label label, String text, boolean error) {
        label.setText(text); label.getStyleClass().removeAll("form-error", "form-success");
        label.getStyleClass().add(error ? "form-error" : "form-success");
    }
    private static String message(Throwable error, String fallback) {
        return error == null || error.getMessage() == null ? fallback : error.getMessage();
    }
    private static String shortId(String id) { return id.substring(0, Math.min(8, id.length())).toUpperCase(); }
    private static void start(Task<?> task, String name) {
        Thread worker = new Thread(task, name); worker.setDaemon(true); worker.start();
    }
    private record AssignmentData(List<AssistanceRequest> requests, List<Skill> skills,
            List<ServiceArea> areas, List<AssignmentContext> activeAssignments) { }
}
