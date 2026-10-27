# ADR 0016 · Mobile: HTTP with HttpURLConnection behind domain interfaces

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-10-22 |
| Story | none |
| Deciders | The team (to be confirmed) |

## Context

Paths are under `movil/app/src/main/java/com/solventa/app/`.

- `data/auth/HttpAuthApi.kt` implements `domain/auth/AuthApi` with `java.net.HttpURLConnection` and
  `org.json`, both part of the platform. It makes nine calls under `auth/`: `otp`, `password/forgot`,
  `login`, `biometric`, `logout`, `refresh`, `devices` (POST), `devices/{id}` (DELETE) and
  `password/reset`.
- `refresh` (HU-MOV-07) connects before sending and uses `setFixedLengthStreamingMode`, so the connection
  never resends a rotated refresh token by itself. It returns `RefreshResult`: `Renewed`, `Rejected` (401),
  `Unavailable` (connection failure or 423) or `Unanswered` (any other outcome once the request may have
  left).
- Each call runs in `withContext(Dispatchers.IO)`, with 10 s connect and read timeouts, and closes the
  connection in `finally`. Device calls send `Authorization: Bearer <access token>`.
- Status codes map to sealed results. Sign-in gives 200, 401, 422, 423 and 429 their own `LoginResult`;
  any other status is `NetworkError`. Code requests map 200, 429 and 503 to `OtpResult`; others are
  `Failed`. Password reset maps 200, 401, 409, 422 and 429 to `ResetResult`; others are `NetworkError`.
  `Retry-After` is read as seconds; missing or not a number means 60.
- `IOException` and `JSONException` are caught in `data`, so no exception reaches the ViewModel. But
  `logout` and `revokeDevice` return `Boolean`, and `registerDevice` returns a nullable value.
- Parsing uses `getString`: a missing field throws and maps to the failure result. Types and empty values
  are not checked.
- `BuildConfig.API_BASE_URL` is set in `defaultConfig` of `movil/app/build.gradle.kts` from the Gradle
  property `apiBaseUrl`, default `http://10.0.2.2:8000/api/v1/`. Since HU-MOV-07 `preReleaseBuild` fails
  unless it starts with `https://`. The `src/main` network security config allows no cleartext; the
  `src/debug` one allows it only to `10.0.2.2` and `localhost`. There is no certificate pinning.
- `HttpAuthApiTest` runs against MockWebServer 4.12.0, a test-only OkHttp artifact. Production code has
  no Retrofit, OkHttp, Ktor or serialization library.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. `HttpURLConnection` and `org.json` | No dependency; platform APIs; little code for eight calls; tested with MockWebServer | Request, parsing and status mapping written per call; no interceptors for auth headers, token refresh or logging |
| B. Retrofit with OkHttp | Android standard; declarative interfaces; interceptors; `CertificatePinner`; JSON converters | Several new dependencies; reflection and dynamic proxies need care once R8 is on; mapping to sealed results still needed |
| C. Ktor client | Kotlin-first and coroutine-native; multiplatform | Engine, content negotiation and serialization modules to choose; smaller Android community; same mapping work |

## Decision

Option A.

1. HTTP code lives in `data/<feature>/`, in a class that implements a domain interface. ViewModels never
   see HTTP types, status codes or JSON.
2. Every call runs on `Dispatchers.IO` with connect and read timeouts and closes its connection.
3. Every call returns a sealed result that covers each status the backend documents for that endpoint,
   plus network failure. Exceptions are caught in `data`; none cross layers. New calls do not use
   `Boolean` or `null` for outcomes the UI must tell apart.
4. Parsing validates required fields and maps a missing or invalid field to the failure result.
   Request and response bodies are never logged.
5. The base URL comes from `BuildConfig`, per build type. Cleartext is allowed only to local development
   hosts, only in debug.
6. Moving to Retrofit with OkHttp, or Ktor, needs a new ADR. The trigger is more endpoints or features, or
   a need for interceptors such as token refresh.

## Consequences

- Easier: no new dependency; behavior is explicit and tested call by call.
- Harder: each endpoint needs handwritten request, parsing and mapping. There is no central place for auth
  headers, token refresh or pinning. Only the policies sync renews the token (HU-MOV-07), without an
  interceptor: one caller does not justify moving to option B.
- Migration when next touched:
  - Rule 5 is met since HU-MOV-07.
  - `registerDevice` and `revokeDevice` still use the access token without renewing it, so after 15 minutes
    they fail with no retry; they renew when they next change.
  - `logout`, `registerDevice` and `revokeDevice` move to sealed results when they next change.
  - The second feature with its own API writes the client-library ADR before adding the library.

## Security and privacy impact

- Until HU-MOV-07, a release built without `-PapiBaseUrl=https://…` called `http://10.0.2.2` in
  cleartext. Now that build fails, and a release allows no cleartext.
- No certificate pinning. `sdd/rules/mobile.md` asks for it when the risk justifies it; decide in the
  hardening spec or the client-library ADR.
- Bodies carry passwords, codes, device secrets and tokens. The app logs no requests or responses today;
  rule 4 keeps it so.
- `HttpAuthApi` passes the server's `debug_code` to the UI, which shows it in any build when the server
  sends it (ADR 0017).
