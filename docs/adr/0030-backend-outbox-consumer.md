# ADR 0030 · Backend: an in-process consumer delivers outbox events effectively once

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-15 |
| Story | none: improvement that completes the outbox pattern (ASR-05), decided by the user on 2026-11-15 |
| Deciders | The team (to be confirmed) |

## Context

- [ADR 0026](0026-backend-outbox-publisher.md) publishes `PolicyIssued`, `PaymentApproved` and `ClaimFiled`
  from the `outbox` table to the SQS queue `events`, but nothing read that queue: the events were sent and
  expired after four days.
- Push notifications were sent inside the request. `ClaimService.commit` (`app/modules/claims/service.py`)
  called `notification_service.claim_changed` after the analyst's decision, the parametric settlement of the
  oracle and the payout retry; the daily task (`app/main.py`, `run_policy_lifecycle`) called `renewal_due`.
  A slow Firebase slowed the analyst's request, and a failed push was logged and lost.
- `NotificationService.notify` swallows every error by design (best effort), so a caller could not tell a
  delivery failure from a disabled category.
- Terraform already gave the queue a dead-letter queue after three receives and encrypts both with the
  storage KMS key (`terraform/modules/storage_events`). The task role could only send.
- `firebase-admin`'s `send_each_for_multicast` does not raise per-token failures (unavailable, internal,
  quota, timeouts): they come back inside the `BatchResponse`.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. A long-polling consumer inside the backend task, with handlers registered by the composition root and a `processed_messages` table for deduplication | No new deployable; reuses the database, the Firebase sender and the lifespan pattern of the publisher; retries and DLQ come from SQS | Each replica polls; the consumer competes with the API for the task's CPU |
| B. A Lambda subscribed to the queue | Scales on its own; isolated from the API | A second deployable with its own image, VPC access to RDS, secrets and IAM for one notification type |
| C. Keep sending pushes in the request and drop the queue | Simplest | The queue stays pointless; a Firebase outage still loses pushes and slows requests |

## Decision

Option A.

1. `app/core/outbox.OutboxConsumer` receives up to 10 messages with `WaitTimeSeconds` 10 and dispatches by
   `event_type` to handlers registered with `register(event_type, handler)`. `core` imports no module: the
   composition root (`app/main.py`, lifespan) registers `notification_service.event_handlers()`.
2. The message still carries only identifiers (ADR 0026). The consumer reads the event from the `outbox`
   table by `message_id` and passes the stored `aggregate_id` and `payload` to the handler; a message whose
   id is not in the outbox, or whose type differs from the stored one, is discarded.
3. Idempotency: before the handler runs, the consumer inserts `message_id` into `processed_messages`
   (migration `0006`) with `ON CONFLICT DO NOTHING`. A conflict means a repeated message: it is deleted with
   no effect. The row and the handler's work commit together; a concurrent replica with the same message
   waits on the row lock and then sees the conflict. Delivery is therefore effectively once, not exactly
   once: if the push leaves and the commit then fails, or the task is shut down between the push and the
   commit (the lifespan cancels the consumer and the transaction rolls back), the message returns and the
   push is sent again (RISK-01).
4. A message is deleted from SQS when it was processed, repeated, malformed, not in the outbox, or of a
   type in the explicit ignore list (`PolicyIssued`, `PaymentApproved`). A type with no handler and not in
   that list is not deleted: its visibility is set to the maximum wait (15 minutes) until SQS moves it to
   the DLQ, so a replica that does not know a new type during a staged deploy does not lose it. When the
   handler raises, or the database or the commit fail, the transaction rolls back, the message is not
   deleted and its visibility is set to 30 s × 2^(n-1), up to 15 minutes, where n is the
   `ApproximateReceiveCount` that `ReceiveMessage` returns with the message (no `GetQueueAttributes`). A
   failure with one message, including a failed `DeleteMessage`, does not stop the rest of the batch. The
   queue has a 120-second visibility timeout and moves a message to the DLQ after eight receives: the
   waits are 30, 60, 120, 240, 480, 900, 900 and 900 seconds, so a Firebase outage of up to about
   60 minutes (3630 s) loses nothing, and an unhandled type reaches the DLQ in about two hours. Logs name
   the event type and the error class only.
5. Handlers call `notify(..., strict=True)`: a Firebase or database failure raises `PushDeliveryError`.
   `FirebaseSender.send` inspects the `BatchResponse`: when no token succeeded and at least one failure is
   transient (unavailable, internal, quota, unknown, deadline or a network error) it raises
   `PushDeliveryError`; `UNREGISTERED` and `INVALID_ARGUMENT` are not retried, unregistered tokens are
   removed from `push_tokens`, and a partial success is not retried, to avoid duplicates. A disabled
   category, a phone without a token or a backend without Firebase credentials return normally and count
   as processed. Callers that do not pass `strict` keep the best-effort behaviour.
6. Events and their push: `ClaimFiled` («Recibimos su siniestro …», claims); `ClaimStatusChanged` with
   `kind` ASSIGNED, REJECTED, SETTLED or PAID, written by `ClaimService.event` in the claim's transaction
   (PAID goes to payments, the others to claims); `RenewalDue`, written by `remind_renewals` with the
   `RENEWAL_REMINDER` policy event (renewals; skipped if the policy is no longer active or its version is
   not the event's). `ClaimFiled` is skipped when the claim is no longer REPORTED or the event is older than
   24 hours, so a backlog does not announce something that already changed. Handlers read the policy and
   the claim status through `policy_service` and `claim_service`, not through other modules' models. No
   push is sent inside a request any more.
7. The consumer starts only when `SQS_EVENTS_URL` or `AWS_ENDPOINT_URL` (the local emulator) is set;
   without them no push is sent. On shutdown the lifespan cancels its tasks and awaits them.

## Consequences

- Requests no longer wait on Firebase, and a Firebase outage of up to about an hour delays pushes instead
  of losing them; after eight failed receives the message waits in the DLQ for a person to replay it.
  There is no DLQ alarm yet.
- A push arrives a few seconds later than before (publisher cycle plus long polling). Events of the same
  transaction can arrive out of order: the standard queue does not keep order.
- The insured now also gets a push when a claim is filed. A parametric claim that matches its rule is
  settled in the same transaction, so its `ClaimFiled` is skipped and it gets its SETTLED and PAID pushes.
- The DEV queue holds up to four days of events nobody read; purge it before the first deployment.
- `processed_messages` grows by one row per consumed event; a cleanup older than the queue retention (four
  days) is pending.
- `PolicyIssued` and `PaymentApproved` still have no handler: they are in the ignore list and deleted.
- The task role gains `sqs:ReceiveMessage`, `sqs:DeleteMessage` and `sqs:ChangeMessageVisibility` on the
  events queue only; it already had `kms:Decrypt` and `kms:GenerateDataKey` on the storage key that
  encrypts the queue. `terraform/tests/consumidor_del_outbox.tftest.hcl` checks it, together with the
  queue's visibility timeout and receive count.

## Security and privacy impact

- No personal data enters the queue or the new table: `processed_messages` holds a random id and a time.
  The insured's document is read from `policies` inside the backend, as before.
- A forged message on the queue cannot choose the recipient or the text: the consumer trusts only the
  `outbox` row with that id and type, and the queue is reachable only with the task role.
- The push content is unchanged: claim or policy number and a general text, no amounts or names.
- The role can now delete messages from the events queue; it still cannot purge or delete the queue, nor
  read the DLQ.
