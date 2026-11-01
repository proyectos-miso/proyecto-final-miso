# ADR 0022 · Mobile: background work with WorkManager

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-10-25 |
| Story | HU-MOV-07 |
| Deciders | The team (to be confirmed) |

## Context

Paths are under `movil/app/src/main/java/com/solventa/app/`.

- HU-MOV-07 refreshes the wallet copy when the network returns, in the foreground or the background, and
  retries with growing waits. HU-MOV-09 will send reports without signal, the next case of the same kind.
- Tokens live only in memory (ADR 0017), so there is nothing to sync after Android kills the process.
- Android freezes cached apps in the background and defers work under Doze and standby buckets. A
  coroutine waiting for a network callback inside the app may not run while frozen.
- The app has no background work before this story and no library for it.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. WorkManager | Android standard; network constraint, exponential backoff and unique work built in; runs when the system allows it, even with the app in the background | A dependency and its own unencrypted database; merges permissions and components into the manifest; scheduling can be delayed |
| B. `ConnectivityManager` callback with retries in a coroutine | No dependency; immediate; easy to test with a fake clock | Stops while the app is frozen in the background; backoff and deduplication written by hand |
| C. `JobScheduler` directly | Platform API; network constraint | WorkManager already wraps it with backoff, unique work and testing support |

## Decision

Option A, `androidx.work:work-runtime` 2.10.5.

1. Each purpose has one unique work (`policy_sync`) enqueued with `ExistingWorkPolicy.KEEP`, the constraints
   it needs and `BackoffPolicy.EXPONENTIAL`. Bursts of requests leave at most one pending work.
2. Work carries no input data and no tags with account data or secrets. The worker gets what it needs from
   the container through a small host interface (`data/policies/PolicySyncHost`, ADR 0014); WorkManager
   keeps its default initializer.
3. A worker that needs a session ends without calling the server when there is none.
4. A worker catches every exception except cancellation and returns `Result.retry()`, so nothing reaches
   logcat through WorkManager.
5. Permissions that WorkManager merges and the app does not use are removed in the manifest with
   `tools:node="remove"`: today `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`.
6. The version stays on 2.10.x while the app uses Room 2.6.1: 2.11 and later require Room 2.7.
7. Tests use `androidx.work:work-testing` (`WorkManagerTestInitHelper`, `TestListenableWorkerBuilder`).

## Consequences

- Easier: syncing when the network returns, with retries and no duplicate work, without app-side
  scheduling code; HU-MOV-09 reuses the pattern.
- Harder: Android may delay the work in the background; the wallet also syncs when the app returns to the
  foreground. Process death drops the session, so pending work ends without effect.
- Follow-up: upgrade WorkManager together with Room 2.7.

## Security and privacy impact

- WorkManager keeps `no_backup/androidx.work.workdb` unencrypted. Rule 2 keeps account data and secrets out
  of it; HU-MOV-07 TC-14 checks the file on a phone.
- WorkManager exports `SystemJobService`, protected by `BIND_JOB_SERVICE`, and `DiagnosticsReceiver`,
  protected by `DUMP`. Both are reachable only by the system or adb.
- Removing the boot and foreground-service permissions narrows what the app can do.
