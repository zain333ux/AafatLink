package pk.edu.nu.aafatlink.application.dashboard;

import java.util.List;
import java.util.Objects;
import pk.edu.nu.aafatlink.domain.account.UserRole;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;

public final class DashboardController {
    private final WorkflowRepository workflows;

    public DashboardController(WorkflowRepository workflows) {
        this.workflows = Objects.requireNonNull(workflows);
    }

    public List<DashboardEntry> load(AuthSession session, UserRole role) {
        return workflows.dashboard(session, role);
    }
}
