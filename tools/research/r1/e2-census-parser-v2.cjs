'use strict';
// Prospective offline grammar only. No historical imports, source transport or D1 admission.
const crypto = require('node:crypto');
const PROGRAM = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA';
const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
const need = (ok, code = 'RESPONSE_INVALID') => { if (!ok) throw Error(code); };
const digest = bytes => 'sha256:' + crypto.createHash('sha256').update(bytes).digest('hex');
const fields = Object.freeze([
  { name: 'discriminator', type: 'bytes[8]', bytes: 8 }, { name: 'index', type: 'u16', bytes: 2 },
  { name: 'base_amount_in', type: 'u64', bytes: 8 }, { name: 'quote_amount_in', type: 'u64', bytes: 8 },
  { name: 'coin_creator', type: 'pubkey', bytes: 32 }, { name: 'is_mayhem_mode', type: 'bool', bytes: 1 },
  { name: 'is_cashback_coin', type: 'OptionBool=struct(bool)', bytes: 1 }
].map(Object.freeze));
const decodedBytes = fields.reduce((n, f) => n + f.bytes, 0);
function encodedCeiling(bytes) { let capacity = 1n, chars = 0; const ceiling = 256n ** BigInt(bytes);
  while (capacity < ceiling) { capacity *= 58n; chars++; } return Math.max(chars, bytes); }
const LAYOUT = Object.freeze({ version: 'pumpswap-create-pool-idl-82dacacf-v1',
  revision: '82dacacf15ca93dc0444ab38714f2226210a0a3d',
  sha256: 'sha256:5a15060f412974e53068bae7e89aa6004defbb70ef0c56e3902ce75d124accb6',
  source: 'https://raw.githubusercontent.com/pump-fun/pump-public-docs/82dacacf15ca93dc0444ab38714f2226210a0a3d/idl/pump_amm.json',
  discriminator: 'e992d18ecf6840bc', fields, decodedBytes, encodedCharacters: encodedCeiling(decodedBytes),
  accounts: 18, deployment: 'UNVERIFIED' });
const LIMITS = Object.freeze({ pageBytes: 64000000, pageRows: 100000, pageInstructions: 100000,
  instructions: 1000000, creations: 100000, signatures: 100000, headers: 100000, pages: 16000,
  stateBytes: 128000000, diagnostics: 1000, path: 64, accounts: 128, timeMs: 1800000 });
function boundedLimits(overrides = {}, defaults = LIMITS) { need(overrides && typeof overrides === 'object');
  const result = { ...defaults }; for (const [k, v] of Object.entries(overrides)) {
    need(Object.hasOwn(defaults, k) && Number.isSafeInteger(v) && v > 0 && v <= defaults[k], 'LIMIT_INVALID'); result[k] = v;
  } return Object.freeze(result); }
