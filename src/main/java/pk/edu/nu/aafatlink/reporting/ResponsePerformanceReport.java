package pk.edu.nu.aafatlink.reporting;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.ReportRepository;

public final class ResponsePerformanceReport extends ReportGenerator {
    private static final List<String> COLUMNS = List.of("Requests", "Closed", "Closure rate",
            "Avg verification hours", "Avg assignment hours", "Avg resolution hours");

    public ResponsePerformanceReport(Clock clock) {
        super(ReportType.RESPONSE_PERFORMANCE, COLUMNS, clock);
    }

    @Override protected List<ReportRow> fetch(AuthSession session, ReportFilters filters, ReportRepository repo) {
        return repo.fetchResponsePerformance(session, filters);
    }

    @Override protected Map<String, String> summarize(List<ReportRow> rows) {
        LinkedHashMap<String, String> summary = new LinkedHashMap<>();
        ReportRow metrics = rows.get(0);
        COLUMNS.forEach(column -> summary.put(column, metrics.value(column)));
        return summary;
    }
}
