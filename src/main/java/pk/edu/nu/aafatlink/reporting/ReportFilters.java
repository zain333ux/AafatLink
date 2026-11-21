package pk.edu.nu.aafatlink.reporting;

import java.time.LocalDate;

import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;

/** Optional committed-data filters shared by every UC11 report. */
public record ReportFilters(LocalDate startDate, LocalDate endDate, String disasterType,
        RequestStatus status, RequestPriority priority, Long serviceAreaId) {
    public ReportFilters {
        disasterType = normalize(disasterType);
        if (serviceAreaId != null && serviceAreaId <= 0) {
            throw new IllegalArgumentException("Service area must be a positive identifier.");
        }
    }

    public static ReportFilters none() {
        return new ReportFilters(null, null, null, null, null, null);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
