package pk.edu.nu.aafatlink.reporting;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.ReportRepository;

public final class OutstandingRequestsReport extends ReportGenerator {
    public OutstandingRequestsReport(Clock clock) {
        super(ReportType.OUTSTANDING_REQUESTS,
                List.of("Request ID", "Submitted", "Disaster type", "Status", "Priority", "Location", "Waiting hours"), clock);
    }

    @Override protected List<ReportRow> fetch(AuthSession session, ReportFilters filters, ReportRepository repo) {
        return repo.fetchOutstandingRequests(session, filters);
    }

    @Override protected Map<String, String> summarize(List<ReportRow> rows) {
        return Map.of("Outstanding requests", Integer.toString(rows.size()));
    }
}
