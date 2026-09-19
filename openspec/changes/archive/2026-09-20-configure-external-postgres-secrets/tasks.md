## 1. Establish configuration behavior

- [x] 1.1 Revise the configuration-focused test to use only a synthetic three-value connection and prove the `managed` profile configures one datasource identity inherited by Flyway; run the targeted test before changing production configuration and record a valid behavioral assertion failure.
- [x] 1.2 Revise and rerun the test contract to prove any missing managed connection setting fails without local-default fallback, the default profile remains compatible with local Compose, and automated tests do not require the workstation secret file.

## 2. Implement safe workstation configuration

- [x] 2.1 Reduce `config/application-managed-secrets.example.properties` and the ignored local-file contract to one mandatory JDBC URL, username and password without reading or modifying the populated operator-owned file; keep the exact ignore rule and verify it with `git check-ignore -v config/application-managed-secrets.properties` plus `git status --short --untracked-files=all` without exposing local values.
- [x] 2.2 Revise `application-managed.yaml` and managed-setting validation to require only the shared datasource values and let Flyway inherit them; verify the targeted managed-profile tests pass without contacting the shared database.
- [x] 2.3 Verify the secrets file is absent from the built JAR and that no real host, username, password or certificate path appears in tracked files or test reports.

## 3. Document operation

- [x] 3.1 Revise `README.md` and `docs/OPERATIONS.md` for one shared datasource/Flyway identity, three-value copy/fill/start instructions, explicit `managed` activation, credential rotation, TLS guidance, readiness/Flyway checks, and the rule that secret values are never committed or pasted into agent prompts.
- [x] 3.2 Revise `docs/TESTING.md` for the shared-identity configuration test and retain the rule that Maven/CI use Testcontainers and never the ignored external configuration; keep Stage 3.0 `Current` in `docs/DELIVERY_PLAN.md` because server version, firewall and backup/restore evidence remain outside this change.

## 4. Verify the complete change

- [x] 4.1 Run the targeted managed-profile configuration tests and confirm all expected shared-identity startup and isolation scenarios pass.
- [x] 4.2 Run `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1` and `mvnw.cmd clean verify` from the repository root and record zero failures, errors and skips.
- [x] 4.3 Run the installed OpenSpec CLI equivalents of `openspec validate --all --strict --no-interactive` and `openspec doctor`, then run `git diff --check` and confirm all checks pass before marking the change ready for review.
