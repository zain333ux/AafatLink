package pk.edu.nu.aafatlink.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.ReportRepository;

class ReportServiceTest {
    private static final AuthSession SESSION = new AuthSession(java.util.UUID.randomUUID(), "test@example.com", "access", "refresh",
            java.time.Instant.now().plusSeconds(3600));

    @Test
    void dispatchesThroughTheSelectedReportGenerator() {
        RecordingRepository repository = new RecordingRepository();
        ReportService service = ReportService.standard(repository);

        GeneratedReport report = service.generateReport(SESSION, ReportType.REQUEST_STATUS_SUMMARY,
                ReportFilters.none());

        assertEquals("request-status", repository.called);
        assertEquals(List.of("Status", "Disaster type", "Priority", "Requests"), report.columns());
        assertEquals("3", report.summary().get("Total requests"));
    }

    @Test
    void rejectsAnInvertedDateRange() {
        ReportService service = ReportService.standard(new RecordingRepository());
        ReportFilters filters = new ReportFilters(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 1),
                null, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> service.generateReport(SESSION, ReportType.OUTSTANDING_REQUESTS, filters));
    }

    @Test
    void exportsCsvWithEscapingAndStableColumnOrder(@TempDir java.nio.file.Path directory) throws Exception {
        ReportService service = ReportService.standard(new RecordingRepository());
        GeneratedReport report = new GeneratedReport(ReportType.OUTSTANDING_REQUESTS,
                List.of("Request ID", "Location"),
                List.of(new ReportRow(Map.of("Request ID", "REQ-1", "Location", "Street, \"North\""))),
                Map.of(), java.time.Instant.now());
        java.nio.file.Path destination = directory.resolve("report.csv");

        service.exportCsv(report, destination);

        assertEquals("Request ID,Location\r\nREQ-1,\"Street, \"\"North\"\"\"\r\n",
                Files.readString(destination));
    }

    private static final class RecordingRepository implements ReportRepository {
        private String called;

        @Override
        public List<ReportRow> fetchRequestStatus(AuthSession session, ReportFilters filters) {
            called = "request-status";
            return List.of(new ReportRow(Map.of("Status", "VERIFIED", "Disaster type", "Flood",
                    "Priority", "HIGH", "Requests", "3")));
        }

        @Override public List<ReportRow> fetchOutstandingRequests(AuthSession s, ReportFilters f) {
            called = "outstanding"; return List.of();
        }
        @Override public List<ReportRow> fetchVolunteerWorkload(AuthSession s, ReportFilters f) {
            called = "workload"; return List.of();
        }
        @Override public List<ReportRow> fetchResponsePerformance(AuthSession s, ReportFilters f) {
            called = "performance"; return List.of();
        }
    }
}
