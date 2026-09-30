# Solana provider spike research — 27 September 2026

Research cutoff: 2026-09-27 17:42 UTC; later updates carry their own times below. This is a non-normative research note for change `define-solana-data-provider-contract`, initially recorded under `openspec/changes/define-solana-data-provider-contract`, specifically design D11. Its dated artifacts remain discoverable through [OpenSpec change history](../../openspec/changes/archive). The initial entry records public documentation research, local access metadata and offline preparation evidence, not successful provider probes. D10 and D11 remain the controlling execution contracts for those entries; later authorized D12/D13 status is recorded below.

## Evidence state

At the original research cutoff, no S2-S5 data API requests or historical downloads had been performed. Later dated sections record the stopped public metadata attempt and bounded continuation, including measured partial S2/S4 evidence. Those measurements do not establish complete capability coverage or a spike PASS. The dashboard baseline remains separate from probe results.

At the original research cutoff, Main reported 20 assertion-level S1 RED failures and implementation underway, with no live provider call. That original RED evidence is in the Builder transcript, not a retained log file. The Architect inspected the safety test source but did not rerun RED. The earlier Node 20 wildcard-discovery failure was not behavioral RED. The offline preparation update below supersedes that implementation status; no six-hour observation or transport verdict is claimed.

Local metadata inspection found the four D10 Alchemy setting names present in the ignored properties file, without exporting values. Under the checked names, Helius, Bitquery, GoPlus and SQD credentials were absent from both that file and the process environment. This is a limited name-presence check, not a search of the whole machine, proof that the accounts do not exist, or verification of any entitlement.

## S1 dashboard baseline — sanitized update at 18:06 UTC

On 2026-09-27 Main inspected owner-supplied dashboard screenshots and reported the following pre-run baseline while S1 was still offline. These are transcribed display values; the Architect did not inspect the images or their payment-method details.

| Dashboard field | Reported display |
|---|---|
| Active plan | Pay As You Go |
| Usage period | September 2026, UTC |
| Monthly usage total | 100 CU |
| Configured monthly limit | 28,571,429 CU, approximately 28.6 million CU |
| Peak throughput | 0.5 CU/s against displayed capacity of 10,000 CU/s |
| September 27 invoice | $0 |

The HTTPS tab was selected. A gRPC tab existed, but no separate gRPC usage value was visible; gRPC usage is therefore unverified, not zero. The screenshots' 23:05 local file-modification time does not establish their capture time. The update time above records this note's transcription, not the dashboard measurement or capture time.

This is a pre-run account baseline, not post-run usage comparison, local-counter/dashboard equality, verified billing attribution or quota-exhaustion evidence. The displayed account limit does not change D10's approved local caps or authorize additional spending. No raw image links or payment-method information are included. The screenshot files were not modified or staged as part of this documentation update.

## S1 offline preparation — reviewed update at 18:08 UTC

Main supplied the fresh Reviewer's APPROVE after repair round 1. The repaired defects concerned smoke-inspection enforcement, reversed reconciliation ranges, final stream termination and Windows process containment. Scope was reported as 14 files and 1,005 handwritten nonblank lines. D10's observable contract and budgets are unchanged.

The targeted command was:

```text
node --test tools/spikes/alchemy-s1/test/safety.test.cjs tools/spikes/alchemy-s1/test/repair.test.cjs
```

| Evidence | Result |
|---|---|
| Original behavioral RED | 20 assertion failures, recorded in the Builder transcript; no original log file |
| Repair 1 RED | 34 executed, 23 passed, 11 assertion failures, zero cancelled/skipped; local `tools/spikes/alchemy-s1/repair-1-red.log` |
| Repair 1 GREEN | 34 executed, 34 passed, zero failures/cancelled/skipped; local `tools/spikes/alchemy-s1/repair-1-green.log` |
| Fresh independent review | Main reports a separate 34-test passing rerun and the full CI-01..CI-15 matrix passing or justified not applicable |

The Architect inspected the two local log summaries for this documentation update; review approval and the independent rerun are attributed to Main's reviewer handoff. `tests_changed_after_red=true` records the authorized additive repair tests; the original 20 establishing tests remained unchanged. Task-wide repair count is 1.

This closes documentation for reviewed S1 preparation only. No live provider call or complete repository gate has run at this update. Main's full gate remains next; task 3.1 stays unchecked because actual observation, recovery and required provider evidence are outstanding. No archive or S1 PASS is claimed.

## S1 offline preparation — repair-two update at 18:19 UTC

Main supplied the fresh Reviewer's APPROVE after repair round 2. The first complete Maven gate had exited 1: 96 tests executed, one failure, zero errors/skips. `RepositoryConventionsTest.markdownHasNoExportMarkersOrBrokenRelativeLinks` found broken links in installed third-party READMEs. Under the D10 dependency-location addendum, the installed tree was preserved and relocated to `C:/crypto-research-tools/alchemy-s1-deps/node_modules`; Main reports all 1,171 files matched the before/after hash inventory. Runtime and tests use process-local `NODE_PATH`. Repository checks were not narrowed.

Repair 2 added compatibility for the exact literal `${ALCHEMY_API_KEY}` template in the approved Alchemy RPC endpoint shape, resolving the effective key before connecting while retaining validation and redacted failures. No real credential value is reproduced here. Main separately validated the owner's configuration locally with result `CONFIG_VALID_NO_NETWORK`, without exposing values or making a provider call.

The targeted command remains the explicit two-file command above, now executing 49 tests with the external dependency path configured.

| Evidence | Result |
|---|---|
| Repair 2 RED | 49 executed, 47 passed, two expected assertion failures, zero cancelled/skipped; local `tools/spikes/alchemy-s1/repair-2-red.log` |
| Repair 2 GREEN | 49 executed, 49 passed, zero failures/cancelled/skipped; local `tools/spikes/alchemy-s1/repair-2-green.log` |
| Fresh independent review | Main reports an independent 49-test passing rerun, matching dependency inventory and the full CI-01..CI-15 matrix passing or justified not applicable |

The Architect inspected both local log summaries; independent review, inventory verification and real-configuration validation are attributed to Main's handoff. The original 34 tests remained unchanged and 15 regression cases were added. `tests_changed_after_red=true` records the authorized TEST_SPEC_ERROR setup correction and additive cases. Task-wide repair count is 2; it was not reset by replanning.

This remains preparation-only DOCS_CLOSE. No live provider call has occurred at this update. Main must rerun the complete repository gate; the earlier failed gate is not PASS. Task 3.1 remains unchecked, and neither S1 PASS nor archive is claimed.

## S1 measured first smoke and reviewed repair three — update at 18:41 UTC

After the preceding complete gate passed (Main reports 96 unit tests and 48 integration tests, zero skips, plus integrity/OpenSpec/doctor/diff checks), the first live smoke stopped `INCONCLUSIVE/SCHEMA_INVALID`. Launch was `2026-09-27T18:21:33.007Z`; the summary ended at `18:21:36.458Z`. It recorded zero LIVE time, three gRPC messages, one indexed transaction, 10,967 received bytes (10,881 gRPC and 86 RPC), two RPC attempts and `abrupt=false`. The Reviewer identified a decoded V1 transaction rejected by the former version contract, not malformed protobuf. The original receipt at `C:/crypto-research-evidence/alchemy-s1/smoke` remains immutable and incomplete; raw SHA-256 is `8d2352f91bf5c12ed383d7eacc945e0da71315ebc75b97d60217aa4fe596c89c`. The Architect inspected its summary and previously verified that raw digest.

D10's approved V1 compatibility and single bounded smoke-retry addendum now controls supported versions and cumulative accounting; this note changes neither that contract nor any allowance. Original smoke elapsed/byte/RPC charges remain charged, and its unresolved interval is not relabeled recovered.

Main supplied the fresh Reviewer's APPROVE after the explicitly authorized third repair. The explicit two-file test command above now executes 85 tests:

| Evidence | Result |
|---|---|
| Repair 3 RED | 85 executed, 76 passed, nine assertion failures, zero cancelled/skipped; local `tools/spikes/alchemy-s1/repair-3-red.log` |
| Repair 3 GREEN | 85 executed, 85 passed, zero failures/cancelled/skipped; local `tools/spikes/alchemy-s1/repair-3-green.log` |
| Fresh independent review | Main reports an independent 85-test passing rerun and the full CI-01..CI-15 matrix passing or justified not applicable |

The Architect inspected both log summaries. `tests_changed_after_red=true` records the authorized unsupported-version fixture correction from 1 to 2 and 36 additive regression tests; the original 49 tests were otherwise preserved. Scope is reported as 14 files and 1,308 handwritten nonblank lines. Task-wide repair count is 3: both ordinary rounds and the authorized final third round are used; no fourth repair is authorized.

This is reviewed preparation readiness, not S1 PASS. At this update, Main's complete gate rerun is pending and the single smoke retry has not started. Full LIVE observation, forced-gap recovery, dashboard comparison and provider-quota evidence remain outstanding. Task 3.1 stays unchecked; no archive is claimed.

## S1 single retry — measured update at 18:47 UTC

Main reports the post-repair-three gate passed: 96 unit tests and 48 integration tests, zero failures/errors/skips, test-integrity preflight, strict validation of all 12 OpenSpec items, doctor and diff checks. Retained gate logs are `%TEMP%/s1-r3-integrity.log`, `s1-r3-maven.log`, `s1-r3-validate.log` and `s1-r3-doctor.log`.

The one authorized retry ran from `2026-09-27T18:43:57.563Z` to `18:46:05.813Z` and stopped cleanly (`abrupt=false`) with **INCONCLUSIVE / RECEIVED_LIMIT**. The local reservation guard stopped before reserving another in-flight response beyond the aggregate 100 MB ceiling; the measured bytes below are not a budget breach. LIVE time remained zero. Initial recovery was incomplete for slots `451080926` through `451080934`; no completed RPC reconciliation, forced five-minute/one-hour gap or six-hour observation occurred. Failed smoke eligibility prevented a full run.

| Counter | Both smoke attempts, cumulative | Retry only |
|---|---:|---:|
| Received application bytes | 75,861,230 | 75,850,263 |
| gRPC bytes | 47,638,937 | 47,628,056 |
| RPC response bytes | 28,222,293 | 28,222,207 |
| RPC attempts | 14 | 12 |
| Stream starts | 2 | 1 |

The cumulative disk counter is 79,793,516 bytes; it is not a fresh whole-root filesystem inventory. Retry statistics are 5,646 messages, 5,541 indexed transactions/unique identities, zero observed duplicates and zero observed out-of-order records, with highest observed slot `451080947`. Those observations do not prove interval completeness.

Only replay latency is available: provider timestamp-to-receive latency across 5,646 updates ranged from approximately 10.768 to 131.153 seconds (mean 74.688 seconds); chain timestamp-to-receive latency across 52 observations ranged from 22.362 to 130.888 seconds (mean 77.898 seconds). There are no LIVE latency samples. Catch-up traffic with zero LIVE time cannot support GB/day, cost/day or cost/month extrapolation.

