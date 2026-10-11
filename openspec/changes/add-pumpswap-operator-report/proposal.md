## Why

The approved Delivery queue now has separately inspectable capture facts and native saved-batch metrics, but no single bounded operator-facing view. A read-only JSON report and brief summary can make those existing diagnostics usable together without inventing session membership, historical coherence, market admission or a new signal.

## What Changes

- Add one synchronous marketdata request/result API composing exactly one explicitly named capture monitor and one exact pinned metrics request.
- Publish two independently observed panels, distinct absence statuses, unchanged decoder dispositions/native metrics and explicit `REQUESTED_TOGETHER_UNVERIFIED` association.
- Render deterministic, escaped JSON with lossless integer strings and a short human summary; reject output overflow or read failure without partial success.
- Add pure meaningful regression and isolated PostgreSQL public-API/no-write/compatibility tests.

## Capabilities

### New Capabilities

- `pumpswap-operator-report`: bounded read-only JSON and human summary over independent retained-session and frozen-batch diagnostic panels.

### Modified Capabilities

None. Existing monitor, operation storage, metrics, replay telemetry, health and signal/evaluation contracts remain unchanged.

## Impact

Marketdata-only additive API/delegate, one composing use case, one pure formatter and owning tests; exact seven future paths are in design. No schema, dependency, cross-module edge, worker, provider access or data write. Implementation waits for Main's metrics full gate/commit and explicit release of shared API paths; new planning artifacts are excluded from that metrics commit.

Non-goals: startup/CLI/web/file exporter/log trigger, managed access or migrations, new network/live/paid run, deployment, pool/budget expansion, batch merging/latest selection/re-decode/raw export, durable session-binding proof or global atomic snapshot, USD/liquidity/risk/ranking/new signal/outcome/admission, R1/holdout/research restart, archive or push. Existing reuse APIs are verified active/unarchived changes, not accepted main-spec capabilities.
