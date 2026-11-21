package pk.edu.nu.aafatlink.reporting;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.ReportRepository;

public final class VolunteerWorkloadReport extends ReportGenerator {
    public VolunteerWorkloadReport(Clock clock) {
        super(ReportType.VOLUNTEER_WORKLOAD,
                List.of("Volunteer", "Assignments", "Active", "Completed"), clock);
    }

    @Override protected List<ReportRow> fetch(AuthSession session, ReportFilters filters, ReportRepository repo) {
        return repo.fetchVolunteerWorkload(session, filters);
    }

    @Override protected Map<String, String> summarize(List<ReportRow> rows) {
        return Map.of("Total assignments", Long.toString(sum(rows, "Assignments")),
                "Completed assignments", Long.toString(sum(rows, "Completed")));
    }
}
