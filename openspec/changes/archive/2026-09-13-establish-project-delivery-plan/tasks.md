## 1. Delivery map

- [x] 1.1 Create `docs/DELIVERY_PLAN.md` with purpose/source boundaries, current position, seven outcome-oriented stages, deferred decisions and the maintenance rule; verify it uses only the agreed status vocabulary and contains no implementation-level Java, schema, library or algorithm inventory.
- [x] 1.2 Represent Stage 1 as current until the delivery-plan change is archived and an authorized reproducible foundation checkpoint exists; identify `build-first-signal-evaluation-skeleton` as the next business change and verify execution remains Deferred.
- [x] 1.3 Keep each stage to two to four major workstreams or change groups where useful, omit empty template fields, and verify the document remains close to the 90–120-line editorial target without adding an automated length check.

## 2. Source navigation

- [x] 2.1 Add `docs/DELIVERY_PLAN.md` to the README document map and verify the relative link resolves.
- [x] 2.2 Add one source-responsibility entry to `AGENTS.md` assigning stage sequence and Current/Next ownership to the Delivery Plan; verify existing authority and conflict rules remain unchanged.
- [x] 2.3 Replace only duplicated operational sequencing in `docs/ROADMAP.md` with a link to the Delivery Plan; verify product hypotheses, long-term capabilities and directional priorities remain in the Roadmap.

## 3. Consistency and verification

- [x] 3.1 Review `DELIVERY_PLAN.md`, `ROADMAP.md`, `README.md`, `AGENTS.md` and the repository-conventions delta together; verify Current/Next, source boundaries, stage names and execution deferral are consistent and no unrelated architecture or business scope changed.
- [x] 3.2 Run `mvnw.cmd clean verify` and verify the existing repository Markdown-link and architecture checks pass without a new line-count or CI gate.
- [x] 3.3 Run `openspec validate establish-project-delivery-plan --strict --no-interactive`, `openspec validate --all --strict --no-interactive` and `openspec doctor`; verify all commands pass.
- [x] 3.4 Run `git diff --check` and `git status --short`; verify there are no whitespace errors, no staging/commit/push was performed, and the final inventory contains only intended additions plus pre-existing user changes.
