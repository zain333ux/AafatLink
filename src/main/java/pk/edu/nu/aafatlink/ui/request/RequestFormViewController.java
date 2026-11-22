package pk.edu.nu.aafatlink.ui.request;

import java.io.File;
import java.util.List;
import java.util.Objects;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.request.RequestController;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.evidence.EvidenceAttachment;
import pk.edu.nu.aafatlink.service.evidence.EvidenceFileValidator;

/** JavaFX presentation controller for UC01 submission and same-ID resubmission. */
public final class RequestFormViewController {
    private final SessionManager sessions;
    private final RequestController requests;
    private final EvidenceFileValidator evidenceValidator;
    private final Runnable openMyRequests;
    private AssistanceRequest resubmission;
    private EvidenceAttachment evidence;
    private boolean coordinatorEntry;

    @FXML private Label formTitleLabel;
    @FXML private Label formIntroLabel;
    @FXML private Label returnReasonLabel;
    @FXML private VBox returnReasonBox;
    @FXML private javafx.scene.layout.HBox coordinatorCallerBox;
    @FXML private TextField requesterNameField;
    @FXML private TextField requesterContactField;
    @FXML private ComboBox<DisasterType> disasterTypeBox;
    @FXML private VBox otherTypeBox;
    @FXML private TextField otherTypeField;
    @FXML private TextField locationField;
    @FXML private TextArea descriptionField;
    @FXML private TextArea assistanceField;
    @FXML private TextArea affectedPeopleField;
    @FXML private Label evidenceNameLabel;
    @FXML private Label evidenceMessageLabel;
    @FXML private Button chooseEvidenceButton;
    @FXML private Button removeEvidenceButton;
    @FXML private Button submitButton;
    @FXML private Button viewRequestsButton;
    @FXML private Label formMessageLabel;
    @FXML private Label accessNoticeLabel;

