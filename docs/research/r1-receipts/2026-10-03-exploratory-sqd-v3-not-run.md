# Exploratory SQD v3 stop receipt — 2026-10-03

`BLOCKED / NOT_RUN`; zero public v3 requests and USD0 public-run cash. The intended public root `C:\crypto-research-evidence\r1-d1\exploratory-sqd-v3` is absent. No v3 public manifest, raw payload or measured availability exists. This is a prelaunch stop, not provider failure, zero availability, D1 passage or archive.

## Why the branch stopped

The ordinary third repair implementation remains in the working tree, unapplied to any public source run and not independently approved. Existing targeted tests passed87/87 (7new v3+80preserved), but the Reviewer found that genuine nonzero setup time makes a valid local run's offline replay fail exact timing checks. Targeted GREEN therefore did not establish run/replay correctness.

The owner's latest direct instruction permitted one exceptional timing-only reconciliation with inclusive0..2000ms per-step start overhead, preserving every other elapsed/order/budget/accounting/hash check, the same Reviewer, new behavioral RED/GREEN and one free fresh-v3 run plus replay. It superseded delegated Claude `b9ee` parking advice; ordinary repair rounds1/2/3 remained consumed, with no budget reset or further fix.

Before any exception edit, Builder's seam inspection, Architect and the same independent Reviewer agreed that unchanged metadata is insufficient:

- requestOnce records attempt endMs/elapsedMs before validation and raw write;
- retryGroup publishes elapsed after raw write;
- final-group post-write duration is unrecorded, and a following start cannot distinguish previous-group write time from next-group setup.

Deriving requestBegan=endMs-elapsedMs can reconcile bounded setup/spacing but cannot recover exact published group/profile elapsed or its deterministic summary hash. Inventing elapsed, weakening aggregate/hash checks, changing metric semantics or adding timing provenance exceeds this exception. Main applied the owner's no-further-fix terminal condition after the independent technical findings; this was not a new owner decision declaring the technical block. No exception code or RED was written; the original87 tests remain unchanged.

## Preserved evidence and gates

External preflight logs remain at `C:\crypto-research-evidence\r1-d1-exploratory-v3-preflight-20261003`:

- `main-v3-tests.log/.exit`:7tests, fail0/skip0, exit0;
- `main-existing-tests.log/.exit`:80tests, fail0/skip0, exit0;
- `main-v2-preserved-replay.log/.exit` and `main-v2-preserved-replay-post-build.log/.exit`: original v2 integrity replay preserved, exit2 for INCOMPLETE;
- Builder RED/GREEN logs and Main integrity/strict/doctor/diff logs remain intact; none grants independent v3 APPROVE.

The three exact v2 source files remain in that preflight folder's `v2-source` checkpoint. The immutable [v1](2026-10-03-exploratory-sqd-v1.md) and [v2](2026-10-03-exploratory-sqd-v2.md) evidence roots are untouched. No public root was created or deleted; no public command or new provider request was executed for v3.

Tasks1.26 (independent review/prelaunch approval) and1.27 (public run/replay) remain unchecked. Section1e is stopped/deferred, not successfully completed. The separate offline field-accounting correction and mapper dependency retain their own review/full-gate requirements. Task1.18 and full D1 section2 remain unchecked; methodology, holdout, source-admission blockers, financial ceilings and false D1 flags remain unchanged. The active D1 change is not archivable.

Same Reviewer APPROVE covers this factual stop documentation only, never the v3 source implementation. Main's stop-documentation checks strict/doctor/diff/cached-diff all exited0; logs remain at `C:\crypto-research-evidence\r1-d1-v3-stop-preflight-20261003\main-{strict,doctor,diff,cached-diff}.log/.exit`. Main reconfirmed the public root absent. No source review/run checkbox is completed by these checks.
