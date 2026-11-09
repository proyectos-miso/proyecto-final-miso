# ADR 0023 · Backend: documents in S3 behind presigned links

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-01 |
| Story | HU-WEB-16 |
| Deciders | The team (to be confirmed) |

## Context

- HU-WEB-16 asks for the policy PDF stored in S3 and downloaded through an HTTPS link that expires in
  15 minutes. Claims evidence (HU-MOV-08, HU-MOV-09) will be the next document of the same kind.
- Terraform already creates a private bucket (`storage_events`) with versioning and default encryption
  with its own KMS key, and passes its name to the backend as `S3_DOCUMENTS_BUCKET`. The backend had no
  S3 client and the task role had no policy.
- Floci emulates S3 locally (`docker-compose.yml`) and in CI. It checks link expiry but not signatures.
- The backend is async (FastAPI, SQLAlchemy async); boto3 is synchronous.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. Presigned `GetObject` link from boto3; the browser downloads from S3 | No file goes through the backend; expiry and signature enforced by S3; one well-known library | The link works for anyone who has it until it expires; the browser talks to S3 directly |
| B. The backend reads the object and streams it | Every download passes through authorization | More load and memory per download; the backend becomes a file server |
| C. A public bucket or CloudFront without signed URLs | Simplest download | Anyone could read every document |
| D. aioboto3 | Native async calls | An extra wrapper over boto3 for two calls per request |

## Decision

Option A with boto3.

1. A port `DocumentStorage` in `core/storage.py` (shared, like `core/sms.py`) with `put_pdf` and
   `download_url`, and the adapter `S3DocumentStorage`. Routers get it through `get_document_storage`;
   tests override that dependency.
2. The backend builds every object key from validated identifiers (`caratulas/{policy_number}.pdf`); a
   client never sends a key.
3. Objects rely on the bucket's default KMS encryption; the backend sends no encryption headers, so a
   request cannot downgrade it to the AWS-managed key.
4. Links are `GetObject` only, expire in 15 minutes, carry `response-content-disposition: attachment` with
   the file name, and must be `https://` when the client talks to AWS. API responses with a link are
   `Cache-Control: no-store` and the link is never logged.
5. The client has its own boto3 session, a 2 s connect and 2 s read timeout and 2 attempts in total, so a
   silent S3 fails in about 7 s. Every boto3 error becomes `StorageUnavailableError`, answered as 503 with
   `Retry-After`.
6. boto3 calls run in a worker thread (`asyncio.to_thread`).
7. The task role may only `s3:PutObject` and `s3:GetObject` under each document prefix it needs, plus
   `kms:GenerateDataKey` and `kms:Decrypt` on the bucket key. No `s3:ListBucket` and no `s3:*`.
8. Local development and CI use Floci through `AWS_ENDPOINT_URL` with path-style addressing and fixed test
   credentials. Only then the backend creates the bucket at startup; in AWS Terraform creates it. Behind
   docker compose, `S3_PUBLIC_ENDPOINT_URL` signs links with an address the browser reaches. CI pins the Floci
   image version.

## Consequences

- Easier: the next document (claims evidence) reuses the port, the key rules and the role policy pattern.
- Harder: Floci does not check signatures, so "a link only opens its own key" rests on SigV4 in AWS and on
  unit tests of the signed parameters, not on an emulator test.
- A link shared within its 15 minutes opens the document for whoever has it; the short expiry and
  `no-store` limit that window.
