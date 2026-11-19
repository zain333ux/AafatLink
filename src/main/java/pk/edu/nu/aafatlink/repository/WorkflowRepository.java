package pk.edu.nu.aafatlink.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.RequestSubmissionResult;
import pk.edu.nu.aafatlink.service.evidence.EvidenceAttachment;

/** Persistence boundary shared by the GRASP workflow controllers. */
public interface WorkflowRepository {
    void beginRequestOperation(AuthSession session, UUID operationId, String operationType);

    void registerStagedEvidence(AuthSession session, UUID evidenceId, UUID operationId,
            String objectPath, EvidenceAttachment evidence);

    Optional<RequestSubmissionResult> reconcileRequestOperation(AuthSession session, UUID operationId);

    boolean abandonRequestOperation(AuthSession session, UUID operationId);

    RequestSubmissionResult submitRequest(AuthSession session, UUID operationId,
            UUID requesterId, AssistanceRequestDraft draft);

    RequestSubmissionResult resubmitRequest(AuthSession session, UUID operationId,
            UUID requestId, long expectedVersion, AssistanceRequestDraft draft);

    List<AssistanceRequest> findRequestsByRequester(AuthSession session, UUID requesterId);

    Optional<AssistanceRequest> findRequestForRequester(AuthSession session, UUID requestId, UUID requesterId);
}
