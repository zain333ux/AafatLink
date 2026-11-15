package pk.edu.nu.aafatlink.application.request;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;

/** GRASP application controller for UC01 and UC02. */
public final class RequestController {
    private final WorkflowRepository workflows;

    public RequestController(WorkflowRepository workflows) {
        this.workflows = Objects.requireNonNull(workflows, "workflows");
    }

    public RequestSubmissionResult submitRequest(AuthSession session, AssistanceRequestDraft details) {
        Objects.requireNonNull(session, "session");
        return workflows.submitRequest(session, UUID.randomUUID(), session.userId(),
                Objects.requireNonNull(details, "details"));
    }

    public RequestSubmissionResult resubmitRequest(AuthSession session, UUID requestId,
            long expectedVersion, AssistanceRequestDraft details) {
        Objects.requireNonNull(session, "session");
        return workflows.resubmitRequest(session, UUID.randomUUID(),
                Objects.requireNonNull(requestId, "requestId"), expectedVersion,
                Objects.requireNonNull(details, "details"));
    }

    public List<AssistanceRequest> getMyRequests(AuthSession session) {
        Objects.requireNonNull(session, "session");
        return workflows.findRequestsByRequester(session, session.userId());
    }

    public Optional<AssistanceRequest> getRequestDetails(AuthSession session, UUID requestId) {
        Objects.requireNonNull(session, "session");
        return workflows.findRequestForRequester(session, Objects.requireNonNull(requestId, "requestId"),
                session.userId());
    }
}
