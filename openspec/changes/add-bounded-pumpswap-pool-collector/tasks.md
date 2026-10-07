PLAN ONLY, 2026-10-07. All tasks below are future and unchecked; live execution is BLOCKED_FOR_LAUNCH.

## 1. Raw capture and durable state

- [ ] 1.1 Establish meaningful regression RED for finalized-only capture, lost checkpoint after raw commit, equal retry/conflict and same-slot state ambiguity; record exact requirement/assertion/expected-actual. Implement additive marketdata persistence/session/lease/dispositions; verify targeted PostgreSQL GREEN and populated V12 compatibility with `mvnw.cmd -Dit.test=PumpSwapPoolCaptureIT,RawTransactionStorageIT -Dtest=PumpSwapPoolCaptureTest -Dsurefire.failIfNoSpecifiedTests=false verify` (new named tests must actually execute).
- [ ] 1.2 Implement JDK finalized WebSocket/HTTP transport with fixture/mock-server RED for null/provisional/failed TX, secrets, truncated/oversized payloads, duplicate/out-of-order delivery and every byte/time/request/cost stop. Verify `mvnw.cmd -Dtest=PumpSwapPoolCaptureTest -DskipITs test` GREEN without keys/provider access; preserve establishing tests and report tests_changed_after_red.

## 2. Interruption and observable status

- [ ] 2.1 Establish RED and implement finite reconnect/transaction overlap recovery, unrecoverable state gap, queue overflow, lease fencing and cumulative limits across restart. Verify capture unit/PG commands above, including crash/retry and two contenders; no recovered-state claim from current reads.
- [ ] 2.2 Add synchronous immutable start/stop/status through marketdata API and replayable raw/configuration/dispositions, with no signal/evaluation admission. Verify status counts/gaps through capture tests and existing `ApplicationModules.verify()` architecture verification in the complete Maven gate.

## 3. Independent verification and launch barrier

- [ ] 3.1 Fresh independent Reviewer checks stable requirements/diff/RED/GREEN, affected CI, budgets and tests_changed_after_red; resolve blockers preserving raw evidence/acceptance. Verify consolidated APPROVE with evidence; author cannot self-approve.
- [ ] 3.2 Update affected module/Architecture/Operations docs to verified scope after APPROVE in assigned DOCS_CLOSE; Main runs `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, host `docker version`, `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor`, `git diff --check`. Verify actual exits/test counts; no current gate/archive claim.
- [ ] 3.3 Before a live sample obtain owner allowlist and fresh finite expense/run authority, sanitized actual account cap/alert/entitlement/retention and pricing evidence; verify startup blocks if missing. Only an explicitly authorized bounded run reports actual RPC/CU/bytes/PG/WAL/freshness/gaps and terminal scope; no R1 restart or production-readiness inference. This task remains blocked for launch, not authority to execute from planning.
