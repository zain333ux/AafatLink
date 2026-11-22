package pk.edu.nu.aafatlink.service.evidence;

import java.util.UUID;

import pk.edu.nu.aafatlink.domain.evidence.EvidenceAttachment;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;

/** Stores private request evidence independently from the PostgreSQL transaction. */
public interface EvidenceStorage {
    String upload(AuthSession session, UUID operationId, EvidenceAttachment evidence);

    void delete(AuthSession session, String objectPath);
}