For scale only, applying the published $75/TB rate to 47,638,937 measured gRPC application bytes, assuming decimal TB, gives approximately $0.00357. This is an indicative payload-component calculation, not a billed charge or financial fact: RPC charges are additional, wire/billable-byte accounting is unverified, and no post-run invoice/dashboard comparison exists. Published rate checked 2026-09-27: [Alchemy Solana gRPC](https://www.alchemy.com/solana-grpc). The earlier dashboard screenshots remain only a pre-run baseline; provider-quota exhaustion remains unverified and is distinct from this local reservation stop.

Evidence remains under `C:/crypto-research-evidence/alchemy-s1/{smoke,smoke-retry-1}`, including each raw journal, checkpoint, summary, manifest and inspection, plus root `shared-budget.json`. The Architect read the retry summary/checkpoint/inspection and shared ledger, and independently hashed both raw files. Original raw SHA-256 remains `8d2352f91bf5c12ed383d7eacc945e0da71315ebc75b97d60217aa4fe596c89c`; retry raw SHA-256 is `f3ecfdfc9d9c59ccf8b9ebfda92318b0f05196d2f4576b2ab6f7b01f07e9cdeb`. Retry inspection records `incomplete=true` at `18:46:44.861Z`.

S1 is stopped and task 3.1 remains open/INCONCLUSIVE. No further S1 attempt or full run is authorized after this budget stop. Future S1 execution would need an owner-approved new bounded plan and budget treatment preserving all existing evidence and debits; this note requests no new allowance. S2/S4 public probes may proceed only through D11's separate reviewed helper and gate while S1 stays stopped. S3 historical state, S5 reproducible holder history and the evidence-dependent 3.6 selection remain unverified as detailed below. No checkbox or archive is changed.

## S2 — Historical completeness and cost

SQD documents a public Solana dataset, metadata queries and finalized range streaming. Its public service is described as suitable for development/evaluation; advertised paid-plan capacity does not establish capacity available to this workstation. Pricing documentation distinguishes published plans from current public access and points to the pricing page for current terms. Sources consulted 2026-09-27: [Solana API](https://docs.sqd.dev/en/portal/solana/api), [Portal plans](https://docs.sqd.dev/en/portal/pricing) and [pricing](https://sqd.dev/pricing/).

This supports preparing the bounded public sample batch in D11. It does not verify dataset retention, completeness of watched-program instructions, commercial suitability, sustained throughput or a full-envelope cost estimate. No price or throughput measurement is asserted in this note.

Remaining acceptance from D11:

- Fix the UTC reference end and its preceding 180-day envelope. Confirm sample timestamps from returned block data; an estimated slot alone does not establish an age.
- Sample the beginning, middle and end for Pump.fun, PumpSwap and Raydium AMM v4, preserving actual slot ranges, continuation and missing-field evidence.
- Compare the bounded produced-block samples with independent finalized public RPC for signatures, complete instruction positions, inner instructions and native/token balances. Refusal or pruning leaves comparison unverified.
- Report spot-check results and a sourced full-envelope cost estimate using explicit measured payload/throughput assumptions. Empty samples do not establish a program's completeness; successful samples do not prove every slot across 180 days was checked.

Task 3.2 remains incomplete until its required sample, reconciliation and cost evidence exists. Full-range backfill and dedicated-service verification remain unperformed.

## S3 — Historical account state and pool reconstruction

Alchemy documents historical `getAccountInfo` queries using a `slot` parameter and separate history-navigation parameters. It distinguishes a finalized at-slot state from a normal latest-state read; `minContextSlot` is not a historical snapshot request. This identifies the method to investigate, not verified access on the owner's account. Source consulted 2026-09-27: [Solana Account Archive](https://www.alchemy.com/docs/solana/account-archive).

Remaining acceptance is a finite matrix of real pool/state accounts at two distinct finalized slots, repeated reads, verified ownership/reserve inputs and independent evidence of historical correctness. Echoing the requested slot does not establish that the returned account data is historical. Limits, entitlement, cost and liquidity-reconstruction feasibility remain unmeasured.

D11 authorizes zero Alchemy calls. Actual S3 execution needs a later bounded PLAN allocating unused shared S1 RPC/traffic/disk budgets with common accounting. Existing credentials alone provide neither that allocation nor evidence of historical capability. Task 3.3 remains incomplete.

## S4 — GoPlus and current RPC risk facts

GoPlus describes free security APIs and a beta Solana token-security GET endpoint taking token addresses. The endpoint reference lists authorization and possible unauthorized/forbidden responses; anonymous success was not tested. Sources consulted 2026-09-27: [API overview](https://docs.gopluslabs.io/reference/api-overview) and [Solana token security](https://docs.gopluslabs.io/reference/solanatokensecurityusingget).

The feasible D11 experiment is one anonymous public-mint request followed, if available, by the bounded additional mints and repeats, with current mint-authority/supply comparisons through public RPC. Authentication denial stops GoPlus work. No account or credential acquisition is part of this note or the probe plan.

Remaining acceptance is a dated field matrix distinguishing returned values, null/missing/unknown values, provenance, timestamps, TTL and historical support. Equal repeated responses alone do not prove a cache lifetime. Current authority or concentration facts cannot be presented as historical decision-time evidence. Documented absence may be recorded as unsupported; undocumented availability remains unverified. Missing required facts or access leaves task 3.4 incomplete.

## S5 — Holder series and reproducibility

Helius documents paginated token-account queries, including an indexed-slot field, and requires an API key. Token-account records are not automatically a count of distinct positive-balance owners at a stable historical cutoff. Source consulted 2026-09-27: [getTokenAccounts](https://www.helius.dev/docs/api-reference/das/gettokenaccounts).

Bitquery's holder guide describes transfer-based historical aggregates and recent balance-update approaches. Its balance-update page contains a historical-balance limitation, while its retention page differentiates chain, dataset, interface and access tier. These statements do not establish one proven historical-holder contract for the available account. Sources consulted 2026-09-27: [Solana holders](https://docs.bitquery.io/docs/blockchain/Solana/solana-token-holders/), [balance updates](https://docs.bitquery.io/docs/blockchain/Solana/solana-balance-updates/) and [coverage/retention](https://docs.bitquery.io/docs/graphql/data-coverage-retention/).

Remaining acceptance is real evidence spanning at least four hours for the D9 consumer, with complete pagination, explicit owner/balance semantics, stable historical cutoffs, availability provenance, reproducibility, limits and cost. A top-holder subset or recently changed-account population cannot establish a total-holder series. Known credential absence and unresolved historical-coverage claims currently prevent real-sample verification. A documentation-backed feasibility statement is useful partial output; task 3.5 remains open.

## Task 3.6 — Decision dependency

| Required evidence | Latest dated status in this note | Decision consequence |
|---|---|---|
| S1 transport, reconnect, quota and latency | Gate passed; original and single retry INCONCLUSIVE; retry stopped at local received-byte reservation limit with zero LIVE and incomplete initial recovery | Transport suitability unverified; no further S1 execution authorized |
| S2 history, sample reconciliation and cost | Bounded continuation yielded dated headers and two parseable block records in a capped partial response; oldest observed date April 29, not the 180-day boundary; no independent block comparison | Required historical coverage/reconciliation/cost basis unverified |
| S3 at-slot pool-state feasibility | Documentation and an unclassified instruction-account candidate; no historical account reads | Historical liquidity reconstruction unverified |
| S4 risk fields, freshness and history | Two anonymous GoPlus responses and current RPC supply/authority observations; freshness/history and broader risk coverage remain unverified | Useful partial field evidence, not historical risk-fact acceptance |
| S5 reproducible holder series | Current GoPlus count/subset observed; historical access/coverage and full-population reproducibility unresolved | Holder-growth consumer coverage unverified |

The active delta's `Evidence-based provider selection` requirement (`define-solana-data-provider-contract`, `specs/solana-data-contract/spec.md#requirement-evidence-based-provider-selection`; [OpenSpec change history](../../openspec/changes/archive)) requires real-sample capability evidence. The current material supports an investigation shortlist, not a final primary transport/history/specialized-source selection. Task 3.6 remains incomplete; no purchase, production integration, completion checkbox or archive follows from this note.

## S2/S4 request preparation — documentation-only update at 18:37 UTC

These examples were assembled from official sources on 2026-09-27 and have not been sent to a data API. They are inputs for the later reviewed D11 helper, not standalone execution permission. D11's allowlist, single finite batch, response/disk limits and no-retry policy still apply; its public probes wait until S1 is stopped. No credentials or new packages are needed for these examples.

### SQD metadata and sampled finalized instructions

The prepared metadata request is `GET https://portal.sqd.dev/datasets/solana-mainnet/metadata`. Its documented required response fields are `dataset` (string), `aliases` (string array), `real_time` (boolean), and `start_block` (integer). This schema does not provide a latest finalized slot or prove 180-day retention. `fromBlock` and `toBlock` are inclusive; `includeAllBlocks` requests headers even without filter matches. Source: [SQD OpenAPI, DatasetMetadata and DataQuery](https://docs.sqd.dev/openapi.json).

Prepared request: `POST https://portal.sqd.dev/datasets/solana-mainnet/finalized-stream`, `Content-Type: application/json`, without authentication. The numeric range below is an illustrative documentation-era ten-slot range, not a measured or selected 180-day sample; substitute the dated, verified sample bounds before any execution.

```json
{
  "type": "solana",
  "fromBlock": 259984950,
  "toBlock": 259984959,
  "includeAllBlocks": true,
  "fields": {
    "block": { "number": true, "hash": true, "parentNumber": true, "parentHash": true, "timestamp": true },
    "transaction": { "transactionIndex": true, "signatures": true, "accountKeys": true, "loadedAddresses": true, "version": true, "err": true },
    "instruction": { "transactionIndex": true, "instructionAddress": true, "programId": true, "accounts": true, "data": true, "isCommitted": true, "error": true },
    "balance": { "account": true, "pre": true, "post": true },
    "tokenBalance": { "transactionIndex": true, "account": true, "preMint": true, "postMint": true, "preOwner": true, "postOwner": true, "preAmount": true, "postAmount": true, "preDecimals": true, "postDecimals": true }
  },
  "instructions": [{
    "programId": [
      "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P",
      "pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA",
      "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8"
    ],
    "transaction": true,
    "transactionInstructions": true,
    "innerInstructions": true,
    "transactionBalances": true,
    "transactionTokenBalances": true
  }]
}
```

This combines D10's three program IDs with the relation/field shape in SQD's [official Solana benchmark example](https://github.com/subsquid/worker_bench#1000-samples) and the [Solana API field/filter reference](https://docs.sqd.dev/en/portal/solana/api). The three IDs are OR alternatives; related instructions can include other programs and must retain their positions. `instructionAddress` supplies call-tree location. Preserve transaction errors and instruction commitment/error separately; an instruction can have no local error yet be rolled back with its transaction. Field rejection is evidence of incompatibility, not permission to silently omit required coverage fields.

For time-to-slot preparation, fix an end UTC and targets at the beginning, midpoint and end of the preceding 180 days. Obtain a finalized upper slot using the already allowlisted public RPC `getSlot` with `[{"commitment":"finalized"}]`; use `getBlockTime` with a candidate slot and returned SQD header timestamps to bracket/refine each target within the existing batch limit. Small header-only finalized-stream probes can retain `includeAllBlocks` and block fields while omitting item selectors. A skipped/null/pruned RPC slot is not an age observation; use bounded `getBlocks` to locate a produced slot, or leave that target unresolved. An estimated slot alone never establishes coverage. These are proposed discovery inputs, not verified results.

SQD documents a timestamp resolver, but `/timestamps/{timestamp}/block` and `/finalized-head` are outside D11's allowlist and are not authorized by this note. For permitted stream continuation, retain the same end/filters/fields and start the next request after the last returned header number, subject to the existing finite caps. An incomplete/empty/204 response cannot establish full 180-day coverage; preserve the remaining range. Source: [SQD timestamp resolution and continuation](https://docs.sqd.dev/en/portal/solana/api#resolve-a-timestamp-to-a-block).

### GoPlus public example and interpretation

The Solana reference publishes mint `HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3`. Prepared first anonymous request:

```text
GET https://api.gopluslabs.io/api/v1/solana/token_security?contract_addresses=HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3
Accept: application/json
```

Preserve mint case. No `Authorization` header is included; the documented 401/403 possibilities remain an access blocker under D11, not a prompt to obtain credentials. The public example does not imply token safety or current availability. Source: [GoPlus Solana endpoint and example](https://docs.gopluslabs.io/reference/solanatokensecurityusingget).

For the later field matrix, retain `total_supply`, `mintable`, `freezable`, `metadata_mutable`, `default_account_state`, `non_transferable` and their returned authority/status subfields. GoPlus defines nested status `"1"` as the feature being available; default state `"0"`, `"1"`, `"2"` denotes uninitialized, initialized and frozen respectively. Preserve absent/null/unknown distinctly, without boolean coercion or treating missing fields as safe. Compare returned mint/freeze authorities and supply against current finalized public RPC; do not assume supply units without returned decimals/provenance. Source: [GoPlus Solana response definitions](https://docs.gopluslabs.io/reference/response-detail-1).

The consulted Solana endpoint and response definitions specify neither a historical slot/time selector nor a cache TTL. Record those as documentation-unverified, not proven unavailable. Capture actual receive UTC and any returned timestamp/cache headers; repeat equality alone cannot prove freshness. GoPlus's [API overview](https://docs.gopluslabs.io/reference/api-overview) describes real-time security detection, not a historical-access or TTL guarantee. These preparations do not complete S2 or S4 and change no acceptance criterion.

## D11 public-probe preparation — blocked update at 18:57 UTC

Main reports Builder preparation of four files and 331 handwritten nonblank lines, with 29 assertion-level RED failures followed by 29 passing GREEN tests. The establishing 29 tests remained unchanged. Local logs are `tools/spikes/provider-feasibility/red.log` and `tools/spikes/provider-feasibility/green.log`; the Architect inspected their summaries. This test result is not implementation approval or provider evidence.

The fresh Reviewer reproduced a defect in the actual send path: `probe.cjs` requests up to 65,536 bytes per stream read, so a received 16-byte partial body can remain buffered until EOF. On timeout, the reproduction lost that partial raw evidence and its accounting, reporting zero received and zero retained bytes. Main reports review outcome ESCALATE against CI-01/02/15. The Architect inspected the bounded read loop but did not independently rerun the reproduction. The helper therefore cannot safely launch its public batch under D11's existing partial-response preservation contract.

Task-wide repair count is already three and is not reset for D11. No fourth repair is authorized. The fresh read-only technical challenge, `provider_probe_escalation`, returned `STATUS: BLOCKED`, verdict `OWNER_DECISION`, confirming the same 16-byte partial-body loss caused by the 65,536-byte read. A bounded technical fix fits the existing D11 contract, but exhausted repair capacity blocks implementation; replanning does not reset that budget. The exact owner question is: "Authorize one additional bounded D11 partial-response retention/accounting repair followed by independent review and the required gate?" Neither this note nor the earlier GREEN result grants that authorization. S1's permanent stop is unchanged, the public batch remains blocked, and no new provider calls were made.

Main prepared the finite, unexecuted 18-request input at `C:/crypto-research-evidence/provider-feasibility-requests.json`, covering GoPlus/current mint RPC, SQD metadata/header/watched-program samples and bounded RPC comparisons. No helper output child exists and no public-probe batch has launched. Estimated slot candidates do not establish target dates; actual returned timestamps, independent comparisons and a defensible cost basis would still be required for S2 acceptance.

S1 remains stopped/INCONCLUSIVE with its existing receipts, limits and debits preserved. Tasks 3.2/3.4 remain blocked before measured public evidence; S3/S5 are preparation-only, and 3.6 selection is deferred. Tasks 3.1-3.6 remain unchecked. No new allowance, paid call, production work, approval or archive is claimed.

## D11 exceptional repair authorization — update at 19:02 UTC

The owner explicitly approved the pending additional D11 repair: "Разрешаю, делай сам я все разрешаю принимай лучшие решения сам". Main scoped this to one exceptional additional bounded repair of partial-response retention/accounting under the existing D11 contract, followed by independent review and the required gate. This supersedes the earlier lack of repair authorization; it does not reset the three exhausted task-wide rounds or grant an open-ended repair allowance.

Because the earlier Builder session was unavailable, Main assigned replacement same-role Sol medium Builder `/root/d11_builder_recovery`. This records session replacement, not a new task or fresh repair budget. No new observable contract, endpoint, spending allowance or S1 restart is authorized. The public batch remains unexecuted and gated on the repair, independent approval and Main's complete gate; S1 remains permanently stopped/INCONCLUSIVE. No task checkbox or archive status changes.

## D11 exceptional repair — reviewed preparation update at 19:05 UTC

Main supplied the fresh Reviewer's APPROVE for the one owner-authorized exceptional partial-response retention/accounting repair, implemented by replacement Builder `/root/d11_builder_recovery`. This supersedes the implementation blocker above, not the remaining execution gates. The targeted command is `node --test tools/spikes/provider-feasibility/test/probe.test.cjs`.

| Evidence | Result |
|---|---|
| Exceptional repair RED | 31 executed, 29 passed, two assertion failures, zero cancelled/skipped; local `tools/spikes/provider-feasibility/repair-red.log` |
| Exceptional repair GREEN | 31 executed, 31 passed, zero failures/cancelled/skipped; local `tools/spikes/provider-feasibility/repair-green.log` |
| Fresh independent review | Main reports an independent 31/31 passing rerun with zero skips, and the full CI-01..CI-15 matrix passing for every applicable invariant with justified non-applicability |

The two added actual-send regressions established timeout/error partial-body loss: expected retained/accounted bytes 16, actual 0 before the fix. The original 29 tests stayed unchanged; the two additions were frozen after their new behavioral RED. `tests_changed_after_red=true` records the authorized additive regression coverage, not weakened assertions. The Architect inspected both retained log summaries; independent approval is attributed to Main's reviewer handoff. The exceptional repair is used and does not reset the three preceding task-wide repair rounds or authorize further repairs.

This is reviewed helper preparation only. Main's complete repository gate is pending, followed only on PASS by the already authorized single 18-request public batch. No public batch has launched at this update. S1 remains permanently stopped/INCONCLUSIVE; S2/S4 have no measured public evidence yet, S3/S5 remain preparation-only, and 3.6 selection remains deferred. No task checkbox or archive is changed.

## D11 single public batch — measured stop at 19:08 UTC

Main reports the complete gate passed: integrity exit 0, Maven 96 unit tests plus 48 integration tests with zero failures/errors/skips, strict OpenSpec validation 12/12, doctor exit 0, and working-tree/cached diff checks exit 0. Frozen S1 regressions separately passed 85/85. Logs are `%TEMP%/d11-integrity.log`, `d11-maven.log`, `d11-validate.log`, `d11-doctor.log` and `d11-s1-regression.log`.

Main launched the single authorized 18-request list through `tools/spikes/provider-feasibility/cli.cjs`, using `--enable-public`, request input `C:/crypto-research-evidence/provider-feasibility-requests.json`, and evidence root `C:/crypto-research-evidence/provider-feasibility`. The actual list put SQD metadata first; earlier suggested ordering was not the executed order. The command exited 1 with `REQUEST_DEADLINE`, one attempt and 87 received bytes. No retry, continuation or second batch was performed.

The request was `GET https://portal.sqd.dev/datasets/solana-mainnet/metadata`, sent at `2026-09-27T19:07:39.394Z`. The retained result records HTTP 200, receipt/termination at `19:07:54.409Z`, `complete=false`, `reason=REQUEST_DEADLINE`, and 87 received/retained bytes; the batch summary ended at `19:07:54.425Z`. Those elapsed times are the bounded request outcome, not a successful endpoint-latency benchmark. The evidence does not establish whether the missing completion originated at the provider, transport or local client.

Offline extraction of the retained body yields:

```json
{"dataset":"solana-mainnet","aliases":["solana-beta"],"real_time":true,"start_block":0}
```

This is parseable metadata inside a request recorded as incomplete, not a successful history probe. `start_block:0` is an advertised starting block, not verification of continuous retention or 180-day completeness; `real_time:true` is a metadata declaration, not measured real-time performance. The Architect inspected `001.request.json`, `001.result.json`, `summary.json` and `001.raw`, and independently verified raw SHA-256 `94159b8532b84ea61d935367715d8ce51910dd0e3c6f0e5bd8c57a7e7d931723`. Preserve those files and `manifest.json`; do not change the recorded incomplete outcome because the body parses.

All remaining 17 requests were unexecuted: no GoPlus response, current mint RPC comparison, dated SQD header/instruction sample or independent RPC block comparison exists. S2 therefore lacks actual sample dates, watched-program coverage/reconciliation and a measured full-envelope cost basis. S4 still lacks an observed field/provenance/freshness/history matrix. The single batch is stopped; a later batch requires a new bounded plan under D11, not reuse of this output or an automatic restart.

Safe offline follow-up is limited to the retained metadata/provenance and the existing documentation-backed capability matrices. No S2 pool/state samples were obtained from which to populate S3's two-finalized-slot/repeated-read matrix; actual historical account calls remain outside this zero-paid slice and require explicit shared-budget planning. For S5, existing Helius/Bitquery documentation supports a feasibility/query plan only: access/entitlement, full owner population/pagination, stable historical cutoffs and real samples spanning at least four hours remain unverified. Neither the metadata nor the stopped S1 replay supplies that holder history. No additional endpoint, purchase, credential acquisition or budget is authorized here.

S1 remains permanently stopped/INCONCLUSIVE. S2/S4 remain incomplete after this measured stop; S3/S5 remain preparation-only; 3.6 provider selection is deferred for lack of required real-sample capability evidence. Tasks 3.1-3.6 remain unchecked, and no archive or provider PASS is claimed.

## D11 local EOF defect and additional repair allocation — update at 19:11 UTC

After the stopped batch, the Reviewer reproduced a local finite-response completion defect using the actual send path: a response ended with 11 bytes and `responseComplete=true`, but the client did not observe its end event and timed out. The earlier review approval missed this regression. This is evidence of a local EOF-handling bug, not evidence of SQD failure; the original HTTP 200 / 87-byte receipt remains unchanged and incomplete.

The fresh technical challenge returned REPAIR and confirmed that a bounded fix fits the existing D11 contract without replanning. Under the owner's broader autonomous-decision instruction, Main explicitly allocated one further exceptional corrective pass, repair 5, to the same replacement Builder. The preceding exceptional repair was used; this additional allocation is a new explicit Main decision under that delegation, not a retroactive claim that the previous one-only allocation was unlimited or that repair accounting reset.

Current work is restricted to the helper and additive actual-send complete/fragmented/empty/cap regressions. Independent review and the required gate remain necessary after implementation. No public retry or new batch, S1 restart, resource increase or contract change is authorized by this repair allocation. Any proposed manual continuation remains separate planning work; no continuation is approved by this note.

## D11 repair 5 — reviewed preparation update at 19:14 UTC

Main supplied the fresh Reviewer's APPROVE after the explicitly allocated exceptional EOF repair. `tools/spikes/provider-feasibility/repair5-red.log` records 35 executed, 33 passed and two assertion failures: finite/fragmented actual-send responses expected `LIST_EXHAUSTED` but returned `REQUEST_DEADLINE`. `repair5-green.log` records 35/35 passing, zero failures/cancelled/skipped. The Architect inspected both summaries. Main reports an independent 35/35 passing rerun and full CI applicability review. The original 31 tests stayed unchanged; four actual-send finite/fragmented/empty/cap cases were added and frozen after new RED (`tests_changed_after_red=true`). The EOF fix restores existing D11 behavior; it does not change the first receipt or blame SQD.

This closes documentation of reviewed repair preparation, not provider verification. Both separately allocated exceptional repairs are now used, in addition to the preceding three rounds; no repair budget reset or further implementation pass is implied. Main's complete gate must run again. The new D11 operational addendum separately proposes one manually selected continuation under the original aggregate limits and absolute deadline, requiring fresh plan review before gate/launch; it does not authorize automatic retry, additional helper changes, S1 restart, task completion or archive.

## D11 manual-continuation preparation — reviewed update at 19:17 UTC

Main supplied the consolidated Reviewer's APPROVE for the operational addendum and repaired helper, with the full CI-01..CI-15 applicability review. Main independently exercised the native cutoff offline: the owned Node process was killed after 2,026 ms for a two-second test deadline. The actual planned wait is 239 seconds, retaining a one-second margin within the 240-second ceiling and unchanged original absolute deadline. Main also prepared and locally validated the fixed 12-request input by exact copying of the approved original indices; no provider call was made for validation.

This records approved preparation only. The continuation has not launched; Main's complete repository gate remains pending. Original evidence, all aggregate limits, S1's permanent stop and incomplete spike/task statuses remain unchanged. No new semantics, completion checkbox or archive is introduced.

## D11 manual continuation — measured evidence update at 19:21 UTC

Main reports the required post-repair gate passed again: integrity exit 0, Maven 96 unit tests plus 48 integration tests with zero failures/errors/skips, all 12 strict OpenSpec items, doctor and diff checks. Logs are `%TEMP%/d11-r5-*`. Launch preflight measured 79,811,376 parent-evidence bytes and 129,720,971,264 free bytes. Main reports no remaining probe/S1 processes after exit. The fresh Reviewer approved the actual receipt as safely stopped/incomplete, verifying hashes/counts and at least two-second request spacing; this is not spike acceptance.

The approved input ran once under `C:/crypto-research-evidence/d11-continuation-1/provider-feasibility`. The process exited normally with code 1 after 15,289 ms, without a watchdog kill; receipt duration was separately 15,177 ms, ending `2026-09-27T19:19:20.328Z`. It stopped `RESPONSE_LIMIT` on request 8: seven complete HTTP 200 responses, followed by a retained 2,000,000-byte partial SQD response. Continuation totals are eight attempts and 2,018,149 received bytes; including the predecessor, nine attempts and 2,018,236 bytes. This is enforcement of the unchanged per-response cap, not a total-budget breach or evidence of provider failure.

Continuation files total 2,038,424 bytes; predecessor files remain 8,708 bytes. Main verified all five predecessor files unchanged. The Architect independently checked all eight continuation raw hashes against their result records. Four continuation requests were not executed: middle/recent watched-program samples and oldest/recent full RPC block comparisons. Five original pending requests were omitted from the approved selection and likewise remain unexecuted. No further batch or paid call is authorized.

### S2: returned dates and partial instruction evidence

Three completed header responses supplied three consecutive slots each:

| Slots | Returned UTC timestamps | Age relative to declared September 27, 18:44 UTC end |
|---|---|---|
| 416520946-416520948 | April 29, 21:36:23-21:36:24 | Approximately 150.88 days, not 180 days |
| 433800946-433800948 | July 19, 01:28:33-01:28:34 | Approximately 70.72 days, not 90 days |
| 451080945-451080947 | September 27, 18:43:53-18:43:54 | Six to seven seconds before the declared end |

Files `004.raw` through `006.raw` retain header numbers, hashes, parent numbers/hashes and timestamps. The estimated slot grid did not reach the March 31 boundary or June 29 midpoint; returned dates are not relabeled to fit the intended envelope.

The watched-program response `008.raw` has SHA-256 `3a3992850a9d83425feee8e763add3858d332c5d6439b6115f4764403316f850`. Offline line parsing found two complete JSON records for slots 416520946 and 416520947; the third line is truncated and does not parse. The complete records contain respectively 59/57 transactions, 941/900 related instructions, 296/196 native balance entries and 374/437 token-balance entries. Returned fields include transaction signatures/index/version/account keys/loaded addresses/error; instruction positions/program/accounts/data/error/commitment; native pre/post balances; and token transaction index, account, mint, owner, decimals and pre/post amounts. Related instructions include nested call positions and other programs; these counts are not counts of successful swaps.

Observed watched-program instruction counts in those two records are Pump.fun 20/10 and PumpSwap 91/97; no Raydium AMM v4 instruction was observed in the two parsed records. That absence and the truncated third record cannot prove program completeness. No independent RPC block comparison ran. Field presence and two parseable records do not make the transport/range complete, establish the 180-day envelope, or justify a full-envelope cost estimate. S2 stays incomplete.

### S4: dated GoPlus/current-RPC field comparison

The published example mint is `HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3`. Anonymous GoPlus responses were received at `2026-09-27T19:19:05.866Z` and `19:19:17.674Z`; each reports `code:1`, contains 7,891 bytes and has identical SHA-256 `a33bcca6ae38a6ee655839e47195b5c5383690afd7b2893413127acb96d99269`. Current finalized RPC supply and mint-account receipts arrived at `19:19:07.638Z` (context slot 451088805) and `19:19:09.463Z` (451088812). These are separate current observations, not one atomic snapshot; GoPlus provides no source slot in the inspected token object.

| Field | Observed GoPlus value | Current RPC comparison / limitation |
|---|---|---|
| Total supply | String `9999959366.561362` | RPC supply and mint both report integer string `9999959366561362`, decimals 6; exact decimal placement matches. Use integer/string values, not rounded RPC `uiAmount`. |
| Mintability | `mintable.status="0"`, empty authority list | Mint account `mintAuthority:null`; consistent current observation |
| Freezing | `freezable.status="0"`, empty authority list | Mint account `freezeAuthority:null`; consistent current observation |
| Metadata mutability | `status="1"` with a returned upgrade-authority address | Not independently checked by the mint-account request |
| Default account state | String `"1"` | Returned field retained; no independent extension/state comparison |
| Non-transferable | String `"0"` | Returned field retained; no independent extension comparison |
| Holders | `holder_count="305701"`, ten returned holder entries | Provider-reported current count/subset, not a verified full owner population or historical series |

The parsed RPC account is an initialized SPL-token mint owned by `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA`. GoPlus also returns metadata/transfer-related and other risk fields; their mere presence is not a complete independent risk assessment. The inspected token object has no timestamp/slot/TTL/cache field, and the helper does not retain response cache headers. Identical replies approximately 11.8 seconds apart do not establish TTL or freshness. Historical selectors/support remain documentation-unverified; no historical security fact was requested or verified. S4 has useful measured field/provenance evidence, but required freshness/history and wider coverage remain unresolved, so task 3.4 stays unchecked.

### Remaining S3/S5 and selection

Offline S3 preparation can now refer to an actual, fully parseable instruction locator: slot 416520946, transaction index 19, instruction address `[7]`, PumpSwap program. Its first listed account is `9zwcjNKadqrnKRcbyRZc3PeT8tojHBuZAgCU67ksTGc3`. This is an unclassified candidate address, not a verified pool/state account; account roles, ownership, reserves and historical account bytes have not been established. The third truncated block supplies no trusted candidate. No at-slot or repeated historical account reads occurred, and none are authorized by this zero-paid slice. S3 remains incomplete.

S5 still lacks real evidence spanning at least four hours, complete owner-population pagination, stable historical cutoffs and demonstrated repeatability/access/cost. The GoPlus count and ten-entry subset do not substitute for that evidence. Existing Helius/Bitquery documentation remains a feasibility basis only. S1 remains permanently stopped/INCONCLUSIVE; S2-S5 requirements remain unresolved and 3.6 provider selection is deferred. Tasks 3.1-3.6 remain unchecked; no archive follows from these partial results.

## S1 stopped-smoke mechanism — read-only diagnosis, 28 September

Main supplied the independent Reviewer's approved diagnosis from `C:/crypto-research-evidence/alchemy-s1/smoke-retry-1/raw.bin`, `checkpoint.json` and `summary.json`, with accounting in parent `shared-budget.json`. The initial recovery range was slots 451080926-451080934 (nine slots). The first gRPC slot was 451080895, 31 behind the anchor; catch-up waited approximately 96 seconds before RPC reconciliation. At RPC start, 37.09 MB of gRPC payload had arrived, including 29.09 MB below the anchor. Eight full RPC blocks contributed approximately 2.48-4.69 MB each (903-1,232 transactions); the ninth retained approximately 1.09 MB before interruption, with total RPC bytes 28.22 MB.

The implementation verifies the entire reconciliation chunk before advancing its completed range, so partial processing conservatively left that initial range unresolved. At the stop, settled received bytes were 75.86 MB; remaining allowance was 24.14 MB, less than simultaneous in-flight reservations of 25.23 MB. The reservation stop was therefore correct—not a breached budget or demonstrated provider bug. These rounded diagnostic metrics are attributed to the Reviewer; exact cumulative counters remain those in the measured section.

A useful future S1 direction is offline journal profiling, then a separately approved smaller bootstrap window/durable per-slot progress contract with new behavioral RED. Neither redesign nor renewed S1 execution is authorized here; the current permanent stop and original evidence remain intact.

## Offline acceptance preparation — 28 September, 16:05 UTC

Current execution permission is closed: the original D11 deadline was `2026-09-27T19:37:39.352Z`. The two stopped receipts retain nine attempts and 2,018,236 received bytes, leaving 71 attempts and 22,981,764 bytes under the original aggregate caps, but byte headroom does not renew the expired time window. Main has requested an explicit renewed zero-paid window; authorization is pending. No data API call was made for this update. Public documentation/IDL reads and offline analysis do not change D10/D11, helper behavior, acceptance or repair count (five used, including the two explicit exceptional passes).

**S4 remaining observed fields.** The two complete GoPlus bodies described above also contain:

| Field group | Observed value | Evidence boundary |
|---|---|---|
| `balance_mutable_authority`, `closable`, `default_account_state_upgradable`, `transfer_fee_upgradable`, `transfer_hook_upgradable` | Each has `status:"0"`, `authority:[]` | Provider flags; no independent extension/account comparison |
| `transfer_fee` / `transfer_hook` | Empty object / empty array | Preserve emptiness; not proof of historical absence |
| `trusted_token` | Integer `1` | Provider classification, not independently verified safety |
| `creators`, `dex`, `lp_holders` | Empty creators, ten DEX entries, empty LP-holder list | Response collections, not complete historical ownership evidence |

Together with the earlier matrix this accounts for all returned top-level token fields. Missing source slot/time, unspecified TTL and unverified historical access remain unknown, not safe/false. The documented feature/status meanings were rechecked on 2026-09-28 in [GoPlus's Solana response definitions](https://docs.gopluslabs.io/reference/response-detail-1); this does not supply a TTL/history guarantee. No headers were retained from which to infer cache age. Current mint/freeze/supply agreement is useful evidence, but cannot establish decision-time historical risk facts.

**S3 concrete candidate and finite unexecuted matrix.** The complete first JSON line of the otherwise capped `008.raw` supplies finalized slot 416520946, block hash `F5KNPrV6FhYmskFo9S1CuXYnrRScxBAdnJf3CYRrArY`, transaction index 19, signature `5NJJ3UqZSwRZcwuTVybrJQmcfTh2cXurkh2pVmAxkgXTZArBfQwWGimrBmU8kFGGDp3EFdebxZqNf8nvhkvFqNuF`, and PumpSwap instruction address `[7]`, committed with null transaction/instruction errors. Base58 data `K4szuY1ujXciiXFx6Vf7K4QcwL5Um7Reo` decodes to hex `c62e1552b4d9e8705513f126000000000100000000000000`.

The discriminator matches `buy_exact_quote_in` in the [official historical PumpSwap IDL](https://github.com/pump-fun/pump-public-docs/blob/82dacacf15ca93dc0444ab38714f2226210a0a3d/idl/pump_amm.json), commit dated 2026-02-17, the latest file commit before the observed block found in the public repository history. IDL text SHA-256 was `5a15060f412974e53068bae7e89aa6004defbb70ef0c56e3902ce75d124accb6`. This identifies candidate prefix roles, not a verified deployed ABI: the observed call has additional accounts and omits the IDL's trailing argument bytes; historical program-revision compatibility remains unresolved. Do not decode financial arguments or unknown trailing roles from that mismatch.

| Candidate prefix role | Observed address | Independent local evidence |
|---|---|---|
| Pool | `9zwcjNKadqrnKRcbyRZc3PeT8tojHBuZAgCU67ksTGc3` | Both vault balance records name this token owner; actual account-program ownership/state unqueried |
| Base vault | `91D3yHPV6qm84JCuUmMMn95uLBYFhjovGMh1fwr43NR5` | Token balances identify mint `3fZMjRUEVVyTbWKPDNAyxG38XnPLrazj7b3rvjqmpump`, decimals 6 |
| Quote vault | `6Z4yb12JoChX7UQN1Q3XxAVjf1YjEmpfR9sfBzhpHuhA` | Token balances identify wrapped SOL mint, decimals 9 |

Prepared matrix only: those three accounts × finalized slots 416520946 and 416520947 × two identical reads = 12 hypothetical historical `getAccountInfo` requests, with `encoding:"base64"` and `slot:S`, no `minContextSlot`. Preserve bytes, owner, context, null/coverage errors and hashes; use adjacent recorded token balances as a limited independent consistency check, not proof of entire account state. [Alchemy's archive documentation](https://www.alchemy.com/docs/solana/account-archive), consulted 2026-09-28, defines inclusive finalized at-slot reads and distinguishes historical data from latest state. These calls are not authorized by D11; entitlement, shared budget enforcement, account-role verification and historical correctness remain outstanding. No credentials or executable request file were created.

**Proposed S2 next step, not execution permission.** Retain the existing declared end and actual targets March 31 / June 29, 18:44 UTC; do not move them to match the measured April/July samples. SQD documents a precise timestamp resolver and server-side discriminators/account filters in its [official Solana API reference](https://docs.sqd.dev/en/portal/solana/api#resolve-a-timestamp-to-a-block). The resolver selects the first block at/after a Unix timestamp, but its `/timestamps/{timestamp}/block` path and discriminator/account filter keys are outside the existing helper allowlist; adopting them would require a separate scoped contract/helper change, not a hidden request-list tweak.

The no-helper-change option is a manually bounded, predeclared grid of public `getBlockTime` or SQD header-only single-slot queries, confirming actual dates before selecting samples in a separately permitted finite stage. For complete watched-program samples, request one verified slot and one program per response with all required fields/relations retained; this reduces expected payload versus the failed three-slot/three-program query without guaranteeing it fits 2 MB. Obtain actual oldest/middle/recent samples for all three programs, then independent finalized RPC comparisons; refusal/oversize/empty programs remain evidence gaps. No date estimate alone, incomplete response or subset proves coverage. A defensible sourced cost basis is still required for S2 acceptance.

**Decision blockers.** S1 transport remains INCONCLUSIVE; S2 lacks the true oldest/midpoint envelope, complete cross-program samples and independent reconciliation/cost basis; S3 lacks verified historical state; S4 lacks freshness/history guarantees for required facts. S5 is not optional for the accepted `HOLDER_GROWTH` consumer: a current count/top-ten subset cannot replace the required reproducible ≥4-hour full-population series. A conditional shortlist is possible, but a primary/specialized-source selection satisfying task 3.6 is not. No checkbox, archive or renewed execution authorization follows from this offline preparation.

## Renewed S2 window preparation — 28 September, reviewed before launch

After the earlier deadline expired, the owner explicitly approved a new zero-paid window of at most 30 minutes within the remaining original limits. The renewed operational addendum received fresh Reviewer APPROVE with the full CI matrix and no findings. This does not resume either stopped receipt or S1, increase resources, reset the five consumed repairs, or complete a spike. Main's complete gate and launch remain pending; no new provider request has occurred.

Main prepared `C:/crypto-research-evidence/provider-s2-sep28a-requests.json`: 11 locally validated requests, exact approved slot/program/field selection, 9,707 file bytes and SHA-256 `391a4fc7349b2b3fc87e736bc2f255cad8d67f3907fd715abcb8ba234cefbc60`. Its compact-content hash matches the planned `6afe1b657f76c1f75e66224dad2ee7a5513b7a8ce4376c8016814dd2845a2b9c`. The first two requests are oldest/midpoint headers; the remaining nine separately sample Pump.fun, PumpSwap and Raydium AMM v4 at slots 410243739, 429644639 and 451080947, retaining all required fields. Actual returned timestamps must determine historical coverage; estimates do not establish it. Main reports 358,981 bytes of retained D11 TEMP logs, included with prior outputs/inputs below the addendum's 3 MB pre-launch bound. The fresh root, launch-fixed deadline, native cutoff and aggregate limits remain those in the approved design. Checkboxes stay unchanged and no archive is authorized.

## Renewed window Stage A — measured 28 September

Main's complete gate passed before launch: 96 unit and 48 integration tests, zero failures/errors/skips; integrity, strict validation of 12 OpenSpec items, doctor and diff checks passed (TEMP `provider-sep28` logs). The new window began `2026-09-28T16:19:25.3634622Z`, fixing its deadline at `16:49:25.3634622Z`. The one enabled batch wrote `C:/crypto-research-evidence/provider-s2-sep28a/provider-feasibility` and stopped cleanly on request 5, retaining five attempts and 535,899 received bytes; cumulative D11 usage is 14 attempts and 2,554,135 bytes. Main verified all 31 predecessor files unchanged. Architect verified all five new raw lengths and SHA-256 values against the summary offline.

| Receipt | Actual date / measured fields |
|---|---|
| Header slot 410243739 | `2026-04-01T05:11:17Z`, age 179.5644 days: 10h27m17s newer than the true 180-day boundary, not proof of 180-day depth |
| Header slot 429644639 | `2026-06-29T10:08:45Z`, age 90.3578 days: 8h35m15s older than the declared midpoint |
| Pump.fun at 410243739, complete 25,613-byte HTTP 200 | 3 transactions, 37 related instructions (6 Pump.fun), 24 inner positions, 20 native balances, 6 token balances |
| PumpSwap at 410243739, complete 509,735-byte HTTP 200 | 44 transactions, 844 related instructions (107 PumpSwap), 537 inner positions, 135 native balances, 237 token balances |

Both complete program responses contain the requested signatures/index/account keys/loaded addresses/version/error, instruction call positions/accounts/data/commitment/error, native pre/post balances and token mint/owner/decimals/raw amounts; observed transactions are legacy/V0. Counts include related instructions and are not counts of successful swaps. Program raw hashes are respectively `0b8d0c339e18cc6bb7226876515130866ff1de21560f26e414e2ef50228b9407` and `6e85de4889eef26d94557261f21583729c105436006d5550e57c3a6e09359d8d`.

Request 5 (Raydium at that slot) returned HTTP 529 with a retained 163-byte `rate_limit_error` / `overloaded` body. This is an explicit service-overload response, not evidence of historical completeness failure; raw hash `e4f048adeeebce683c9359a8613160cac95f6cde4b0dd034f5f9268c85aee743`. All six midpoint/recent requests remained unexecuted. No automatic retry occurred. S2 remains incomplete: oldest coverage, Raydium, independent RPC reconciliation, continuation/reorg observations and cost basis are unresolved. S1's permanent stop, S3/S4/S5 limitations and evidence-dependent 3.6 are unchanged; no checkbox or archive follows.

## Stage B preparation — reviewed 28 September, before launch

Fresh Reviewer approved the separate fixed Stage B plan and Stage A evidence note with the full CI matrix and no findings. Main prepared `C:/crypto-research-evidence/provider-s2-sep28b-requests.json`: 11 locally validated requests, 9,685 file bytes, SHA-256 `54a5b1488a0da048c5d7c9b70919c527de1a5136facbbc598cab1ed733fc198e`; compact-content SHA-256 matches the planned `dfb3ade063a9ea36a24ad40532e2c25c99d23d45c047432ea6f4ebee4b638cc2`. The fixed list is corrected-old slot 410100000 header/three program samples, six previously unexecuted midpoint/recent samples and one last bounded RPC block comparison. Main's gate and this single enabled launch remain pending. The same renewed-window deadline, retained predecessors, aggregate caps, S1 stop and incomplete acceptance remain unchanged; no checkbox or archive follows.

## Renewed window Stage B — measured 28 September

Main reran the complete gate successfully (96 unit/48 integration tests, zero failures/errors/skips; integrity, strict 12-item validation, doctor and diff checks; TEMP `provider-sep28b*` logs). The single enabled Stage B wrote `C:/crypto-research-evidence/provider-s2-sep28b/provider-feasibility`, from first send `16:28:22.899Z` to summary end `16:28:39.260Z`; Main measured normal process duration 16.476 seconds. It retained nine attempts/2,399,015 bytes, giving cumulative D11 usage 23 attempts/4,953,150 bytes. Main verified all 48 predecessor files unchanged; Architect verified all nine new raw lengths/hashes offline. The same `16:49:25.3634622Z` window deadline remains; there was no retry or reset.

Corrected-old slot 410100000 actually dates to `2026-03-31T13:33:43Z`, age 180.2155 days, 5h10m17s before the declared boundary. This establishes sampled availability at that depth, not completeness over 180 days. Midpoint/recent dates remain June 29 10:08:45 / September 27 18:43:54 UTC.

| Complete sample | Transactions / related instructions / native balances / token balances | Watched-program instructions |
|---|---|---|
| Corrected-old Pump.fun | 10 / 124 / 54 / 18 | 20 |
| Corrected-old PumpSwap | 60 / 1,324 / 156 / 352 | 173 |
| Corrected-old Raydium | Header only, 194 bytes; no returned rows | 0; does not prove program coverage |
| Midpoint Pump.fun | 32 / 255 / 71 / 65 | 38 |
| Midpoint PumpSwap | 71 / 1,303 / 192 / 472 | 166 |
| Midpoint Raydium | 2 / 52 / 10 / 25 | 2 |
| Recent Pump.fun | 38 / 610 / 173 / 247 | 78 |

Every nonempty complete sample contains the requested transaction, instruction/call-position, native-balance and token-balance field categories described above. Recent Pump.fun includes legacy/V0/V1; these are preserved raw observations, not a production parser or derived swap count. Midpoint Raydium is positive real field evidence; the empty corrected-old Raydium response does not establish its historical completeness.

Request 9, recent PumpSwap at 451080947, returned HTTP 529 with the same `rate_limit_error` / `overloaded` class, retaining 163 bytes and SHA-256 `34aaa4ed6280f8da17d52daf03e0b19adfcdc0147bcefdeb2d1410a7c657808c`. This is an overload observation, not a completeness failure. Recent Raydium and the corrected-old public RPC block comparison were never attempted; independent reconciliation remains unverified. No failed query is retried. S2 still lacks complete positive cross-program samples/reconciliation/continuation-reorg/cost evidence; S1, S3-S5 and 3.6 remain incomplete, with checkboxes unchanged and no archive.

## Final Stage C preparation — reviewed 28 September, before launch

Fresh Reviewer approved the final two-request Stage C plan and Stage B evidence note, with all applicable CI checks passing. Main locally validated `C:/crypto-research-evidence/provider-s2-sep28c-requests.json`: 1,328 file bytes, SHA-256 `ac83fda1cbb7801c3c3c3f075c298d8438043ce06d4c21edc351598f71375f3d`; compact-content hash matches `20d840718df0761d3968c4d6c339a0ab33d7669d7c13f9f75c76e962352fe85f`. The list copies only the unexecuted recent Raydium and corrected-old RPC requests. Main's last gate and single enabled launch remain pending. The same `16:49:25.3634622Z` absolute window, caps, immutable predecessors, S1 stop and final-stage/no-retry boundary remain unchanged; no acceptance checkbox or archive is authorized.

## Final Stage C and bounded handoff — 28 September

Main's pre-Stage-C complete gate passed: 96 unit/48 integration tests, zero failures/errors/skips; integrity, strict 12-item validation, doctor and diff checks (TEMP `provider-sep28c*` logs). Final Stage C wrote `C:/crypto-research-evidence/provider-s2-sep28c/provider-feasibility`, first send `16:35:53.850Z`, summary end `16:35:59.687Z`. The Reviewer approved the actual receipt: 5.843-second receipt interval versus Main's 5.954-second process duration, normal exit 1, `RESPONSE_LIMIT`, two attempts/2,042,330 bytes, no overshoot. Both raw lengths/hashes were independently verified; Main verified all 77 predecessor files unchanged and no experiment Node processes remain. No further stage or call follows. Main's complete post-DOCS_CLOSE gate remains pending; the preceding gate counts do not claim that final result.

Recent Raydium returned complete HTTP 200 at slot 451080947 (`2026-09-27T18:43:54Z`): 42,330 bytes, 4 transactions, 44 related instructions (4 Raydium), 32 inner positions, 11 native balances and 27 token balances, V0/V1. All requested field categories are present; raw SHA-256 `2317694d9ccfb573eb54b995e92f2c22ed907e716dac5b91a2aa454959b9d6b9`.

The old public RPC block returned HTTP 200 but was retained only to the exact 2,000,000-byte response cap, SHA-256 `21fde77b6b245454ffde8b82743d072e3a88819a6a2e573387f2b4cf1cf4c02d`. Its bounded prefix agrees with SQD's corrected-old block time, block hash and parent identity. This is weak fragment consistency only: the full response is truncated, so filtered signature/instruction/balance reconciliation remains unverified. No partial prefix, empty range or transport success is promoted to completeness/PASS.

| Actual sample position | Pump.fun transactions / watched instructions | PumpSwap transactions / watched instructions | Raydium transactions / watched instructions |
|---|---|---|---|
| March 31 13:33:43 UTC, slot 410100000, age 180.2155 days | 10 / 20 | 60 / 173 | Header only; no rows |
| June 29 10:08:45 UTC, slot 429644639, age 90.3578 days | 32 / 38 | 71 / 166 | 2 / 2 |
| September 27 18:43:54 UTC, slot 451080947 | 38 / 78 | HTTP 529 overload, incomplete | 4 / 4 |

Every nonempty complete sample supplies the required field categories; these are sampled observations, not proven swaps or envelope-wide coverage. D11's five receipts total **25 attempts and 6,995,480 received bytes**; the renewed A/B/C window contributes 16 attempts/4,977,244 bytes. Main measured 7,099,053 bytes across all five output trees, including 5,051,921 in A/B/C; these output-only totals exclude input files/TEMP logs, which were counted separately in launch preflights. The original caps and `16:49:25.3634622Z` window were not increased or reset. Resource headroom is not permission to restart. Dashboard evidence remains pre-run only.

| Remaining task | Evidence boundary / practical blocker |
|---|---|
| S1 / 3.1 | INCONCLUSIVE, permanently stopped; zero LIVE, no forced gaps/six-hour observation or completed recovery/quota comparison. The documented bootstrap/reservation mechanism needs a separately approved redesign/RED/run budget, not another smoke under this authority. |
| S2 / 3.2 | Real sampled depth reaches 180.2155 days, with positive field evidence for all three programs at some positions. Empty old Raydium, recent PumpSwap overload, capped independent RPC, continuation/reorg checks and sourced full-envelope cost remain unresolved. |
| S3 / 3.3 | Exact conditional pool/vault × two-slot × repeated-read matrix is prepared above but unexecuted; deployed ABI/ownership, entitlement, shared-budget enforcement and historical reconstruction remain unverified. |
| S4 / 3.4 | Current supply/mint/freeze facts agree for the example mint; field inventory is observed. Source slot/time, TTL, required historical risk access and wider coverage remain unknown. |
| S5 / 3.5 | Access/entitlement and real reproducible ≥4-hour full-owner series remain unverified; current count/top-ten or empty LP subsets cannot substitute. |
| 3.6 | Provider selection deferred until required capability evidence exists; no primary/history/specialized selection, purchase or production work follows. |

The smallest useful next decision is a separately bounded S2 independent-verification plan evaluating a **complete** smaller public `getBlock` representation/payload strategy while retaining account membership, instruction positions and balances. It must first prove feasibility under the remaining original caps; signature-only data cannot prove the complete watched-program set, and 55 remaining attempts cannot be assumed sufficient to fetch every transaction in a large block. Any encoding/helper/test scope and a new execution window require explicit authorization/planning/review; no larger response cap or implementation is assumed here. Renewed S1 bootstrap execution is a separate owner budget/scope decision. All tasks 3.1-3.6 stay unchecked, the change remains active, and no archive or commit is authorized by this handoff.

## Complete RPC representation feasibility — offline 28 September

Main subsequently completed the post-Stage-C final gate: 96 unit/48 integration tests, zero failures/errors/skips; integrity, strict 12-item validation, doctor and both diff checks passed (TEMP `provider-sep28-final-*.log`). This later result does not change the historical pre-gate statements above or complete a spike. The existing public-probe baseline still passes all 35 frozen tests (TEMP `provider-s2-next-baseline-node.log`); five task-wide repairs remain consumed.

No new provider call, code, decoder, dependency or evidence file was created for this assessment. Architect reconstructed the eight already retained complete `getBlock` responses from `C:/crypto-research-evidence/alchemy-s1/smoke-retry-1/raw.bin`, grouping RPC frames by recorded request IDs 6-13 and verifying each frame's SHA-256. The ninth block is incomplete and excluded. Full JSON bodies are 2,484,630-4,686,054 bytes, all above D11's unchanged 2,000,000-byte response cap.

The [official RPC structures](https://solana.com/docs/rpc/json-structures) distinguish encoded transaction bytes from status metadata; changing to base64 does not itself remove required metadata. The [official transaction structure](https://solana.com/docs/core/transactions/transaction-structure), checked 28 September, defines legacy/V0 wire components. For a conservative same-metadata base64 lower bound, retain every original non-transaction byte and replace each transaction object with `["","base64"]`; add only legacy/V0 signatures (64 bytes each), static keys (32 each), recent hash (32), header (3), program/account-index bytes and decoded instruction-data bytes, rounded upward to base64 length per transaction. Omit all V1 transaction bytes, compact-vector prefixes, version prefixes and lookup-table descriptors. Replacement counts match all transaction counts; no numeric fact is reserialized. This is an offline lower-bound calculation, not a measured base64 response or implemented parser.

| Retained slot | Complete JSON bytes | Conservative full base64 lower bound, bytes |
|---|---|---|
| 451080926 | 2,928,180 | 2,499,218 |
| 451080927 | 2,586,527 | 2,180,750 |
| 451080928 | 4,686,054 | 4,048,056 |
| 451080929 | 3,599,854 | 3,081,160 |
| 451080930 | 3,303,276 | 2,803,958 |
| 451080931 | 3,586,214 | 3,073,488 |
| 451080932 | 2,484,630 | 2,077,129 |
| 451080933 | 3,958,281 | 3,333,550 |

Fresh Reviewer independently verified all eight calculations and reported `BLOCKED / OWNER_DECISION` for launch. A base64 decoder is not justified merely to reproduce these known cap stops. Complete sizes for the fixed S2 slots 410100000/429644639/451080947 remain unknown; these retained nearby blocks do not prove that every possible complete block exceeds 2 MB. No response-driven scan or smaller replacement sample is authorized. Signature-only or `accounts`-mode payloads cannot replace the required complete watched-set/instruction/balance comparison.

The pending, unapproved owner option is a separate finite full-JSON RPC plan for at most two fixed blocks with an 8,000,000-byte response ceiling and one new launch-fixed window of at most 30 minutes. It would keep the original aggregate 25,000,000 received bytes, 50,000,000 evidence bytes and 80 requests: prior usage 25 attempts/6,995,480 bytes leaves 55 attempts/18,004,520 bytes; two maximum responses would total at most 27 attempts/22,995,480 bytes. This arithmetic is not permission to increase the current per-response cap or launch. Exact request selection, aggregate input/output/log storage, helper/test changes with meaningful RED and fresh review/gate still need an approved PLAN. No further repair allowance is created. All prior receipts, permanent S1 stop, historical deadlines, incomplete S2-S5/3.6 acceptance and unchecked tasks remain unchanged.

The owner subsequently explicitly approved that narrow 8 MB/two-fixed-block/new-window option on 28 September, superseding the pending-permission state above. The new D11 PLAN fixes full-JSON public RPC slots 410100000 and 429644639, keeps ordinary probes at 2 MB and all aggregate ceilings/debits unchanged, and requires additive behavioral RED/GREEN, independent review and Main's complete gate before one enabled launch. Preparation is not a measured comparison or spike PASS; no new call or implementation has occurred in Architect PLAN, and five consumed repairs are not reset.

## Fixed full-JSON RPC profile — reviewed preparation, 28 September

Fresh Reviewer `/root/s2_rpc_profile_review` approved the bounded profile implementation with the full CI-01..CI-15 matrix, no findings and `red_suspect=false`. Builder's additive suite executed 61 tests: RED 54 passed/7 behavioral assertion failures (`REQUEST_INVALID` instead of the required acceptance/stop results), then GREEN 61 passed. The original 35-test prefix is unchanged; tests froze before implementation and `tests_changed_after_red=false`. Logs are TEMP `provider-s2-rpc-profile-red.log` and `provider-s2-rpc-profile-green.log`; 507 handwritten nonblank lines, 110 added, remain within the existing file/line budget. This feature creates no further repair allowance; five D11 repairs remain consumed.

Main locally validated the exact profiled C/M input: 923 bytes, SHA-256 `2efe0a5eb7176d47793c164a2bc62394aeea069274f84ace5ab306f89ce26ffa`. The two fixed full-JSON finalized RPC slots, ordinary 2 MB versus opt-in 8 MB limits, cumulative sunk debits and exclusive new-root/deadline protections remain those in the approved D11 addendum. No public launch or measured same-slot comparison has occurred; Main's complete gate and aggregate preflight remain pending.

Main also verified the native owned-process cutoff offline: TEMP `provider-s2-rpc-profile-native-cutoff-2.log` records a 250 ms timeout, owned PID termination, 275 ms elapsed and no network. An earlier logging attempt's PowerShell `false` versus `$false` setup error was retained; it is not behavioral RED or provider evidence. Reviewed preparation is not S2 PASS, task 3.2 remains unchecked, and no archive follows.

## Fixed full-JSON RPC profile — measured 28 September

Main's prelaunch complete gate passed: 96 unit/48 integration tests, zero failures/errors/skips; integrity, strict all-item validation (12/12), doctor and both diff checks passed. Logs are TEMP `provider-s2-rpc-profile-{integrity,maven,validate,doctor}.log`. This supersedes the preparation-only pending gate above, not any incomplete spike acceptance.

The single authorized launch began `2026-09-28T17:23:32.8816693Z`, with Main's fixed absolute deadline `17:53:32.8816693Z`; it exited normally with code 1 at `17:23:44.8082581Z` (11.929-second process duration). Receipt manifest/summary times are `17:23:33.007Z` to `17:23:44.782Z`, a distinct 11.775-second receipt interval. Evidence is `C:/crypto-research-evidence/provider-s2-rpc-cm-1/provider-feasibility`: `manifest.json`, `summary.json`, `001.request.json`, `001.result.json` and `001.raw`.

The first fixed `getBlock`, slot 410100000, returned HTTP 200 but stopped at `RESPONSE_LIMIT`: exactly **8,000,000 received/retained bytes**, `complete=false`, raw SHA-256 `c8803b80f62c7b582815832b01ebe9d28cb9d48ab3c297b35585349642d0fdb5`. Architect independently verified this length/hash and read only a bounded 512-byte prefix. Its timestamp `1774964023` (March 31 13:33:43 UTC), block hash `JEJU6EzY1qsAr8iSg7vsFDR398JVud6JQLhhUeFRY7jZ`, parent slot 410099999 and parent hash agree with the complete SQD Stage-B header. This is fragment identity consistency only, not a complete watched-transaction/instruction/balance comparison. The full JSON size and any alternative complete representation remain unknown; no truncated-body reconciliation or decoder was attempted. The second fixed slot 429644639 was **unexecuted**. No retry or further stage follows this stop.

This run adds one attempt/8,000,000 bytes; cumulative D11 usage is **26 attempts/14,995,480 received bytes**, leaving 54 attempts/10,004,520 bytes under the unchanged aggregate limits. Headroom is not permission to launch. The new five-file output tree is 8,003,870 bytes; all six output trees total 15,102,923 bytes, excluding separately inventoried inputs/logs. Main's preflight counted actual retained evidence E=8,186,892 bytes and a worst-case combined reservation of 25,317,964 bytes, below the unchanged 50 MB evidence ceiling. Main verified all 415 predecessor parent files, including S1 receipts and inputs, unchanged before/after. Owner's manually changed four-thread configuration remains untouched, SHA-256 `4696b881bd78b438cb4a34ba15bdeb7837415d32c9b51cb56ecb85645873d5ba`; no separate capacity-plan artifacts were created. Five consumed D11 repairs are not reset.

S2 remains **INCONCLUSIVE**: sampled 180.2155-day depth and the existing program-field matrix are real, but old Raydium is empty, recent PumpSwap overloaded, independent full RPC reconciliation is unverified, and continuation/reorg/full-envelope cost evidence is pending. S1 remains permanently stopped/INCONCLUSIVE with zero LIVE and incomplete recovery/gap/quota checks. S3's conditional historical pool/vault matrix is unexecuted; S4 has current example-mint fact agreement but unknown source time/TTL/historical access; S5 lacks access and a reproducible full-owner ≥4-hour series. Evidence-dependent selection in 3.6 remains deferred. Tasks 3.1-3.6 stay unchecked; no archive, production work or budget increase follows.

The next useful decision is not another response-cap increase or automatic retry. A separately authorized contract would first need a documented **complete** representation/independent comparison strategy (for example, lossless transport compression only if its actual availability, received-byte accounting and preservation of all required facts are established). No such capability or fit is proven here; signature-only or incomplete filtered data cannot establish the complete watched set. Any new method/helper scope/window requires owner choice, bounded planning, meaningful tests where implementation changes, independent review and the complete gate. Actual-receipt/documentation review and Main's post-documentation gate are still pending at this entry.

## Enlarged fixed full-JSON RPC V2 — preparation, 28 September

After the retained V1 stop, the owner explicitly approved the new `s2-rpc-c-m-v2` plan: the same finalized full-JSON slots 410100000/429644639, 32,000,000 bytes per response, cumulative public traffic 100,000,000 bytes and combined evidence 200,000,000 bytes, retaining 80 attempts and parent 10 GB/30 GB free limits. Fixed sunk usage is 26 attempts/14,995,480 bytes; request/native-process ceilings are 120/300 seconds within one new launch-fixed 30-minute window. This is separate authorization, not a rewrite/restart of V1 or another S1 attempt; ordinary 2 MB/V1 8 MB behavior and historical receipts remain unchanged. No automatic retry follows a stop.

Builder reports implementation complete within 575 handwritten nonblank lines. The additive suite executed 80 tests: RED 72 passed/8 expected behavioral failures because the previously unsupported profile returned `REQUEST_INVALID`, then GREEN 80 passed/zero failures or skips. The original 61 tests are unchanged and additive tests froze before implementation; `tests_changed_after_red=false`. Command remains `node --test tools/spikes/provider-feasibility/test/probe.test.cjs`; logs are TEMP `provider-s2-rpc-v2-red.log` and `provider-s2-rpc-v2-green.log`. This initial feature build does not reset the five consumed D11 repairs.

The fresh fixed input `C:/crypto-research-evidence/provider-s2-rpc-cm-2-requests.json` has SHA-256 `ee638fb40937a913a53f8bbacdc07986fd0fec82f5c4fedd4c7a038d5ca2ebff`, independently checked offline. The approved new evidence root is `C:/crypto-research-evidence/provider-s2-rpc-cm-2`, with helper child `provider-feasibility`. Independent code/documentation review, Main's complete gate/aggregate preflight and the single public launch are pending at this entry. No V2 provider response or complete same-slot comparison exists yet. S2 remains INCONCLUSIVE, all other documented S1-S5/3.6 limitations and unchecked tasks remain unchanged, and no archive or production work follows preparation.

Fresh Reviewer subsequently approved V2 implementation and this preparation note with the full CI matrix: Builder's 80/80 GREEN evidence was independently reviewed together with code/freeze/inventory; the Reviewer did not rerun those 80 tests. There were no blockers and `red_suspect=false`. DOCS_CLOSE records reviewed readiness only: Main's complete gate, exclusive aggregate preflight, single enabled launch and actual same-slot comparison remain pending at that historical entry. Tasks 3.1-3.6 stay unchecked; owner configuration, five consumed repairs, historical receipts and all acceptance boundaries remain unchanged. No archive is authorized.

## Enlarged fixed full-JSON RPC V2 — actual comparison, 28 September

Main's complete prelaunch gate passed: integrity/strict validation/doctor/diff checks exited zero, all 12 OpenSpec items passed, and Maven executed 96 unit/48 integration tests with zero failures/errors/skips. The one authorized V2 launch exited normally with code 0 (`LIST_EXHAUSTED`), owned-process duration 19.101 seconds. Receipt manifest/summary span `2026-09-28T17:43:47.334Z` to `17:44:06.299Z` (18.965 seconds); request starts were 17:43:47.340 / 17:43:59.863 UTC, with 12.430 / 6.411-second response intervals. Both HTTP 200 bodies are `COMPLETE_RESPONSE`, not cap prefixes. Live control logs are TEMP `provider-s2-rpc-v2-live-{launch,stdout,stderr}.log`.

| Fixed RPC slot / actual chain date | Complete bytes / SHA-256 | Full block transactions |
|---|---|---|
| 410100000 / March 31 13:33:43 UTC | 9,547,733 / `18a6226b5c8d47e35bff23529cc188563aaffdf4c92002c0cff3ee2334abd477` | 1,653: 945 legacy, 708 V0 |
| 429644639 / June 29 10:08:45 UTC | 4,217,225 / `7f028de71b26e8f8fa9ca05ee5cef8647863226bf4cef74301deb0a7ff4a6cae` | 1,154: 897 legacy, 257 V0 |

Raw evidence is `C:/crypto-research-evidence/provider-s2-rpc-cm-2/provider-feasibility/{001.raw,002.raw}` with request/result/manifest/summary receipts; source SHA-256 `33cbfba486f187fc39ded40387c2c05513e2f643550ef72ed985e1d4298731b4`. Architect verified both new and six SQD raw lengths/hashes/completion before comparison. No V1 appears in these two full blocks; earlier recent V1 observations are not reclassified.

Read-only comparison uses SQD Stage-B `002.raw`/`003.raw`/`004.raw` for corrected-old Pump.fun/PumpSwap/Raydium and `005.raw`/`006.raw`/`007.raw` for midpoint, under `C:/crypto-research-evidence/provider-s2-sep28b/provider-feasibility`. Both block hash/time/parent identities agree. Join on the **entire signature array**, never SQD's reported transaction index or a filtered position. Effective account order is static keys, loaded writable, loaded readonly. RPC outer positions and complete inner preorder/stack heights derive call-tree addresses; all compared inner stack heights are available and paths agree. Failed transactions remain in the comparison.

| Position / program | Watched transactions (failed) | Related outer / inner instructions | Native changed triplets / all token rows | SQD index vs RPC ordinal differences |
|---|---|---|---|---|
| Old Pump.fun | 10 (0) | 42 / 82 | 54 / 18 | 0/10 |
| Old PumpSwap | 60 (22) | 529 / 795 | 156 / 352 | 0/60 |
| Old Raydium | 0 | 0 / 0 | 0 / 0 | No rows |
| Midpoint Pump.fun | 32 (26) | 204 / 51 | 71 / 65 | 28/32 |
| Midpoint PumpSwap | 71 (6) | 458 / 845 | 192 / 472 | 66/71 |
| Midpoint Raydium | 2 (0) | 12 / 40 | 10 / 25 | 2/2 |

All six complete signature sets and their relative order match: zero missing/extra signatures. Full signature arrays, legacy/V0 version literals, transaction error objects, static/loaded account arrays, all 3,058 related outer/inner instruction program/accounts/base58-data/call paths and `isCommitted` facts match. Watched instruction counts are respectively 20/173/0 and 38/166/2. The 96 midpoint index discrepancies are real provider-field differences across program rows, not missing transactions: examples SQD 47→RPC 55 (Pump.fun), 48→56 (PumpSwap), 348→887 (Raydium). Cause is unverified; do not reinterpret that field as a canonical RPC ordinal. Complete signature membership does not erase the discrepancy.

All 483 changed-native `(account,pre,post)` multiset rows and all 932 token side-fact rows match exactly, with zero extra/missing/quantity differences. SQD native rows lack a transaction index; duplicate triplet groups and ambiguous candidate attribution within each compared transaction subset are both zero here, not a general attribution guarantee. Token comparison includes unchanged rows (166 old PumpSwap, 55 midpoint Pump.fun, 121 midpoint PumpSwap, 4 midpoint Raydium), account/mint/owner/decimals/raw amount, and explicit absent-side nulls; unchanged rows are not filtered and null is not zero. Per-instruction textual errors remain a limitation: SQD reports 31/26/9 non-null fault messages in old PumpSwap/midpoint Pump.fun/midpoint PumpSwap, but full RPC has no directly corresponding instruction-error string field. Transaction error and commitment comparisons passed; independent textual fault equivalence was not established. [SQD's field definitions](https://docs.sqd.dev/en/portal/solana/api) and [RPC structures](https://solana.com/docs/rpc/json-structures), checked 28 September, distinguish those categories.

This run adds two attempts/13,764,958 received bytes; cumulative usage is **28 attempts/28,760,438 bytes** under the owner-approved 80/100 MB limits. Eight new files total 13,771,002 bytes. Main independently verified all 421 predecessor hashes unchanged and owner configuration untouched. Preflight actual evidence E=16,431,534/worst reservation 81,562,606 bytes was below 200 MB; parent usage/free space were 94,928,157/127,372,316,672 bytes. Postflight combined evidence was 30,203,458 bytes and parent usage 108,699,159, before the bounded comparison log; Main verified no experiment process/lock. No further call follows unused headroom. Historical 2/8 MB stops, old deadlines, S1 stop and five consumed repairs remain immutable.

The fresh Reviewer approved the actual receipts, this note and six-sample comparison with the full CI matrix, independently reproducing the documented read-only command with exit 0 and matching all reported counts. DOCS_CLOSE records that approval only. This establishes two-slot sampled membership/field reconciliation, **not S2 PASS**: old Raydium's zero membership is now independently corroborated for that one slot, not positive old-range program coverage; recent PumpSwap, continuation/reorg observations, 180-day envelope-wide coverage and sourced cost remain unresolved. S1 remains INCONCLUSIVE/permanently stopped; S3 historical state reads are unexecuted; S4 TTL/history/source-time and wider coverage are unknown; S5 access/full reproducible ≥4-hour owner series are unverified; 3.6 selection remains deferred. Tasks 3.1-3.6 remain unchecked. Main's complete final gate remains pending; no further launch, archive or production work is authorized.

### Bounded offline reproducer

The following one-off read-only PowerShell/Node command produced TEMP `provider-s2-rpc-v2-comparison.log` (6,630 bytes, exit 0). It reads only the eight fixed complete receipts, validates synthetic unsafe integer/escaped string/decimal/exponent cases, quotes numeric lexemes outside JSON strings before parsing, and uses exact strings for quantities. `Number` is restricted to safe nonnegative array indices/stack heights, not balances or financial values. It creates no code/tool file or network request; plain log redirection retains the inspection output. Report counts describe program subsets and must not be summed as unique block transactions.

```powershell
@'

const fs=require('node:fs'),crypto=require('node:crypto'),assert=require('node:assert/strict');
function lossless(text) {
 let at=0,last=0,pieces=[];
 while(at<text.length) {
  if(text[at]==='"') { at++;while(at<text.length){if(text[at]==='\\'){at+=2;continue;}if(text[at++]==='"')break;}continue; }
  if(text[at]==='-' || /[0-9]/.test(text[at])) {
   const token=/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/.exec(text.slice(at));if(!token)throw Error('numeric token invalid');
   pieces.push(text.slice(last,at),JSON.stringify(token[0]));at+=token[0].length;last=at;continue;
  }
  at++;
 }
 pieces.push(text.slice(last));return JSON.parse(pieces.join(''));
}
const synthetic=String.raw`{"unsafe":900719925474099312345,"quoted":"001\\\"2","decimal":-12.3400,"exponent":1.23e+45,"truth":true,"none":null,"array":[0,-0,2]}`;
const checked=lossless(synthetic);assert.equal(checked.unsafe,'900719925474099312345');assert.equal(checked.quoted,'001\\"2');assert.equal(checked.decimal,'-12.3400');assert.equal(checked.exponent,'1.23e+45');assert.equal(checked.truth,true);assert.equal(checked.none,null);assert.deepEqual(checked.array,['0','-0','2']);
const eq=(a,b)=>JSON.stringify(a)===JSON.stringify(b), n=x=>{const value=Number(x);if(!Number.isSafeInteger(value)||value<0)throw Error('unsafe index');return value;};
function raw(root,index) {
 const id=String(index).padStart(3,'0'),bytes=fs.readFileSync(root+'/'+id+'.raw'),receipt=JSON.parse(fs.readFileSync(root+'/'+id+'.result.json'));
 assert.equal(receipt.complete,true);assert.equal(receipt.status,200);assert.equal(bytes.length,receipt.bytes);assert.equal(receipt.retainedBytes,bytes.length);assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),receipt.sha256);
 return {receipt,bytes,data:root.includes('provider-s2-rpc-cm-2')?lossless(bytes.toString('utf8')).result:bytes.toString('utf8').trim().split(/\r?\n/).map(lossless)[0]};
}
const rpcRoot='C:/crypto-research-evidence/provider-s2-rpc-cm-2/provider-feasibility',sqdRoot='C:/crypto-research-evidence/provider-s2-sep28b/provider-feasibility';

const canon=x=>JSON.stringify(x,(k,v)=>v&&typeof v==='object'&&!Array.isArray(v)?Object.fromEntries(Object.entries(v).sort(([a],[b])=>a.localeCompare(b))):v);
const keyFor=t=>canon(t.transaction?.signatures||t.signatures), effective=t=>[...t.transaction.message.accountKeys,...(t.meta?.loadedAddresses?.writable||[]),...(t.meta?.loadedAddresses?.readonly||[])];
function instructions(t) {
 const keys=effective(t), out=t.transaction.message.instructions.map((x,i)=>({address:[String(i)],program:x.programIdIndex!==undefined?keys[n(x.programIdIndex)]:undefined,accounts:x.accounts.map(y=>keys[n(y)]),data:x.data,kind:'outer'}));
 for(const group of t.meta?.innerInstructions||[]){
  const paths={1:[group.index]},counters=new Map();let prior=1;
  for(const x of group.instructions){const height=x.stackHeight==null?null:n(x.stackHeight);if(height===null||height<2||height>prior+1)throw Error('unavailable/invalid stack path');
   const parent=paths[height-1];if(!parent)throw Error('unavailable parent');const id=canon(parent),child=counters.get(id)||0;counters.set(id,child+1);
   const address=parent.concat(String(child));paths[height]=address;prior=height;
   out.push({address,program:keys[n(x.programIdIndex)],accounts:x.accounts.map(y=>keys[n(y)]),data:x.data,kind:'inner'});
  }
 } return out;
}
function multi(values){const map=new Map();for(const x of values){const k=canon(x);map.set(k,(map.get(k)||0)+1);}return map;}
function difference(left,right){const a=multi(left),b=multi(right);return {missing:[...b].flatMap(([k,count])=>Array(Math.max(0,count-(a.get(k)||0))).fill(JSON.parse(k))),extra:[...a].flatMap(([k,count])=>Array(Math.max(0,count-(b.get(k)||0))).fill(JSON.parse(k)))};}
const tokenFact=(entry,field)=>entry===undefined?null:Object.hasOwn(entry,field)?entry[field]:'__MISSING__';
function tokenRows(t,signature){
 const keys=effective(t),pre=new Map((t.meta?.preTokenBalances||[]).map(x=>[x.accountIndex,x])),post=new Map((t.meta?.postTokenBalances||[]).map(x=>[x.accountIndex,x]));
 return [...new Set([...pre.keys(),...post.keys()])].map(index=>{const a=pre.get(index),b=post.get(index);return{signature,account:keys[n(index)],preMint:tokenFact(a,'mint'),postMint:tokenFact(b,'mint'),preOwner:tokenFact(a,'owner'),postOwner:tokenFact(b,'owner'),preDecimals:a===undefined?null:tokenFact(a.uiTokenAmount,'decimals'),postDecimals:b===undefined?null:tokenFact(b.uiTokenAmount,'decimals'),preAmount:a===undefined?null:tokenFact(a.uiTokenAmount,'amount'),postAmount:b===undefined?null:tokenFact(b.uiTokenAmount,'amount')};});
}
console.log('SYNTHETIC_LOSSLESS_PASS unsafeInteger/escapedString/decimal/exponent/bool/null/array; no financial Number conversion');
for(const [rpcIndex,samples] of [[1,[2,3,4]],[2,[5,6,7]]]) {
 const rr=raw(rpcRoot,rpcIndex),block=rr.data,rpcTx=block.transactions.map((t,i)=>({t,index:String(i),signature:keyFor(t),instructions:instructions(t)})),rpcMap=new Map(rpcTx.map(t=>[t.signature,t]));
 assert.equal(rpcMap.size,rpcTx.length);
 console.log(canon({rpcIndex,bytes:rr.bytes.length,sha:rr.receipt.sha256,totalTransactions:rpcTx.length,versions:rpcTx.reduce((r,x)=>(r[x.t.version]=(r[x.t.version]||0)+1,r),{}),header:block.blockhash}));
 for(const sample of samples){
 const sr=raw(sqdRoot,sample),s=sr.data,request=JSON.parse(fs.readFileSync(sqdRoot+'/'+String(sample).padStart(3,'0')+'.request.json')),program=request.request.body.instructions[0].programId[0];
 const selected=rpcTx.filter(x=>x.instructions.some(i=>i.program===program)),sqTx=s.transactions||[],sqMap=new Map(sqTx.map(t=>[keyFor(t),t])),txBySqIndex=new Map(sqTx.map(t=>[t.transactionIndex,keyFor(t)]));
 assert.equal(sqMap.size,sqTx.length);assert.equal(txBySqIndex.size,sqTx.length);
 const missing=selected.filter(t=>!sqMap.has(t.signature)),extra=sqTx.filter(t=>!selected.some(x=>x.signature===keyFor(t))),mappings=[],txMismatch=[];
 for(const st of sqTx){const sig=keyFor(st),rt=rpcMap.get(sig);if(!rt){txMismatch.push({signature:sig,category:'missingRPC'});continue;}
   if(st.transactionIndex!==rt.index)mappings.push([st.transactionIndex,rt.index]);
   for(const [category,a,b] of [['version',st.version,rt.t.version],['err',st.err,rt.t.meta.err],['signatures',st.signatures,rt.t.transaction.signatures],['staticKeys',st.accountKeys,rt.t.transaction.message.accountKeys],['loadedWritable',st.loadedAddresses?.writable||[],rt.t.meta.loadedAddresses?.writable||[]],['loadedReadonly',st.loadedAddresses?.readonly||[],rt.t.meta.loadedAddresses?.readonly||[]]])if(canon(a)!==canon(b))txMismatch.push({signature:sig,category});
 }
 const expectedInstructions=selected.flatMap(rt=>rt.instructions.map(i=>({signature:rt.signature,address:i.address,program:i.program,accounts:i.accounts,data:i.data})));
 const sqInstructions=(s.instructions||[]).map(i=>({signature:txBySqIndex.get(i.transactionIndex),address:i.instructionAddress,program:i.programId,accounts:i.accounts,data:i.data}));
 const ins=difference(sqInstructions,expectedInstructions),committedMismatch=(s.instructions||[]).filter(i=>i.isCommitted!==(rpcMap.get(txBySqIndex.get(i.transactionIndex))?.t.meta.err===null));
 const expectedNative=selected.flatMap(rt=>{const keys=effective(rt.t);assert.equal(keys.length,rt.t.meta.preBalances.length);assert.equal(keys.length,rt.t.meta.postBalances.length);return keys.flatMap((account,i)=>rt.t.meta.preBalances[i]===rt.t.meta.postBalances[i]?[]:[{account,pre:rt.t.meta.preBalances[i],post:rt.t.meta.postBalances[i]}]);});
 const native=difference(s.balances||[],expectedNative),nativeOwners=new Map();
 for(const rt of selected){const keys=effective(rt.t);keys.forEach((account,i)=>{if(rt.t.meta.preBalances[i]!==rt.t.meta.postBalances[i]){const key=canon({account,pre:rt.t.meta.preBalances[i],post:rt.t.meta.postBalances[i]}),owners=nativeOwners.get(key)||new Set();owners.add(rt.signature);nativeOwners.set(key,owners);}});}
 const ambiguousNative=(s.balances||[]).filter(row=>(nativeOwners.get(canon(row))?.size||0)>1).length;
 const expectedTokens=selected.flatMap(rt=>tokenRows(rt.t,rt.signature)),sqTokens=(s.tokenBalances||[]).map(row=>({...row,signature:txBySqIndex.get(row.transactionIndex)})).map(({transactionIndex,...row})=>row),tokens=difference(sqTokens,expectedTokens);
 const headerEq=s.header.hash===block.blockhash&&s.header.parentNumber===block.parentSlot&&s.header.parentHash===block.previousBlockhash&&s.header.timestamp===block.blockTime;
 const summary={sample,program,rawBytes:sr.bytes.length,sha:sr.receipt.sha256,headerEq,rpcWatchedTx:selected.length,sqdTx:sqTx.length,missingSignatures:missing.length,extraSignatures:extra.length,signatureArraysMismatch:txMismatch.filter(x=>x.category==='signatures').length,ordinalMismatch:mappings.length,ordinalMappings:mappings,otherTxMismatch:txMismatch,failedTx:selected.filter(x=>x.t.meta.err!==null).length,
 instructions:{sqd:sqInstructions.length,rpc:expectedInstructions.length,watched:expectedInstructions.filter(x=>x.program===program).length,outer:selected.flatMap(t=>t.instructions).filter(x=>x.kind==='outer').length,inner:selected.flatMap(t=>t.instructions).filter(x=>x.kind==='inner').length,missing:ins.missing.length,extra:ins.extra.length,isCommittedMismatch:committedMismatch.length,sqdNonNullErrors:(s.instructions||[]).filter(x=>x.error!==null).length},
 native:{sqd:(s.balances||[]).length,rpcChanged:expectedNative.length,missing:native.missing.length,extra:native.extra.length,ambiguousRowAttribution:ambiguousNative,duplicateTupleKinds:[...multi(expectedNative)].filter(([,v])=>v>1).length,missingExamples:native.missing.slice(0,2),extraExamples:native.extra.slice(0,2)},
 tokens:{sqd:sqTokens.length,rpc:expectedTokens.length,missing:tokens.missing.length,extra:tokens.extra.length,missingExamples:tokens.missing.slice(0,1),extraExamples:tokens.extra.slice(0,1)},
 instructionMissingExamples:ins.missing.slice(0,1),instructionExtraExamples:ins.extra.slice(0,1)};
 console.log(canon(summary));
 }
}

'@ | node > "$env:TEMP/provider-s2-rpc-v2-comparison.log"
$taskComparisonExit=$LASTEXITCODE
Write-Output "COMPARISON_EXIT=$taskComparisonExit"
```

## Offline ordinal hypothesis and next bounded stage, 28 September

Main subsequently completed V2's final gate: TEMP `provider-s2-rpc-v2-final-*` logs record integrity/strict validation/doctor/diff exits zero, 12/12 OpenSpec items and Maven 96 unit/48 integration tests with zero failures/errors/skips. This is the completed historical gate, not approval of a new launch.

A further read-only inspection of the same eight complete, hash-verified bodies tested whether vote exclusion explains the ordinal differences. Classify a vote transaction here only by an outer instruction referencing `Vote111111111111111111111111111111111111111`; all classified transactions in these two blocks have exactly one outer vote instruction. Midpoint RPC contains 706 such transactions among 1,154. For all 105 watched program rows (32/71/2), `RPC ordinal - SQD index` equals the number of preceding vote transactions: zero hypothesis mismatches, including all 96 nonzero differences. Oldest RPC contains 767 votes among 1,653, but all 70 watched rows (10/60/0) retain RPC ordinals: the same vote-exclusion prediction mismatches all 70. Counts are program rows, not necessarily unique transactions across programs.

Thus midpoint indices are **consistent with vote-excluded ordinals in this sample**, not a proven provider implementation cause or a universal normalization rule. Oldest and midpoint differ; full signature identity remains mandatory and the original 96 field differences are not erased. No provider call, numeric-quantity calculation, code/tool file or normalized evidence rewrite was performed. The lossless synthetic checks and raw completion/hash checks were reused; command exited zero:

```powershell
$taskText = Get-Content -Raw docs/notes/SOLANA_PROVIDER_SPIKE_RESEARCH_2026-09-27.md
$taskSection = $taskText.IndexOf('### Bounded offline reproducer')
$taskCode = $taskText.IndexOf('const fs=', $taskSection)
$taskEnd = $taskText.IndexOf('const canon=', $taskCode)
if ($taskSection -lt 0 -or $taskCode -lt 0 -or $taskEnd -le $taskCode) { throw 'Missing reviewed lossless setup' }
$taskBase = $taskText.Substring($taskCode, $taskEnd - $taskCode)
$taskBody = @'

const vote='Vote111111111111111111111111111111111111111';
for(const [rpcIndex,samples] of [[1,[2,3,4]],[2,[5,6,7]]]) {
 const block=raw(rpcRoot,rpcIndex).data, bySignature=new Map(), prefix=[0], onlyPrefix=[0];
 block.transactions.forEach((t,i)=>{
  const keys=[...t.transaction.message.accountKeys,...(t.meta?.loadedAddresses?.writable||[]),...(t.meta?.loadedAddresses?.readonly||[])], outer=t.transaction.message.instructions;
  const programs=outer.map(x=>keys[n(x.programIdIndex)]),anyVote=programs.includes(vote),onlyVote=programs.length===1&&programs[0]===vote;
  bySignature.set(JSON.stringify(t.transaction.signatures),i);prefix.push(prefix[i]+Number(anyVote));onlyPrefix.push(onlyPrefix[i]+Number(onlyVote));
 });
 const output={slot:block.parentSlot==='410099999'?'410100000':'429644639',transactions:block.transactions.length,voteOuterTransactions:prefix.at(-1),singleOuterVoteTransactions:onlyPrefix.at(-1),samples:[]};
 for(const sqdIndex of samples) {
  const sample=raw(sqdRoot,sqdIndex).data,failedAny=[],failedOnly=[];let differing=0;
  for(const t of sample.transactions||[]) {
   const ordinal=bySignature.get(JSON.stringify(t.signatures));assert.notEqual(ordinal,undefined);
   const reported=n(t.transactionIndex),delta=ordinal-reported;if(delta!==0)differing++;
   if(delta!==prefix[ordinal])failedAny.push({sqd:reported,rpc:ordinal,delta,precedingVotes:prefix[ordinal]});
   if(delta!==onlyPrefix[ordinal])failedOnly.push({sqd:reported,rpc:ordinal,delta,precedingVotes:onlyPrefix[ordinal]});
  }
  output.samples.push({sqdIndex,rows:(sample.transactions||[]).length,differing,anyVoteHypothesisMismatch:failedAny.length,singleOuterVoteHypothesisMismatch:failedOnly.length,firstMismatch:failedAny.slice(0,2)});
 }
 console.log(JSON.stringify(output));
}

'@
($taskBase + $taskBody) | node
$taskVoteExit = $LASTEXITCODE
Write-Output "VOTE_HYPOTHESIS_EXIT=$taskVoteExit"
```

The owner's continuation is now captured as a separate three-request operational plan in design D11: recent PumpSwap at retained slot 451080947, Raydium at 410100000-410100009, and a public finalized produced-slot list for that ten-slot range. It uses the unchanged ordinary 2 MB helper path, not V2's restricted 32 MB profile; Main charges sunk 28 attempts/28,760,438 bytes against approved cumulative 80/100 MB/200 MB limits. Worst-case new traffic is three attempts/6 MB. New input/root/window and fresh safety review/DOCS_CLOSE/Main gate remain pending at this planning entry; no query has run. Actual returned headers determine any unreturned suffix; no automatic follow-up, pagination/reorg PASS, S1 restart or completed spike/selection is implied. S3/S4/S5 access/history/TTL/series limitations, all unchecked tasks and five consumed repairs remain unchanged.

Fresh Reviewer subsequently approved the exact three-request plan/input, frozen source/tests and reproduced vote-hypothesis inspection with the full CI matrix. Main's unchanged regression suite passed 80/80. The actual input `C:/crypto-research-evidence/provider-s2-recent-oldray-1-requests.json` is 2,251 bytes including its final LF, SHA-256 `ae405b746cf4aeb2676d13545bb63844a6cbe363704e65012ddd1e916492b67f`; canonical 2,250-byte serialization matches design SHA-256 `ac424aeb7fbf4e8420e9ee8a852c059b04ce80773c3714f7dd35bffa49d6216f`. Main verified sunk 28 attempts/28,760,438 bytes and inventoried 430 predecessor files, parent usage 108,701,410 bytes. DOCS_CLOSE records reviewed readiness only: Main's complete gate, exclusive aggregate preflight and one enabled launch remain pending. Tasks 3.1-3.6 stay unchecked; no code/configuration/semantics change, archive or further launch is authorized here.


## Recent PumpSwap / oldest Raydium stage — actual stop, 28 September

Main's complete prelaunch gate passed: integrity/strict validation/doctor/diff exits zero, OpenSpec 12/12, Maven 96 unit/48 integration tests with zero failures/errors/skips; logs TEMP `provider-s2-recent-oldray-prelaunch-*.log`. The single enabled process exited normally with code 1 in 618 ms. Only recent PumpSwap slot 451080947 was sent, at `2026-09-28T18:12:31.448Z`, and its response ended at `18:12:31.911Z` (463 ms). Summary reason is `HTTP_ERROR`, status 529, `complete:false`. The retained 163-byte JSON error envelope reports `type:rate_limit_error`, `code:overloaded`; it is no transaction sample and no completeness FAIL. Architect independently checked exact length/retained count/status/completion/SHA-256 against the result receipt: `1d033e413a5c2f2ec6e30ce25548e39a4bd9c7591386563388b3dc2cf6b26f97`.

Evidence root is `C:/crypto-research-evidence/provider-s2-recent-oldray-1/provider-feasibility`; five files total 6,872 bytes. Logs are TEMP `provider-s2-recent-oldray-live-{launch,stdout,stderr}.log`. The stage adds one attempt/163 received bytes: cumulative **29 attempts/28,760,601 bytes**, leaving 51 attempts/71,239,399 bytes under 80/100 MB, not another launch permission. Both oldest Raydium range 410100000-410100009 and the same-range public `getBlocks` request remain **UNEXECUTED**. Main verified all 430 predecessor hashes unchanged. Preflight E=30,435,627/worst reservation 37,566,699 bytes was below 200 MB; parent usage/free space were 108,701,410/126,923,173,888 bytes. This plan is stopped; no retry, tail query, resumed root or new window has run. Fresh Reviewer APPROVE covers the actual receipt, 430 predecessor hashes, bounded official-source claims and full CI matrix, with frozen tests/configuration unchanged. DOCS_CLOSE records that approval only; Main's complete assembly gate remains pending. S2 stays INCONCLUSIVE; no task is checked.

### Remaining feasibility — official documentation only, checked 28 September

- **S3 historical account state:** Alchemy documents standard `getAccountInfo` extended with `slot` (inclusive finalized state), `lastUpdateBeforeSlot` and `firstUpdateAfterSlot` (exclusive update cursors), mutually exclusive with each other and `minContextSlot`. It explicitly has no separate `getAccountInfoAtSlot` method, claims coverage since July 2025, and distinguishes an absent/closed account from its coverage error. These are provider claims, not tested access. Ordinary current-state RPC cannot replace the required historical reads. The owner's PAYG archive entitlement, exact CU price and actual pool-state/repeat correctness remain unverified; the existing zero-paid D11 slice authorizes no Alchemy request. A new bounded historical-read plan with explicitly shared paid counters/authorization is required before execution. [Alchemy Account Archive](https://www.alchemy.com/docs/solana/account-archive); [standard Solana getAccountInfo](https://solana.com/docs/rpc/http/getaccountinfo).
- **S4 source time/TTL/history:** GoPlus calls its APIs real-time and its Solana beta reference accepts `contract_addresses`; the inspected reference exposes no historical cutoff or numeric cache TTL. Its Solana changelog records reduced cache-update time in V0.0.4 but supplies no numeric interval there. These inspected documents do **not** prove historical support absent or a TTL of zero. Retained current example-mint agreement remains valid; identical repeat bodies cannot establish cache lifetime. Exact source-time/cache/history semantics require additional primary clarification or verified capability evidence, not another unchanged-response inference. [API overview](https://docs.gopluslabs.io/reference/api-overview), [Solana reference](https://docs.gopluslabs.io/reference/solanatokensecurityusingget), [Solana changelog](https://docs.gopluslabs.io/changelog/token-security-api-for-solana).
- **S5 holder series/access:** Helius documents mint/owner-filtered token-account pagination, a required API key obtainable free, and `last_indexed_slot`; that index watermark is not itself an historical snapshot/cutoff guarantee, and token-account count is not distinct positive-balance-owner count. Bitquery describes historical transfer-aggregate V1 versus approximately eight-hour balance-update V2, with top-limited examples, and separately documents authenticated access tokens. These claims do not verify complete pagination, immutable cutoffs, zero-cost owner entitlement or a reproducible full-owner ≥4-hour series. No Helius/Bitquery account/token was acquired or API called. Use authorized existing access and a bounded query/cost contract before real samples; current top holders or recent active accounts cannot substitute. [Helius getTokenAccounts](https://www.helius.dev/docs/api-reference/das/gettokenaccounts), [Bitquery holders](https://docs.bitquery.io/docs/blockchain/Solana/solana-token-holders/), [Bitquery authorization](https://docs.bitquery.io/docs/authorization/how-to-generate/).

These are bounded feasibility findings, not a new executable plan or provider selection. D11 permits later investigation only through a separately bounded plan, preserving stopped receipts, cumulative limits and review/gate requirements. The two unexecuted free requests are a concrete remaining S2 candidate, distinct from automatically retrying failed PumpSwap; this DOCS entry enables no calls. S1 stays permanently stopped/INCONCLUSIVE; S2 continuation/reorg/full-envelope cost and recent PumpSwap remain unresolved; S3-S5 actual capability evidence and 3.6 selection remain open. All tasks 3.1-3.6 remain unchecked; five consumed repairs, 80 frozen tests, source SHA and owner configuration remain unchanged. No archive or production work follows.

The subsequent separately bounded D11 tail plan selects only the two unsent Raydium/produced-slot requests, copied unchanged into a new input/root. It preserves the absolute `2026-09-28T18:42:31.3303831Z` window and sunk 29 attempts/28,760,601 bytes; it is not a retry of the failed PumpSwap query. Exact 1,241-byte compact input validated offline, SHA-256 `d68c61c21e96ee5e9ea34a9a183d44825b2f57b932f457c3a4e0e8b798eea42e`. Fresh review, Main's assembly gate and exclusive aggregate preflight remain pending; no tail query has run and no spike acceptance changes.

Fresh Reviewer subsequently APPROVE'd this exact two-request tail plan/input, frozen source/80 tests and immutable deadline with the full CI matrix. Main's concrete `provider-s2-oldray-tail-1-requests.json` is 1,242 bytes including its final LF, SHA-256 `778b42480042da90e6e3af9bb9a7857549500759e535b599261047a33efc7e42`; its canonical serialization matches the 1,241-byte design digest above. Main inventoried 436 predecessor files, parent usage 108,709,524 bytes. DOCS_CLOSE records reviewed readiness only: Main's complete assembly gate, exclusive aggregate preflight and one launch remain pending before the unchanged absolute deadline. No task is checked, no plan/code/configuration changes or archive, and no call is enabled by this documentation closure.

## Oldest Raydium / produced-slot tail — actual complete responses, 28 September

Main's complete assembly gate passed: integrity/strict validation/doctor/diffs zero, OpenSpec 12/12, Maven 96 unit/48 integration tests with zero failures/errors/skips; TEMP `provider-s2-oldray-tail-prelaunch-*.log`. One owned process (PID 16216) exited normally with code 0 in 2,735 ms, `LIST_EXHAUSTED`, preserving absolute deadline `2026-09-28T18:42:31.3303831Z`. Both receipts are HTTP 200/`COMPLETE_RESPONSE`, not a whole-spike PASS. Evidence: `C:/crypto-research-evidence/provider-s2-oldray-tail-1/provider-feasibility`; eight files/385,276 bytes. TEMP `provider-s2-oldray-tail-live-{launch,stdout,stderr}.log` records process execution. Raydium sent `18:29:51.427Z` to `18:29:52.964Z` (1,537 ms), 378,121 bytes, SHA-256 `a4b3e47ebc9fae1fe2a30a892054a0ae8a049563e2c69ab77976a145f9ef8072`; finalized `getBlocks` sent `18:29:53.431Z` to `18:29:54.018Z` (587 ms), 137 bytes, SHA-256 `d1b8eae379a36c415ed38596d17820f44c0b4dafa5e3919e6021e42039308e5f`. Two new attempts/378,258 bytes bring cumulative usage to **31 attempts/29,138,859 received bytes**; remaining 49 attempts/70,861,141 bytes is arithmetic, not a retry or fresh-window permission. Main verified all 436 predecessor hashes unchanged; preflight E=30,765,524/worst 35,896,596 bytes, parent/free 108,709,524/126,932,197,376 bytes.

Architect reused the reviewed lossless scanner and unsafe-integer/string/decimal/exponent synthetic assertions from the bounded offline reproducer above, then checked both raw lengths/digests/complete receipts before parsing all ten NDJSON records and the RPC produced-slot array (`TAIL_INSPECTION_EXIT=0`; no tool/code file or network). The SQD header numbers exactly equal the ten finalized RPC produced slots **410100000-410100009**: zero skipped slots, missing produced slots, extra returned slots, duplicate headers/signature arrays, adjacent parent-number/hash discontinuities or unreturned suffix; last header is 410100009. First header matches retained Stage B request 4. Actual UTC range is **2026-03-31T13:33:43Z-13:33:47Z**, still beyond the declared 180-day boundary relative to the retained reference end. Slot 410100000 remains an empty Raydium subset; the following nine slots provide positive oldest-range examples. Field-presence inspection found zero missing requested header/transaction/instruction/native/token fields on emitted records; quantities remained original strings, not floating-point values.

| Slot | Transactions | Failed | Related instructions | Watched instructions | Native rows | Token rows |
|---|---:|---:|---:|---:|---:|---:|
| 410100000 | 0 | 0 | 0 | 0 | 0 | 0 |
| 410100001 | 2 | 1 | 23 | 2 | 3 | 14 |
| 410100002 | 2 | 0 | 16 | 2 | 4 | 10 |
| 410100003 | 4 | 0 | 35 | 4 | 6 | 20 |
| 410100004 | 1 | 0 | 8 | 1 | 2 | 5 |
| 410100005 | 3 | 2 | 22 | 3 | 4 | 17 |
| 410100006 | 2 | 2 | 14 | 2 | 2 | 12 |
| 410100007 | 8 | 2 | 100 | 9 | 20 | 61 |
| 410100008 | 8 | 1 | 89 | 10 | 18 | 53 |
| 410100009 | 14 | 4 | 150 | 16 | 38 | 90 |
| Total | 44 | 12 | 457 | 49 | 97 | 282 |

The 44 unique full-signature arrays comprise nine legacy/35 V0 transactions, including failed outcomes. Instructions include all emitted related transaction instructions, not only the 49 Raydium-program instructions. The independently checked produced-slot list and parent chain establish only this ten-slot range's **header/slot-list reconciliation**. No full RPC transaction payload for the new positive slots was downloaded, so watched-signature/instruction/balance completeness against an independent provider remains **unverified**; no pagination/reorg/full-envelope/cost PASS follows. Fresh Reviewer APPROVE independently confirms the receipt, bounded counts/header facts, predecessor integrity and full CI matrix, with frozen source/tests/configuration unchanged. DOCS_CLOSE records that approval only; Main's complete final gate remains pending. S2 stays INCONCLUSIVE; S1 stays permanently stopped, S3-S5/3.6 remain open, all tasks 3.1-3.6 unchecked. Five consumed repairs, 80 frozen tests/source and owner configuration are unchanged. No further query, restart or archive follows this receipt.

## Current read-only diagnosis and remaining access — 2026-09-28 19:07 UTC

This factual update preserves the earlier receipts/authorizations. Main subsequently completed the oldest-Raydium tail's final gate: `TEMP/provider-s2-oldray-tail-final-*`, Maven 96 unit/48 integration tests with zero failures/errors/skips, strict OpenSpec 12/12, integrity/doctor/diff checks zero. S3 D12 is now frozen initial BUILD in progress, not provider execution or capability completion; its review, gate and single launch remain pending. All tasks 3.1-3.6 stay unchecked, with five consumed repairs and no archive. This note changes no D12 behavior, resource allowance, source/test freeze or owner configuration.

**S1 diagnosis, independently confirmed by fresh read-only review.** Eight complete RPC `getBlock` bodies (IDs 6-13) retained in `C:/crypto-research-evidence/alchemy-s1/smoke-retry-1/raw.bin` total 27,133,016 bytes, mean 3,391,627. The interval from preceding `getBlocks` last-body receipt `18:45:36.809Z` through block 13's last receipt `18:46:01.776Z` is 24.967 seconds, or 3.120875 seconds per produced block: an elapsed request-cycle measurement, **not** isolated response-body latency, send-to-receive latency or provider TTFB. Raw metadata lacks actual send times; observed body-receipt spans average approximately 2.531 seconds. Per the reviewed receipt diagnosis, the permanent RECEIVED_LIMIT decision projects `75,861,230 - 1,088,979 + 16,842,752 + 8,388,608 = 100,003,611` bytes, above 100 MB by 3,611: final settled traffic includes the 1,088,979-byte in-flight RPC partial already covered by its reservation, so it is excluded when reconstructing decision-point settled traffic before adding the simultaneous RPC/gRPC reservations. It retained 14 RPC attempts, two stream starts, zero actual LIVE and unresolved 451080926-451080934 with `complete=null`; this is not evidence of provider failure or a demonstrated accounting bug.

The existing `tools/spikes/alchemy-s1/collector.cjs` `position()` uses RPC-verified complete+1 (or unresolved/anchor), not a LIVE-advanced frontier. Its fixed target is captured before reconciliation; `core.cjs` `reconcile()` verifies every produced slot, including already journaled gRPC replay/LIVE, advances complete only after a 1,000-slot chunk, and compares signatures without importing missing RPC transactions. Thus later recovery includes prior LIVE and slow verification time, not only the forced gap. At the tiny sample's nominal 9,000 produced slots/hour, the measured cycle/body-size model projects approximately 30.525 GB and 7.802 hours of RPC work for one hour alone; the observed 9/9 produced ratio is not a chain-wide guarantee. The 10-hour ceiling leaves only 2h55 after six LIVE hours and 65 forced-disconnect minutes, before setup/replay. Raising smoke or disk alone cannot establish achievable recovery. Escalation's final verdict is **OWNER_DECISION**: the needed fixed-interval/source-aware recovery and separate durable/per-range completeness contract correct existing S1 behavior and needs explicit authorization for one additional bounded repair/replan with new RED/fresh review/gate/capacity assessment. That decision is pending; no old stop/root/debit is reset, and five repairs remain consumed.

**S2 scope and cost sensitivity, not exhaustive-download acceptance.** D10 asks sampled slots across the 180-day envelope, independent chain spot-checks, fields, continuation/reorg behavior and cost—not full reconciliation/download of every historical slot. Retained `provider-s2-sep28b/provider-feasibility/002.raw`-`007.raw` measure 74,460/787,843/194 bytes for the corrected-old three-program queries and 220,619/843,792/42,610 for midpoint. Their sums are 862,497 and 1,107,021 bytes per sampled slot, but related transactions/instructions overlap between separate program queries; these are not deduplicated union-stream volumes or representative daily means. For sensitivity only, assuming every nominal 216,000 slot/day produced the corresponding sample shape yields 186,299,352,000-239,116,536,000 bytes/day and 33,533,883,360,000-43,040,976,480,000 bytes/180 days. That explicit, deliberately restrictive assumption is not a measured daily forecast/confidence interval and cannot select a production tier.

Canonical [SQD pricing](https://sqd.dev/pricing/) and [Portal pricing documentation](https://docs.sqd.dev/en/portal/pricing), checked September 28, announce Public $0/~50 MB/month, Testing $0/~1 GB/month, Starter $99/25 GB and Growth $499/400 GB, measuring uncompressed data. They explicitly say billing is not live yet; paid launch/opt-in extension scenarios are not an existing owner purchase or invoice. Neither base paid allowance covers the sensitivity model; extension/throughput/retention choices and representative deduplicated payload measurements are still needed for a defensible full-envelope estimate. The current public calls were zero-paid. [SQD API](https://docs.sqd.dev/en/portal/solana/api) defines continuation from the actual last header+1 and stale-parent HTTP 409 `base_block_mismatch`, even on finalized queries. Current helper validation rejects `parentBlockHash`; a separately planned/tested feature would be needed to exercise that protocol. A controlled wrong/stale-parent response would not be proof of an actual finalized-chain reorg, and arbitrary adjacent ranges are not server-pagination PASS.

**S4 current evidence, not PIT security facts.** The existing dated field matrix and current RPC authority/supply agreement remain measured facts. The inspected [GoPlus Solana endpoint](https://docs.gopluslabs.io/reference/solanatokensecurityusingget), [Solana response fields](https://docs.gopluslabs.io/reference/response-detail-1) and [API overview](https://docs.gopluslabs.io/reference/api-overview) describe current/dynamic security information and a top-ten holder list. They establish no TTL or historical selector in the inspected material. TTL/history therefore remain **unknown**, not zero or universally unsupported; identical repeats and documentation absence prove neither freshness nor PIT correctness. A later feasibility conclusion may preserve these limitations rather than invent a historical fallback.

**S5 newly identified alternative, documentation only.** [Alchemy getTokenHoldersAtSlot](https://www.alchemy.com/docs/chains/solana/solana-api-endpoints/get-token-holders-at-slot) documents historical ranked token accounts with a required slot, optional limit (default 1,000/maximum 10,000), sort order, owner and raw-balance fields. The inspected response schema has no pagination or total distinct-owner count. It uses the same RPC key path, so missing Helius/Bitquery names do not prove no possible access; actual app entitlement, coverage and method-specific quota/CU price remain unverified. The published [CU table](https://www.alchemy.com/docs/reference/compute-unit-costs) does not list this method; no price is borrowed from getAccountInfo.

A future separately authorized small classic-SPL sample could conditionally prove population exhaustion by matching the exact sum of unique, independently validated positive token-account amounts to the same-cutoff historical mint supply, excluding Token-2022 fee/extension complications, then counting distinct owners. Supply equality alone cannot authenticate fabricated/redistributed rows, and list length below the limit alone is not completeness proof. Two independently dated snapshots at least four hours apart with equal-cutoff repeatability could support a sample series under D9/D11, not unrestricted large-mint coverage, sampling cadence or proof of when the historical snapshot originally became available. This is a proposed proof condition, **not** an actual holder receipt, allowed S3 request or S5 implementation plan. S1's additional-repair decision, post-run provider usage attribution and S5 pricing/access remain concrete pending inputs; task 3.6 still depends on honest capability evidence.

### S3 initial RED escalation — 2026-09-28 19:16 UTC

Fresh Reviewer's final verdict is **ESCALATE / TEST_SPEC_ERROR**, not implementation APPROVE. `TEMP/provider-s3-archive-red.log` records 98 executed tests: 85 pass, 13 fail, zero skips/cancellations; no GREEN or provider call follows. New valid/boundary fixtures hard-code JSON-RPC response ID 15 although the declared ordered requests use IDs 15-26. Matching response/request IDs is the existing protocol requirement, not a D12 acceptance change. Reviewer permits only bounded additive fixture correction, including retry/error fixtures, with `requires_new_red=true`, preserving unsafe numeric lexemes, exact 131,072/131,073-byte boundaries, raw-prefix/matrix assertions and all 85 original S1 tests. The frozen invalid fixtures cannot be silently corrected or reused as valid RED evidence.

Execution and implementation continuation are **blocked pending owner authorization for additional repair capacity**: the combined question covers the bounded S3 fixture correction and S1 recovery correction; five repairs remain consumed, with no budget reset. D12 requirements and declared source/test limits are unchanged. Main's documentation integrity/strict validation/doctor/diff checks remain pending; the new implementation's complete Maven gate is unrun because implementation is not ready. The preceding recorded gate PASS belongs to completed S2 work, not S3. This factual blocked-status update awaits independent review; all tasks remain unchecked, with no archive, accepted-spec/configuration change or new traffic.

### Owner-approved research replan — 2026-09-28 UTC

The owner subsequently answered yes to all three escalated choices: exactly two addressed additional passes (S3's bounded fixture correction/new RED then original initial build; S1's bounded recovery correction/replan with no monetary-limit increase), honest unsupported/unverified conclusions without purchases or paid alternatives, and a provisional provider-research conclusion with explicit deferred validations when a verified final choice is infeasible. This supersedes the pending-owner statements above, not their historical evidence or the five consumed repairs. D13 retains the original 3.1-3.6 targets visibly as deferred/unverified and introduces separate research-disposition tasks; none is checked here. S1's corrective exception remains allocated and unused: existing measurements do not establish feasible recovery under current ceilings, so no correction implementation or live run is planned in this slice. D12 remains frozen during S3 initial build. Provisional research closure is not verified primary selection and does not unblock F1/F3, mass recording or any unsupported capability; final factual disposition/review/gate remain pending.

## Research disposition and provisional source recommendation — 2026-09-29 03:07 UTC

This is D13's research-only disposition, with the latest 2026-09-29 owner authority superseding earlier pending-owner statements without rewriting their history. Five earlier repairs plus exceptions A (S3 fixtures) and B (the two S3 CLI defects) are now used: seven maximum, no reset or remaining corrective pass; S1 stays deferred. Main's complete S3 launch gate passed; the enabled CLI launch then rejected locally with STATE_DEBIT_INVALID before provider calls. S3 is **UNEXECUTED**, not provider FAIL. Fresh Reviewer APPROVE covers the amended research-only scope and actual evidence, with all applicable CI passing, CI-06/14 N/A, no blocking integrity/safety findings and `red_suspect=false`. The owner substitutes that review plus Main's complete gate for D13 documentation RED only. Main's research final gate passed (`TEMP/provider-contract-research-final-gate-*`: integrity/Docker/Maven zero, 96 unit/48 integration tests with zero failures/errors/skips, strict OpenSpec 12/12, doctor/diff/cached-diff zero). All seven current research/planning tasks are verified and checked; original 3.1-3.6 and D12 runtime acceptance remain deferred/unmet. Main's subsequent assembled-checkbox gate/checkpoint/archive are separate, not claimed here. No runtime certification, provider query or purchase follows this status.

| Spike / tested tier | Measured support and evidence scope | Research disposition / remaining limits |
|---|---|---|
| S1 — Alchemy PAYG Yellowstone + RPC | Two retained smoke roots `alchemy-s1/smoke` and `smoke-retry-1`: 75,861,230 received bytes, 14 RPC attempts, two stream starts, zero LIVE; final clean RECEIVED_LIMIT, unresolved 451080926-934. The reviewed capacity/recovery diagnosis above separates body receipt timing from cycle time and preserves correct reservation accounting. | **INCONCLUSIVE; transport candidate only.** No six-hour observation, forced-gap completion, recovered-range verification or post-run dashboard/quota proof. Current full-block recovery workload does not establish a safe protocol within unchanged 10 GB/10-hour/20,000-attempt ceilings; another smoke is not a solution. |
| S2 — SQD Public Portal + independent public finalized RPC | Across corrected-old/midpoint/recent sample positions, retained full fields and real 180.2155-day depth; `provider-s2-rpc-cm-2` provides complete independent blocks for 410100000/429644639. All six program-query signature sets, transaction metadata, flattened instructions, changed native-balance multisets and all token sidefacts matched under full-signature joins. Midpoint filtered ordinals differed in 28/66/2 rows; their vote-exclusion consistency is a sample hypothesis, not a universal identity rule. `provider-s2-oldray-tail-1` adds ten complete Raydium headers matching the finalized produced-slot list/parent chain and 44 transactions, 457 instructions, 97 native/282 token rows. | **Supported on those real samples; overall S2 INCONCLUSIVE.** Old single-slot Raydium was empty; the positive range has only independent header/slot-list reconciliation, not new transaction-payload comparison. Recent PumpSwap remains unverified after retained HTTP 529 overload receipts. No actual server-driven continuation or stale-parent 409 exercise. Announced pricing sensitivity is not a representative full-envelope forecast or purchased entitlement. D11 total remains 31 attempts/29,138,859 bytes; headroom is not retry permission. |
| S3 — configured Alchemy PAYG; no actual historical-account receipt | D12 fixes 12 reads of one pool/two vaults at actual parent 429644638 and child 429644639, with independent complete child-block token facts and exact raw-amount expectations. [Account archive documentation](https://www.alchemy.com/docs/solana/account-archive) and [getAccountInfo](https://www.alchemy.com/docs/chains/solana/solana-api-endpoints/get-account-info), inspected September 28, document finalized historical reads. | **UNEXECUTED: local STATE_DEBIT_INVALID before calls.** No Alchemy capability/entitlement failure is inferred. Paired historical bytes, owners/mint/authority/raw amounts and parent-cutoff proof remain unverified, as does deployed pool layout. Zero new RPC attempts/received bytes; inherited Alchemy counters remain 14/75,861,230. D12's ceilings are unchanged; published CU estimates are not actual usage/invoices. |
| S4 — public GoPlus Solana + current public RPC | Two complete retained GoPlus example-mint responses and contemporaneous RPC mint/supply facts in `d11-continuation-1`: the dated 20-field availability matrix, current authority absence and exact supply agreement are supported. The inspected [Solana field reference](https://docs.gopluslabs.io/reference/response-detail-1) identifies current security facts/top-ten holder information. | **Current-only sample support; historical/PIT risk capability unverified.** TTL, source-visible time and historical selector semantics remain unknown, not TTL zero or universally unsupported. Equal repeats do not establish freshness; top-ten rows are not an exhaustive holder population. |
| S5 — no historical-holder query executed | No Helius/Bitquery holder-series entitlement or usable receipt was established. Alchemy's documented [getTokenHoldersAtSlot](https://www.alchemy.com/docs/chains/solana/solana-api-endpoints/get-token-holders-at-slot), inspected September 28, is a possible same-key alternative, not an admitted D12 method or measured entitlement. | **No source selected; historical series unverified.** Ranked token-account limit 10,000 does not prove a complete distinct-owner count; pagination/total-count guarantee, method CU price, historical access and repeatable cutoff remain unknown. The conditional classic-SPL conservation idea above is not actual evidence and cannot substitute for a validated population. |

### Provisional choices, not verified primary selection

| Consumer need | Provisional research candidate | Admission boundary |
|---|---|---|
| Watched-program live transport | Alchemy PAYG Yellowstone | Candidate because it is the configured, actually received transport; **not verified suitable**, no S1 PASS or production adapter. |
| Historical instruction ranges | SQD, with independently checked public RPC samples | Candidate supported on specified samples only. [Canonical SQD pricing](https://sqd.dev/pricing/) is announced ahead of billing launch; current public use was zero-paid. No purchased Dedicated tier, guaranteed 180-day production completeness or cost commitment is implied. |
| Historical pool/account state | Alchemy PAYG D12 candidate | No capability admission until actual paired-slot repeat/owner/mint/raw-amount checks and limitations are reviewed; deployed pool layout may remain unresolved. |
| Current security-field enrichment | GoPlus plus independent current RPC facts | Supported only for the dated example facts; cannot supply historical decision-time risk evidence while TTL/history/source-time are unknown. |
| Historical holder-count growth | None | Leave the capability unimplemented; no paid alternative or newly acquired account is proposed. |

The recommendation does **not** select a verified production primary, complete F1/F3 readiness, authorize mass recording or provide signal-grade USD price/liquidity/holder facts. Missing capabilities stay unimplemented; no current-data fallback is admitted into a historical decision. The normative verified-primary barrier remains unchanged. Research closure can document this provisional result, not turn these limited observations into completed original spikes.

### Retained original targets and future conditions

| Original target — still not complete | Missing validation | Condition before future continuation |
|---|---|---|
| 3.1 / S1 | Six actual LIVE hours; both forced gaps; source-aware durable recovery/completeness; live latency and provider usage/quota evidence. | Future correction needs new owner repair authorization and a separate bounded contract demonstrating feasible fixed-gap/per-range recovery under unchanged ceilings, meaningful RED/freeze/GREEN and fresh review/gate, preserved stop/debits and a newly authorized immutable launch. B is reassigned to S3; no old root resumes. |
| 3.2 / S2 | Recent PumpSwap, positive-Ray transaction spot-check, real continuation/stale-parent protocol behavior, representative deduplicated cost/throughput evidence. | Any additional finite query/helper change needs its own approved scope, fresh review/gate, remaining cumulative 80/100 MB/200 MB accounting and new bounded deadline. Preserve all stopped roots; no overload auto-retry, exhaustive 180-day-download requirement or manufactured actual-reorg PASS. |
| 3.3 / S3 | Actual historical-account entitlement and paired-slot/repeat facts; owner/mint/authority/raw amounts; supported layout/reconstruction limits and actual usage attribution. | The single authorized CLI launch hit an unresolved legacy-summary compatibility defect: first smoke has no `attempt` field, which the new debit validator requires. No retry/corrective capacity remains. Any fix needs new owner authority, a bounded contract/meaningful RED/fresh review/gate and separately authorized immutable launch; no old root or budget reset. Real D12 compatibility and historical capability remain **UNEXECUTED/unverified**. |
| 3.4 / S4 | Numeric TTL/source availability, historical/PIT access and coverage guarantees. | Primary provider clarification or separately authorized evidence is needed before admitting historical risk facts. Otherwise retain this dated current-field matrix and the explicit unknowns; more identical repeats do not resolve them. |
| 3.5 / S5 | Actual available historical access, exhaustive distinct-positive-owner populations, repeatable dated counts/availability, limits and price. | Evidence of existing access and method cost, then a separately approved finite historical completeness/repeat plan within authorized spend; no purchases/paid alternatives. A ranked subset or supply-equality assumption alone is insufficient. Until then holder growth remains unimplemented. |
| 3.6 / verified provider selection | Verified required capabilities and a decision justified by them. | Resolve the relevant original validations before verified-primary/F1/F3 claims. Current D13 permits only this provisional research disposition with explicit disabled capabilities and follow-on gates. |

Fresh Reviewer APPROVE and Main's completed research gate verify research-disposition tasks 4.1-4.3 under the amended D13 scope. Under the owner's explicit research-deferral authority, D13 defers only the known fail-closed nonproduction legacy-`attempt` compatibility finding from functional completion. Review covers evidence integrity/safety, the provisional decision and every named deferral, with no blocking integrity/safety findings; it does not certify D12 runtime functionality. The actual S3 refusal, exact defect and unmet original acceptance remain visible below, and any fix still needs new owner repair authority/meaningful RED/review/gate/launch. Runtime, budgets, frozen tests, owner configuration and verified-primary/F1/F3 barriers are unchanged; no archive is performed in DOCS_CLOSE.

### Current blocked handoff — 2026-09-29 03:13 UTC

The owner's three yes answers are applied in D13; they do not authorize unbounded repairs or a documentation RED waiver. S3's specifically authorized fixture correction produced valid new RED in `TEMP/provider-s3-archive-fixture-red.log` (98 executed, 85 pass/13 expected assertion failures, zero skips/cancellations), followed by Builder GREEN in `TEMP/provider-s3-archive-fixture-green.log` (98/98, zero failures/skips/cancellations) and D11 regression 80/80. Original frozen assertions remain retained; corrected additive fixtures were frozen after their new RED. These are offline tests, not provider evidence.

Fresh independent D12 review nevertheless returned **ESCALATE**, identifying two blockers: `tools/spikes/alchemy-s1/cli.cjs:139` invokes predecessor Journal inspection that can create a directory on a missing-predecessor refusal path, and `cli.cjs:195` does not propagate child IPC/exit loss reliably, allowing false parent success. No API call occurred. The proposed bounded remedy is the same Builder's CLI correction plus additive regression RED; it is **not authorized yet**, because further owner repair permission is pending. S1's separately allocated exception remains unused and may be reassigned only with owner approval.

Separately, D13's Escalation verdict is **OWNER_DECISION**: D12 behavioral RED does not establish D13's distinct research-closure predicates; an explicit one-time documentation RED exception is needed, with fresh full-CI review and Main's complete gate substituting only for D13. No waiver is applied. There is no new complete Main gate, enabled launch or archive; the earlier PASS belongs to S2. Research tasks 4.1-4.3 remain unchecked and original 3.1-3.6 targets remain deferred/unverified. This factual status supersedes earlier BUILD-pending entries without changing D12/D13 semantics, budgets, code, source/test freeze or owner configuration.

### Latest owner authority reconciliation — 2026-09-29 UTC

The owner answered yes to both final requests: unused exception B is reassigned from S1 to **one** same-Builder S3 CLI repair of missing-predecessor mutation and false parent success after IPC/exit loss, with new behavioral RED/freeze/GREEN and fresh independent review; S1 stays deferred. The ledger is five prior passes plus A for fixtures plus B for these two CLI defects, seven maximum and no reset. The owner also explicitly grants **one D13 documentation-only RED exception**, substituting fresh full-CI evidence review and Main's complete gate for its new research-disposition predicates only. This supersedes the pending/no-waiver/unused-B entries above; they remain historical. CORE_RISK, actual D12 behavioral RED/freeze/GREEN, future S1 RED, original validation targets, monetary/resource caps, verified-primary/F1/F3/production barriers and global workflow/configuration are unchanged. No task is checked, gate claimed complete, provider launch performed or archive authorized by this reconciliation.

**Reviewed prelaunch assembly (2026-09-29 UTC):** fresh Reviewer returned APPROVE with all applicable CI-01..CI-15 passing (CI-06/14 N/A), independently reproducing both CLI defects as fixed and accepting only the scoped owner D13 exception; `red_suspect=false`. Fixture RED/GREEN remains 98/85/13 then 98/98. Exception B's `TEMP/provider-s3-exception-b-verified-red.log` records 104 executed/98 pass/six expected failures; `provider-s3-exception-b-green.log` records 104/104 and `provider-s3-exception-b-d11.log` 80/80, all zero skips/cancellations. Log summaries were inspected; no independent test rerun is attributed beyond the Reviewer's reported work. Frozen assertions remain intact; feature additions are 262/300 nonblank lines, total 1,570/1,800 across 14 files. DOCS_CLOSE records readiness only: Main's complete prelaunch gate, exclusive accounting preflight and single D12 launch remain pending, measured result UNEXECUTED, tasks 4.1-4.3 unchecked, no archive or additional repair allowance.

**Actual D12 local-launch receipt — 2026-09-29 03:26 UTC:** Main's complete launch gate passed (`TEMP/provider-contract-s3-launch-gate-*`: integrity zero, Docker 29.8 available, Maven 96 unit/48 integration tests with zero failures/errors/skips, strict OpenSpec 12/12, doctor/diff/cached checks zero). Launch `03:26:51.2268099Z` fixed deadline `03:56:51.2268099Z` and shorter native cutoff `03:30:41.2268099Z`; owned PID 23520 exited normally with code 1 in 0.5605746 seconds. `TEMP/provider-s3-archive-1-stdout.log` is empty; its `stderr.log` is 20 bytes, STATE_DEBIT_INVALID. The intended S3 root is absent, with no provider call/experiment process/new evidence file. Main's read-only Builder diagnosis identifies an unresolved legacy-summary compatibility defect: `cli.cjs:154` requires `s.attempt?.[field] === budget[field] - prior[field]` for both predecessors, but the first smoke summary/checkpoint has no `attempt` property, so `undefined !== 2 - 0` rejects; the retry does record `attempt.rpc=12`. Debit validation at line 189 occurs before directory creation at 193 and fork at 198, proving zero new RPC/gRPC. First/retry journals remain valid (5/8,145 frames, 12,001/77,522,145 framed bytes; source gRPC 10,881/47,628,056 and RPC 86/28,222,207). This is neither corrupted evidence, quota/provider FAIL nor real D12 compatibility PASS. The fail-closed nonproduction defect stays explicitly unresolved and D12 runtime acceptance unmet; the latest D13 research-only replan names its deferral for fresh review without changing code, repair allowance or retry permission.

Main postflight verified all 444 predecessor files/hashes unchanged, parent usage 109,094,800 bytes and owner configuration unchanged. Preflight inventory `TEMP/provider-s3-archive-1-preflight-inventory.log` SHA-256 `A688AD4DBE7A5B510AADA27E8D4F20950198D6EEAF1AD78DB68E3F2103E66877` recorded 1,908,510 external-log bytes, 5 MB+65,536-byte reservation and 125,712,109,568 free bytes. Source hashes independently re-read after the refusal are CLI `70AAB5AA80C030D5E41CF630E299A609FA80022CADB7A44A3975F2F27714B21C`, collector `7B438FB55EFC3CF49BD186D58A6855B0F7A1E199335681F03495924C6907AAB2`, tests `00320E091501EB7EEA5A073D00C6EAA846E1D148D7245AFCB3A3EBF72D0C385E`. Alchemy debits remain 14 RPC/75,861,230 received bytes and D11 remains 31/29,138,859; no new usage is inferred from a rejected CLI launch. Seven repair passes remain used; no repair/retry is authorized. D13 permits research closure with this unexecuted status only after fresh actual-evidence review and Main's final gate; no research task is checked here.

## Later measured evidence

Both S1 smoke measurements, the stopped SQD metadata attempt, the manual continuation, renewed A/B/C partial S2/S4 measurements and stopped fixed full-JSON RPC profile are recorded above; no complete S1-S5 capability verdict is established. Offline test evidence is not provider evidence. Any later addition should identify its actual execution date, provider/access tier, exact bounded command, UTC sample range, raw-evidence location and digest, observed results and unresolved checks. Documentation assertions must remain distinguishable from those measurements. Applicable execution limits and approval gates are already defined in D10/D11 and are not changed by this note.

## Bounded Alchemy A/B receipts — 30 September 2026, factual analysis after tool review

The later `run-bounded-solana-provider-spikes` change produced one `a-live-replay` receipt from `2026-09-30T02:49:58.106Z` to `02:56:22.075Z` and one `b-history` receipt from `02:56:57.059Z` to `02:57:04.450Z` under `C:/crypto-research-evidence/provider-ab-1`. The manifests identify the configured Alchemy PAYG modes, Node `v20.18.0`, a dirty source revision `9abd90d6bed02a46ff9e6c69fcda2d99f4074763`, the bounded filters and the revised `525,000` pico-USD/CU local-estimate rate. The receipts do not retain the exact operator command line; it is not reconstructed here. This dated update supersedes earlier statements that the new S3 matrix was unexecuted, while leaving the archived original F1 targets and their dated outcomes intact. Subsequent independent review approved the bounded CLI TDZ correction, and Main's final code gate passed; neither changes the terminal A receipt or establishes A success. A pre-paid combined review and Maven success are evidenced, but the other complete prelaunch-gate exits were not retained; the later gate cannot certify their pre-paid timing.

| Receipt | Recorded result | What it supports and limits |
|---|---|---|
| A LIVE | `INCONCLUSIVE/A_INCOMPLETE`; stream stop `INTERNAL_ERROR`; zero clean LIVE milliseconds, zero LIVE messages and one recorded gap. The aggregate catch-up/non-LIVE counters include 5,090 unique transactions, of which 2,528 have failure status; these are not LIVE rate or failure-share measurements. | No 60-minute sample, LIVE lag percentiles, bytes/hour, decimal GB/day, TB/30-day month, traffic tier or Alchemy-versus-Chainstack economic decision is supported. The later CLI correction was not exercised in a new paid A run. Database growth is N/A because the experiment wrote no database rows. The offline framed-byte compression sample below is not a LIVE traffic measurement. |
| A slot-only replay | First probe requested `from_slot=451827113` after tip 451836113 and returned first slot 451827082 in 1,488 ms/41 bytes. Second requested `from_slot=451776117` after tip 451836117 and returned first slot 451776085 in 1,050 ms/41 bytes. Each retained one returned slot message. | Both returned slots precede their requested start by 31 and 32 slots respectively. They are raw first-slot samples only; whether the requested lower bound was honored is unresolved. Neither proves transaction replay, the intervening range, a recovered gap or interval completeness. |
| B historical account matrix | `INCONCLUSIVE/MATRIX_COMPLETE`; 12 of 12 fixed finalized `getAccountInfo` requests returned `COLLECTED` with matching context-slot echoes; all six same-account/same-slot repeat comparisons are `MATCH`. Each vault's parent and child data differ. The 301-byte PumpSwap pool data hashes match across slots, but its layout remains `UNDECODABLE_OR_INCOMPLETE`. | The fixed paid read was accessible on this configured account for this matrix, with no observed entitlement denial. Collection and repeat equality do not establish general archive entitlement, a decoded pool reserve layout, complete historical correctness or pool liquidity reconstruction. |

The read-only A journal walk verified 5,128 complete hash-matched frames (5,125 gRPC and three RPC), 29,789,690 response-body bytes and 30,779,771 framed bytes. B's journal verified 12 complete hash-matched RPC frames, 6,438 response-body bytes and 10,752 framed bytes; its checkpoint says `incomplete=false`. The shared ledger records 15 new RPC attempts, 29,789,561 gRPC application bytes, 6,567 RPC response bytes, 38,184,736 reserved/recorded received bytes and 2,957,862,675 pico-USD of local estimated debit. Its recorded received count exceeds delivered gRPC plus RPC bytes by exactly 8,388,608 bytes (8 MiB), with a corresponding 629,145,600 pico-USD reservation difference at the local gRPC rate; independent tool review is complete, but the reservation's cause and billable status remain unresolved by the retained evidence. Reserved bytes must not be relabeled billed transfer. The $3 and 15 GB local guards were not reported as crossed. This ledger estimate is **not an invoice**. A dated post-run Alchemy dashboard or invoice was unavailable for this analysis, so actual billed cost and byte basis remain pending. The earlier stopped S1 smoke and retry raw SHA-256 values still match their dated entries above; this read-only check did not re-inventory every predecessor file.

| Retained file | SHA-256 |
|---|---|
| `provider-ab-1/a-live-replay/raw.bin` | `8d76f0e845fe85aa949bf699d0dc350bc8ff9bfe5c15cdd233dafc1798b188b3` |
| `provider-ab-1/a-live-replay/summary.json` | `f0bfb101af9128960eae24eeaade5e9c41787cc55d6fefe497bb9be448ac406d` |
| `provider-ab-1/b-history/raw.bin` | `4c22a24aab2178eff415710226197159160011fe88d0394ac735f38da5e37a87` |
| `provider-ab-1/b-history/summary.json` | `49281f6865852feb7c33d75157c1c2db1a4e7caafdd9c7e1b42644ec8e35c4ef` |
| `provider-ab-1/shared-budget.json` | `428b449ac2f65e51f9331fd462c73893a3acb9101c32f5da4efd0580a89d2184` |

### Offline local compression sample, 30 September 2026

No provider request was made for this sample. A synthetic-file preflight used Windows `bsdtar 3.7.6` with bundled `libzstd 1.5.4` to create and extract a `.tar.zst`; the archive began with zstd magic `28B52FFD`, and the synthetic original/extracted SHA-256 both equaled `df6465643846107a7bafc2d850d6ec0d584dcda4b88fc6ec7ad6473ef806630b`. A standalone `zstd` executable was unavailable on this host.

The deterministic sample is the longest prefix of complete frames from `a-live-replay/raw.bin` fitting within **10,000,000 bytes**, starting at byte offset 0 and ending before offset **9,998,137**. Each frame's recorded body length and SHA-256 was verified before inclusion. It contains 1,585 frames (1,584 gRPC, one RPC), 9,692,094 response-body bytes and 9,998,137 framed bytes. The source raw SHA-256 is `8d76f0e845fe85aa949bf699d0dc350bc8ff9bfe5c15cdd233dafc1798b188b3`; the selected prefix SHA-256 is `9f83e4db9b9d91e3f2d75c91437916b2641381f14363880e3fd95858c6677bba`. Using `tar -a -c -f <archive>.tar.zst -C <sample-directory> a-prefix-frames.bin` produced a **1,124,790-byte** zstd archive (SHA-256 `c7e0d0fbb4f8d75b8093e3c82314bb6d8c4b612999a15ac08b0a622eabe97373`), with zstd magic `28B52FFD`. Extraction reproduced the prefix SHA-256. The measured archive/framed-input ratio is **0.112499959** (11.25%; approximately 8.89:1 input/archive). This is a local tar+zstd ratio, including archive overhead, on one non-LIVE catch-up prefix; it is not an on-wire compression ratio, sustained disk-growth estimate, GB/day projection or billable-transfer figure.

### Offline child-block reserve-input comparison

The retained SQD PumpSwap sample `C:/crypto-research-evidence/provider-s2-sep28b/provider-feasibility/006.raw` (SHA-256 `0cabe2acea6178358ac7c18033fc12c6a4a8eda452479ee984bcd9d4a7869f14`) and independent complete finalized RPC block `C:/crypto-research-evidence/provider-s2-rpc-cm-2/provider-feasibility/002.raw` (SHA-256 `7f028de71b26e8f8fa9ca05ee5cef8647863226bf4cef74301deb0a7ff4a6cae`) both represent child slot 429644639. A read-only join used the full transaction signature `Mk21NZ4UpvJpvwCoofWTRzLXuYFPJ9uspSFP4MEynpdL12APMENXFyKf2eLjwui4FAVnxiaoCg5w3VeTwwCYwp1`, then resolved RPC token-balance account indices through static keys followed by loaded writable and readonly keys. SQD's direct vault addresses, mint, owner and exact raw integer pre/post amounts matched the independent RPC rows, at account indices 16 and 17. Each vault has exactly one pre/post token-balance row in that complete child block. A separate read of all eight B vault response bodies decoded their 165-byte SPL token-account raw amounts as little-endian unsigned integers at byte offset 64; both repeats match the independent pre/post amounts:

| Vault / mint | Child-block account index | B parent = SQD/RPC pre | B child = SQD/RPC post | Owner/authority |
|---|---:|---:|---:|---|
| Base `BWquordxHk39m9d7LRyQeg5tGmu1z19ismnJTTiWHJ7F` / `73jj6SFe9FKn6qqpaGwbSd7TL1sHheh3HqDo9RwA7BHB` | 16 | 87951829991918 | 87765605657728 | SPL Token program / `9rPogiERgqQPCYJ5hbXu7LsUjXA5G9DcA3d9pX1bvUox` |
| Quote `CUhM4HepHThb6zcTj4BSiA7RovTokQwgQCvQLWQpqz7e` / `So11111111111111111111111111111111111111112` | 17 | 360709739798 | 361476637907 | SPL Token program / same pool authority |

This corroborates the two vault reserve **inputs for this one child-block transaction** at the sampled parent and child cutoffs. It does not decode the PumpSwap pool's deployed layout, establish a general historical-state service guarantee or derive price/liquidity from unknown pool fields. Independent A/B implementation review is complete; the B outcome remains `INCONCLUSIVE` because those historical-state and pool-layout claims are still unproven.

### Recovery design boundary and next evidence

A future provider-independent F3 design would durably record each finalized source position and explicit missing slot interval, attempt provider replay for the declared interval, then use SQD/backfill only for remaining ranges after independent signature, instruction and produced-slot coverage checks. Each raw input would retain provider identity and observation/admission times. A gap would close only with demonstrated range completeness; an overlapping or conflicting source would remain visible rather than becoming a silent replacement. This is a design sketch, not implemented recovery. A's two slot-only first responses do not exercise it.

The owner's traffic decision thresholds remain `<30`, `30–55` and `>55` projected decimal GB/day, with a new authorized 6–24-hour LIVE measurement required in the middle tier. Because A has zero clean LIVE time, none of the three tiers is assigned. The A/B runs are terminal single-use receipts; any new paid A attempt requires a separate owner decision. Alchemy remains a provisional research candidate; Chainstack remains untested and unpurchased. The bounded CLI TDZ correction has independent review and Main's final code gate, but a clean LIVE measurement, supported replay-bound interpretation, pool-layout validation, actual dashboard billing and full recovery proof remain open. No original F1/F3 target, verified production primary, mass recording or archive is claimed by this note.

### Offline A catch-up readout and authorized retry prelaunch stop — 30 September 2026

Main decoded and SHA-verified the existing `C:/crypto-research-evidence/provider-ab-1/a-live-replay/raw.bin` (SHA-256 `8d76f0e845fe85aa949bf699d0dc350bc8ff9bfe5c15cdd233dafc1798b188b3`) without a provider call. Its **non-LIVE CATCHUP** phase contains 5,123 gRPC frames and 29,789,479 gRPC application bytes across observed slots 451834650–451834666 (17 distinct observed slots), or 1,752,322.294 bytes per distinct observed slot in this sample. It contains 5,090 unique transactions, 2,528 failed (49.6660%). The two separate 41-byte slot-only replay frames are excluded. These are catch-up observations, **not** a representative LIVE rate, GB/day projection, billable-byte measurement or basis for provider selection.

The owner then authorized exactly one fresh-root, A-only paid attempt, with no B/replay repetition and no implementation change. Read-only prelaunch inspection found that the reviewed CLI fixes the root to existing `provider-ab-1` (`tools/spikes/alchemy-s1/cli.cjs:141-145`) and rejects its existing A child as `PATH_REUSE` (`cli.cjs:173-174`); the collector also requires that root basename (`collector.cjs:228-229`). Its LIVE stream always sets `from_slot` to the tip anchor (`collector.cjs:328-333`), and after LIVE it automatically starts both replay offsets unless a budget has stopped (`collector.cjs:345-350`). There is no reviewed option for the authorized fresh-root, no-`from_slot`, no-replay execution. Per the owner's explicit STOP rule, this attempt **stopped before prelaunch gate, dashboard baseline or paid call**; it consumed no new experiment budget and created no replacement A receipt. The earlier A/B receipts and their task statuses remain unchanged. No new feature, repair, retry, Chainstack connection, provider decision or archive is implied.

The catch-up bytes per observed slot are only a **prior**: extrapolation under an assumed slot rate might exceed 55 GB/day, but no clean LIVE rate or provider decision follows from that assumption. Only 60 continuous clean LIVE minutes would activate the declared traffic tiers; 20–59 minutes would be partial/inconclusive and less than 20 minutes or a pre-LIVE stop inconclusive. If a future separately planned compatible attempt repeated `INTERNAL_ERROR` before clean LIVE, the factual report would retain the observed phase, elapsed time, last slot, counters and available client/provider error evidence, compare them with the first A receipt, and label the result `INCONCLUSIVE / REPEATED_INTERNAL_ERROR`. The decision note would then say Alchemy did not pass bounded operational validation **on the tested implementation**. Neither client nor provider fault would be assigned without explicit evidence; an unknown cause stays unresolved. This is an interpretation rule, **not** a second observed failure, paid-call authorization, repair or retry.

### Final A-only terminal receipt — 30 September 2026, supersedes the prelaunch-stop plan

The owner subsequently authorized **one exceptional root-handling-only repair and one final A launch**, allowing the existing `from_slot` catch-up and automatic bounded slot-only replay. This superseded only the preceding no-code/no-replay attempt, not its historical facts or the first A/B receipts. Reviewer-authorized `TEST_SPEC_ERROR` correction preserved the synthetic CU assertions: A three 20-CU calls / 31,500,000 pico-USD, B twelve 10-CU calls / 63,000,000 pico-USD, both at 525,000 pico-USD/CU, 94,500,000 pico-USD combined across separate ledgers; B behavior was unchanged. New behavioral RED was 95 executed / 91 pass / four expected assertion failures before source edits (`root-repair-red-rerun.log`); targeted GREEN was 121/121 A/B and 80/80 feasibility, zero failures, with `tests_changed_after_red=true`. Fresh independent Reviewer returned APPROVE for the root-only diff. The three earlier ordinary repair rounds remain consumed; this one exceptional repair is also consumed. No additional repair or paid retry is authorized in this change.

The complete prelaunch record is under `C:/crypto-research-evidence/a-final-prelaunch-20260930/`: independent review, test-integrity preflight, Docker, `mvnw.cmd clean verify`, strict OpenSpec validation (13/13), doctor, working-tree/cached diff checks, targeted tests, each with retained exit/log evidence **before** the final paid call. The full Maven retry passed 96 Surefire + 48 Failsafe tests, zero failures/errors/skips. The first Maven invocation failed solely because the convention scan encountered broken README links in an ignored local `tools/spikes/alchemy-s1/node_modules` dependency tree; Main recoverably moved that exact tree to `C:/crypto-research-tools/a-final-local-deps-20260930`, retained the first failure log and reran the same full Maven command. External `NODE_PATH` dependencies remained available. This infrastructure retry was not a product-code repair. The earlier A/B run's missing pre-paid gate exits remain an **unrepaired historical evidence gap**; the final A prelaunch logs do not retroactively prove them. The owner-provided `docs/notes/img_2.png` and `img_3.png` baseline copies show 730 current-month UTC CUs, PAYG and no invoice visible, but no separate gRPC charge/price; source file timestamps are not an attested screenshot capture time. No post-run Alchemy dashboard image or invoice was available for comparison. Absence of a visible charge is **not** zero gRPC usage or a cost-control guarantee.

Main invoked the reviewed A CLI **once**, with explicit fresh `C:/crypto-research-evidence/provider-a-final-1` root and the existing `alchemy-s1` debit reference; B was not rerun. CLI exit was zero, but its own receipt is **`INCONCLUSIVE/A_INCOMPLETE`**, from `2026-09-30T16:00:22.149Z` to `16:02:19.782Z` (117.633 seconds). It recorded **zero clean LIVE milliseconds**. The initial anchor was finalized slot 452012059; highest received finalized slot was 452012035, so catch-up never reached the anchor. The stream stopped with `PROCESSING_LIMIT`. In `transport.cjs`, this is a local synchronous `onRaw` processing-time guard (>250 ms), not the prior A `INTERNAL_ERROR` and not evidence of a provider-side error. The exact disk, CPU or handler subcause remains unresolved. Thus the conditional `REPEATED_INTERNAL_ERROR` classification above was **not** exercised. Alchemy did not pass bounded operational validation **on this implementation**; client-versus-provider causation beyond this observed local guard is not established, and neither vendor rejection nor recovery completeness is inferred.

| Final A observation | Measured fact and boundary |
|---|---|
| Non-LIVE catch-up | SHA-verified offline raw analysis (`a-final-prelaunch-20260930/final-raw-analysis.json`) found 1,546 catch-up frames, 11,769,881 gRPC bytes, observed slots 452012028–452012035 (eight distinct), 1,530 unique transactions: 1,033 successful and 497 failed (32.48366% failed); zero duplicates and 78 multi-program overlaps. Program-attributed counts were Pump.fun 412, PumpSwap 1,144, Raydium AMM v4 52; these overlap and **must not be summed** as unique volume. Successful unique transaction payloads used 8,893,224 bytes, failed ones 2,874,809 bytes, and non-transaction gRPC 1,848 bytes. All are catch-up only, not successful-only LIVE traffic or a GB/day basis. |
| Aggregate local receipt | 11,769,963 gRPC application bytes including two 41-byte replay samples; 129 RPC response-body bytes from three `getSlot` calls (60 CU); 11,770,092 received bytes total; 12,069,719 raw journal bytes. Summary disk budget records 12,696,699 bytes, checkpoint budget 12,698,970 bytes: different recorded snapshots, not interchangeable with physical DB growth. DB growth is N/A; no production database rows were written. Local estimated spend was 914,247,225 pico-USD = **US$0.000914247225**, including conservative raw-byte `$75/TB` gRPC estimate plus local RPC-CU estimate; this is **not an invoice**. Hard 15 GB/$3 limits were not reached. |
| Final slot-only replay | The -9,000 probe requested 452003487 after tip 452012487 and first returned 452003456 (delta -31); the -60,000 probe requested 451952494 after tip 452012494 and first returned 451952463 (delta -31). Both returned one 41-byte slot sample. Server response establishes a returned sample only: `from_slot` lower-bound semantics, transaction replay, interval coverage and recovery remain unresolved. These bytes are excluded from catch-up and any hypothetical LIVE rate. |
| LIVE and decision | Zero LIVE messages/transactions/bytes and no LIVE lag samples. LIVE bytes/hour, decimal GB/day, TB/30-day month, events/hour, unique-transactions/hour, p50/p95 lag, failed-inclusive versus successful-only LIVE traffic, dashboard reconciliation and provider tier are **unavailable**. Recorded LIVE reconnects 0, stalls 0, gaps 1; no gap was shown recovered. The `<30`, `30–55`, `>55` GB/day rules cannot be applied. No verified production primary, Chainstack comparison result or F3 admission follows. |

Final receipt SHA-256: `provider-a-final-1/a-live-replay/raw.bin` `c24db33fab43213e285aa558a87d7102fecb34d926d5978c6b613f4445837fe1`; `summary.json` `0bdcac738af534822c707b67d6f4c4ea05c1fb4a31fee4a5773fc78b2679c494`; `checkpoint.json` `464d4116f97b090ed703ab8c1466370ecb2a61d3e15183176194739242b3f0d6`; final-root `shared-budget.json` `fa5b5a7e50543a04595e4dd3c79cb358816431c788edcdffb9bc68716aa8cdda`. The raw walker verified all 1,550 frames. Main's before/after inventory reports all **608** predecessor files unchanged (`predecessors-after.json`, zero mismatches), and the post-run owned-process check reports zero spike processes. These facts preserve earlier A/B, the B 12/12 `INCONCLUSIVE/MATRIX_COMPLETE` result, the SQD/vault reserve comparison, local zstd sample and provider-independent recovery sketch above. They do not make historical account reads generally complete or render the old replay evidence verified.

**Terminal research disposition: INCONCLUSIVE.** The one final A attempt is spent; the change may be archived as a truthful terminated research record after its closure gate, even though the original F1 S1–S5/3.6 remain deferred/unverified. No further paid attempt or code repair is authorized here. A future provider decision needs separately authorized, measured clean LIVE evidence and recovery validation; neither Alchemy nor Chainstack is selected as verified primary today.
