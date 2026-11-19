# ADR 0032 · Mobile: document scanning behind a port, with ML Kit and a camera fallback

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-15 |
| Story | none: improvement of feature FE-MOV-05, agreed on 2026-11-14 (`specs/HOTFIX-escaneo-de-documentos`) |
| Deciders | The team (to be confirmed) |

## Context

- FE-MOV-05 asks the insured to add documents (invoices, prescriptions, police reports) to a claim that is
  still open. Until now the app only captured evidence photos with its own CameraX camera
  (`ui/claims/EvidenceCamera.kt`, HU-MOV-08), with no edge detection or perspective correction.
- Scanning well (edge detection, cropping, de-skewing, filters) is a large piece of UI and image processing.
- Claim evidence never leaves memory in clear (`CTL-05` of HU-MOV-08, `PhotoCipher` for the queue); the same
  bar applies to documents.
- `minSdk` is 33 and the release is distributed through Google Play, but some devices ship without Google Play
  services.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. A `DocumentScanner` port with an ML Kit Document Scanner adapter (`play-services-mlkit-document-scanner`) and a fallback to the app's back camera | Google's scanner UI with cropping and filters, no camera permission for it, small APK impact (the model ships in Play services) | Proprietary terms; needs Google Play services; the scanner writes each page to the app's cache before handing it over |
| B. Build scanning on CameraX with an open-source edge detector | No Google dependency | Weeks of image-processing work; larger APK |
| C. Camera only, no scanning | No new dependency | Skewed, poorly lit pages; analysts ask for documents again |

## Decision

Option A.

1. `ui/claims/DocumentScanner.kt` defines the port: `available()` and a composable `rememberLauncher(onResult)`
   that returns `ScanResult.Pages`, `Cancelled`, `Unavailable` or `Failed` (`domain/claims/ClaimDocuments.kt`).
2. `MlKitDocumentScanner` opens the scanner with gallery import off, JPEG output and
   `SCANNER_MODE_BASE_WITH_FILTER`, with a page limit equal to the pages still allowed (10 per upload).
3. `data/claims/ScannedPageReader.kt` reads every page the scanner left in the cache into memory, re-encodes it
   with `EvidenceEncoder` (JPEG without EXIF, at most 2 MB) and deletes the file whether reading succeeded or
   not; a cancelled scan deletes its files too. It only deletes files inside the app's own directories.
4. Without Google Play services, or when the scanner cannot start, the screen switches to the existing
   `CameraXEvidenceCamera` (back lens, no cropping), which never writes to disk.
5. Pages are sent with the open session to `POST /insured/claims/{n}/documents`. There is no offline queue:
   without network the app says so and keeps the pages in memory while the screen is open.

## Consequences

- One new runtime dependency, `com.google.android.gms:play-services-mlkit-document-scanner:16.0.0` (ML Kit
  Terms of Service), with `play-services-base`, `mlkit:common` and `datatransport` as transitive dependencies.
- The merged manifest gains non-exported ML Kit components and no new permissions.
- Tests use a `FakeDocumentScanner`; the real scanner and the fallback on a device without Play services need a
  manual run on a phone.
- Leaving the screen discards unsent pages; a queued upload would need its own encrypted store.

## Security and privacy impact

- ML Kit processes the pages on the device; Google's scanner UI and its usage telemetry (`datatransport`) are
  governed by the ML Kit terms. Page content is not sent to Google by the scanner according to its
  documentation; this is not verified here (RISK-01 of the spec).
- The pages exist in clear in the app's cache between the end of the scan and the read, a short window that
  the app cannot avoid with this scanner (RISK-02 of the spec); deletion on flash storage is not a secure erase.
