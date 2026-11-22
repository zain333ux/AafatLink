package pk.edu.nu.aafatlink.application.request;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.domain.request.RequesterContact;
import pk.edu.nu.aafatlink.repository.WorkflowRepository;
import pk.edu.nu.aafatlink.service.evidence.EvidenceAttachment;
import pk.edu.nu.aafatlink.service.evidence.EvidenceStorage;

/** GRASP application controller for UC01 and UC02. */
public final class RequestController {
    private final WorkflowRepository workflows;
    private final EvidenceStorage evidenceStorage;

    public RequestController(WorkflowRepository workflows) {
        this(workflows, null);
    }

    public RequestController(WorkflowRepository workflows, EvidenceStorage evidenceStorage) {
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.evidenceStorage = evidenceStorage;
    }

    public List<DisasterType> getDisasterTypes(AuthSession session) {
        return workflows.findActiveDisasterTypes(Objects.requireNonNull(session, "session"));
    }

    public RequestSubmissionResult submitRequest(AuthSession session, AssistanceRequestDraft details) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(details, "details");
        return executeWithoutEvidence(session, "SUBMIT_REQUEST",
                operationId -> workflows.submitRequest(session, operationId, session.userId(), details));
    }

    public RequestSubmissionResult submitRequest(AuthSession session, AssistanceRequestDraft details,
            EvidenceAttachment evidence) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(evidence, "evidence");
        return executeWithEvidence(session, "SUBMIT_REQUEST", evidence,
                operationId -> workflows.submitRequest(session, operationId, session.userId(), details));
    }

    public RequestSubmissionResult recordCoordinatorRequest(AuthSession session, String requesterName,
            String requesterContact, AssistanceRequestDraft details) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(details, "details");
        RequesterContact contact = new RequesterContact(requesterName, requesterContact);
        return executeWithoutEvidence(session, "RECORD_COORDINATOR_REQUEST",
                operationId -> workflows.recordCoordinatorRequest(session, operationId, contact, details));
    }

    public RequestSubmissionResult recordCoordinatorRequest(AuthSession session, String requesterName,
            String requesterContact, AssistanceRequestDraft details, EvidenceAttachment evidence) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(evidence, "evidence");
        RequesterContact contact = new RequesterContact(requesterName, requesterContact);
        return executeWithEvidence(session, "RECORD_COORDINATOR_REQUEST", evidence,
                operationId -> workflows.recordCoordinatorRequest(session, operationId, contact, details));
    }

    public RequestSubmissionResult resubmitRequest(AuthSession session, UUID requestId,
            long expectedVersion, AssistanceRequestDraft details, EvidenceAttachment evidence) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(evidence, "evidence");
        return executeWithEvidence(session, "RESUBMIT_REQUEST", evidence,
                operationId -> workflows.resubmitRequest(session, operationId, requestId, expectedVersion, details));
    }

    private RequestSubmissionResult executeWithEvidence(AuthSession session, String operationType,
            EvidenceAttachment evidence, OperationAction action) {
        if (evidenceStorage == null) {
            throw new IllegalStateException("Evidence storage is not configured.");
        }

        UUID operationId = UUID.randomUUID();
        String objectPath = null;
        try {
            workflows.beginRequestOperation(session, operationId, operationType);
            objectPath = evidenceStorage.upload(session, operationId, evidence);
            workflows.registerStagedEvidence(session, UUID.randomUUID(), operationId, objectPath, evidence);
            return action.execute(operationId);
        } catch (RuntimeException failure) {
            Optional<RequestSubmissionResult> committed = workflows.reconcileRequestOperation(session, operationId);
            if (committed.isPresent()) {
                return committed.get();
            }
            if (objectPath != null && workflows.abandonRequestOperation(session, operationId)) {
                evidenceStorage.delete(session, objectPath);
            }
            throw failure;
        }
    }

    private RequestSubmissionResult executeWithoutEvidence(AuthSession session, String operationType,
            OperationAction action) {
        UUID operationId = UUID.randomUUID();
        workflows.beginRequestOperation(session, operationId, operationType);
        try {
            return action.execute(operationId);
        } catch (RuntimeException failure) {
            Optional<RequestSubmissionResult> committed = workflows.reconcileRequestOperation(session, operationId);
            if (committed.isPresent()) {
                return committed.get();
            }
            workflows.abandonRequestOperation(session, operationId);
            throw failure;
        }
    }

    @FunctionalInterface
    private interface OperationAction {
        RequestSubmissionResult execute(UUID operationId);
    }

    public RequestSubmissionResult resubmitRequest(AuthSession session, UUID requestId,
            long expectedVersion, AssistanceRequestDraft details) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(details, "details");
        return executeWithoutEvidence(session, "RESUBMIT_REQUEST",
                operationId -> workflows.resubmitRequest(session, operationId, requestId, expectedVersion, details));
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
