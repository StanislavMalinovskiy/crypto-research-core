# Solana provider spike research — 27 September 2026

Research cutoff: 2026-09-27 17:42 UTC; later updates carry their own times below. This is a non-normative research note for the active [provider-contract change](../../openspec/changes/define-solana-data-provider-contract/proposal.md), specifically [design D11](../../openspec/changes/define-solana-data-provider-contract/design.md#d11-additional-owner-authorized-investigation-of-s2-s5-2026-09-27). It records public documentation research, local access metadata and offline preparation evidence, not successful provider probes. D10 and D11 remain the controlling execution contracts.

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

The active delta's [Evidence-based provider selection requirement](../../openspec/changes/define-solana-data-provider-contract/specs/solana-data-contract/spec.md#requirement-evidence-based-provider-selection) requires real-sample capability evidence. The current material supports an investigation shortlist, not a final primary transport/history/specialized-source selection. Task 3.6 remains incomplete; no purchase, production integration, completion checkbox or archive follows from this note.

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

## Later measured evidence

Both S1 smoke measurements, the stopped SQD metadata attempt, the manual continuation, renewed A/B/C partial S2/S4 measurements and stopped fixed full-JSON RPC profile are recorded above; no complete S1-S5 capability verdict is established. Offline test evidence is not provider evidence. Any later addition should identify its actual execution date, provider/access tier, exact bounded command, UTC sample range, raw-evidence location and digest, observed results and unresolved checks. Documentation assertions must remain distinguishable from those measurements. Applicable execution limits and approval gates are already defined in D10/D11 and are not changed by this note.
