# Authentication implementation

## Implemented foundation

- Supabase Auth signup, password sign-in, refresh-token rotation, logout and safe error mapping over Java HTTP Client.
- Database-authoritative role and account-status loading through PostgREST under the user's access token.
- Public registration UI for Requester and Volunteer applicants. Volunteer eligibility still requires the readiness/approval workflow.
- Auth-created accounts default to Requester through a database trigger. A restricted `register_as_volunteer` RPC can create a pending Volunteer profile but can never grant Coordinator or Administrator.
- JavaFX performs network calls on daemon worker threads and does not report success before the complete operation returns.

## Password recovery design

The recovery implementation uses PKCE S256 and a one-use server bound to `127.0.0.1:49152`. The fixed high port permits a narrowly scoped Supabase redirect rule while avoiding a privileged port. It validates a one-use flow identifier, exchanges the returned code with the in-memory verifier, updates the password, revokes the recovery session and shuts down the listener.

Local automated tests verify the RFC 7636 challenge, fixed loopback callback, code exchange and password update orchestration. The hosted development project allows only `http://127.0.0.1:49152/auth/recovery**`, which covers the one-use flow query while remaining restricted to the exact loopback host, port and callback path. The JavaFX recovery screen keeps the password local and waits for the email link for up to ten minutes.

## Token handling

Tokens live only in memory at this stage. `AuthSession.toString()` omits both access and refresh tokens. Persistent session storage will require an operating-system credential store; plaintext files and Java Preferences are not acceptable for refresh tokens.