class NumericToken { constructor(text) { this.text = text; Object.freeze(this); } }
// A small bounded JSON reader preserves lexemes and rejects duplicate decoded keys.
function parseExact(bytes, { maxBytes = 64000000, maxDepth = 64, maxNodes = 1000000, check = () => {} } = {}) {
  need(Buffer.isBuffer(bytes) && bytes.length <= maxBytes, 'INPUT_LIMIT');
  const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes); let at = 0, nodes = 0;
  const ws = () => { while (/^[\x20\x09\x0a\x0d]$/.test(text[at] ?? '')) at++; };
  function str() { const start = at++; need(text[start] === '"'); let closed = false;
    while (at < text.length) { const c = text[at++]; if (c === '\\') at++; else if (c === '"') { closed = true; break; } }
    need(closed); return JSON.parse(text.slice(start, at)); }
  function value(depth) { need(depth <= maxDepth && ++nodes <= maxNodes, 'INPUT_LIMIT'); if (!(nodes % 1024)) check(); ws(); const c = text[at];
    if (c === '"') return str();
    if (c === '{') { at++; const o = Object.create(null), keys = new Set(); ws(); if (text[at] === '}') { at++; return o; }
      while (true) { ws(); const k = str(); need(!keys.has(k)); keys.add(k); ws(); need(text[at++] === ':'); o[k] = value(depth + 1); ws();
        const end = text[at++]; if (end === '}') return o; need(end === ','); } }
    if (c === '[') { at++; const a = []; ws(); if (text[at] === ']') { at++; return a; }
      while (true) { a.push(value(depth + 1)); ws(); const end = text[at++]; if (end === ']') return a; need(end === ','); } }
    for (const [token, v] of [['true', true], ['false', false], ['null', null]]) if (text.startsWith(token, at)) { at += token.length; return v; }
    const match = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/.exec(text.slice(at)); need(match); at += match[0].length;
    return new NumericToken(match[0]);
  }
  const out = value(0); ws(); need(at === text.length); check(); return out;
}
function stringifyExact(v) {
  if (v instanceof NumericToken) return v.text;
  if (Array.isArray(v)) return '[' + v.map(stringifyExact).join(',') + ']';
  if (v && typeof v === 'object') return '{' + Object.keys(v).map(k => JSON.stringify(k) + ':' + stringifyExact(v[k])).join(',') + '}';
  need(v === null || typeof v === 'string' || typeof v === 'boolean' || Number.isSafeInteger(v)); return JSON.stringify(v);
}
function canonical(v) { const atom = (tag, s) => tag + Buffer.byteLength(s) + ':' + s;
  if (v instanceof NumericToken) return atom('n', v.text);
  if (v === null) return 'z0:';
  if (typeof v === 'string') return atom('s', v);
  if (typeof v === 'boolean') return atom('b', v ? '1' : '0');
  if (Number.isSafeInteger(v)) return atom('n', String(v));
  if (Array.isArray(v)) return atom('a', v.map(canonical).join(''));
  need(v && typeof v === 'object'); return atom('o', Object.keys(v).sort().map(k => atom('k', k) + canonical(v[k])).join(''));
}
function uint(v, max = Number.MAX_SAFE_INTEGER) { const text = v instanceof NumericToken ? v.text : typeof v === 'number' ? String(v) : '';
  need(/^(0|[1-9][0-9]*)$/.test(text) && BigInt(text) <= BigInt(max)); return Number(text); }
function decode(text, maxChars, maxBytes, code = 'RESPONSE_INVALID') {
  // Both encoded work and decoded allocation are bounded before the BigInt loop.
  need(typeof text === 'string' && text.length > 0 && text.length <= maxChars, code);
  let n = 0n, zeros = 0; while (text[zeros] === '1') zeros++;
  need(zeros <= maxBytes, code);
  for (const ch of text) { const digit = ALPHABET.indexOf(ch); need(digit >= 0); n = n * 58n + BigInt(digit); }
  const hex = n.toString(16), bodyBytes = n ? Math.ceil(hex.length / 2) : 0;
  need(zeros + bodyBytes <= maxBytes, code);
  return Buffer.concat([Buffer.alloc(zeros), n ? Buffer.from(hex.padStart(bodyBytes * 2, '0'), 'hex') : Buffer.alloc(0)]);
}
const address = v => { need(decode(v, 44, 32).length === 32); return v; };
const signature = v => { need(decode(v, 88, 64).length === 64); return v; };
function numericPath(v, limit = 64) { need(Array.isArray(v) && v.length > 0 && v.length <= limit); return v.map(n => uint(n)); }
function comparePath(a, b) { for (let i = 0; i < Math.min(a.length, b.length); i++) if (a[i] !== b[i]) return a[i] < b[i] ? -1 : 1; return a.length - b.length; }
function compare(a, b) { return a.slot - b.slot || a.transactionIndex - b.transactionIndex || comparePath(a.instructionPath, b.instructionPath)
  || (a.signature < b.signature ? -1 : a.signature > b.signature ? 1 : 0); }
