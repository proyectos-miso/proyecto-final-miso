# ADR 0024 · Mobile: claim evidence encrypted on the device

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-05 |
| Story | HU-MOV-08 |
| Deciders | The team (to be confirmed) |

## Context

Paths are under `movil/app/src/main/java/com/solventa/app/`.

- HU-MOV-08 lets the insured report a claim with 1 to 5 photos taken in the app, each with its location
  and time, and keep the report encrypted on the phone until it is sent (HU-MOV-09). Photos may show
  injuries, so they are special-category data, and they cannot be taken again once the person leaves.
- The wallet already keeps an encrypted copy per account (ADR 0021): a SQLCipher database whose passphrase
  is wrapped by a per-account Android Keystore key (`data/policies/WalletKeys.kt`, alias
  `solventa_wallet_<account>`). HU-MOV-05 discards that copy when its key or file is invalid and downloads it
  again, and its schema is not exported because it is a cache. ADR 0021 asks a feature that stores data only
  on the phone to export its schema and write migrations.
- Photos are up to 2 MB each. Android cannot read a row larger than the cursor window (2 MB).
- The app switches screens by UI state in one activity (ADR 0015) and has no camera or location code.
- Android 13 (the minimum SDK) offers the system location providers, `Location.isMock()` and clocks that do
  not follow the user-set time (`SystemClock.currentGnssTimeClock()`, `SystemClock.currentNetworkTimeClock()`).

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. A separate SQLCipher database per account for the report, and each photo in its own file with AES-256-GCM and a random key kept in that database | Evidence survives a wallet discard; one key per purpose and account; rows stay small; becomes the HU-MOV-09 queue through a migration | Two layers to keep consistent (a file without a row or a row without a file) |
| B. Photos as BLOBs inside the report database, in chunks | One encryption mechanism; transactional | Manual chunking under the cursor window; the database grows and needs compaction; slower writes |
| C. One encrypted folder per report, with an encrypted JSON index | Simple to delete | The HU-MOV-09 queue would need atomic rewrites and locking; listing means decrypting every index |
| D. Tables in the wallet database | No new keys | A wallet discard would delete evidence that cannot be recovered |

## Decision

Option A.

1. `data/claims/EncryptedClaimStore` keeps `claims_<account>.db` (Room and SQLCipher) with the tables
   `reports` and `photos`, and the photos in `files/claims/<account>/<photo>.bin`. Operations of an account
   run in turn, and deleting is not cancellable, as in `EncryptedPolicyStore`. In HU-MOV-08 an account has
   at most one report, a draft or one ready to send; a ready report is not edited.
2. The database exports its schema to `movil/app/schemas/` and changes only through migrations that keep
   the data. It never uses `fallbackToDestructiveMigration`; HU-MOV-09 adds the first migration.
3. The database passphrase is wrapped by a per-account Keystore key that requires an unlocked device.
   `PassphraseVault` takes its preferences name and alias prefix as parameters: the wallet keeps
   `solventa_wallet` and `solventa_wallet_<account>`; the report uses `solventa_claims` and
   `solventa_claims_<account>`.
4. Each photo is encrypted with AES-256-GCM, a random 32-byte key and a 96-bit nonce used once; the key
   and the nonce live in the photo row. A SHA-256 of the JPEG bytes is stored for verification on receipt
   (HU-MOV-09).
5. Nothing deletes evidence by itself: an invalid key or a damaged database leaves the report as "cannot be
   opened" and the person decides to discard it. A locked phone or a transient failure reports it as
   unavailable.
6. CameraX 1.5.3 (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) captures in memory
   (`ImageCapture.takePicture` with a callback), shown through `PreviewView` inside Compose; the app
   re-encodes the image as a JPEG of at most 2 MB without EXIF. No plaintext file, MediaStore entry or disk
   cache. `camera-compose` is not used because it would raise Compose UI from 1.7.5 to 1.7.8.
7. Location comes from `LocationManager` with the system `fused` provider, or `gps` and `network` when it
   is missing, only while the report camera is open and in the foreground. The time is the device time plus
   an independent GNSS or network time when available. No Google Play services location library.
8. The report screens are one more branch of the state-driven navigation (ADR 0015, amended on
   2026-11-05) and set `FLAG_SECURE` in release builds.
9. The report of an account is deleted on logout, on a password reset and when the server no longer
   recognizes the phone for the account. Unlike the wallet copy, it is kept when the session expires: it
   stays encrypted and is shown again only after signing in to that account.

## Consequences

- Easier: HU-MOV-09 turns `reports` into its queue with a migration; HU-MOV-10 adds the claim number.
- Harder: a missing or altered photo file must be detected and the photo retaken; the store deletes files
  without a row when it opens.
- Background sending (HU-MOV-09) waits for the device to be unlocked, as the wallet sync does (ADR 0022).
- Location and time can still be spoofed on a rooted phone; the claims analyst reviews the evidence
  (HU-WEB-15, RISK-01 of the HU-MOV-08 spec).

## Security and privacy impact

- A copied database or photo file is unreadable without the account's Keystore key, which never leaves the
  device and cannot be used while the phone is locked. Each photo has its own key, so swapping or altering a
  file makes it fail to decrypt.
- Photos never touch disk, MediaStore or a cache in plaintext; EXIF is dropped, so the location and time
  live only in the encrypted database. Backup and device transfer exclude every storage domain (HU-MOV-05).
- Location is read only while the report camera is open, never in the background. On phones with Google
  services the system location provider is implemented by those services, under the person's settings.
- Evidence that cannot be recovered is never deleted automatically or by a schema change; a session expiry
  keeps it encrypted. It is deleted on logout, a password reset or when the server unbinds the phone.
- `FLAG_SECURE` hides the report from screenshots and recents in release builds only, so debug builds can
  record the course video.
