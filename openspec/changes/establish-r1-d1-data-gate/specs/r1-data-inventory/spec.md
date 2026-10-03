## Purpose

Defines outcome-blind, reproducible validation of the R1 D1 field inventory and the mandatory boundary between an inventory and authorization to extract historical evidence.

## ADDED Requirements

### Requirement: Hash-derived offline provisional discovery
Design4l SHALL implement only the three new bounded offline discovery paths, reusing unchanged mapper/helpers over the fixed conditional4j hashset. It SHALL read SQD v1/v2's94 raw files only after exact manifest/raw proof, and SHALL NOT read/project Helius raw, infer candidates from fee payers, repair the old mapper guard, add decoders or call providers. Provisional observations SHALL remain distinct from R1§5.1 RECONSTRUCTED candidates and all D1 authorization/evidence/pass flags SHALL remain false.

#### Scenario: Parent bytes become an eligible child input
- **WHEN** a pinned successful SQD payload contains a complete JSONL block within design4l limits
- **THEN** its child SHALL be an unchanged contiguous original-byte slice with parent/hash/offset/length/child-hash lineage and independent lossless error-lexeme audit before unchanged mapBlock
- **AND** missing proof, unsafe filesystem path, numeric-lexeme violation or parse failure SHALL reject with DISCOVERY_INVALID/no provisional rows, never round, rewrite, filter fields or automatically admit a new raw.

#### Scenario: Payload format or mapper state cannot support discovery
- **WHEN** source metadata is resolver/header/census, a retained request lacks successful payload, or mapper rejects an audited child
- **THEN** the report SHALL explicitly retain RESOLVER_ONLY/HEADER_ONLY_NO_PAYLOAD/request failure evidence or DISCOVERY_INCOMPLETE with the mapper code and no provisional rows as applicable
- **AND** it SHALL NOT map oversized/RPC-shaped inputs, invent missing status/balances, claim zero field availability or silently allocate another adapter/repair.

### Requirement: Provisional owned-address evidence and retained gaps
Discovery SHALL retain all mapper invocation/ownedDelta/token-state/transfer/diagnostic observations with exact financial strings, source-child lineage, unknowns and source-scoped coverage. Address evidence SHALL use only nonnull declaredTrader, never payer/index proxies. DECLARED_OWNER_WITH_EXACT_DELTAS SHALL require no invocation error, both ownedDelta sides present and no vault-check reason; otherwise the declared address SHALL remain unresolved. Both SHALL remain unverified as actual traders, economic trades, eligible wallets or reconstructed universe; empty address output SHALL be valid. Missing transaction.err SHALL remain separately diagnosable without changing the mapper's UNKNOWN semantics.

#### Scenario: Non-DEX or unsupported transfer is present
- **WHEN** a mapped record contains an unassociated classic-SPL transfer or unsupported System/Token2022 instruction
- **THEN** discovery SHALL retain its facts or explicit reason/identity/raw reference, independent of candidate/trade membership
- **AND** unlinked native balances, missing complete funder history, quote eligibility, fees/tips, SOLUSD, layout applicability and visibility SHALL remain explicit gaps, never reconstructed or zero-filled by balance arithmetic.

#### Scenario: Duplicate evidence or conflicting observation arrives
- **WHEN** equal source-scoped observations recur or distinct child observations share an event identity
- **THEN** equal observations SHALL deduplicate with every lineage retained, while distinct observations SHALL remain separate and conflicting roles/delta content SHALL be a counted unresolved gap
- **AND** ordering/permutation SHALL preserve deterministic semantic identity without provider-filter ordinals, best-evidence guessing or complete-history claims.

### Requirement: Offline range, resource and publication boundaries
Discovery SHALL enforce design4l's fixed preholdout range/cutoff/default60s MODELED C-3 before mapping/counts, independent read/row/address/output/runtime/cumulative-disk caps, expiry and no secrets/network/writes. New behavioral RED/freeze/GREEN, fresh full-CI review and Main's complete gate SHALL precede one offline run plus one identical reproduction; old124 tests/mapper stops and repair counters SHALL remain untouched. Pyth/Jito alternatives SHALL remain documentary/unverified with no source implementation or price/tip/retention confirmation; fullD1UpperBound/fullD1Fits SHALL remain null.

#### Scenario: Future, forbidden or unbounded evidence is encountered
- **WHEN** query/header time is missing/out-of-range/holdout, modeled availability exceeds cutoff, or a fixed cap is exceeded
- **THEN** admission SHALL stop explicitly before child rows/counts or provisional publication, without truncation, fallback or guessed availability
- **AND** failed/partial diagnostic evidence SHALL be bounded and distinguish semantic data status from independently measured end-to-end budget; no general mapper/D1 PASS or new raw permission SHALL follow.

### Requirement: Separately bounded Helius Free exploratory history
Design4k SHALL allocate only future H1/H3 Free getTransactionsForAddress entitlement/volume steps, H2 terms disposition, offline H4 sensitivity and unselected H5 inventory candidate. It SHALL preserve owner provenance, fixed preholdout004-derived addresses, USD0/Free-only/cumulative finite credits/attempts/bytes/disk/time/retries, false D1 flags and unchanged frozen protocol. The requested light-process exception SHALL remain ineffective while conflicting with controlling CORE_RISK role instructions; baseline RED/freeze/fresh full-CI review/Main complete gate and the explicitly published Helius-access/H2 admission preconditions SHALL precede calls. Confirmatory200 receipts SHALL require a separate full CORE_RISK allocation. PLAN SHALL NOT read secrets, call providers, implement scripts/procedure or reopen SQD/mapper tasks.