    public RequestFormViewController(SessionManager sessions, RequestController requests,
            EvidenceFileValidator evidenceValidator, Runnable openMyRequests) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.requests = requests;
        this.evidenceValidator = Objects.requireNonNull(evidenceValidator, "evidenceValidator");
        this.openMyRequests = Objects.requireNonNull(openMyRequests, "openMyRequests");
    }

    @FXML
    private void initialize() {
        disasterTypeBox.setItems(FXCollections.observableArrayList(DisasterType.values()));
        disasterTypeBox.setConverter(new StringConverter<>() {
            @Override public String toString(DisasterType type) {
                return type == null ? "" : type.displayName();
            }
            @Override public DisasterType fromString(String value) {
                return null;
            }
        });
        disasterTypeBox.valueProperty().addListener((observable, previous, selected) -> updateOtherTypeVisibility());
        disasterTypeBox.getSelectionModel().select(DisasterType.FLOOD);
        updateOtherTypeVisibility();
        updateEvidenceDisplay();
        loadDisasterTypes();

        coordinatorEntry = sessions.currentUser().orElseThrow().role() == UserRole.COORDINATOR;
        coordinatorCallerBox.setVisible(coordinatorEntry);
        coordinatorCallerBox.setManaged(coordinatorEntry);
        if (coordinatorEntry) {
            formTitleLabel.setText("Record a phone assistance request");
            formIntroLabel.setText("Capture the caller's details and request facts. Your Coordinator identity is stored separately as the recorder.");
            submitButton.setText("Record request");
            accessNoticeLabel.setText("Read-only demonstration: sign in with a connected Coordinator account to record operational data.");
        }

        boolean authenticated = sessions.authSession().isPresent() && requests != null;
        submitButton.setDisable(!authenticated);
        accessNoticeLabel.setVisible(!authenticated);
        accessNoticeLabel.setManaged(!authenticated);
    }

    private void loadDisasterTypes() {
        if (sessions.authSession().isEmpty() || requests == null) return;
        Task<List<DisasterType>> task = new Task<>() {
            @Override protected List<DisasterType> call() {
                return requests.getDisasterTypes(sessions.authSession().orElseThrow());
            }
        };
        task.setOnSucceeded(event -> {
            List<DisasterType> types = task.getValue();
            if (types.isEmpty()) return;
            DisasterType selected = disasterTypeBox.getValue();
            disasterTypeBox.setItems(FXCollections.observableArrayList(types));
            if (types.contains(selected)) disasterTypeBox.setValue(selected);
            else disasterTypeBox.getSelectionModel().selectFirst();
        });
        Thread worker = new Thread(task, "aafatlink-disaster-types");
        worker.setDaemon(true);
        worker.start();
    }

    public void prepareResubmission(AssistanceRequest request) {
        resubmission = Objects.requireNonNull(request, "request");
        if (!request.status().canRequesterResubmit()) {
            throw new IllegalArgumentException("Only a request returned for more information can be resubmitted.");
        }
        AssistanceRequestDraft details = request.details();
        formTitleLabel.setText("Update returned request");
        formIntroLabel.setText("Amend the details below. AafatLink will keep request ID " + request.id() + ".");
        returnReasonLabel.setText(request.coordinatorReason() == null
                ? "The coordinator requested more information."
                : request.coordinatorReason());
        returnReasonBox.setVisible(true);
        returnReasonBox.setManaged(true);
        disasterTypeBox.setValue(details.disasterType());
        otherTypeField.setText(details.disasterTypeOther());
        locationField.setText(details.locationAddress());
        descriptionField.setText(details.description());
        assistanceField.setText(details.requiredAssistance());
        affectedPeopleField.setText(details.affectedPersonDetails());
        submitButton.setText("Update and resubmit");
        updateOtherTypeVisibility();
    }

    @FXML
    private void chooseEvidence() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose supporting evidence");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Supported evidence (JPG, PNG, PDF)", "*.jpg", "*.jpeg", "*.png", "*.pdf"));
        File selected = chooser.showOpenDialog(submitButton.getScene().getWindow());
        if (selected == null) {
            return;
        }
        try {
            evidence = evidenceValidator.validate(selected.toPath());
            setEvidenceMessage("Ready for secure upload with this request.", false);
        } catch (IllegalArgumentException exception) {
            evidence = null;
            setEvidenceMessage(exception.getMessage(), true);
        }
        updateEvidenceDisplay();
    }

    @FXML
    private void removeEvidence() {
        evidence = null;
        setEvidenceMessage("Evidence is optional. JPG, PNG and PDF files up to 5 MB are accepted.", false);
        updateEvidenceDisplay();
    }

    @FXML
    private void submit() {
        if (sessions.authSession().isEmpty() || requests == null) {
            showMessage("Sign in with a connected Requester account to submit a request.", true);
            return;
        }
        AssistanceRequestDraft draft;
        try {
            draft = new AssistanceRequestDraft(disasterTypeBox.getValue(), otherTypeField.getText(),
                    locationField.getText(), descriptionField.getText(), assistanceField.getText(),
                    affectedPeopleField.getText());
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), true);
            return;
        }

        AuthSession session = sessions.authSession().orElseThrow();
        EvidenceAttachment submittedEvidence = evidence;
        String requesterName = requesterNameField.getText();
        String requesterContact = requesterContactField.getText();
        setBusy(true);
        showMessage(resubmission == null ? "Submitting request…" : "Resubmitting request…", false);
        Task<RequestSubmissionResult> task = new Task<>() {
            @Override protected RequestSubmissionResult call() {
                if (coordinatorEntry) {
                    return submittedEvidence == null
                            ? requests.recordCoordinatorRequest(session, requesterName, requesterContact, draft)
                            : requests.recordCoordinatorRequest(session, requesterName, requesterContact, draft,
                                    submittedEvidence);
                }
                if (resubmission == null) {
                    return submittedEvidence == null
                            ? requests.submitRequest(session, draft)
                            : requests.submitRequest(session, draft, submittedEvidence);
                }
                return submittedEvidence == null
                        ? requests.resubmitRequest(session, resubmission.id(), resubmission.version(), draft)
                        : requests.resubmitRequest(session, resubmission.id(), resubmission.version(), draft,
                                submittedEvidence);
            }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            RequestSubmissionResult result = task.getValue();
            String duplicateNote = result.possibleDuplicate()
                    ? " It was flagged as a possible duplicate for coordinator review." : "";
            showMessage("Request submitted successfully. Request ID: " + result.requestId() + "." + duplicateNote, false);
            viewRequestsButton.setVisible(!coordinatorEntry);
            viewRequestsButton.setManaged(!coordinatorEntry);
            submitButton.setDisable(true);
        });
        task.setOnFailed(event -> {
            setBusy(false);
            Throwable failure = task.getException();
            showMessage(failure == null || failure.getMessage() == null
                    ? "The request was not submitted. Your entries are still here; please retry."
                    : failure.getMessage() + " Your entries are still here; please retry.", true);
        });
        Thread worker = new Thread(task, "aafatlink-request-submit");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void openMyRequests() {
        openMyRequests.run();
    }

    private void updateOtherTypeVisibility() {
        boolean other = disasterTypeBox.getValue() == DisasterType.OTHER;
        otherTypeBox.setVisible(other);
        otherTypeBox.setManaged(other);
        if (!other) {
            otherTypeField.clear();
        }
    }

    private void updateEvidenceDisplay() {
        evidenceNameLabel.setText(evidence == null ? "No file selected"
                : evidence.fileName() + " · " + formatBytes(evidence.sizeBytes()));
        removeEvidenceButton.setVisible(evidence != null);
        removeEvidenceButton.setManaged(evidence != null);
    }

    private void setEvidenceMessage(String message, boolean error) {
        evidenceMessageLabel.setText(message);
        evidenceMessageLabel.getStyleClass().removeAll("form-error", "helper-text");
        evidenceMessageLabel.getStyleClass().add(error ? "form-error" : "helper-text");
    }

    private void showMessage(String message, boolean error) {
        formMessageLabel.setText(message);
        formMessageLabel.getStyleClass().removeAll("form-error", "form-success");
        formMessageLabel.getStyleClass().add(error ? "form-error" : "form-success");
    }

    private void setBusy(boolean busy) {
        submitButton.setDisable(busy);
        disasterTypeBox.setDisable(busy);
        otherTypeField.setDisable(busy);
        locationField.setDisable(busy);
        descriptionField.setDisable(busy);
        assistanceField.setDisable(busy);
        affectedPeopleField.setDisable(busy);
        requesterNameField.setDisable(busy);
        requesterContactField.setDisable(busy);
        chooseEvidenceButton.setDisable(busy);
        removeEvidenceButton.setDisable(busy || evidence == null);
    }

    private static String formatBytes(long bytes) {
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
