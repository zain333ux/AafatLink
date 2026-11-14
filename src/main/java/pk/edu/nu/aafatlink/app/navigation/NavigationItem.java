package pk.edu.nu.aafatlink.app.navigation;

/** A destination displayed by the role-aware application shell. */
public enum NavigationItem {
    DASHBOARD("Dashboard", "mdi2v-view-dashboard-outline"),
    SUBMIT_REQUEST("Submit request", "mdi2p-plus-circle-outline"),
    MY_REQUESTS("My requests", "mdi2f-format-list-bulleted"),
    READINESS_PROFILE("Readiness profile", "mdi2a-account-check-outline"),
    ASSIGNMENTS("Assignments", "mdi2c-clipboard-text-outline"),
    TASK_PROGRESS("Task progress", "mdi2p-progress-clock"),
    VERIFICATION_QUEUE("Verification queue", "mdi2s-shield-check-outline"),
    PRIORITY_QUEUE("Priority queue", "mdi2a-alert-circle-outline"),
    VOLUNTEER_ASSIGNMENT("Volunteer assignment", "mdi2a-account-switch-outline"),
    CLOSURE_REVIEW("Closure review", "mdi2c-clipboard-check-outline"),
    REPORTS("Reports", "mdi2c-chart-box-outline"),
    ACCESS_MANAGEMENT("Access management", "mdi2a-account-cog-outline");

    private final String label;
    private final String iconLiteral;

    NavigationItem(String label, String iconLiteral) {
        this.label = label;
        this.iconLiteral = iconLiteral;
    }

    public String label() {
        return label;
    }

    public String iconLiteral() {
        return iconLiteral;
    }
}
