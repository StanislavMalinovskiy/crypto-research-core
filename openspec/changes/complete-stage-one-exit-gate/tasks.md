## 1. Prove the remote Stage 1 gate

- [ ] 1.1 Tester records the exact local `HEAD`, verifies it equals the primary remote branch, runs `mvnw.cmd clean verify`, `openspec validate --all --strict --no-interactive`, `openspec doctor` and `git diff --check`, and Researcher or Tester uses authenticated GitHub access to prove that the same SHA has a successful `quality-gate` run and that `quality-gate` is required on the primary branch; if access, platform support or the setting is missing, leave this task incomplete and report the exact blocker without changing GitHub administration unless the user separately authorizes it.

## 2. Advance the Delivery Plan

- [ ] 2.1 Only after task 1.1 passes, Developer updates `docs/DELIVERY_PLAN.md` so Stage 1 is `Done`, Stage 2 is `Current`, and `build-first-signal-evaluation-skeleton` is the current/next business work; then Tester repeats the applicable Maven/OpenSpec/Git gates and Reviewer independently verifies the two delta specs, the remote evidence, the narrow documentation diff, preservation of `.codex/` and `docs/AGENT_WORKFLOW.md`, and absence of business, dependency, schema, module or DAG changes before apply is declared complete.