#### Scenario: Free entitlement or retention is unresolved
- **WHEN** H1 is refused, actual Free debit is unknown/unexpected, or H2 lacks the delegated exploratory-retention decision
- **THEN** the affected source step SHALL stop explicitly without method/host/key/purchase/paid fallback
- **AND** published history/tariffs or a prior key-name/dashboard observation SHALL NOT establish current entitlement, retention permission or D1 source admission.

#### Scenario: H1-only attestation and bounded disk reconciliation
- **WHEN** H1 uses f038's explicitly accepted same-day OWNER_PASTED_TEXT and design4k's fixed metadata-only CLI preflight
- **THEN** its manifest SHALL distinguish that evidence from screenshot/autoscaling verification and record measured cumulative sizes before any key read/request, with absent directories explicitly zero and scan errors/cap overflow stopping calls
- **AND** Main SHALL independently verify the10GB cumulative ceiling; H3 SHALL still require fresh post-H1 dashboard/no-paid-tariff and actual debit reconciliation, without treating H1 attestation as confirmed entitlement or retention rights.

### Requirement: Helius holdout and secret admission boundary
Every H1/H3 request SHALL include design4k's fixed half-open blockTime filter before transport. The entire forbidden2026-08-31..2026-09-29 interval SHALL be excluded from fetches/counts; a page with missing/invalid/out-of-range time SHALL be rejected entirely before raw retention or row publication. Only transport-at-request-time SHALL load CRYPTO_HELIUS_API_KEY from its ignored properties file; authenticated URLs, keys and provider errors SHALL never enter output/identity/logs/receipts. Missing or invalid transaction-error state SHALL remain unknown, not success or FAILED_TRANSACTION. No outcome/price/ranking/selection/reconstruction or field promotion SHALL occur.

#### Scenario: Page contains forbidden time or leaked authentication
- **WHEN** a page contains a holdout/out-of-range/null-time row or the actual authentication value
- **THEN** the whole page SHALL be discarded before any raw write/count
- **AND** safe attempted/received-byte/error accounting SHALL remain without publishing provider content or silently continuing.

### Requirement: Finite Helius attempts and reproducible accounting
H1 SHALL make exactly one bounded attempt with zero retries; H3 SHALL enforce design4k's generous finite caps, credit reservation on every attempt, sequential round-robin cursor work and finite eligible backoff without Retry-After bypass. Failed/partial bytes, waits and uncertain charges SHALL remain accounted, while failed responses SHALL provide no cursor/coverage. Exclusive external files/manifest lineage SHALL preserve previous roots and cumulative history. Offline replay SHALL verify raw SHA-256, deterministic admission/counts/attempt ordering and semantic hashes without requests, secrets, writes or waits; elapsed/rate/dashboard presentation SHALL remain operational metadata outside deterministic summary identity.

For H3 only, delegated6a2882dc/eaf28793 SHALL replace the hard120-minute all-writes assertion with elapsed+full pending wait+60000<=6900000ms before source attempts, subject ONLY to the accepted capacity-check gap below, and a300000ms final-publication reserve within a nominal7200000ms Main-measured budget. Manifest/returned elapsed SHALL be explicitly labelled PRE_PUBLICATION/prePublicationElapsedMs with consistently scoped cumulative aliases. Main SHALL independently measure launch/all CLI writes/exit into the existing source log; replay COMPLETE SHALL mean semantic admission/reproduction only, not run-budget passage. No immutable file deletion/rewrite/marker or new state/publication protocol SHALL be introduced; H1 and every other cap/admission rule SHALL remain unchanged.

#### Scenario: Owner accepts the exact elapsed-before-capacity-check limitation
- **WHEN** helius-probe.cjs423's elapsed check precedes a store.free delay, under delegatedabc5bdb6 corrected68d30d32 KNOWN_LIMITATION_ACCEPTED
- **THEN** that gap alone MAY permit at most one late full request after an unknown delay crossing115 or120min, with100reserved credits/60s timeout; strict zero late-start/fail-closed time enforcement SHALL NOT be claimed
- **AND** synthetic6900000ms capacity delay/1attempt/TIME_LIMIT, unchanged independent resource/holdout/admission/order/replay checks, actual incomplete/replay dispositions and Main budget SHALL remain explicit; current Reviewer approval and Main full gate SHALL precede source release without a fourth repair/test edit/reset.

#### Scenario: Final immutable publication exceeds the nominal time budget
- **WHEN** admitted complete H3 data finish final publication after Main's independently measured7200000ms
- **THEN** Main SHALL retain immutable EXPLORATORY data COMPLETE and record budget OVERRUN/no overall PASS without another source attempt, rewriting or fabricated prepublication timing
- **AND** semantic replay SHALL reproduce the admitted data/hash, with budget unmeasured offline; H4 MAY use these data with the separate Main receipt's OVERRUN label, while interrupted/incomplete data SHALL remain incomplete/INCONCLUSIVE.

