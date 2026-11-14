package pk.edu.nu.aafatlink.repository;

import pk.edu.nu.aafatlink.domain.account.AccountProfile;
import pk.edu.nu.aafatlink.domain.auth.AuthSession;

/** Reads the database-authoritative account associated with an Auth session. */
@FunctionalInterface
public interface AccountRepository {
    AccountProfile findCurrentAccount(AuthSession session);
}
