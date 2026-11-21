package pk.edu.nu.aafatlink.reporting;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.ReportRepository;

public final class RequestStatusReport extends ReportGenerator {
    public RequestStatusReport(Clock clock) {
        super(ReportType.REQUEST_STATUS_SUMMARY,
                List.of("Status", "Disaster type", "Priority", "Requests"), clock);
    }

    @Override protected List<ReportRow> fetch(AuthSession session, ReportFilters filters, ReportRepository repo) {
        return repo.fetchRequestStatus(session, filters);
    }

    @Override protected Map<String, String> summarize(List<ReportRow> rows) {
        return Map.of("Total requests", Long.toString(sum(rows, "Requests")));
    }
}
