# ADR 0027 · Backend: parametric claims from signed oracle events

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-14 |
| Story | HU-MOV-10 (RC-26) |
| Deciders | The team (to be confirmed) |

## Context

- HU-WEB-15 defined the parametric rule `SISMO-6.5` (`app/modules/claims/parametric.py`): an earthquake
  of magnitude 6.5 or more pays 5 % of the disability coverage without an analyst. Nothing created such
  claims: there was no way for an external event to enter the system.
- A fake or replayed event would trigger real payouts, so the channel needs stronger assurance than a
  user session: proof of origin, proof of integrity and protection against replay.
- Policies did not store the insured's city; quotes receive it but did not persist it.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. Public endpoint with an HMAC-SHA256 signature over timestamp and body, a shared key, a 5-minute window and a unique `event_id` | Integrity and origin in one check; replay blocked by the window and the id; no session to manage | A shared secret to distribute and rotate; the oracle is simulated for now |
| B. Bearer token for the oracle | Reuses `require_roles` | Does not cover the body: a captured request can be replayed or altered |
| C. Asymmetric signature (the oracle signs with its private key) | No shared secret | Key distribution and a verification library for one simulated client |

## Decision

Option A.

1. `POST /oracle/events` (`app/bff/oracle/router.py`) reads the raw body (max 4 KB), checks
   `X-Oracle-Timestamp` is within ±5 minutes and that `X-Oracle-Signature` equals
   `HMAC-SHA256(ORACLE_SECRET, "<timestamp>.<body>")` in constant time, then validates the event model.
2. `app/modules/claims/oracle.Oracle.receive` matches the event against the parametric rule, finds the
   ACTIVE and in-force policies whose quote city equals the event city (accents and case ignored) and
   that include the rule's coverage, and registers one parametric claim per policy with
   `next_claim_number`; the rule then settles and pays it as in HU-WEB-15.
3. The event is stored in `oracle_events` with its `event_id` as primary key, in the same transaction as
   the claims; a duplicate id returns the claims already created and creates nothing.
4. The quote persists `city` (migration `0002`); policies without a city never match an event.
5. Without `ORACLE_SECRET` the endpoint answers 503: the channel is closed by default.

## Consequences

- A simulated oracle (a script with the shared key) can exercise the whole parametric path locally and in
  DEV.
- Rotating the key means changing it in both places; the window of 5 minutes limits replay without
  requiring clocks to match exactly.
- New event types need a rule in `parametric.py` and a literal in `OracleEvent.type`.

## Security and privacy impact

- The secret lives in GitHub secrets and in AWS Secrets Manager, and reaches the container as
  `ORACLE_SECRET` through the ECS `secrets` block (ADR 0029); the task definition only holds its ARN. It is
  never logged.
- Parametric claims carry the event payload, not personal data; the insured is reached through the policy.
- A leaked key would allow fabricated payouts: the risk is recorded as RISK-01 of HU-MOV-10 and mitigated
  by the payout cap of the rule and the immutable claim history.