function createParser(options = {}) {
  const limits = boundedLimits(options.limits), now = options.now ?? (() => performance.now()), started = now();
  const check = () => { const t = now(); need(Number.isFinite(t) && t >= started && t - started <= limits.timeMs, 'TIME_LIMIT'); };
  let state = { signatures: new Map(), transactions: new Map(), paths: new Map(), headers: new Map(), parentHashes: new Map(), creations: new Map(), pages: new Set(),
    instructions: 0, unknown: 0, success: 0, depth: {}, reasons: {}, bytes: 0 };
  const snapshot = () => ({ version: 'e2-census-parser-v2', canonicalizationVersion: 'e2-census-c14n-v2', limits, layout: LAYOUT,
    pages: state.pages.size, instructions: state.instructions,
    unknown: state.unknown, success: state.success, bytes: state.bytes, depth: { ...state.depth }, reasons: { ...state.reasons },
    signatures: [...state.signatures].sort(), transactions: [...state.transactions].sort(), paths: [...state.paths].sort(), headers: [...state.headers].sort(),
    parentHashes: [...state.parentHashes].sort(),
    creations: [...state.creations.values()].sort(compare).map(c => ({ ...c, instructionPath: [...c.instructionPath] })) });
  function admit(bytes) { try {
    check(); need(Buffer.isBuffer(bytes) && bytes.length <= limits.pageBytes, 'INPUT_LIMIT');
    const rawHash = (options.pageDigest ?? digest)(bytes); need(/^sha256:[0-9a-f]{64}$/.test(rawHash));
    const next = { ...state, signatures: new Map(state.signatures), transactions: new Map(state.transactions), paths: new Map(state.paths), headers: new Map(state.headers),
      parentHashes: new Map(state.parentHashes),
      creations: new Map(state.creations), pages: new Set(state.pages), depth: { ...state.depth }, reasons: { ...state.reasons } };
    let lineOrdinal = 0, rows = 0, pageInstructions = 0, priorHeader = null;
    const creations = new Map(), counts = { instructions: 0, success: 0, unknown: 0, depth: {}, reasons: {} };
    const insert = (map, key, content, maximum) => { if (map.has(key)) { need(map.get(key) === content, 'IMMUTABLE_CONFLICT'); return false; }
      need(map.size < maximum, 'STATE_LIMIT'); next.bytes += Buffer.byteLength(key) + Buffer.byteLength(content) + 128;
      need(next.bytes <= limits.stateBytes, 'STATE_LIMIT'); map.set(key, content); return true; };
    const bump = (map, key) => { map[key] = (map[key] ?? 0) + 1; };
    // Byte slicing avoids making a second full-page UTF-8 string/split array.
    for (let at = 0; at < bytes.length;) { check(); const end = bytes.indexOf(10, at), stop = end < 0 ? bytes.length : end;
      const line = bytes.subarray(at, stop); at = stop + 1; lineOrdinal++; if (!line.toString('utf8').trim()) continue;
      need(++rows <= limits.pageRows, 'ROW_LIMIT'); const b = parseExact(line, { maxBytes: limits.pageBytes, check });
      need(b && !Array.isArray(b) && b.header); const h = b.header;
      const slot = uint(h.number), timestamp = uint(h.timestamp), parentNumber = uint(h.parentNumber); need(parentNumber < slot);
      address(h.hash); address(h.parentHash);
      if (priorHeader) need(slot > priorHeader.slot && timestamp >= priorHeader.timestamp);
      const parent = next.headers.get(String(parentNumber));
      if (parent) need(parseExact(Buffer.from(parent)).hash === h.parentHash, 'IMMUTABLE_CONFLICT');
      const childReference = next.parentHashes.get(String(slot));
      if (childReference) need(childReference === h.hash, 'IMMUTABLE_CONFLICT');
      // One bounded reverse reference per parent slot also validates child-before-parent arrival.
      insert(next.parentHashes, String(parentNumber), h.parentHash, limits.headers);
      // Header storage is exact JSON for ancestry access; equality remains exact and property-order independent.
      const headerJson = stringifyExact(Object.fromEntries(Object.keys(h).sort().map(k => [k, h[k]])));
      insert(next.headers, String(slot), headerJson, limits.headers); priorHeader = { slot, timestamp };
      const txs = b.transactions ?? [], instructions = b.instructions ?? []; need(Array.isArray(txs) && Array.isArray(instructions));
      need(txs.length <= limits.signatures && (pageInstructions += instructions.length) <= limits.pageInstructions, 'ROW_LIMIT');
      const transactions = new Map();
      for (const tx of txs) { check(); const index = uint(tx.transactionIndex); need(!transactions.has(index)); transactions.set(index, tx);
        insert(next.transactions, canonical([slot, index]), canonical(tx), limits.signatures);
        if (tx.signatures !== undefined) { need(Array.isArray(tx.signatures) && tx.signatures.length <= 64);
          const seen = new Set(); for (const s of tx.signatures) { signature(s); need(!seen.has(s)); seen.add(s);
            insert(next.signatures, s, canonical({ slot, transactionIndex: index, transaction: tx }), limits.signatures); } } }
      const localPaths = new Map();
      for (const ins of instructions) { check(); const index = uint(ins.transactionIndex), ip = numericPath(ins.instructionAddress, limits.path);
        // Never decode instruction data before its exact supported encoded ceiling.
        need(typeof ins.data === 'string' && ins.data.length <= LAYOUT.encodedCharacters, 'INSTRUCTION_DATA_LIMIT');
        need(Array.isArray(ins.accounts) && ins.accounts.length <= limits.accounts); ins.accounts.forEach(address);
        const tx = transactions.get(index), localKey = canonical([index, ip]), contents = canonical(ins);
        if (localPaths.has(localKey)) { need(localPaths.get(localKey) === contents, 'IMMUTABLE_CONFLICT'); continue; } localPaths.set(localKey, contents);
        const pathKey = canonical([tx?.signatures?.[0] ?? null, slot, index, ip]);
        const fresh = insert(next.paths, pathKey, contents, limits.instructions);
        const data = decode(ins.data, LAYOUT.encodedCharacters, LAYOUT.decodedBytes, 'INSTRUCTION_DATA_LIMIT');
        need(data.length === LAYOUT.decodedBytes, 'UNSUPPORTED_LAYOUT');
        let reason = null;
        if (!tx?.signatures?.length) reason = 'LINK_UNKNOWN';
        else if (ins.isCommitted !== true || !Object.hasOwn(ins, 'error') || ins.error !== null || !Object.hasOwn(tx, 'err') || tx.err !== null) reason = 'STATE_UNKNOWN';
        else if (ins.programId !== PROGRAM) reason = 'PROGRAM_UNKNOWN';
        else if (data.subarray(0, 8).toString('hex') !== LAYOUT.discriminator) reason = 'DISCRIMINATOR_UNKNOWN';
        else if (ins.accounts.length !== LAYOUT.accounts || data[58] > 1 || data[59] > 1) reason = 'LAYOUT_UNKNOWN';
        if (fresh) { next.instructions++; counts.instructions++; bump(next.depth, String(ip.length - 1)); bump(counts.depth, String(ip.length - 1));
          if (reason) { next.unknown++; counts.unknown++; bump(next.reasons, reason); bump(counts.reasons, reason); }
          else { next.success++; counts.success++; } }
        need(next.instructions <= limits.instructions && Object.keys(next.depth).length + Object.keys(next.reasons).length <= limits.diagnostics, 'STATE_LIMIT');
        if (reason) continue;
        const c = { status: 'OBSERVED_DECLARED_CREATE_POOL', slot, timestamp, signature: tx.signatures[0], transactionIndex: index,
          instructionPath: ip, cpiDepth: ip.length - 1, pool: ins.accounts[0], globalConfig: ins.accounts[1], creator: ins.accounts[2],
          baseMint: ins.accounts[3], quoteMint: ins.accounts[4], accountCount: ins.accounts.length, dataLength: data.length,
          rawHash, lineOrdinal, layoutVersion: LAYOUT.version, layoutApplicability: 'UNVERIFIED' };
        if (!next.creations.has(pathKey)) { need(next.creations.size < limits.creations, 'CREATION_LIMIT');
          next.bytes += Buffer.byteLength(pathKey) + Buffer.byteLength(stringifyExact(c)) + 128;
          need(next.bytes <= limits.stateBytes, 'STATE_LIMIT'); next.creations.set(pathKey, c); }
        creations.set(pathKey, c);
      }
    }
    need(rows > 0, 'NO_PROGRESS'); if (!next.pages.has(rawHash)) { need(next.pages.size < limits.pages, 'PAGE_LIMIT');
      next.bytes += Buffer.byteLength(rawHash); need(next.bytes <= limits.stateBytes, 'STATE_LIMIT'); next.pages.add(rawHash); }
    const returnedCreations = [...creations.values()].sort(compare).map(c => ({ ...c, instructionPath: [...c.instructionPath] }));
    check(); state = next; // The sole publication point; all validation and aggregate limits precede it.
    return { version: 'e2-census-parser-v2', canonicalizationVersion: 'e2-census-c14n-v2', limits,
      code: null, rawHash, creations: returnedCreations, counts, layout: LAYOUT,
      d1Evidence: false, cohortAdmitted: false, authoritativeCensusComplete: false };
  } catch (e) { return { code: e.message, creations: [], counts: {}, d1Evidence: false, cohortAdmitted: false }; } }
  return Object.freeze({ admit, snapshot });
}
module.exports = { createParser, LAYOUT, LIMITS, boundedLimits, digest, parseExact, stringifyExact, canonical,
  NumericToken, uint, decode, address, signature, numericPath, comparePath, compare };