#### Scenario: A source attempt cannot fit the revised cutoff
- **WHEN** current elapsed plus the full required wait and60000ms request reservation exceed6900000ms, or a terminal disposition has occurred
- **THEN** outside only the accepted elapsed-before-store.free gap H3 SHALL start no new source attempt/retry, and SHALL publish its honest existing data disposition; no source start after terminal disposition SHALL be allowed even under that exception
- **AND** current Reviewer authorization/new behavioral RED SHALL precede any correction of the three superseded frozen timing tests; replanning SHALL NOT reset the consumed two repairs or authorize the final third pass.

#### Scenario: Retry or cursor cannot fit the remaining allowance
- **WHEN** the next reservation/full wait+request deadline cannot fit, a cursor repeats, or an immutable signature conflicts
- **THEN** requests SHALL stop with explicit incomplete/integrity disposition and no quota reset, duplicate admission or fallback
- **AND** unexecuted/censored wallet ranges SHALL remain unknown/lower-bound, not complete histories.

#### Scenario: Replay has identical evidence but different execution timing
- **WHEN** raw/config/code lineage and semantic counters match but live elapsed/rate differs from offline processing
- **THEN** replay SHALL preserve deterministic semantic summary hash without requiring byte-identical receipt packaging or reconstructed live timing
- **AND** changed/missing raw evidence SHALL fail integrity rather than receive fabricated counts.

### Requirement: Helius sensitivity and candidate are not D1 passage
H4 SHALL report exact rational/ceiling sample min/pooled/max wallet-history sensitivities for the fixed populations and1–3Free-month assumptions, retaining censored denominators and missing full-envelope discovery/pool/ancillary/holdout/tail/state costs. fullD1UpperBound SHALL remain null. H5 SHALL preserve the old inventory snapshot/frozen tests and add only a separate unselected candidate with explicit unknown coverage/cost/retention and vault-only depth limitations. H6 SHALL preserve historical SQD529 cause uncertainty, v3NOT_RUN and mapperUNAPPROVED status without universal source-failure inference; section2 SHALL remain unchecked and D1 unarchived.

#### Scenario: Wallet-history sample fits a Free-month scenario
- **WHEN** an assumed wallet-only forecast fits one or more monthly Free allowances
- **THEN** it SHALL be labelled sample sensitivity, not a defensible complete D1 cost/coverage bound or run extension
- **AND** unknown required inputs, fixed fourteen-day execution ceiling and full confirmatory gates SHALL remain unchanged.

### Requirement: Separate offline PumpSwap development mapping
The design4j dependency SHALL map only bounded offline development input into full-signature/instruction-path declared PumpSwap user/mint/pool/vault identities, exact transaction-token-owned deltas, separate supported classic-SPL transfer facts and explicit ambiguity. It SHALL pin the historical upstream IDL/SPL source commits/hashes, preserve unverified deployment applicability, immutable conflict rejection, deterministic fact/report lineage and false D1/run flags. Missing/changed/unsupported state SHALL remain null with a reason, never zero-filled, per-invocation execution, reserves or verified historical coverage. Only inline synthetic tests and the sole hash-pinned preholdout v1 `004.raw` smoke are allocated; no providers, holdout/outcomes, price/returns/ranking/selection, field promotion or old-root writes. Behavioral RED/GREEN, fresh independent full-CI review and Main's complete gate SHALL precede dependency completion; this SHALL NOT consume/reset exploratory repair history or complete D1.

The separately authorized design4j err-only exception SHALL use its pinned conservative closed subset: explicit null means success; `AccountInUse` or exactly `{InstructionError:[i,{Custom:c}]}` with integer i0..255/c0..4294967295 and no extra keys means FAILED_TRANSACTION. Missing, malformed and unsupported values SHALL remain UNKNOWN via existing UNSUPPORTED_DATA/context, never inferred success/failure or discarded ambiguity. Historical STOP/counters SHALL remain recorded; exactly one exception requires current Reviewer TEST_SPEC_ERROR/new RED, full independent review and Main complete gate, with no additional source/smoke/discovery permission.

The later design4j conditional-admission exception SHALL preserve the arbitrary-input numeric-rounding repro as unfixed/BLOCK and SHALL allocate no further code/test repair. Only its four pinned immutable manifests and419listed raw hashes MAY be audited once using the existing lossless parser, sequentially under fixed file/byte/time caps, with numeric err lexemes constrained to nonnegative dot/exponent-free u32 and InstructionError index u8; unsupported states SHALL remain UNKNOWN/counted. Complete hash-valid zero-violation audit plus independent current Reviewer APPROVE SHALL be required for EXPLORATORY KNOWN_LIMITATION_ACCEPTED of only that exact hashset, never arbitrary/provider/production/D1 correctness or discovery authorization.

#### Scenario: Conditional audit fails or input changes
- **WHEN** an audit violation/incomplete/integrity failure occurs, Reviewer returns BLOCK or a different raw is proposed
- **THEN** failure/BLOCK SHALL leave4j STOP/UNAPPROVED without another repair; a different raw SHALL require its own new bounded contract and the same audit, not automatic admission
- **AND** immutable roots, existing error/ambiguity semantics, preholdout/source-retention restrictions, false D1 flags and spent repair counters SHALL remain unchanged.

#### Scenario: Error state is absent or unsupported
- **WHEN** transaction err is missing, an unsupported string/variant or an invalid type/payload under the pinned subset
- **THEN** existing invocation/transfer records SHALL retain UNSUPPORTED_DATA and deterministic unknown-state diagnostic context without successful ownedDelta or committed-successful transfer claims
- **AND** unchanged identity/conflict/order/balance rules, bounds, declared-layout uncertainty and false D1 flags SHALL remain.

