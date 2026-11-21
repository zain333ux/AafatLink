package pk.edu.nu.aafatlink.ui.reporting;

import java.io.File;
import java.util.Objects;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import pk.edu.nu.aafatlink.app.SessionManager;
import pk.edu.nu.aafatlink.application.assignment.AssignmentController;
import pk.edu.nu.aafatlink.application.reporting.ReportController;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;
import pk.edu.nu.aafatlink.domain.volunteer.ServiceArea;
import pk.edu.nu.aafatlink.reporting.GeneratedReport;
import pk.edu.nu.aafatlink.reporting.ReportFilters;
import pk.edu.nu.aafatlink.reporting.ReportRow;
import pk.edu.nu.aafatlink.reporting.ReportType;

/** JavaFX presentation controller for UC11. */
public final class ReportViewController {
    private final SessionManager sessions;
    private final ReportController reports;
    private final AssignmentController assignments;
    private GeneratedReport currentReport;

    @FXML private ComboBox<ReportType> reportTypeBox;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<DisasterType> disasterTypeBox;
    @FXML private ComboBox<RequestStatus> statusBox;
    @FXML private ComboBox<RequestPriority> priorityBox;
    @FXML private ComboBox<ServiceArea> serviceAreaBox;
    @FXML private Button generateButton;
    @FXML private Button exportButton;
    @FXML private TableView<ReportRow> reportTable;
    @FXML private VBox emptyState;
    @FXML private Label emptyTitle;
    @FXML private Label emptyCopy;
    @FXML private FlowPane summaryPane;
    @FXML private Label messageLabel;

    public ReportViewController(SessionManager sessions, ReportController reports,
            AssignmentController assignments) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.reports = reports;
        this.assignments = assignments;
    }

    @FXML
    private void initialize() {
        reportTable.setPlaceholder(new javafx.scene.layout.Region());
        reportTypeBox.setItems(FXCollections.observableArrayList(ReportType.values()));
        reportTypeBox.getSelectionModel().select(ReportType.REQUEST_STATUS_SUMMARY);
        disasterTypeBox.setItems(FXCollections.observableArrayList(DisasterType.values()));
        statusBox.setItems(FXCollections.observableArrayList(RequestStatus.values()));
        priorityBox.setItems(FXCollections.observableArrayList(RequestPriority.values()));
        boolean unavailable = sessions.currentUser().orElseThrow().demonstrationAccount()
                || reports == null || assignments == null;
        generateButton.setDisable(unavailable);
        exportButton.setDisable(true);
        if (unavailable) {
            showEmpty("Demonstration report preview", "Connected reporting uses committed authorized data only.");
        } else {
            loadServiceAreas();
            showEmpty("Choose a report", "Select filters, then generate one of the four documented UC11 reports.");
        }
    }

    private void loadServiceAreas() {
        Task<java.util.List<ServiceArea>> task = new Task<>() {
            @Override protected java.util.List<ServiceArea> call() {
                return assignments.getServiceAreas(session());
            }
        };
        task.setOnSucceeded(event -> serviceAreaBox.setItems(FXCollections.observableArrayList(task.getValue())));
        task.setOnFailed(event -> showMessage("Service-area filters could not be loaded; other filters remain available.", true));
        run(task, "aafatlink-report-options");
    }

    @FXML
    private void generateReport() {
        ReportType type = reportTypeBox.getValue();
        ReportFilters filters;
        try {
            filters = new ReportFilters(startDatePicker.getValue(), endDatePicker.getValue(),
                    disasterTypeBox.getValue() == null ? null : disasterTypeBox.getValue().databaseValue(),
                    statusBox.getValue(), priorityBox.getValue(),
                    serviceAreaBox.getValue() == null ? null : serviceAreaBox.getValue().id());
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage(), true);
            return;
        }
        setBusy(true);
        Task<GeneratedReport> task = new Task<>() {
            @Override protected GeneratedReport call() { return reports.generateReport(session(), type, filters); }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            currentReport = task.getValue();
            showReport(currentReport);
        });
        task.setOnFailed(event -> {
            setBusy(false);
            currentReport = null;
            reportTable.getItems().clear();
            summaryPane.getChildren().clear();
            showEmpty("Report unavailable", message(task.getException(), "No partial or misleading result was displayed."));
            showMessage(message(task.getException(), "Report generation failed."), true);
        });
        run(task, "aafatlink-generate-report");
    }

    private void showReport(GeneratedReport report) {
        reportTable.getColumns().clear();
        for (String column : report.columns()) {
            TableColumn<ReportRow, String> tableColumn = new TableColumn<>(column);
            tableColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().value(column)));
            tableColumn.setPrefWidth(Math.max(115, column.length() * 9));
            reportTable.getColumns().add(tableColumn);
        }
        reportTable.setItems(FXCollections.observableArrayList(report.rows()));
        summaryPane.getChildren().clear();
        report.summary().forEach((label, value) -> {
            Label chip = new Label(label + ": " + value);
            chip.getStyleClass().add("status-chip");
            summaryPane.getChildren().add(chip);
        });
        if (report.rows().isEmpty()) {
            showEmpty("No matching records", "Change the filters and try again. No totals were fabricated.");
            exportButton.setDisable(true);
        } else {
            emptyState.setVisible(false); emptyState.setManaged(false);
            exportButton.setDisable(false);
            showMessage(report.rows().size() + " report row(s) generated from committed data.", false);
        }
    }

    @FXML
    private void exportCsv() {
        if (currentReport == null || currentReport.rows().isEmpty()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export AafatLink report");
        chooser.setInitialFileName(currentReport.type().name().toLowerCase() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File destination = chooser.showSaveDialog(reportTable.getScene().getWindow());
        if (destination == null) return;
        try {
            reports.exportReportToCsv(currentReport, destination.toPath());
            showMessage("CSV exported to " + destination.getName() + ".", false);
        } catch (RuntimeException exception) {
            showMessage(message(exception, "The CSV could not be written; the report remains visible."), true);
        }
    }

    @FXML private void clearFilters() {
        startDatePicker.setValue(null); endDatePicker.setValue(null); disasterTypeBox.setValue(null);
        statusBox.setValue(null); priorityBox.setValue(null); serviceAreaBox.setValue(null);
    }

    private AuthSession session() { return sessions.authSession().orElseThrow(); }
    private void setBusy(boolean busy) { generateButton.setDisable(busy); exportButton.setDisable(busy || currentReport == null); }
    private void showEmpty(String title, String copy) {
        emptyTitle.setText(title); emptyCopy.setText(copy); emptyState.setVisible(true); emptyState.setManaged(true);
    }
    private void showMessage(String message, boolean error) {
        messageLabel.setText(message); messageLabel.getStyleClass().removeAll("form-message-error", "form-message-success");
        messageLabel.getStyleClass().add(error ? "form-message-error" : "form-message-success");
    }
    private static String message(Throwable error, String fallback) {
        return error == null || error.getMessage() == null || error.getMessage().isBlank() ? fallback : error.getMessage();
    }
    private static void run(Task<?> task, String name) { Thread thread = new Thread(task, name); thread.setDaemon(true); thread.start(); }
}
