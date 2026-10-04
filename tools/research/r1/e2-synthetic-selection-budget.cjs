'use strict';
// Trusted callers must retain the ledger head. Pure functions cannot detect
// deliberate external discard or reinitialization, or prove supplied bytes unreal.
const { createHash } = require('node:crypto');
const VERSION = 'e2-synthetic-selection-budget-v1';
const MODE = 'SYNTHETIC_ONLY';
const DIMENSIONS = Object.freeze(['credits', 'pages', 'requests', 'timeMs', 'receivedBytes', 'retainedBytes']);
const DATES = Object.freeze([1775001600, 1777593600, 1780272000, 1782604800]);
const LIMITS = Object.freeze({ records: 10000, tokens: 10000, attempts: 10000, identifierBytes: 64, serializedBytes: 1000000 });
const flags = () => ({ cohortAdmitted: false, d1Passed: false, realSelection: false });
function fail(code) { throw new Error(code); }
function identifier(value) { if (typeof value !== 'string' || !value.trim() || Buffer.byteLength(value) > LIMITS.identifierBytes) fail('IDENTIFIER'); return value; }
function integer(n) { if (!Number.isSafeInteger(n) || n < 0) fail('SAFE_INTEGER'); return n; }
function hex(value) { if (typeof value !== 'string' || !/^[0-9a-f]{64}$/.test(value)) fail('HASH'); return value; }
function bytes(value) {
  if (!(value instanceof Uint8Array) && !Array.isArray(value)) fail('MINT_BYTES');
  if (value.length !== 32 || Array.from(value).some(n => !Number.isInteger(n) || n < 0 || n > 255)) fail('MINT_BYTES');
  return Buffer.from(value);
}
function hashMintBytes(mintBytes) { return createHash('sha256').update(bytes(mintBytes)).digest(); }
function canonical(value) {
  if (value === null || typeof value === 'boolean' || typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'number') { integer(value); return JSON.stringify(value); }
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (!value || Object.getPrototypeOf(value) !== Object.prototype) fail('SNAPSHOT');
  return '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical(value[k])).join(',') + '}';
}
const digest = value => createHash('sha256').update(canonical(value)).digest('hex');
function freeze(value) { if (value && typeof value === 'object') { Object.values(value).forEach(freeze); Object.freeze(value); } return value; }
function publish(value) { if (Buffer.byteLength(canonical(value)) > LIMITS.serializedBytes) fail('RESULT_LIMIT'); return freeze(value); }
function exactKeys(value, keys) {
  if (!value || Object.getPrototypeOf(value) !== Object.prototype || Object.keys(value).sort().join('|') !== [...keys].sort().join('|')) fail('SNAPSHOT');
}
function cost(value) { exactKeys(value, DIMENSIONS); return DIMENSIONS.map(d => integer(value[d])); }
function vector(value) { if (!Array.isArray(value) || value.length !== 6) fail('SNAPSHOT'); return Array.from(value, integer); }
function add(a, b) { return a.map((n, i) => { if (b[i] > Number.MAX_SAFE_INTEGER - n) fail('ARITHMETIC_OVERFLOW'); return n + b[i]; }); }
function fits(value, caps, code) { if (value.some((n, i) => n > caps[i])) fail(code); }
function mode(value) { if (value !== MODE) fail('SYNTHETIC_MODE_REQUIRED'); }
function selectSynthetic({ mode: usage, records, eligibilityPolicyId, eligibilitySetHash }) {
  mode(usage); identifier(eligibilityPolicyId); hex(eligibilitySetHash);
  if (!Array.isArray(records) || records.length > LIMITS.records) fail('RECORD_LIMIT');
  const seen = new Set(), groups = [[], [], []], normalized = [];
  for (const r of records) {
    exactKeys(r, ['mintBytes', 'createdAtSeconds', 'eligible']);
    const b = bytes(r.mintBytes), mint = b.toString('hex'), timestamp = integer(r.createdAtSeconds);
    if (typeof r.eligible !== 'boolean') fail('ELIGIBILITY_REQUIRED');
    if (timestamp < DATES[0] || timestamp >= DATES[3]) fail('DATE_ENVELOPE');
    if (seen.has(mint)) fail('DUPLICATE_MINT'); seen.add(mint);
    const stratum = timestamp < DATES[1] ? 0 : timestamp < DATES[2] ? 1 : 2;
    const record = { mint, createdAtSeconds: timestamp, eligible: r.eligible }; normalized.push(record);
    if (r.eligible) groups[stratum].push({ record, hash: hashMintBytes(b) });
  }
  normalized.sort((a, b) => a.mint < b.mint ? -1 : a.mint > b.mint ? 1 : 0);
  const common = { mode: MODE, algorithm: VERSION + '/sha256-raw32-unsigned', eligibilityPolicyId, eligibilitySetHash,
    inputHash: digest(normalized), counts: groups.map(g => g.length), ...flags() };
  common.fingerprint = digest(common);
  if (groups.some(g => g.length < 100)) return publish({ ...common, status: 'INSUFFICIENT_SYNTHETIC_ELIGIBILITY' });
  const syntheticSelected = groups.map((g, stratum) => {
    g.sort((a, b) => Buffer.compare(a.hash, b.hash) || Buffer.compare(Buffer.from(a.record.mint, 'hex'), Buffer.from(b.record.mint, 'hex')));
    return g.slice(0, 100).map(({ record, hash }) => ({ stratum, mintBytes: Array.from(Buffer.from(record.mint, 'hex')), digest: hash.toString('hex'), createdAtSeconds: record.createdAtSeconds }));
  });
  return publish({ ...common, status: 'SYNTHETIC_SELECTION_PREPARED', syntheticSelected });
}
function policy(value) {
  exactKeys(value, ['policyId', 'perToken', 'aggregate']); identifier(value.policyId);
  return { policyId: value.policyId, perToken: cost(value.perToken), aggregate: cost(value.aggregate) };
}
function stateBody(ledger) { const { headHash, ...body } = ledger; return body; }
function seal(body) { return publish({ ...body, headHash: digest(body) }); }
function createSyntheticBudget({ mode: usage, ledgerId, policy: suppliedPolicy, openingCharges }) {
  mode(usage); identifier(ledgerId); const p = policy(suppliedPolicy);
  exactKeys(openingCharges, ['tokens', 'aggregate']);
  if (!Array.isArray(openingCharges.tokens) || openingCharges.tokens.length > LIMITS.tokens) fail('TOKEN_LIMIT');
  const tokens = [], seen = new Set(); let total = [0, 0, 0, 0, 0, 0];
  for (const t of openingCharges.tokens) {
    exactKeys(t, ['mintBytes', 'cost']); const mint = bytes(t.mintBytes).toString('hex'), c = cost(t.cost);
    if (seen.has(mint)) fail('DUPLICATE_MINT'); seen.add(mint); fits(c, p.perToken, 'PER_TOKEN_CAP'); total = add(total, c); tokens.push([mint, c]);
  }
  const aggregate = cost(openingCharges.aggregate);
  if (canonical(aggregate) !== canonical(total)) fail('OPENING_TOTAL_CONFLICT'); fits(aggregate, p.aggregate, 'AGGREGATE_CAP');
  tokens.sort((a, b) => a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0);
  return seal({ version: VERSION, mode: MODE, ledgerId, policy: p, policyHash: digest(p), opening: tokens,
    tokens: tokens.map(([m, c]) => [m, [...c]]), aggregate, attempts: [], durability: 'TRUSTED_CALLER_HEAD_REQUIRED_NOT_PROVED', ...flags() });
}
function validate(ledger) {
  exactKeys(ledger, ['version', 'mode', 'ledgerId', 'policy', 'policyHash', 'opening', 'tokens', 'aggregate', 'attempts', 'durability', 'cohortAdmitted', 'd1Passed', 'realSelection', 'headHash']);
  mode(ledger.mode); identifier(ledger.ledgerId);
  if (ledger.version !== VERSION || ledger.durability !== 'TRUSTED_CALLER_HEAD_REQUIRED_NOT_PROVED' || ledger.cohortAdmitted !== false || ledger.d1Passed !== false || ledger.realSelection !== false) fail('SNAPSHOT');
  exactKeys(ledger.policy, ['policyId', 'perToken', 'aggregate']); identifier(ledger.policy.policyId);
  const p = { policyId: ledger.policy.policyId, perToken: vector(ledger.policy.perToken), aggregate: vector(ledger.policy.aggregate) };
  if (hex(ledger.policyHash) !== digest(p)) fail('POLICY_CONFLICT');
  if (!Array.isArray(ledger.opening) || !Array.isArray(ledger.tokens) || ledger.opening.length > LIMITS.tokens || ledger.tokens.length > LIMITS.tokens) fail('TOKEN_LIMIT');
  if (!Array.isArray(ledger.attempts) || ledger.attempts.length > LIMITS.attempts) fail('ATTEMPT_LIMIT');
  vector(ledger.aggregate); hex(ledger.headHash);
  // Validate bounded leaf shapes before recursively serializing untrusted snapshots.
  for (const collection of [ledger.opening, ledger.tokens]) for (const e of collection) {
    if (!Array.isArray(e) || e.length !== 2) fail('SNAPSHOT'); hex(e[0]); vector(e[1]);
  }
  for (const e of ledger.attempts) {
    if (!Array.isArray(e) || e.length !== 3) fail('SNAPSHOT'); identifier(e[0]); hex(e[1]); vector(e[2]);
  }
  if (Buffer.byteLength(canonical(ledger)) > LIMITS.serializedBytes) fail('RESULT_LIMIT');
  if (hex(ledger.headHash) !== digest(stateBody(ledger))) fail('HEAD_CONFLICT');
  const tokens = new Map(), attempts = new Map(); let total = [0, 0, 0, 0, 0, 0];
  for (const entry of ledger.opening) {
    if (!Array.isArray(entry) || entry.length !== 2) fail('SNAPSHOT'); const [m, c0] = entry, c = vector(c0); hex(m);
    if (tokens.has(m)) fail('DUPLICATE_MINT'); fits(c, p.perToken, 'PER_TOKEN_CAP'); tokens.set(m, c); total = add(total, c);
  }
  for (const entry of ledger.attempts) {
    if (!Array.isArray(entry) || entry.length !== 3) fail('SNAPSHOT'); const [id, m, c0] = entry, c = vector(c0); identifier(id); hex(m);
    if (attempts.has(id)) fail('ATTEMPT_CONFLICT'); attempts.set(id, [m, c]);
    tokens.set(m, add(tokens.get(m) || [0, 0, 0, 0, 0, 0], c)); total = add(total, c);
  }
  if (tokens.size > LIMITS.tokens) fail('TOKEN_LIMIT'); const claimed = new Map();
  for (const entry of ledger.tokens) {
    if (!Array.isArray(entry) || entry.length !== 2) fail('SNAPSHOT'); const [m, c0] = entry, c = vector(c0); hex(m);
    if (claimed.has(m) || !tokens.has(m) || canonical(tokens.get(m)) !== canonical(c)) fail('CHARGE_CONFLICT'); claimed.set(m, c); fits(c, p.perToken, 'PER_TOKEN_CAP');
  }
  if (claimed.size !== tokens.size || canonical(vector(ledger.aggregate)) !== canonical(total)) fail('CHARGE_CONFLICT');
  fits(total, p.aggregate, 'AGGREGATE_CAP'); return { tokens, attempts, p, total };
}
function reserveSyntheticBudget({ ledger, expectedHeadHash, mintBytes, attemptId, cost: suppliedCost }) {
  const { tokens, attempts, p, total } = validate(ledger);
  if (hex(expectedHeadHash) !== ledger.headHash) fail('HEAD_CONFLICT');
  const mint = bytes(mintBytes).toString('hex'), c = cost(suppliedCost); identifier(attemptId);
  if (attempts.has(attemptId)) {
    if (canonical(attempts.get(attemptId)) !== canonical([mint, c])) fail('ATTEMPT_CONFLICT');
    return publish(JSON.parse(canonical(ledger)));
  }
  if (attempts.size >= LIMITS.attempts) fail('ATTEMPT_LIMIT');
  if (!tokens.has(mint) && tokens.size >= LIMITS.tokens) fail('TOKEN_LIMIT');
  const token = add(tokens.get(mint) || [0, 0, 0, 0, 0, 0], c), aggregate = add(total, c);
  fits(token, p.perToken, 'PER_TOKEN_CAP'); fits(aggregate, p.aggregate, 'AGGREGATE_CAP'); tokens.set(mint, token);
  const body = JSON.parse(canonical(stateBody(ledger)));
  body.tokens = [...tokens].sort((a, b) => a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0);
  body.aggregate = aggregate; body.attempts.push([attemptId, mint, c]); return seal(body);
}
module.exports = { VERSION, MODE, DIMENSIONS, DATES, LIMITS, hashMintBytes, selectSynthetic, createSyntheticBudget, reserveSyntheticBudget };
if (require.main === module) { console.error('LIBRARY_ONLY'); process.exitCode = 1; }