#### Scenario: Error state belongs to the supported subset
- **WHEN** err is explicitly null, AccountInUse or the exactly validated InstructionError/Custom form
- **THEN** only explicit null SHALL permit existing success-dependent mapping; supported error forms SHALL retain FAILED_TRANSACTION, with no guessed payload or unsupported-enum membership.

#### Scenario: Documentary role mapping has incomplete token state
- **WHEN** a supported PumpSwap instruction matches the pinned declared layout but an owned token account has null pre-state or changed ownership/mint/decimals
- **THEN** the mapper SHALL retain declared identities, exact separate transfer facts where supported, and null affected ownedDelta with deterministic ambiguity diagnostics
- **AND** historical deployment applicability and D1 sufficiency SHALL remain unverified, with false authorization/D1 flags.

#### Scenario: Repeated identity conflicts or unsupported route
- **WHEN** an immutable identity repeats with differing content or a transfer lacks a unique descendant invocation association
- **THEN** immutable conflict SHALL invalidate the result without partial facts, while unsupported/ambiguous association SHALL remain explicitly unassociated without guessed execution amounts
- **AND** equal duplicates SHALL deduplicate once and reordered equal semantic inputs SHALL preserve factsHash without erasing byte-level input provenance.

### Requirement: Separately authorized exploratory public sample
Under the owner's 2026-10-03 authorization and offline-procedure section 11, a fixed zero-paid public SQD probe SHALL produce only availability, field-presence, sample-coverage and volume/cost feasibility outputs labelled `EXPLORATORY`, `d1Evidence = false` and `d1Passed = false`. It SHALL NOT compute returns, PnL, profitability, rankings, selection, strategy outcomes or reconstruction; change frozen methodology/calibration/thresholds; promote inventory fields; or claim full-envelope completeness. Meaningful behavioral RED, targeted GREEN, the two inventory suites, integrity preflight, strict all-item validation, doctor, diff check and ONE fresh independent review SHALL precede source requests. The owner's localized exception SHALL omit the full Maven/Docker gate for this probe only and SHALL NOT relax authoritative D1/P1 closure or permit archive.

#### Scenario: Exploratory probe proceeds while D1 remains blocked
- **WHEN** design 4f's targeted checks and independent review pass but full-D1 population/retention/cost prerequisites remain unknown
- **THEN** only the fixed bounded exploratory public sample SHALL be executable
- **AND** its result SHALL retain false D1 flags, explicit unknowns and unchecked full-D1 tasks.

### Requirement: Exploratory v1 holdout and payload admission guard
The v1 probe SHALL exclude every raw fetch, count and inspection of `[2026-08-31T00:00:00Z, 2026-09-29T00:00:00Z)`. It SHALL use only design 4f's six fixed preholdout timestamp anchors, validated header-first single-slot requests and three fixed program filters; no previous raw sample SHALL be read. Every returned header SHALL match the admitted safe slot, timestamp and hash before any raw retention or row count. Missing/unsafe/forbidden time, extra blocks, malformed/partial/oversized responses SHALL produce an explicit incomplete sample with no retained raw body or published rows from that response. Logs/receipts SHALL contain only safe accounting/fixed error codes, never provider error bodies or secrets.

#### Scenario: Provider returns a holdout block
- **WHEN** a requested preholdout slot response includes a timestamp on September 28 or another forbidden/missing/mismatched header
- **THEN** the response SHALL be rejected before raw-file retention and row counting
- **AND** only its attempted-request/received-byte/error accounting SHALL remain, with no fallback or retry.

### Requirement: Exploratory v1 finite budget and replay provenance
The fixed exploratory query/summary/configuration versions SHALL be `exploratory-sqd-v1`, `exploratory-counts-v1` and `exploratory-config-c14n-v1`. The Node-built-in-only probe SHALL bound aggregate execution to USD 0, 30 minutes, 80 attempts, 25,000,000 received bytes, 50,000,000 retained bytes, 2,000,000 bytes/response, 30 seconds/request, two seconds between starts, concurrency one and retries zero. It SHALL reserve response/disk capacity before requests, retain 30,000,000,000 bytes free, record 80-percent checkpoints and stop unfinished work at 100 percent. Files SHALL be exclusive outside git; prior output SHALL NOT be overwritten/resumed. The manifest SHALL identify exact selectors, exclusion, limits, code/runtime/source/query versions, raw SHA-256, safe gaps and unresolved strata. Offline replay SHALL verify raw hashes and reproduce the deterministic summary hash without network or writes. Full-D1 upper bounds SHALL remain unknown unless supported; sensitivities SHALL explicitly identify assumed populations/workload and omitted components.

#### Scenario: Finite sample reaches its byte ceiling
- **WHEN** remaining received/disk capacity cannot reserve the next response or a request times out
- **THEN** further requests SHALL stop with explicit incomplete accounting
- **AND** neither ceiling resets, automatic retry, paid fallback nor a D1 success claim SHALL occur.

#### Scenario: Replay detects changed raw evidence
- **WHEN** a manifest-listed raw file has changed or is missing
- **THEN** offline replay SHALL return a fixed integrity error without a successful summary
- **AND** a byte-identical admitted manifest SHALL reproduce the original deterministic sample-summary hash without re-fetching data.

