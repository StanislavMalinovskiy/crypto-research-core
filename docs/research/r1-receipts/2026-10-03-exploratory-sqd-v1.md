# Exploratory SQD availability receipt — 2026-10-03

`EXPLORATORY / INCOMPLETE`; `d1Evidence = false`; `d1Passed = false`. Outcome-blind availability/field-presence/volume sample under [procedure section 11](../R1_OFFLINE_RESEARCH_PROCEDURE.md#11-exploratory-probe--owner-authorized-2026-10-03) and [active design 4f](../../../openspec/changes/establish-r1-d1-data-gate/design.md). Not confirmatory R1 evidence, inventory promotion, a D1 pass or archive authorization. Frozen protocol, freeze entry, thresholds and calibration are unchanged.

## Run and provenance

Main executed the public command **once**, after targeted gates and ONE fresh independent Reviewer APPROVE; no provider rerun followed. The second command was offline replay only:

```powershell
node tools/research/r1/exploratory-probe-cli.cjs --enable-public --output C:\crypto-research-evidence\r1-d1\exploratory-sqd-v1
node tools/research/r1/exploratory-probe-cli.cjs --replay C:\crypto-research-evidence\r1-d1\exploratory-sqd-v1\manifest.json
```

Both commands exited 2 because the sample remained incomplete. Replay returned `code = null` and identical summary/manifest hashes: integrity succeeded, completeness did not.

| Identity/accounting | Observed value |
|---|---|
| UTC start / end | `2026-10-03T06:53:41.934Z` / `2026-10-03T06:54:39.309Z` |
| Budget elapsed | 57,272 ms; UTC endpoints span 57,375 ms including finalization |
| Runtime / tree | Node `v24.19.0`; commit `ae7b3a3e9cc5dfb8eef88d5b50f32820be1f17c8`; dirty tree recorded |
| Source / query / summary | `sqd-public-solana-mainnet` / `exploratory-sqd-v1` / `exploratory-counts-v1` |
| Requests / retries / concurrency | 29 attempted of 30 planned; retries 0; concurrency 1 |
| Received / disk | 7,962,660 bytes / 6,025,734 bytes; rejected bytes charged; exact disk sum checked |
| Cash | USD 0; `cashMicrousd = "0"`; unauthenticated; no paid fallback |
| Ceilings | 30 minutes; 80 attempts; 25,000,000 received bytes; 50,000,000 disk bytes; 2,000,000 bytes/response; 30 seconds/request; two-second start spacing; 30 GB free-space floor |
| Stop | Local response cap; no cumulative 80-percent checkpoint reached |

Endpoint: `https://portal.sqd.dev/datasets/solana-mainnet/`. Fixed anchors used `GET timestamps/{unix-seconds}/block`, then header-only and program-filtered `POST finalized-stream` with `fromBlock = toBlock`, `includeAllBlocks = true`. Every admitted payload matched its validated slot/hash/timestamp; all six headers equal the UTC anchor exactly. Entire `[2026-08-31T00:00:00Z, 2026-09-29T00:00:00Z)` excluded from requests, raw retention and counts. No previous raw sample reused.

[Official API](https://docs.sqd.dev/en/portal/solana/api) and [public development/evaluation access](https://sqd.dev/developers/) checked 2026-10-03. [OpenAPI document](https://docs.sqd.dev/openapi.json): `SQD Portal API` version `1.0.0`, 77,881 bytes, SHA-256 `0af342338e732a39e4251c31d45a82dfb264ebe80d1316bec6d70852f461129a`. Dataset revision unknown; formal full-D1 retention/tariffs/capacity unverified.

## Observed strata

Cells: transaction / instruction / balance / token-balance **row occurrences**, not unique transactions/trades. Related instructions include other programs in selected transactions; query rows may overlap. No amounts, prices, returns, PnL or rankings calculated.

| Fixed UTC anchor | Validated slot | Pump.fun | PumpSwap | Raydium AMM v4 |
|---|---:|---|---|---|
| 2026-04-01 00:00:00 | 410195947 | 7 / 54 / 27 / 11 | 45 / 783 / 118 / 300 | 2 / 21 / 4 / 8 |
| 2026-05-01 00:00:00 | 416762082 | 5 / 66 / 32 / 8 | 45 / 953 / 179 / 305 | 4 / 52 / 9 / 36 |
| 2026-06-01 00:00:00 | 423478907 | 12 / 114 / 60 / 31 | 85 / 1594 / 201 / 550 | EMPTY: header only |
| 2026-07-01 00:00:00 | 429980965 | 41 / 405 / 105 / 86 | 71 / 1384 / 210 / 462 | 1 / 12 / 3 / 4 |
| 2026-08-01 00:00:00 | 436454957 | 56 / 529 / 166 / 143 | 131 / 1988 / 308 / 903 | 1 / 13 / 3 / 9 |
| 2026-08-28 00:00:00 | 442216324 | 60 / 497 / 187 / 147 | INCOMPLETE: local cap | UNEXECUTED after stop |

Program IDs, in fixed order: Pump.fun `6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P`; PumpSwap `pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA`; Raydium AMM v4 `675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8`.

Sample: six distinct slots, 15 populated strata, one valid empty, one rejected/incomplete and one unexecuted, of 18 planned. Accepted totals: 566 transaction, 8,465 instruction, 1,612 balance and 3,003 token-balance row occurrences. June 1 EMPTY means only an empty selection at that slot, not missing Raydium history. No continuous time coverage or full-population denominator measured.

August 28 PumpSwap returned HTTP 200, then exceeded **our** 2,000,000-byte response ceiling. Guard consumed/charged 2,000,001 bytes to detect the breach, discarded the partial body and admitted no raw file or rows. Next Raydium query was not attempted. This is not provider refusal or evidence of missing history. All retained responses returned HTTP 200.

## Available fields and unresolved inputs

All requested fields present on every corresponding populated row (zero missing fields); raw numeric lexemes preserved, no quantity arithmetic/reserve reconstruction:

| Collection | Requested and observed fields |
|---|---|
| Block | `number hash parentNumber parentHash timestamp` |
| Transaction | `transactionIndex signatures accountKeys loadedAddresses version err` |
| Instruction | `transactionIndex instructionAddress programId accounts data isCommitted error` |
| Balance | `account pre post` |
| Token balance | `transactionIndex account preMint postMint preOwner postOwner preAmount postAmount preDecimals postDecimals` |

`err`/`error` nullable; token-balance pre-state fields null on 51 rows, post-state fields on 9. Presence is not non-null availability or semantic validation. Empty Raydium response omitted all four selected collections, supplying no row-level field evidence.

Account keys/balance records are possible decoder inputs, **not** verified pool/vault mapping, reserves or executable depth. Query did not select fee fields, account-state bytes, SOL/USD observations or observed-arrival timestamps. No decoding of tips, pool identities, venue actions, historical mint/freeze state, curve state, CLMM ticks or DLMM bins. These omissions do not establish that SQD cannot supply them. Fee separation, tip attribution, exact SOL/USD, visibility/latency, liquidity-event/swap decoding, independent 200-trade checks, lookup coverage, depth denominators and full-envelope gaps remain unverified.

## Cost and integrity disposition

`fullD1UpperBound = null`. USD 0 describes this bounded sample only. Six fixed slots and a censored large response cannot bound candidate-wallet/token/pool populations, discovery, related histories, ancillary state or tail costs. No representative rate, six-month storage/request/cash forecast or unsupported upper bound inferred. Full-D1 estimates require separately authorized population/query definitions and defensible retention/capacity/tariff evidence. Alchemy PAYG remains prohibited.

External root: `C:\crypto-research-evidence\r1-d1\exploratory-sqd-v1`; manifest contains exact queries, admitted raw filenames/per-file hashes, accounting, script identities and provenance. No raw chain payload copied into the repository.

| Integrity identity | SHA-256 |
|---|---|
| Manifest file | `fc1fe02982ec49885e7553b2e35331c9f5444e2fca5924327bd0e4d4eed74a4a` |
| Deterministic summary fingerprint | `7778f3aafb6baffd36eda5ee2a8d6fd6236558db220d2b65eb8b8065443e50a8` |
| Summary file | `e0bc2cb581e0ac055805fc2be1a322d34a5c26b531bf9418df915fa454ededac` |
| Configuration fingerprint | `0b0e30c38d67141ebbf3554ef6f284c2e0db1682060a035cd3d99c6108f78cfd` |
| Probe script | `1c5a1695fcce9f06592288621513479fef6d1c5fd076b61da6a897f71eec8318` |
| CLI script | `644637915b5598113b20f2f1eb36aa19c684222d592527833499e70f40a155b3` |

Offline replay verified hashes and reconstructed identical counts. Architect independently checked all 28 admitted raw SHA-256 values, script hashes and exact directory byte total: no discrepancy. Credential-marker scan of 28 raw files plus three metadata files: zero matches, no matched content/credentials logged; not a universal guarantee against every possible secret format.

Main logs: `C:\crypto-research-evidence\r1-d1-exploratory-preflight-20261003\repair1-*`. All exited 0: probe 19/19, inventory 41/41, candidate 8/8 (zero failures/skips), integrity preflight, strict all-item OpenSpec 16/16, doctor and diff. Original valid behavioral RED: 17 tests, 17 assertion failures. Repair-round-1 RED: 19 tests, original 17 passing, two new assertion failures for duplicate-key holdout bypass/partial-write accounting. ONE fresh Reviewer approved the repair in the same review thread with full CI-01..CI-15 matrix; original assertions unchanged, authorized regression additions made `tests_changed_after_red = true`, no changes after new RED. Final Builder footprint: 552 nonblank lines / 850 allowed across three files.

`docker version` and `mvnw.cmd clean verify` **NOT RUN** for this slice under exact owner-approved procedure-section-11 exception; no full-gate PASS claimed. Confirmatory D1 retains its complete gate. Only exploratory tasks 1.11–1.14 complete; section 2 unchecked, change active.
