package pk.edu.nu.aafatlink.service.auth;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.auth.RegistrationResult;

/** Authentication operations required by the desktop application. */
public interface AuthService {
    RegistrationResult register(String displayName, String email, String password);

    AuthSession signIn(String email, String password);

    AuthSession refresh(String refreshToken);

    void requestPasswordRecovery(PasswordRecoveryRequest request);

    AuthSession exchangePkceCode(String authCode, String codeVerifier);

    void updatePassword(String accessToken, String newPassword);

    void logout(String accessToken);
}