### Requirement: Exploratory v2 continuous sample and exact input diagnostics
The separately owner-authorized v2 SHALL execute only design4g's four fixed preholdout ten-minute windows, with header-census-validated continuous lightweight six-program and fixed-Jito-System-transfer profiles plus twelve separately labelled sparse rich-size samples. It SHALL exclude all holdout raw fetches/counts, admit no payload outside its census/time range, preserve v1 behavior/frozen tests, and retain `EXPLORATORY`/false-D1 flags. Cursor continuation and deterministic cap bisection SHALL be finite, identity-safe and gap-explicit; no-progress/partial/conflicting/malformed/forbidden data SHALL NOT become completeness. Fees/transfers SHALL preserve exact raw integers; venue instruction shares and fee-payer/mint/account proxies SHALL NOT be called reconstructed trade, candidate or depth coverage. Unsupported transfer forms, historical tip-label applicability, fee separation, SOL/USD, visibility and tick/bin state SHALL remain explicit unknowns.

#### Scenario: Lightweight window succeeds but rich sample is sparse
- **WHEN** all census blocks are admitted for the two lightweight profiles but rich payloads cover only fixed sampled slots
- **THEN** v2 SHALL report continuous lightweight coverage and sparse rich-size evidence separately
- **AND** neither matched-instruction share nor an empty selection SHALL establish D1 depth coverage or historical source absence.

#### Scenario: Large response requires finite smaller pages
- **WHEN** a multi-slot v2 response exceeds 16,000,000 bytes
- **THEN** its partial body SHALL be discarded and charged, and only bounded deterministic bisection of the unadmitted range MAY proceed
- **AND** admitted slots SHALL NOT be duplicated, single-slot failure SHALL remain a gap, and network failures SHALL NOT trigger retries.

#### Scenario: Counts-only recognized swap share
- **WHEN** A contains recognized design4g swap prefixes, initialization/liquidity instructions, unknown variants and distinct CPI invocations
- **THEN** v2 SHALL report unique recognized committed swap-instruction counts/shares separately from all-program instruction shares and explicit unknown/error counts
- **AND** the Orca/CLMM/DLMM diagnostic share SHALL NOT be called reconstructed volume, historical depth coverage or the frozen D1 trigger denominator.

### Requirement: Exploratory v2 generous finite limits and numerical sensitivity
V2 SHALL enforce design4g's Node24-major support and fixed fresh root, USD0/120-minute/1,000-attempt/1GB-received/1.1GB-disk/16MB-response/45-second-request ceilings, sequential two-second spacing, zero same-query retries, capacity reservation/free-space floor and cumulative checkpoint/stop accounting. It SHALL process and replay bounded manifest-listed pages incrementally, verify hashes and publish only deterministic summaries. Available full-duration lightweight rates and sparse rich-size denominators SHALL produce exact rational/ceiling low/central/high numerical workload and hypothetical cash sensitivities under the named fractions/multipliers/tariffs, not unsupported full-D1 forecasts or proven bounds; missing denominators SHALL remain unavailable. Meaningful RED, four Node suites, targeted repository checks and ONE fresh independent full-CI review SHALL precede the one public run. The owner-authorized exploratory-only Maven/Docker exception SHALL NOT weaken confirmatory D1 closure, change methodology or authorize archive/spending.

#### Scenario: Numerical workload assumptions do not prove full-D1 feasibility
- **WHEN** v2 has usable sample rates but actual eligible populations, ancillary state and full-D1 tariffs remain unknown
- **THEN** it SHALL show supported numeric low/central/high assumed-workload scenarios with formulas/omissions
- **AND** `fullD1UpperBound` SHALL remain null, actual cash zero, full-D1 tasks unchecked and source-selection/outcome work unauthorized.

#### Scenario: Replay or final budget is incomplete
- **WHEN** a v2 page hash differs, a partial file is unresolved, or a finite ceiling stops the run
- **THEN** the affected result SHALL remain explicitly incomplete or integrity-invalid without further provider calls
- **AND** an identical valid manifest SHALL reproduce the original summary without loading all raw pages into memory.

### Requirement: Separately gated exploratory v3 bounded retries
Design4i's fresh v3 SHALL preserve v2 windows, selectors, admission, metrics, arithmetic, holdout exclusion and all aggregate ceilings. Only HTTP_ERROR with529/503/429 or the owned45-second transport-timer TIMEOUT with no status/200/529/503/429 SHALL permit same-canonical-query retry after body discard. At most four retries with15/45/120/300-second backoff and150 globally SHALL occur. Every attempt, wait and discarded byte SHALL be charged; rejected attempts SHALL NOT contribute coverage, rows or denominators. Valid decimal Retry-After seconds SHALL lengthen waits; invalid/unsupported supplied values SHALL stop the query. Insufficient wait plus45-second remaining room SHALL stop the run without an early retry. V3 SHALL use a fresh immutable root and fingerprinted retry policy; existing80 tests and v1/v2 evidence SHALL remain preserved. New meaningful RED/freeze, current-reviewer third-repair authorization, independent full-CI APPROVE and Main's procedure11 targeted gate SHALL precede the one public attempt; replanning SHALL NOT reset repair capacity or authorize archive.

