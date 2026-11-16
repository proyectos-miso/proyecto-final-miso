# ADR 0031 · Backend: identity verification behind a port, with a simulated adapter

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-15 |
| Story | none: improvement of feature FE-MOV-01, agreed on 2026-11-14 (`specs/HOTFIX-onboarding-con-documento-y-selfie`) |
| Deciders | The team (to be confirmed) |

## Context

- FE-MOV-01 asks for self-service sign-up in the app: the person sends a photo of the identity document and a
  selfie, and a provider compares them. Solventa has no contract with an identity-verification provider.
- Insured data is keyed by document number (`policies.insured_document`): an account created with someone
  else's document would see that person's policies and claims.
- Providers answer synchronously for most cases and asynchronously for manual review; their latency varies.
- The document photo and the selfie are biometric data: they must not be stored longer than the verification
  needs.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. A port `IdentityVerifier` in `modules/identity/ports.py` with a deterministic simulated adapter, a 400 ms budget and a deferred decision | Sign-up can be built and tested now; a real provider is one adapter; latency and outages degrade to PENDING | The simulated adapter decides nothing real; production cannot approve anyone until a provider is chosen |
| B. Integrate a specific provider now | Real verification | No contract or privacy assessment; ties the module to one SDK |
| C. Manual review by an analyst in the portal | No third party | Analysts would see biometric images; a new portal flow and queue |

## Decision

Option A.

1. `IdentityVerifier.verify(document_type, document_number, full_name, document_front, selfie)` returns
   `VerificationResult(status, reference)` with `APPROVED`, `PENDING` or `REJECTED`; `decide(reference)`
   returns the deferred decision. Routes receive the adapter through `Depends(get_identity_verifier)`.
2. The registration service waits at most 400 ms (`asyncio.wait_for`); a timeout or any provider error is
   PENDING without reference.
3. `SimulatedIdentityVerifier` is deterministic: a document ending in 9 is REJECTED, in 8 PENDING, any other
   APPROVED; `decide` approves. Outside development it answers PENDING to everything, so production never
   approves without a real provider.
4. APPROVED and REJECTED store no images. PENDING stores them in the documents bucket under
   `identidad/{user_id}/{verification_id}/`, encrypted with the bucket key and never served by any route.
   `decide_pending`, run by the daily task, asks the provider (or re-sends the stored images when there is no
   reference), records the decision and deletes the images. A REJECTED decision bumps `token_version` and
   revokes the account's fingerprints.
5. Until an account is APPROVED, the insured routes that read data by document answer 403.

## Consequences

- Choosing a provider means writing one adapter and evaluating its contract, data location and transfers;
  nothing else changes.
- With the simulated adapter outside development every new account stays PENDING and sees no data. Sign-up
  should stay disabled in production until a provider is in place (RISK-01 of the spec).
- The daily task now also runs the deferred decision; a provider that never decides leaves the account
  PENDING (no expiry yet).
- The task role gains `s3:PutObject`, `s3:GetObject` and `s3:DeleteObject` on `identidad/*` only.

## Security and privacy impact

- Biometric images exist only while a verification is pending and are deleted on decision or when the
  insured revokes the biometric authorization (`POST /insured/consents/biometric/revoke`).
- The PENDING gate prevents impersonation through a foreign document number while verification is open.
- The provider receives name, document number and both images: the real adapter needs a privacy assessment
  and a data-processing agreement before use.
