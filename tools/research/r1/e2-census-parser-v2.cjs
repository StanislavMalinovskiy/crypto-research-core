'use strict';
// Prospective offline grammar only. No historical imports, source transport or D1 admission.
const { NumericToken, parseExact, uint, decode, address, signature, numericPath, boundedLimits: validateLimits } = require('./lib/v1/parsing.cjs');
const { digest, encodedCeiling, stringifyExact, canonical, comparePath, compare } = require('./lib/v1/canonicalization.cjs');
const PROGRAM = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA';
const need = (ok, code = 'RESPONSE_INVALID') => { if (!ok) throw Error(code); };
const fields = Object.freeze([
  { name: 'discriminator', type: 'bytes[8]', bytes: 8 }, { name: 'index', type: 'u16', bytes: 2 },
  { name: 'base_amount_in', type: 'u64', bytes: 8 }, { name: 'quote_amount_in', type: 'u64', bytes: 8 },
  { name: 'coin_creator', type: 'pubkey', bytes: 32 }, { name: 'is_mayhem_mode', type: 'bool', bytes: 1 },
  { name: 'is_cashback_coin', type: 'OptionBool=struct(bool)', bytes: 1 }
].map(Object.freeze));
const decodedBytes = fields.reduce((n, f) => n + f.bytes, 0);
const LAYOUT = Object.freeze({ version: 'pumpswap-create-pool-idl-82dacacf-v1',
  revision: '82dacacf15ca93dc0444ab38714f2226210a0a3d',
  sha256: 'sha256:5a15060f412974e53068bae7e89aa6004defbb70ef0c56e3902ce75d124accb6',
  source: 'https://raw.githubusercontent.com/pump-fun/pump-public-docs/82dacacf15ca93dc0444ab38714f2226210a0a3d/idl/pump_amm.json',
  discriminator: 'e992d18ecf6840bc', fields, decodedBytes, encodedCharacters: encodedCeiling(decodedBytes),
  accounts: 18, deployment: 'UNVERIFIED' });
const LIMITS = Object.freeze({ pageBytes: 64000000, pageRows: 100000, pageInstructions: 100000,
  instructions: 1000000, creations: 100000, signatures: 100000, headers: 100000, pages: 16000,
  stateBytes: 128000000, diagnostics: 1000, path: 64, accounts: 128, timeMs: 1800000 });
function boundedLimits(overrides = {}, defaults = LIMITS) { return validateLimits(overrides, defaults); }
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
