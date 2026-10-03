'use strict';
const { createHash } = require('node:crypto');
const SCHEMA = 'r1-d1-inventory-v1', CANONICALIZATION = 'r1-d1-inventory-c14n-v1';
const FROM = '2026-04-01T00:00:00Z', TO = '2026-09-29T04:02:00Z';
const FIELD_IDS = ['trade-legs', 'spl-transfers', 'sol-transfers', 'base-priority-fees', 'tips',
  'reserves-depth', 'liquidity-events', 'mint-decimals-freeze', 'executable-entry-exit',
  'sol-usd', 'block-time', 'visibility-latency'];
const INVALID_CODES = new Set(['SCHEMA_INVALID', 'DATE_INVALID', 'MONEY_INVALID', 'URL_INVALID',
  'DUPLICATE_RECORD', 'INPUT_LIMIT', 'BLOCKER_LIMIT', 'REPORT_LIMIT', 'UNSUPPORTED_CANONICALIZATION_VERSION']);
function invalid(code) {
  return { classification: 'INVENTORY_INVALID', code, runAuthorized: false, d1Passed: false };
}
function demand(condition, code = 'SCHEMA_INVALID') { if (!condition) throw code; }
function object(value, keys) {
  demand(value !== null && typeof value === 'object' && !Array.isArray(value));
  demand(Object.getPrototypeOf(value) === Object.prototype || Object.getPrototypeOf(value) === null);
  const own = Reflect.ownKeys(value);
  demand(own.length === keys.length && own.every(k => keys.includes(k)));
  demand(own.every(k => Object.getOwnPropertyDescriptor(value, k).get === undefined));
}
function text(value, max = 256) {
  demand(typeof value === 'string' && value.length > 0 && value.length <= max && value.trim().length > 0);
  // Reject control characters and malformed UTF-16 to keep UTF-8 encoding injective.
  demand(!/[\u0000-\u001f\u007f]/u.test(value));
  for (let i = 0; i < value.length; i++) {
    const n = value.charCodeAt(i);
    if (n >= 0xd800 && n <= 0xdbff) {
      const next = value.charCodeAt(++i); demand(next >= 0xdc00 && next <= 0xdfff);
    } else demand(n < 0xdc00 || n > 0xdfff);
  }
}
function nullableText(value) { if (value !== null) text(value); }
function id(value) { text(value, 64); demand(/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value)); }
function instant(value) {
  text(value);
  demand(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{0,5}[1-9])?Z$/.test(value), 'DATE_INVALID');
  const parsed = new Date(value);
  demand(Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 19) === value.slice(0, 19), 'DATE_INVALID');
}
function list(value, max, visit) {
  demand(Array.isArray(value) && value.length <= max, 'INPUT_LIMIT');
  const seen = new Set();
  for (const entry of value) {
    visit(entry); const key = encode(entry).toString('hex');
    demand(!seen.has(key), 'DUPLICATE_RECORD'); seen.add(key);
  }
}
function references(value) {
  list(value, 16, ref => {
    object(ref, ['url', 'retrievedAt', 'version', 'claim']); text(ref.url); instant(ref.retrievedAt);
    nullableText(ref.version); text(ref.claim, 1000);
    let url; try { url = new URL(ref.url); } catch { throw 'URL_INVALID'; }
    demand(url.protocol === 'https:' && url.hostname.length > 0 && !url.username && !url.password &&
      !url.search && !url.hash && !/[?#\\\s]/u.test(ref.url), 'URL_INVALID');
  });
}
function schema(input) {
  object(input, ['schemaVersion', 'canonicalizationVersion', 'protocolVersion', 'freezeEntry', 'envelope', 'sources', 'fields']);
  text(input.canonicalizationVersion);
  demand(input.canonicalizationVersion === CANONICALIZATION, 'UNSUPPORTED_CANONICALIZATION_VERSION');
  demand(input.schemaVersion === SCHEMA && input.protocolVersion === '1.0.0' && input.freezeEntry === 1);
  object(input.envelope, ['from', 'to']); demand(input.envelope.from === FROM && input.envelope.to === TO);
  const sourceIds = new Set();
  list(input.sources, 32, source => {
    object(source, ['id', 'selected', 'sourceVersion', 'queryVersion', 'costUpperMicrousd', 'retention', 'evidence']);
    id(source.id); demand(!sourceIds.has(source.id), 'DUPLICATE_RECORD'); sourceIds.add(source.id);
    demand(typeof source.selected === 'boolean'); nullableText(source.sourceVersion); nullableText(source.queryVersion);
    demand(source.costUpperMicrousd === null || (typeof source.costUpperMicrousd === 'string' &&
      /^(?:0|[1-9]\d{0,17})$/.test(source.costUpperMicrousd)), 'MONEY_INVALID');
    object(source.retention, ['status', 'evidence']);
    demand(['CONFIRMED', 'UNVERIFIED', 'FORBIDDEN'].includes(source.retention.status));
    references(source.retention.evidence); references(source.evidence);
  });
  const fieldIds = new Set();
  demand(Array.isArray(input.fields) && input.fields.length === 12);
  list(input.fields, 12, field => {
    object(field, ['id', 'status', 'sourceIds', 'coveredFrom', 'coveredTo', 'granularity', 'gaps', 'evidence']);
    demand(FIELD_IDS.includes(field.id) && !fieldIds.has(field.id)); fieldIds.add(field.id);
    demand(['CONFIRMED', 'DOCUMENTED', 'UNVERIFIED', 'UNAVAILABLE'].includes(field.status));
    list(field.sourceIds, 32, supplier => { id(supplier); demand(sourceIds.has(supplier)); });
    demand(field.sourceIds.length > 0 || ['UNVERIFIED', 'UNAVAILABLE'].includes(field.status));
    for (const date of [field.coveredFrom, field.coveredTo]) if (date !== null) instant(date);
    if (field.coveredFrom !== null && field.coveredTo !== null) demand(compareInstants(field.coveredFrom, field.coveredTo) < 0, 'DATE_INVALID');
    nullableText(field.granularity); list(field.gaps, 32, gap => text(gap)); references(field.evidence);
    demand(field.evidence.length > 0 || ['UNVERIFIED', 'UNAVAILABLE'].includes(field.status));
    if (field.status === 'CONFIRMED') demand(field.granularity !== null && field.evidence.length > 0);
  });
}
// Compare canonical UTC instants at microsecond precision without floating-point fractions.
function compareInstants(a, b) {
  const normalized = s => s.slice(0, 19) + '.' + (s.slice(19, -1).replace('.', '')).padEnd(6, '0');
  const left = normalized(a), right = normalized(b); return left < right ? -1 : left > right ? 1 : 0;
}
function tagged(tag, payload) {
  const bytes = Buffer.isBuffer(payload) ? payload : Buffer.from(payload, 'utf8');
  return Buffer.concat([Buffer.from(tag + bytes.length + ':', 'ascii'), bytes]);
}
function encode(value) {
  if (value === null) return tagged('z', '');
  if (typeof value === 'boolean') return tagged('b', value ? '1' : '0');
  if (typeof value === 'string') return tagged('s', value);
  if (value === 1) return tagged('n', '1');
  if (Array.isArray(value)) return tagged('a', Buffer.concat(value.map(encode).sort(Buffer.compare)));
  return tagged('o', Buffer.concat(Object.keys(value).sort((a, b) => Buffer.compare(Buffer.from(a), Buffer.from(b)))
    .flatMap(key => [encode(key), encode(value[key])])));
}
function validateInventory(input) {
  try {
    schema(input);
    const blockers = [], availabilityDiagnostics = [];
    const add = (code, type, value) => blockers.push(type ? { code, [type]: value } : { code });
    let cost = 0n, costKnown = true;
    for (const source of input.sources.filter(s => s.selected)) {
      const block = code => add(code, 'sourceId', source.id);
      if (source.sourceVersion === null) block('SOURCE_VERSION_UNKNOWN');
      if (source.queryVersion === null) block('QUERY_VERSION_UNKNOWN');
      if (source.costUpperMicrousd === null) { costKnown = false; block('SOURCE_COST_UNKNOWN'); }
      else cost += BigInt(source.costUpperMicrousd);
      if (source.evidence.length === 0) block('SOURCE_EVIDENCE_MISSING');
      if (source.evidence.some(ref => ref.version === null)) block('SOURCE_EVIDENCE_VERSION_UNKNOWN');
      if (source.retention.status !== 'CONFIRMED') block('SOURCE_RETENTION_UNCONFIRMED');
      if (source.retention.evidence.length === 0) block('SOURCE_RETENTION_EVIDENCE_MISSING');
      if (source.retention.evidence.some(ref => ref.version === null)) block('SOURCE_RETENTION_EVIDENCE_VERSION_UNKNOWN');
    }
    if (cost > 100000000n) add('COST_ABOVE_CEILING');
    const selected = new Set(input.sources.filter(s => s.selected).map(s => s.id));
    if (selected.size === 0) add('SOURCE_SELECTION_MISSING');
    for (const field of input.fields) {
      const block = code => availabilityDiagnostics.push({ code, fieldId: field.id });
      if ((field.sourceIds.length === 0 && (field.status !== 'UNAVAILABLE' || field.gaps.length === 0)) ||
        ((field.coveredFrom === null || field.coveredTo === null || field.granularity === null) && field.gaps.length === 0))
        add('FIELD_ACCOUNTING_INCOMPLETE', 'fieldId', field.id);
      if (field.status !== 'CONFIRMED') block('FIELD_NOT_CONFIRMED');
      if (!field.sourceIds.some(supplier => selected.has(supplier))) block('FIELD_SELECTED_SOURCE_MISSING');
      if (field.coveredFrom === null || field.coveredTo === null || compareInstants(field.coveredFrom, FROM) > 0 ||
        compareInstants(field.coveredTo, TO) < 0) block('FIELD_COVERAGE_INCOMPLETE');
      if (field.granularity === null) block('FIELD_GRANULARITY_UNKNOWN');
      if (field.gaps.length > 0) block('FIELD_GAPS');
      if (field.evidence.length === 0) block('FIELD_EVIDENCE_MISSING');
      if (field.evidence.some(ref => ref.version === null)) block('FIELD_EVIDENCE_VERSION_UNKNOWN');
    }
    demand(blockers.length + availabilityDiagnostics.length <= 2048, 'BLOCKER_LIMIT');
    const compare = (a, b) => {
      const left = a.code + '\0' + (a.sourceId || a.fieldId || ''), right = b.code + '\0' + (b.sourceId || b.fieldId || '');
      return left < right ? -1 : left > right ? 1 : 0;
    };
    blockers.sort(compare); availabilityDiagnostics.sort(compare);
    const bytes = Buffer.concat([encode(SCHEMA), encode(CANONICALIZATION), encode(input)]);
    const report = { schemaVersion: SCHEMA, canonicalizationVersion: CANONICALIZATION,
      reportVersion: 'r1-d1-inventory-report-v2',
      accountingComplete: !blockers.some(b => b.code === 'FIELD_ACCOUNTING_INCOMPLETE'), availabilityDiagnostics,
      availabilityStatus: availabilityDiagnostics.length ? 'DECLARED_INCOMPLETE_UNMEASURED' : 'DECLARED_COMPLETE_UNMEASURED',
      classification: blockers.length ? 'INVENTORY_BLOCKED' : 'INVENTORY_COMPLETE',
      fingerprint: 'sha256:' + createHash('sha256').update(bytes).digest('hex'),
      selectedCostUpperMicrousd: costKnown ? cost.toString() : null, blockers, runAuthorized: false, d1Passed: false };
    demand(Buffer.byteLength(JSON.stringify(report) + '\n') <= 262144, 'REPORT_LIMIT');
    return report;
  } catch (code) { return invalid(INVALID_CODES.has(code) ? code : 'SCHEMA_INVALID'); }
}
module.exports = { validateInventory };
