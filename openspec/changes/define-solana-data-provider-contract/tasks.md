# Tasks

## 1. Contract artifacts (this change)

- [x] 1.1 Confirm all planning artifacts exist for this change; verify with `openspec status --change define-solana-data-provider-contract` showing proposal, specs, design and tasks done
- [x] 1.2 Pass strict change validation; verify with `openspec validate define-solana-data-provider-contract --strict --no-interactive`
- [x] 1.3 Pass the full repository gate after adding the change artifacts; verify with `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `.\mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive` and `openspec doctor` all succeeding

## 2. Planning-document sync

- [x] 2.1 Mark remediation package F1 as design-complete in `docs/DELIVERY_PLAN_FIXES.md` with the owner-action spike list recorded; verify the documentation conventions test suite stays green in the full gate

## 3. Provider spike actions (S1 under D10; additional autonomous investigation under D11)

D11 authorizes bounded zero-paid investigation and offline query preparation for tasks 3.2-3.6. Its partial evidence does not automatically complete a spike or provider selection. Existing checked planning tasks record earlier verification; all new executable work retains behavioral testing, independent review and Main's complete gate.

D11's manual-continuation addendum governs the one finite continuation of previously unexecuted public requests: immutable prior receipt, aggregate caps and original absolute deadline, Main-owned cutoff/exclusive parent-wide preflight, fresh review and complete gate. It authorizes no S1 restart or additional helper changes and completes no task below.

D10's V1 addendum governs the live-evidence compatibility repair and the single explicit `smoke-retry-1` attempt. The 5min/100 MB smoke limits are aggregate across the original attempt and that retry; the original evidence and all shared RPC/disk debits remain. Repair round 3 needs current-Reviewer authorization and fresh behavioral RED/GREEN before another complete gate or live launch.

- [ ] 3.1 Prepare and execute the bounded Alchemy PAYG S1 experiment under design D10 and its approved resource ceilings (10 GB total local evidence, 30 GB free-space floor, smoke 5min/100 MB, full 100 GB received and shared 20,000 RPC attempts): ≥6h actual LIVE finalized observation excluding disconnect/replay, forced 5min and 1h disconnects with replay and Alchemy RPC/history reconciliation, and a hard 10h wall-clock limit (36,000 seconds); verification: behavioral RED/GREEN via `node --test tools/spikes/alchemy-s1/test/safety.test.cjs` with any additional test paths explicitly listed as required by D10, independent safety review, Main's repository gate, then dated `docs/notes/` evidence with raw-evidence/recovery references, latency, messages/bytes/slots, duplicates/out-of-order/gaps, recovery results, dashboard usage comparison and provider quota/exhaustion evidence, plus explicit PASS/INCONCLUSIVE/FAIL under D10 (early budget stop is not PASS; local budget tests do not substitute for provider quota evidence)
- [ ] 3.2 Execute spike S2 (historical completeness over the 180-day envelope for watched programs) and record the evidence note; verification: completeness spot-check results plus cost estimate
- [ ] 3.3 Execute spike S3 (at-slot account state for pool liquidity reconstruction) and record the evidence note; verification: feasibility, limits and cost of historical pool state reads
- [ ] 3.4 Execute spike S4 (GoPlus Solana and RPC risk facts: fields, provenance, TTL, historical access) and record the evidence note; verification: field availability matrix with dates
- [ ] 3.5 Execute spike S5 (holder-count series availability and reproducibility) and record the evidence note; verification: historical holder series feasibility statement
- [ ] 3.6 Record the provider selection decision referencing the spike evidence; verification: decision note in `docs/notes/` naming primary transport, history source and specialized sources with the evidence notes it relies on
