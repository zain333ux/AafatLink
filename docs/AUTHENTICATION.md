# Authentication implementation

## Implemented foundation

- Supabase Auth signup, password sign-in, refresh-token rotation, logout and safe error mapping over Java HTTP Client.
- Database-authoritative role and account-status loading through PostgREST under the user's access token.
- Public registration UI for Requester and Volunteer applicants. Volunteer eligibility still requires the readiness/approval workflow.
- Auth-created accounts default to Requester through a database trigger. A restricted `register_as_volunteer` RPC can create a pending Volunteer profile but can never grant Coordinator or Administrator.
- JavaFX performs network calls on daemon worker threads and does not report success before the complete operation returns.

## Password recovery design

The recovery implementation uses PKCE S256 and an ephemeral server bound to `127.0.0.1` on an operating-system-selected port. It validates a one-use flow identifier, exchanges the returned code with the in-memory verifier, updates the password, revokes the recovery session and shuts down the listener.

Local automated tests verify the RFC 7636 challenge, loopback callback, code exchange and password update orchestration. The exact loopback URL must be added to the development project's Auth redirect allowlist. Because the port is ephemeral, the project must permit the reviewed loopback wildcard pattern supported by Supabase, or the application must switch to a configured fixed high port. This requires a real development-project proof before the recovery button is exposed in the JavaFX UI.

## Token handling

Tokens live only in memory at this stage. `AuthSession.toString()` omits both access and refresh tokens. Persistent session storage will require an operating-system credential store; plaintext files and Java Preferences are not acceptable for refresh tokens.