#### Scenario: Eligible failure recovers without duplicate admission
- **WHEN** a safe fixed v3 query receives an eligible failure then a validated successful body within retry/budget limits
- **THEN** the identical query SHALL retry after its full applicable delay, with every failed byte/attempt/wait recorded and charged
- **AND** only the successful body SHALL be retained/admitted once; other statuses/errors SHALL remain terminal.

#### Scenario: Retry-After and finite deadline cannot be bypassed
- **WHEN** Retry-After exceeds schedule, is invalid/unsupported, or the next full wait plus45seconds cannot fit the remaining run allowance
- **THEN** v3 SHALL honor the longer valid wait or stop explicitly before retrying
- **AND** no header clamping, shortened deadline, uncharged wait, fifth retry or151st global retry SHALL occur.

#### Scenario: Replay preserves complete retry accounting and old evidence
- **WHEN** Main replays v3 offline or replays preserved v2 source bytes against the original v2 manifest
- **THEN** v3 SHALL validate ordered retry history and reproduce its original summary/accounting/hashes without network/writes/waits, and v2 SHALL reproduce its original hashes/status
- **AND** tampered retry order/timing/counters SHALL fail integrity; prior v1/v2 pre-run totals, separate v3 counters and exact cumulative post-run totals SHALL remain explicit without double charging or zeroing history; unmeasured profiles SHALL remain unknown with false D1 flags and unchecked confirmatory tasks.

### Requirement: Complete required-field accounting
The inventory SHALL account for every frozen R1 section 8.1 field with explicit source references or an explicit unavailable reason, actual covered dates or declared unknowns, granularity or declared unknowns, gaps, cost and retention evidence. It SHALL distinguish `CONFIRMED`, `DOCUMENTED`, `UNVERIFIED` and `UNAVAILABLE` field status. `CONFIRMED` SHALL denote an author-supplied, evidenced assertion subject to independent review, not a measured D1 availability result or a fact certified by the validator. Missing, duplicate, unknown or malformed required-field records SHALL remain `INVENTORY_INVALID`. Design4h's default report-v2 SHALL distinguish field-accounting/source-admission blockers from every existing field-availability diagnostic, without suppressing unavailable fields or reporting documentary/modelled assertions as measured coverage. Valid unaccounted fields SHALL block completion; explicitly accounted insufficiency SHALL remain visible for the separate unchanged frozen D1 gate.

#### Scenario: Trade table without historical depth
- **WHEN** trade legs are documented but historical reserve/depth inputs remain unverified
- **THEN** the report SHALL include the reserve/depth availability diagnostic
- **AND** it SHALL NOT present the trade table as proof of depth coverage or a passed D1 gate.

#### Scenario: Missing and duplicate fields
- **WHEN** a required-field record is missing or appears twice
- **THEN** validation SHALL return `INVENTORY_INVALID`
- **AND** no extraction or successful inventory classification SHALL be emitted.

#### Scenario: Multiple unresolved prerequisites
- **WHEN** a valid inventory has unknown source cost, unconfirmed retention and an unavailable field
- **THEN** the report SHALL retain source cost/retention blockers and the unavailable-field availability diagnostic
- **AND** it SHALL NOT discard unavailable fields.

### Requirement: Exact bounded source cost and retention
Each selected source SHALL identify its source version, query/export version, dated evidence and explicit retention permission for reproducible local exports. Unknown cost or retention SHALL block inventory completion. Selected-source cost upper bounds SHALL be exact nonnegative integer micro-USD strings, summed without binary floating-point arithmetic and compared with the approved D1 USD 100 ceiling. An estimate over the ceiling SHALL return an explicit budget blocker before any spending; equality SHALL NOT authorize spending or bypass the remaining prerequisites.

#### Scenario: Exact spending ceiling
- **WHEN** selected-source upper bounds sum to `100000000` micro-USD
- **THEN** the cost comparison SHALL record that the declared bounds do not exceed the USD 100 ceiling
- **AND** a sum of `100000001` SHALL record `COST_ABOVE_CEILING`.

#### Scenario: Unknown retention
- **WHEN** a selected source documents available fields but its export-retention permission is unconfirmed
- **THEN** the report SHALL be `INVENTORY_BLOCKED`
- **AND** access to that source SHALL NOT be treated as retention permission.

### Requirement: Evidenced offline candidate correction
The candidate inventory SHALL include unselected `alchemy-solana-account-archive` and `solana-public-rpc` alongside the existing Dune and SQD candidates, linking historical account state to reserve/depth and mint-state fields and independent raw receipts to transaction fields. Its evidence SHALL distinguish the retained Alchemy B 12/12 bounded matrix and sampled SQD/public RPC agreement from unfulfilled formal S3 and full-envelope D1 requirements. Dune SHALL remain explicitly unconnected. Source costs and query/source versions SHALL remain unknown, retention unverified and full-envelope dates unset until supported by evidence; no required field SHALL become `CONFIRMED` from these samples. Updating candidate input SHALL NOT alter the offline validator schema or authorize provider calls.

#### Scenario: Historical state candidate is retained without false readiness
- **WHEN** the offline candidate input incorporates the retained Alchemy B matrix and sampled independent receipt agreement
- **THEN** Alchemy SHALL be present and linked to reserve/depth and mint-state fields with the measured sample scope and unresolved layout/transition limits retained
- **AND** public RPC SHALL be present as the independent transaction candidate, all sources SHALL remain unselected, and validation SHALL report `INVENTORY_BLOCKED` with `runAuthorized = false` and `d1Passed = false`.

