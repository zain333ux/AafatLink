package pk.edu.nu.aafatlink.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.account.AccessChangeLog;
import pk.edu.nu.aafatlink.domain.account.ControlledReferenceValue;
import pk.edu.nu.aafatlink.domain.account.ReferenceDataChange;
import pk.edu.nu.aafatlink.domain.account.ReferenceDataType;
import pk.edu.nu.aafatlink.domain.account.Role;
import pk.edu.nu.aafatlink.domain.account.UserAccount;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;

/** Protected Variation boundary for UC12 account administration. */
public interface UserRepository {
    String findContactNumber(AuthSession session, UUID accountId);

    List<Role> findRoles(AuthSession session);
    List<ControlledReferenceValue> findReferenceValues(AuthSession session, ReferenceDataType type);
    ControlledReferenceValue saveReferenceValue(AuthSession session, UUID operationId, ReferenceDataChange change);
    List<UserAccount> findUsers(AuthSession session);
    Optional<UserAccount> findUser(AuthSession session, UUID accountId);
    int countActiveAdministrators(AuthSession session);
    UserAccount saveAccessWithAudit(AuthSession session, UUID operationId, UserAccount account,
            AccessChangeLog log, long expectedVersion);
}
