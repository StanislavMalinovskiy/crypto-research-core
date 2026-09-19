## 1. Establish configuration behavior

- [x] 1.1 Add a configuration-focused test that uses only synthetic connection values and proves the `managed` profile binds distinct runtime and Flyway identities; run the targeted test before production configuration and record a valid behavioral assertion failure.
- [x] 1.2 Extend the test contract to prove missing managed secrets fail without local-default fallback, the default profile remains compatible with local Compose, and automated tests do not require the workstation secret file.

## 2. Implement safe workstation configuration

- [x] 2.1 Add `config/application-managed-secrets.example.properties` with placeholder-only mandatory runtime/Flyway JDBC and credential fields, create blank ignored `config/application-managed-secrets.properties`, add its exact path to `.gitignore`, and verify it with `git check-ignore -v config/application-managed-secrets.properties` plus `git status --short --untracked-files=all`.
- [x] 2.2 Add `application-managed.yaml` with a required native import and no development fallback, map separate datasource and Flyway values, and verify the targeted managed-profile tests pass without contacting the shared database.
- [x] 2.3 Verify the secrets file is absent from the built JAR and that no real host, username, password or certificate path appears in tracked files or test reports.

## 3. Document operation

- [x] 3.1 Update `README.md` and `docs/OPERATIONS.md` with copy/fill/start instructions, explicit `managed` activation, credential rotation and separation, TLS guidance, readiness/Flyway checks, and the rule that secret values are never committed or pasted into agent prompts.
- [x] 3.2 Update `docs/TESTING.md` to state that Maven/CI use Testcontainers and never the ignored external configuration; keep Stage 3.0 `Current` in `docs/DELIVERY_PLAN.md` because server version, firewall and backup/restore evidence remain outside this change.

## 4. Verify the complete change

- [x] 4.1 Run the targeted managed-profile configuration tests and confirm all expected startup, separation and isolation scenarios pass.
- [x] 4.2 Run `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1` and `mvnw.cmd clean verify` from the repository root and record zero failures, errors and skips.
- [x] 4.3 Run the installed OpenSpec CLI equivalents of `openspec validate --all --strict --no-interactive` and `openspec doctor`, then run `git diff --check` and confirm all checks pass before marking the change ready for review.
