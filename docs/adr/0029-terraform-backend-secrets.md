# ADR 0029 · Infrastructure: backend secrets in Secrets Manager, injected through ECS `secrets`

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-15 |
| Story | none (`specs/HOTFIX-secretos-en-ecs`) |
| Deciders | The team (to be confirmed) |
| Supersedes | Rule 5 of ADR 0018 |

## Context

- Rule 5 of ADR 0018 lets secrets reach the backend container as plain environment variables, accepting
  that they are readable in the ECS task definition and in the Terraform state, and asks for a new ADR to
  move them to a secrets manager.
- `HOTFIX-configuracion-de-produccion` (RC-33) already moved `DB_PASSWORD` to AWS Secrets Manager
  (`solventa-rds-password-<env>`, `terraform/modules/rds/main.tf`), encrypted with the RDS KMS key
  `aws_kms_key.rds` (automatic rotation) and injected through the `secrets` block.
- `JWT_SECRET_KEY`, `OTP_SECRET_KEY`, `FIREBASE_CREDENTIALS_JSON` and `ORACLE_SECRET` were still written
  into the `environment` block of the task definition in `terraform/modules/ecs/main.tf`. Anyone with
  `ecs:DescribeTaskDefinition` could read them, and every old revision kept them.
- CI fills the values from GitHub secrets as `TF_VAR_*` (`.github/workflows/deploy.yml`, `destroy.yml`);
  the team wants to keep that entry point. The workflows pin Terraform 1.10.5, which has no write-only
  arguments.
- The backend reads its configuration from environment variables (`backend/app/core/config.py`) and
  should not need code to fetch secrets.

## Options considered

| Option | Pros | Cons |
|---|---|---|
| A. Keep plain environment variables (rule 5 of ADR 0018) | No change | Values readable by any ECS reader and in every task definition revision |
| B. Secrets Manager with a customer-managed KMS key, injected through ECS `secrets` | Same mechanism as `DB_PASSWORD`; the task definition only holds ARNs; reading needs IAM and KMS; the backend is unchanged | Values still in the Terraform state while CI writes them through Terraform; one more AWS service in the path of task start |
| C. SSM Parameter Store `SecureString`, injected through ECS `secrets` | Cheaper; same injection | A second secrets service next to the one RDS already uses; no rotation hooks |
| D. The backend fetches secrets with the SDK at start-up | Values never in the container environment | Code and IAM on the task role; a compromised process could read more; local development changes |

## Decision

Option B. Backend secrets live in AWS Secrets Manager and reach the container only through the ECS
`secrets` block.

Rules:

1. A backend secret is never a value in `environment`, a variable default or a committed `.tfvars` entry.
   It is a `sensitive` Terraform variable that CI fills from its encrypted secrets (`TF_VAR_*`).
2. The `ecs` module stores each secret as `aws_secretsmanager_secret` named
   `<project>-<name>-<environment>`, encrypted with a customer-managed KMS key, and the task definition
   references it by ARN (`valueFrom`). The task definition depends on the secret versions and on the
   execution role policy.
3. Only the task execution role reads the secrets: `secretsmanager:GetSecretValue` on the exact ARNs of
   the task and `kms:Decrypt` on the key, conditioned on `kms:ViaService = secretsmanager.<region>.amazonaws.com`.
   The task role has no Secrets Manager permissions.
4. An optional secret with an empty value is not created and not injected; the backend treats the missing
   variable as the feature being off.
5. After a deployment that removes values from the task definition, the old revisions are deregistered and
   deleted, and the exposed values are rotated (`docs/ci-cd.md`).

## Consequences

- Easier: new secrets follow one path; reviews check `secrets` and the execution role policy; the backend
  and local development are unchanged.
- Harder: a wrong ARN or a missing permission stops the task from starting (the circuit breaker rolls
  back); once old revisions are deleted the circuit breaker cannot go back to them.
- With `recovery_window_in_days = 0`, emptying an optional secret in GitHub deletes it at the next deploy
  with no recovery window; the value must be loaded again from its source.
- Migration: done at once by `HOTFIX-secretos-en-ecs` for the four backend secrets; `DB_PASSWORD` already
  followed it.
- Pending, each for its own change:
  - **Rotation:** values are rotated by hand (new GitHub secret and deploy); the values that were in the
    task definition must be rotated once (T-06 of the hotfix). Generating `JWT_SECRET_KEY` and
    `OTP_SECRET_KEY` inside AWS is a planned window, since changing them closes sessions.
  - **Values in the state:** `aws_secretsmanager_secret_version.secret_string` keeps them in the state and
    its previous versions. Removing them needs `secret_string_wo` with Terraform 1.11 or later in the
    workflows and a version number per secret.
  - **Dedicated KMS key:** the RDS key encrypts the database, its password and these secrets; a key only
    for backend secrets, with a key policy limited to the execution role and the deploy identity, removes
    that coupling.
  - **`kms:ViaService`:** added to the execution role policy by the hotfix; a dedicated key should carry
    the same condition in its key policy.

## Security and privacy impact

- Removes a disclosure path: identities that can read ECS but not the secrets no longer see the token
  signing key, the one-time code MAC key, the Firebase service account or the oracle key.
- Adds a trust boundary between the task definition (readable by operations) and Secrets Manager
  (readable only by the execution role through KMS); CloudTrail records `GetSecretValue` and `kms:Decrypt`.
- Residual risks: values in the Terraform state and its versioned bucket; values read from old revisions
  before they were deleted (rotation required); one KMS key shared with the database. No personal data is
  involved. Who accepts the residual risks is recorded in the hotfix spec; agents do not accept them.
