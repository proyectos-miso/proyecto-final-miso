# ADR 0026 · Backend: outbox events published to SQS at least once

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-14 |
| Story | HU-MOV-10 (RC-14) |
| Deciders | The team (to be confirmed) |

## Context

- The backend already wrote domain events to the `outbox` table in the same transaction as the aggregate
  (`PolicyIssued` on issuance, ASR-05), but nothing read that table: events never left the database.
- Terraform already provisions the SQS queue `events` with a dead-letter queue and KMS encryption
  (`terraform/modules/storage_events`), and the task receives `SQS_EVENTS_URL`.
- Consumers (notifications, partners, reporting) were not built when this was decided; the queue must not
  carry personal data because its readers are not known. The first consumer, for push notifications, is
  [ADR 0030](0030-backend-outbox-consumer.md).

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. A polling publisher inside the backend task, every few seconds, with row locks | No new infrastructure; survives SQS outages by leaving rows pending; works locally against the emulator | Latency of a few seconds; each replica polls |
| B. Publish directly in the request after commit | Lowest latency | A failure after commit loses the event; mixes HTTP latency with SQS latency |
| C. A separate worker service or Lambda reading the table | Isolates the load | A second deployable to build, deploy and secure |

## Decision

Option A.

1. `app/core/outbox.OutboxPublisher.publish_pending` selects `PENDING` rows in creation order with
   `FOR UPDATE SKIP LOCKED`, sends each one and marks it `PUBLISHED` with `published_at`. A failed send
   leaves the row `PENDING`, increments `attempts` and stops the batch; the next cycle retries.
2. `app/main.py` runs `publish_outbox()` every 5 seconds as a background task of the lifespan, like the
   daily policy lifecycle.
3. The message body carries only `message_id`, `event_type`, `aggregate_id` and `occurred_at`, plus the
   same values as message attributes. Consumers fetch the details with their own permissions: the
   in-process consumer of ADR 0030 reads the `outbox` row by `message_id`; an external one would use the API.
4. `register_claim` writes `ClaimFiled` in the claim's transaction; other modules add their events the
   same way.
5. Delivery is at least once: a consumer must deduplicate by `message_id`.

## Consequences

- Events reach SQS within a few seconds while SQS is up, and are never lost while the database is up.
- Each replica runs a publisher; `SKIP LOCKED` keeps them from sending the same row twice in the normal
  case, and `message_id` covers the rare double send.
- The task role gets `sqs:SendMessage` and `sqs:GetQueueUrl` on the events queue only.

## Security and privacy impact

- No personal data in the queue (CTL-04 of HU-MOV-10); the queue is encrypted with the storage KMS key.
- The publisher only sends. Since ADR 0030 the same task role also receives, defers and deletes messages
  of the events queue for the consumer; it still cannot purge the queue or read the DLQ.
