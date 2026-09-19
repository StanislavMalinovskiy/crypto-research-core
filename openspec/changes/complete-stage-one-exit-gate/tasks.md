## 1. Make the delivery route visible

- [x] 1.1 Expand `docs/DELIVERY_PLAN.md` with a carefully ordered, one-to-two-line work-package map for every stage, current statuses and an explicit adaptability rule; verify it remains concise, links detail instead of duplicating OpenSpec, and does not advance Stage 1 before remote evidence.

## 2. Prove the remote Stage 1 gate

- [ ] 2.1 Record the exact local `HEAD`, verify it equals the primary remote branch, run `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`, and use authenticated GitHub access to prove that the same SHA has a successful `quality-gate` run and that `quality-gate` is required on the primary branch; if access, platform support or the setting is missing, leave this task incomplete and report the exact blocker without changing GitHub administration unless the user separately authorizes it.

## 3. Advance the Delivery Plan

- [ ] 3.1 Only after task 2.1 passes, update `docs/DELIVERY_PLAN.md` so Stage 1 is `Done`, Stage 2 is `Current`, and `build-first-signal-evaluation-skeleton` is the current/next business work; then repeat the applicable Maven/OpenSpec/Git gates and independently verify the two delta specs, remote evidence, documentation diff, preservation of `.codex/` and agent-workflow documents, and absence of business, dependency, schema, module or DAG changes before apply is declared complete.
