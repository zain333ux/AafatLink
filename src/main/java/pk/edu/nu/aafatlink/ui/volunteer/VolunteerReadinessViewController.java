package pk.edu.nu.aafatlink.ui.volunteer;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.volunteer.VolunteerController;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.volunteer.AvailabilityStatus;
import pk.edu.nu.aafatlink.domain.volunteer.ServiceArea;
import pk.edu.nu.aafatlink.domain.volunteer.Skill;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerProfile;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerReadinessChanges;

/** JavaFX presentation controller for UC07. */
public final class VolunteerReadinessViewController {
    private final SessionManager sessions;
    private final VolunteerController volunteers;

    @FXML private Label approvalStatusLabel;
    @FXML private TextField contactField;
    @FXML private ComboBox<AvailabilityStatus> availabilityBox;
    @FXML private TextArea notesArea;
    @FXML private ListView<Skill> skillsList;
    @FXML private ListView<ServiceArea> serviceAreasList;
    @FXML private Label messageLabel;
    @FXML private Button saveButton;
    @FXML private Button refreshButton;

    public VolunteerReadinessViewController(SessionManager sessions, VolunteerController volunteers) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.volunteers = volunteers;
    }

    @FXML
    private void initialize() {
        availabilityBox.setItems(FXCollections.observableArrayList(AvailabilityStatus.values()));
        skillsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        serviceAreasList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        loadProfile();
    }

    @FXML
    private void loadProfile() {
        if (sessions.authSession().isEmpty() || volunteers == null) {
            setDisabled(true);
            approvalStatusLabel.setText("Demo only");
            showMessage("Demonstration mode does not change a readiness profile.", false);
            return;
        }
        setDisabled(true);
        showMessage("Loading readiness profile…", false);
        AuthSession session = sessions.authSession().orElseThrow();
        Task<ReadinessData> task = new Task<>() {
            @Override protected ReadinessData call() {
                VolunteerProfile profile = volunteers.getReadinessProfile(session)
                        .orElseThrow(() -> new IllegalStateException("Volunteer readiness profile was not found."));
                return new ReadinessData(profile, volunteers.getSkills(session), volunteers.getServiceAreas(session));
            }
        };
        task.setOnSucceeded(event -> {
            populate(task.getValue());
            setDisabled(false);
            showMessage("Profile loaded. Approval is controlled by an administrator.", false);
        });
        task.setOnFailed(event -> {
            setDisabled(false);
            showMessage(message(task.getException(), "Readiness profile could not be loaded."), true);
        });
        start(task, "aafatlink-readiness-load");
    }

    @FXML
    private void saveProfile() {
        if (sessions.authSession().isEmpty() || volunteers == null) return;
        try {
            Set<Long> skillIds = skillsList.getSelectionModel().getSelectedItems().stream()
                    .map(Skill::id).collect(Collectors.toSet());
            Set<Long> areaIds = serviceAreasList.getSelectionModel().getSelectedItems().stream()
                    .map(ServiceArea::id).collect(Collectors.toSet());
            VolunteerReadinessChanges changes = new VolunteerReadinessChanges(contactField.getText(),
                    availabilityBox.getValue(), notesArea.getText(), null, null, skillIds, areaIds);
            setDisabled(true);
            showMessage("Saving readiness profile…", false);
            Task<VolunteerProfile> task = new Task<>() {
                @Override protected VolunteerProfile call() {
                    return volunteers.updateReadiness(sessions.authSession().orElseThrow(), changes);
                }
            };
            task.setOnSucceeded(event -> {
                setDisabled(false);
                approvalStatusLabel.setText(task.getValue().approvalStatus().displayName());
                showMessage("Readiness profile saved. Assignment search now uses these details.", false);
            });
            task.setOnFailed(event -> {
                setDisabled(false);
                showMessage(message(task.getException(), "Readiness profile was not saved."), true);
            });
            start(task, "aafatlink-readiness-save");
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), true);
        }
    }

    private void populate(ReadinessData data) {
        VolunteerProfile profile = data.profile();
        approvalStatusLabel.setText(profile.approvalStatus().displayName());
        contactField.setText(profile.contactNumber());
        availabilityBox.setValue(profile.availability());
        notesArea.setText(Objects.requireNonNullElse(profile.availabilityNotes(), ""));
        skillsList.setItems(FXCollections.observableArrayList(data.skills()));
        serviceAreasList.setItems(FXCollections.observableArrayList(data.areas()));
        selectSkills(profile.skills());
        selectAreas(profile.serviceAreas());
    }

    private void selectSkills(List<Skill> selected) {
        skillsList.getSelectionModel().clearSelection();
        for (int index = 0; index < skillsList.getItems().size(); index++) {
            long id = skillsList.getItems().get(index).id();
            if (selected.stream().anyMatch(skill -> skill.id() == id)) skillsList.getSelectionModel().select(index);
        }
    }

    private void selectAreas(List<ServiceArea> selected) {
        serviceAreasList.getSelectionModel().clearSelection();
        for (int index = 0; index < serviceAreasList.getItems().size(); index++) {
            long id = serviceAreasList.getItems().get(index).id();
            if (selected.stream().anyMatch(area -> area.id() == id)) serviceAreasList.getSelectionModel().select(index);
        }
    }

    private void setDisabled(boolean disabled) {
        saveButton.setDisable(disabled);
        refreshButton.setDisable(disabled);
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

    private record ReadinessData(VolunteerProfile profile, List<Skill> skills, List<ServiceArea> areas) { }
}
