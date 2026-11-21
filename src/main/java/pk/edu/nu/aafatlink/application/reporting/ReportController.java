package pk.edu.nu.aafatlink.application.reporting;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.reporting.GeneratedReport;
import pk.edu.nu.aafatlink.reporting.ReportFilters;
import pk.edu.nu.aafatlink.reporting.ReportService;
import pk.edu.nu.aafatlink.reporting.ReportType;

/** GRASP application controller for UC11. */
public final class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public List<ReportType> getReportOptions() {
        return List.of(ReportType.values());
    }

    public GeneratedReport generateReport(AuthSession session, ReportType type, ReportFilters filters) {
        return service.generateReport(session, type, filters);
    }

    public void exportReportToCsv(GeneratedReport report, Path destination) {
        service.exportCsv(report, destination);
    }
}
