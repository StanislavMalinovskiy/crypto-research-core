## 1. Agent Routing Contract

- [ ] 1.1 Reconcile `AGENTS.md`, `docs/AGENT_WORKFLOW.md` and the Architect, Developer, Tester and Reviewer TOML files so skeleton, targeted-test, full-verification and single repair-counter ownership match the spec; verify only Reviewer can emit `CODE_WRONG`, `TEST_WRONG` or `SPEC_AMBIGUOUS` and Tester emits `TEST_SUSPECT` instead.
- [ ] 1.2 Verify Developer guidance explicitly forbids test/fixture/configuration edits, test disabling or narrowing, test-specific production branches and full `mvnw clean verify`, while retaining targeted Surefire/Failsafe execution and the `TEST_SUSPECT` exit.

## 2. Mechanical Test-Integrity Gate

- [ ] 2.1 Add focused convention-test coverage for allowed Java test source and for disabled, ignored and unconditional-false-assumption examples; verify the new focused tests fail for every forbidden example and pass for the allowed example.
- [ ] 2.2 Extend repository Java test-source inspection with the proven narrow checks and verify `mvnw.cmd test -Dtest=RepositoryConventionsTest` passes on the repository.
- [ ] 2.3 Add XML-scoped checks for test-skip and test-selection configuration in Surefire, Failsafe, Maven properties and profiles; verify representative forbidden configurations are rejected while Maven Enforcer dependency exclusions remain allowed.
- [ ] 2.4 Strengthen quality-gate inspection against committed Maven skip, selection and narrowing flags or environment settings; verify the current exact `./mvnw clean verify` workflow passes.

## 3. Verification

- [ ] 3.1 Run `mvnw.cmd clean verify` from the repository root and record a successful complete unit, integration and repository-convention result.
- [ ] 3.2 Run `openspec validate --all --strict --no-interactive` and `openspec doctor`, then confirm the implementation diff contains no production, dependency, module, schema, migration or unrelated changes.