### Requirement: Filtered-envelope feasibility before spending
An R1 8.1 feasibility sample SHALL produce only volume/cost feasibility evidence for the complete frozen six-month extraction envelope, including its tail, filtered to outcome-blind candidate wallets and relevant pools of their tokens under the frozen universe rules. Estimates SHALL account for cohort/pool discovery, all required related histories, independent checks, overlaps, requests, received/retained bytes, throughput, tariffs and finite retries. All-block rates or post-D1 ranked winners SHALL NOT substitute for that workload. Unknown population, source version, retention, tariff or upper-bound evidence SHALL remain blocking. A complete bound above any applicable OD-2 resource ceiling SHALL stop work before spending/full extraction and return the concrete scope, estimate, excess and budget/narrower-experiment decision to the owner. The sample SHALL NOT demonstrate D1 coverage or passage, change frozen thresholds or authorize a provider call before a new PLAN_READY and mandatory review/gate/run preconditions.

#### Scenario: Filtered workload differs from complete blocks
- **WHEN** only broad program/full-block samples exist and the complete candidate-wallet/token-pool population or related-state volume is unknown
- **THEN** the filtered whole-envelope estimate SHALL remain unresolved
- **AND** an all-block byte-rate extrapolation SHALL NOT be reported as its complete cost bound.

#### Scenario: Full workload exceeds the approved ceiling
- **WHEN** the full filtered-envelope estimate including discovery, tail and ancillary evidence exceeds an applicable OD-2 ceiling
- **THEN** work SHALL stop before spending or full extraction with the estimate, ceiling excess and precise owner decision recorded
- **AND** R1 dates, universe and thresholds SHALL NOT be silently narrowed to fit the sample.

### Requirement: Vault inputs preserve transaction state and missing-depth counts
Later reserve reconstruction SHALL use raw vault-account balances immediately before/after the transaction, with full transaction identity, account/mint/pool mapping and exact integer amounts. Trader balances, slot-wide archive states or transaction aggregate deltas SHALL NOT be silently treated as intermediate leg reserves. Post-state SHALL NOT become evidence for a pre-transaction decision. Vault totals without required historical ticks/bins SHALL NOT establish CLMM/DLMM executable depth. The later full-envelope gate SHALL retain all candidate-pool trigger events in the frozen R1 8.2 denominator and explicitly count unavailable-depth shares by venue, month and reason; availability SHALL meet the unchanged >=90 percent rule or cost-modelled D1 SHALL be `INCONCLUSIVE/data insufficient`.

#### Scenario: Vault sample lacks CLMM or DLMM state
- **WHEN** pre/post vault balances are available but historical ticks/bins needed for a venue's pricing form are missing
- **THEN** the affected triggers SHALL remain counted as missing depth in the complete denominator
- **AND** neither their removal nor a sample's apparent coverage SHALL establish the frozen depth gate.

### Requirement: Exact field source or explicit unavailability
Next-run admission SHALL state an exact supported source with version, cutoff/time semantics, covered dates, cost and retention evidence for each of tips, SOL/USD and measured visibility latency, or SHALL record `UNAVAILABLE`. Unobservable tips SHALL NOT be assumed zero; non-exact display or after-cutoff prices SHALL NOT supply authoritative SOL/USD; historical block/retrieval times SHALL NOT be fabricated into measured visibility. The frozen C-3 default SHALL remain labelled `MODELED` with its version and SHALL NOT be presented as an observed source or a relaxed D1 threshold.

#### Scenario: Three fields lack supported sources
- **WHEN** no admitted historical tip attribution, exact point-in-time SOL/USD feed or observed source-visibility evidence is available
- **THEN** all three next-run source dispositions SHALL be `UNAVAILABLE` with their missing evidence retained
- **AND** the existing modeled default or documentary candidates SHALL NOT turn the inventory into a complete measured result.

### Requirement: Deterministic inventory identity
Every structurally valid inventory report SHALL identify inventory schema and canonicalization versions and a SHA-256 content fingerprint. Equivalent field, source and evidence-list ordering SHALL produce identical ordered classifications, blockers and fingerprint. Changes to accepted inventory content SHALL produce distinguishable identity. The only supported canonicalization version SHALL be `r1-d1-inventory-c14n-v1`; another nonempty string version SHALL produce `INVENTORY_INVALID` with fixed code `UNSUPPORTED_CANONICALIZATION_VERSION` and no fingerprint. Supporting a different version SHALL require an approved contract update. The JSON numeric `freezeEntry` value `1` SHALL encode as the exact ASCII bytes `n1:1`; its string form SHALL be rejected. Documentary references SHALL record their URL, retrieval date and known content version or an explicit unknown version; missing version evidence SHALL remain visible.

#### Scenario: Input permutation
- **WHEN** the same valid records are supplied in a different collection or object-key order
- **THEN** their fingerprint and ordered report SHALL be identical
- **AND** changing a source query/export version SHALL change the fingerprint.

#### Scenario: Unsupported canonicalization version
- **WHEN** an otherwise valid inventory supplies a nonempty canonicalization version other than `r1-d1-inventory-c14n-v1`
- **THEN** validation SHALL return `INVENTORY_INVALID` with `UNSUPPORTED_CANONICALIZATION_VERSION`
- **AND** it SHALL NOT emit a fingerprint for that unsupported version.

