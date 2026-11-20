# ADR 0028 · Mobile and backend: a report-only token in the session family

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-14 |
| Story | HU-MOV-09 (AC-02) |
| Deciders | The team (to be confirmed) |

## Context

- HU-MOV-09 must send a queued claim report when the phone gets connectivity back, even if the app is
  closed (WorkManager, ADR 0022).
- ADR 0017 keeps access and refresh tokens only in memory, and HU-MOV-07 revokes the refresh token on the
  server when the activity finishes. With the app closed there is no token for the background job, and
  storing the refresh token on disk would give a thief full access to the account.
- The report is already stored encrypted per account (ADR 0024) and nothing else of the account is
  readable without a session.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. A separate JWT of type `report` and scope `claims:submit`, valid 7 days, that only `POST /insured/claims` accepts, stored encrypted with a Keystore key that needs no user authentication | Least privilege: a stolen token can only submit the queued report; AC-02 fully met | One more token to issue, store and revoke |
| B. Store the refresh token on disk | No new token | Breaks ADR 0017; full account access from a stolen phone |
| C. Send only while a session is open | No stored credential | AC-02 partially met: reports wait until the person opens the app |
| D. Tie the report token to the refresh token's `jti` | Simple revocation | Closing the app revokes the refresh and kills the report token exactly when it is needed |

## Decision

Option A, with the token in a **session family**:

1. Each login creates a family id `sid` shared by the refresh token and the report token; a refresh keeps
   the `sid`. `InsuredAuthService._issue_tokens` returns `report_token` in `TokenPair`.
2. `POST /auth/logout` takes `keep_reporting`. Finishing the activity sends `keep_reporting=true`: the
   refresh token is revoked (HU-MOV-07 unchanged) but the family stays valid, so the stored report token
   keeps working. An explicit "Cerrar sesión" sends `false` and revokes the family (`RevokedTokenDB` with
   the `sid`); a password change bumps `token_version`. Both invalidate the report token.
3. `account_for_report` accepts only type `report`, scope `claims:submit`, a non-revoked family and the
   current `token_version`; the dependency `reporting_account` of `POST /insured/claims` accepts an access
   token or a report token. No other endpoint accepts the report token.
4. On the phone, `data/session/ReportTokenStore` keeps the latest report token per account, encrypted
   with AES-GCM under a per-account Keystore key without user authentication (`unlockedDeviceRequired`
   off, so a background refresh can replace it while the phone is locked). It is deleted on logout, device
   rejection and password reset, and kept when the session merely expires.
5. `ClaimSender` uses the open session's access token when the session belongs to the queued report's
   account; otherwise the stored report token; with neither it waits for the next sign-in.

## Consequences

- A queued report leaves the phone within a minute of connectivity even with the app closed, for up to 7
  days after the last login or refresh.
- A stolen phone with the app closed exposes one capability: submitting that account's queued report.
  This is RISK-01 of HU-MOV-09, accepted.
- The family model is the hook for any future long-lived, narrow-scope credential: add a type and scope,
  never widen the report token.

## Security and privacy impact

- The report token never grants reads; `GET /insured/*` rejects it (tested).
- Explicit logout and password change revoke it server-side; closing the app does not, by design.
- The token is encrypted at rest with a Keystore key that does not leave the hardware.
