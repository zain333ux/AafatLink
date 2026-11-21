package pk.edu.nu.aafatlink.reporting;

/** The four report strategies mandated by UC11. */
public enum ReportType {
    REQUEST_STATUS_SUMMARY("Request Status Summary"),
    OUTSTANDING_REQUESTS("Outstanding Requests"),
    VOLUNTEER_WORKLOAD("Volunteer Workload"),
    RESPONSE_PERFORMANCE("Response Performance");

    private final String displayName;

    ReportType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