#### Scenario: Numeric freeze reference
- **WHEN** the accepted inventory has the JSON numeric `freezeEntry` value `1`
- **THEN** canonicalization SHALL encode that value as the exact ASCII bytes `n1:1`
- **AND** replacing it with the string `"1"` SHALL produce `INVENTORY_INVALID`.

### Requirement: Offline validation boundary
The validator SHALL read only its explicitly supplied inventory, bound input to 1,000,000 bytes, 32 sources, 12 required fields and 16 evidence references per source or field, and emit a bounded report without filesystem writes, credential loading, provider calls or data extraction. It SHALL reject unknown schema properties, URLs containing user information or query strings, malformed input, and input limits before publishing a successful report. Diagnostics SHALL use fixed codes and SHALL NOT echo input values, file content, credentials, provider error bodies or stack traces.

#### Scenario: Oversized or unsafe input
- **WHEN** the inventory exceeds a declared bound or contains an authenticated URL
- **THEN** the CLI SHALL exit with an invalid-input result and a fixed diagnostic code
- **AND** it SHALL make zero provider calls and zero filesystem writes.

### Requirement: Inventory readiness is separate from extraction authorization
A syntactically valid inventory SHALL report accounting completeness only when all12 fields satisfy design4h's explicit accounting contract; missing source references SHALL require an explicit `UNAVAILABLE` reason, and unknown coverage/granularity SHALL require an explanatory gap. `INVENTORY_COMPLETE` SHALL additionally require selected-source version, evidence, retention and exact cost prerequisites; no selected source SHALL retain an explicit source-admission blocker. Every field-shortfall code SHALL remain in deterministically ordered availability diagnostics even when accounting is complete. The corrected default SHALL identify `r1-d1-inventory-report-v2` while preserving input schema/canonicalization and its content fingerprint. Every report SHALL retain `runAuthorized = false` and `d1Passed = false`. Sufficient coverage SHALL be judged only by unchanged frozen R1 8.2/8.3 measurements, never inventory classification. Before any D1 data run, mandatory review and an updated Architect PLAN_READY SHALL name admitted sources, exact query/export versions, reviewed scripts, output location and finite run ceilings. Bulk extraction SHALL follow inventory review; frozen calibration/outcome guards remain unchanged.

#### Scenario: Complete inventory does not permit extraction
- **WHEN** every field is explicitly accounted for and selected-source admission prerequisites are satisfied
- **THEN** the report SHALL be `INVENTORY_COMPLETE` with `runAuthorized = false` and `d1Passed = false`
- **AND** no data run SHALL start from that report alone.
- **AND** all remaining availability diagnostics and confirmed claims SHALL remain subject to separate sufficiency measurement and independent evidence review.

#### Scenario: Explicit unavailable inputs do not disappear
- **WHEN** source admission is satisfied and the complete12-field inventory explicitly records unavailable depth or partial coverage with reasons
- **THEN** report-v2 SHALL permit accounting completion while retaining the existing unavailable/coverage/gap codes in availability diagnostics
- **AND** it SHALL NOT assert that the frozen depth or other D1 threshold passed.

#### Scenario: Historical visibility uses the frozen modeled default
- **WHEN** observed historical visibility is unavailable and its field explicitly records that fact together with versioned C-3 MODELED60-second availability
- **THEN** accounting SHALL NOT require fabricated observed latency or remove its availability diagnostic
- **AND** the modeled default SHALL NOT be reported as measurement, calibration or D1 passage.

#### Scenario: Frozen assertion correction is reviewed before editing
- **WHEN** implementation of design4h requires changing an establishing assertion that encoded the superseded all-CONFIRMED or zero-gap readiness rule
- **THEN** the current reviewer SHALL explicitly authorize only the named correction with requires_new_red=true before Builder edits it
- **AND** meaningful new behavioral RED, unchanged unrelated assertions, fresh full-CI review and Main's complete gate SHALL precede completion.

#### Scenario: Initial implementation slice
- **WHEN** only the offline inventory slice has been implemented and reviewed
- **THEN** extraction and full D1 gate tasks SHALL remain incomplete
- **AND** the D1 stage change SHALL NOT be archived as complete.

#### Scenario: Retained provider samples
- **WHEN** prior provider receipts prove historical account reads or independent transaction agreement only for a bounded sample
- **THEN** source admission SHALL preserve that measured evidence and its exact sampled scope
- **AND** it SHALL NOT infer full-envelope depth, mint-state, visibility, retention or cost completeness from those receipts.

#### Scenario: Source-admission sample is not a D1 pass
- **WHEN** an updated reviewed plan permits only finite source-admission samples and the frozen full-envelope gates remain unmeasured
- **THEN** the D1 conclusion SHALL remain `INCONCLUSIVE`
- **AND** no sample result SHALL replace the frozen 200-trade check, full-envelope thresholds, full-stage task completion or archive prerequisites.

#### Scenario: Owner declines PAYG spending
- **WHEN** the owner has explicitly declined Alchemy PAYG D1 spending
- **THEN** source-run planning SHALL preserve that denial and allocate only verified zero-paid sample options until a future explicit owner spending decision
- **AND** retained paid spike receipts SHALL NOT be treated as permission for new paid calls or a paid-run PLAN_READY.
