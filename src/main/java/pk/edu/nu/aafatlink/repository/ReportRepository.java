package pk.edu.nu.aafatlink.repository;

import java.util.List;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.reporting.ReportFilters;
import pk.edu.nu.aafatlink.reporting.ReportRow;

/** Read-only Protected Variation boundary for UC11 committed reporting data. */
public interface ReportRepository {
    List<ReportRow> fetchRequestStatus(AuthSession session, ReportFilters filters);
    List<ReportRow> fetchOutstandingRequests(AuthSession session, ReportFilters filters);
    List<ReportRow> fetchVolunteerWorkload(AuthSession session, ReportFilters filters);
    List<ReportRow> fetchResponsePerformance(AuthSession session, ReportFilters filters);
}
