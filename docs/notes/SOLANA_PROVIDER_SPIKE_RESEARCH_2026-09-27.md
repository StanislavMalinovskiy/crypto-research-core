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

## Later measured evidence

Both S1 smoke measurements, the stopped SQD metadata attempt and the approved manual continuation's partial S2/S4 measurements are recorded above; no complete S1-S5 capability verdict is established. Offline test evidence is not provider evidence. Any later addition should identify its actual execution date, provider/access tier, exact bounded command, UTC sample range, raw-evidence location and digest, observed results and unresolved checks. Documentation assertions must remain distinguishable from those measurements. Applicable execution limits and approval gates are already defined in D10/D11 and are not changed by this note.
