# Tasks

## 1. Contract artifacts (this change)

- [x] 1.1 Confirm all planning artifacts exist for this change; verify with `openspec status --change define-solana-data-provider-contract` showing proposal, specs, design and tasks done
- [x] 1.2 Pass strict change validation; verify with `openspec validate define-solana-data-provider-contract --strict --no-interactive`
- [x] 1.3 Pass the full repository gate after adding the change artifacts; verify with `pwsh -NoProfile -File .codex/scripts/verify-test-integrity.ps1`, `.\mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive` and `openspec doctor` all succeeding

## 2. Planning-document sync

- [x] 2.1 Mark remediation package F1 as design-complete in `docs/DELIVERY_PLAN_FIXES.md` with the owner-action spike list recorded; verify the documentation conventions test suite stays green in the full gate

## 3. Owner spike actions (require owner API keys and possibly paid tiers; not completable inside the repository)

- [ ] 3.1 Execute spike S1 (live transport, ≥24h finalized stream, replay and quota behavior) and record the evidence note under `docs/notes/`; verification: dated evidence note with measured latency, gap and quota numbers
- [ ] 3.2 Execute spike S2 (historical completeness over the 180-day envelope for watched programs) and record the evidence note; verification: completeness spot-check results plus cost estimate
- [ ] 3.3 Execute spike S3 (at-slot account state for pool liquidity reconstruction) and record the evidence note; verification: feasibility, limits and cost of historical pool state reads
- [ ] 3.4 Execute spike S4 (GoPlus Solana and RPC risk facts: fields, provenance, TTL, historical access) and record the evidence note; verification: field availability matrix with dates
- [ ] 3.5 Execute spike S5 (holder-count series availability and reproducibility) and record the evidence note; verification: historical holder series feasibility statement
- [ ] 3.6 Record the provider selection decision referencing the spike evidence; verification: decision note in `docs/notes/` naming primary transport, history source and specialized sources with the evidence notes it relies on
