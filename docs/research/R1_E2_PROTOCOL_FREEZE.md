# R1-E2 protocol freeze record

This separate record pins [R1-E2 protocol](R1_E2_RESEARCH_PROTOCOL.md) without modifying [R1-E1 freeze](R1_PROTOCOL_FREEZE.md). It is prospective: no source receipt, admission or D1 PASS is created by recording it. Later amendments append entries and never rewrite an effective entry.

## Entry 1: owner-authorized bounded window, protocol `1.0.0`

| Field | Value |
|---|---|
| Experiment | R1-E2, separate from R1-E1 |
| Status | FREEZE_PREPARED; amended independent review APPROVE recorded; not effective until Main's applicable final checks, one scoped freeze commit and committed-blob verification |
| Prepared UTC | 2026-10-04T17:06:46Z |
| Protocol version | 1.0.0; section10 controls new window, sections1–9 preserve historical/draft context |
| Prior HEAD / source checkpoint | `6a47de505b506e2774290875057a880ada035034` |
| Exact LF protocol SHA-256 | `aa0a0d72bce40b3a1c6bfd9194a01204a6c6bb0b33cf0f310e593824232f5f6c` |
| Git-clean protocol blob identity | `90e731b4e249df0d4a3cfc87ee768f5cffdafb0d` |
| Final freeze commit | Resolve the single commit adding this entry together with the exact protocol from Git path history and external saved receipt; deliberately not self-referenced in its own contents |
| Active change | `preregister-r1-e2-pumpswap-cohort`, section8; unchanged D1 criteria from `establish-r1-d1-data-gate` |
| Window | Start2026-10-04T16:42:41Z; absolute STOP2026-10-05T04:42:41Z |
| Owner scope | One bounded free window; fixed300 raw-mint-hash tokens,100 each April/May/June1–27, no replacements; existing mapper plus necessary minimal reviewed compatibility correction; no paid/holdout/outcomes/calibration/P1 |
| Cumulative resource limits | 450000 credits including first5/full300/ancillary/retries;14 pages/1400 credits per token;50GB all retained evidence/min30GB free after reservations/80–100percent guards; no reset between sessions or phases |
| Historical deployment disposition | UNVERIFIED; owner-preauthorized1B/EXPLORATORY, not confirmatory D1 PASS |

### Owner and delegated decision provenance

Owner explicitly approved the 2026-10-04 six-hour target/twelve-hour maximum instruction and continuation, packet1A with at-most-one-hour proof then automatic1B,2A and revised3A existing-mapper path. The scope is the actual owner instruction, not permission inferred from a peer message.

Live designated Claude delegate technical decision `8d466c32-f646-4e71-82f9-02448965224d`, relayed and verified by Main, resolves only inputformat: minimal additional entrypoint in existing mapper, same economic logic, old mapBlock/config/literal outputs unchanged, distinct `helius-tx-adapter-v1` raw/actual slot/blockTime provenance, missing header hashes UNVERIFIED and missing reconstruction facts UNKNOWN. No bulk header retrieval, invented parent/knownAt, new economic parser, historical rerun or changed D1 method follows. Same Reviewer has checked the amended plan with APPROVE in the reference below; executable implementation/source access still requires its own checks.

Deployment receipt: `C:\crypto-research-evidence\r1-e2\owner-window-20261004\deployment-proof\deployment-proof-receipt.json`, SHA256 `4054fa20175b8313d98d800f4fe2e233550d9b7ffe7304162244889f43e6cedc`. Three free public requests/zero Helius credits/latest upgrade October2 do not establish April–June executable applicability. Earliest observed census, PIT/knownAt, rights and complete wallet/envelope coverage remain independent limitations.

### Unchanged holdout and method provenance

E1 freeze reference: commit `6e5647e61ba28bbc5bba6042f6dc97a7ffc3930e`, protocol1.0.0 SHA256 `283ea9d5023795fb8d6252a2e8677f3a5893e0d4e7a13c1f8d3cc6ea46b176ba`. Original owner attestation dated2026-10-01 is inherited as provenance, not fabricated anew: no wallet/token returns from August31–September28 were viewed or used for choosing rules. Original date windows and C-3 MODELED availability remain unchanged. The conservative protected exclusion `[2026-08-31T00:00:00Z,2026-09-29T00:00:00Z)` forbids source/inspection/counts. Exact unchanged§8.2–8.3 thresholds,200 aggregate venue/month checks and whole-envelope limitations govern the verdict; first-five technical compatibility is not statistical acceptance.

### Review and gate references

Existing external evidence directory: `C:\crypto-research-evidence\r1-e2-path-v1-gate-20261003`.

- Initial seven-file documentary review: `reviewer-window-a-plan-final.md`, APPROVE, independently read. It predates this inputformat amendment; it is not substituted approval of the amended state.
- Same independent Reviewer's amendment review: `reviewer-window-a-amendment-final.md`, STATUS APPROVE, independently read in DOCS_CLOSE. It covers the amended A plan and prepared freeze record, confirms exact LF protocolSHA/Git blob/prior HEAD and no blockers, with RED_NOT_REQUIRED/red_suspect=false. It does not review executable implementation or declare freeze effective. It explicitly preserves UNKNOWN reconstruction facts and the implementation's duty to prove actual CPI/owned-state mapping.
- Main-reported full gate logs: `main-window-freeze-docker.log`, `main-window-freeze-integrity.log`, `main-window-freeze-maven.log`, `main-window-freeze-node.log`, `main-window-freeze-strict.log`, `main-window-freeze-doctor.log`, `main-window-freeze-diff.log`, `main-window-freeze-cached-diff.log`. Main reports native0, Maven168/0failures/0errors/0skips and Node233/0failures/0skips; Architect inspected Maven BUILD SUCCESS, Node counts and strict17/0. These logs predate this documentary amendment. Required affected strict/doctor/link/working+cached diff checks on the final amended bytes remain pending Main verification; executable implementation additionally requires its own meaningful RED/GREEN/review/checks before source.
- This record is not a substituted gate or future PASS. Main updates verified references/status only after actual checks, then commits protocol/record/planning documents together and saves final commit/blob verification in the existing external log.

### Exact commit verification

Main amended documentary checkpoint, 2026-10-04T17:13:24Z: `main-window-freeze-amended-strict.log`, `main-window-freeze-amended-doctor.log`, `main-window-freeze-amended-diff.log` and `main-window-freeze-amended-cached-diff.log` each returned native exit0 on the reviewed amended state. The pre-amendment complete Maven/Node gate remains applicable to unchanged executable inputs for this documentary-only freeze commit; later executable edits require their own checks. Commit/blob effectiveness verification follows this checkpoint, not a guessed future PASS.

Resolve the freeze commit that introduced this entry via `git log --format=%H -- docs/research/R1_E2_PROTOCOL_FREEZE.md`, inspect its scope, and read the protocol bytes with `git cat-file blob <freeze-commit>:docs/research/R1_E2_RESEARCH_PROTOCOL.md`. SHA256 must equal the pinned value and the blob identity must equal the pinned Git object. Hash raw committed bytes, not PowerShell text-pipeline output or CRLF checkout bytes. A later protocol change invalidates this prepared pin and requires review/applicable checks plus a new explicit entry; no guessed future commit or protocol self-hash is permitted.

Следующее разрешённое действие: Main проверяет окончательные документальные входы после фактического amended APPROVE, фиксирует единый scoped freeze commit и отдельно подтверждает его точные blob bytes; затем выполняет уже разрешённый first-five маршрут только после проверки реализации.
