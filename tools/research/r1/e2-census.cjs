'use strict';
const fs = require('node:fs'), path = require('node:path'), https = require('node:https');
const { execFileSync } = require('node:child_process');
const { parse, canonical, digest, fingerprint } = require('./exploratory-probe.cjs');
const OUTPUT = 'C:\\crypto-research-evidence\\r1-e2\\exploratory-census-v1';
const BASE = 'https://portal.sqd.dev/datasets/solana-mainnet/', PROGRAM = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA';
const DATES = [1775001600, 1777593600, 1780272000, 1782604800], ENVELOPE = [410195947, 429980965];
const EXPIRY = '2026-10-17T11:42:35.392Z', ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
const FLAGS = { classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false, cohortAdmitted: false,
  authoritativeCensusComplete: false, fullD1UpperBound: null, fullD1Fits: null };
const LIMITS = { distinct: 300, dataQueries: 292, attempts: 900, retries: 600, response: 64000000, received: 4000000000,
  retained: 4100000000, metadata: 4000000, stdout: 1000000, creationBytes: 64000000, creations: 100000,
  pageInstructions: 100000, instructions: 1000000, diagnostics: 1000, deadline: 60000, spacing: 250,
  sourceMs: 6900000, totalMs: 7200000, free: 30000000000, aggregate: 50000000000 };
const SEALED_ROOT = 'C:\\crypto-research-evidence\\r1-d1\\exploratory-sqd-v2';
const SEAL = { manifestHash: 'sha256:5e8dc296b2ce20c9a20e7b9728c202278153516d702bdbbe01762bc45cffb224', headers: [
  { name: '0003.raw', bytes: 194, hash: 'sha256:54bed3c1978a2bd6db5a04d9da81520151c92289dc67dbc6e8cead170ef326c0', number: 410195947, timestamp: 1775001600 },
  { name: '0063.raw', bytes: 194, hash: 'sha256:5c6f4a3435f63a1e57d22c9b01373e6deaf91fe7d09fb62d6b8e64c56ef3473e', number: 429980965, timestamp: 1782864000 }] };
const CONFIG = { version: 'e2-census-v1', canonicalVersion: 'e2-census-c14n-v1', base: BASE, program: PROGRAM,
  dates: DATES, envelope: ENVELOPE, output: OUTPUT, limits: LIMITS, seal: SEAL, expiry: EXPIRY, retention: 'UNVERIFIED',
  schema: { revision: '26cffab2a19d47f27d5d4d661b2b6d21a220b109', version: 'solana-v2', deployment: 'UNVERIFIED',
    references: ['metadata/solana.yaml', 'src/query/parse.rs', 'src/query/plan.rs', 'src/output/block_index.rs', 'src/output/assembly.rs'] },
  idl: { revision: '82dacacf15ca93dc0444ab38714f2226210a0a3d', hash: 'sha256:5a15060f412974e53068bae7e89aa6004defbb70ef0c56e3902ce75d124accb6',
    prefix: ['pool', 'global_config', 'creator', 'base_mint', 'quote_mint'], discriminator: 'e992d18ecf6840bc', applicability: 'UNVERIFIED' },
  helperHash: 'sha256:8f8790026241de1bf3bcded6fef3fe99a6b954af34931184d04897ca393b0a47', fallback: 'NOT_RUN', cashMicrousd: '0' };
const CODES = new Set(['DISABLED', 'ARGUMENTS_INVALID', 'UNSAFE_PATH', 'ROOT_EXISTS', 'EXPIRY', 'RUNTIME_INVALID', 'SOURCE_IDENTITY_INVALID',
  'INTEGRITY_ERROR', 'RESPONSE_INVALID', 'IMMUTABLE_CONFLICT', 'NO_PROGRESS', 'HTTP_ERROR', 'HTTP_529_EXHAUSTED', 'PARTIAL_RESPONSE',
  'TIMEOUT', 'NETWORK_ERROR', 'RESPONSE_LIMIT', 'TIME_LIMIT', 'FREE_SPACE_LIMIT', 'DISK_LIMIT', 'RECEIVED_LIMIT', 'ATTEMPT_LIMIT',
  'DISTINCT_LIMIT', 'DATA_QUERY_LIMIT', 'INSTRUCTION_LIMIT', 'CREATION_LIMIT', 'METADATA_LIMIT', 'STORAGE_ERROR']);
const need = (ok, code = 'RESPONSE_INVALID') => { if (!ok) throw Error(code); };
const safe = (e, fallback = 'INTEGRITY_ERROR') => CODES.has(e?.message ?? e) ? e.message ?? e : fallback;
const json = x => Buffer.from(JSON.stringify(x) + '\n');
const error = code => ({ ...FLAGS, code, status: 'INCOMPLETE', runBudget: 'UNMEASURED' });
const uint = (x, code = 'RESPONSE_INVALID') => { need(typeof x === 'number' && Number.isSafeInteger(x) && x >= 0, code); return x; };
function integerLexeme(value, token) { uint(value); need(typeof token === 'string' && /^(0|[1-9][0-9]*)$/.test(token) && String(value) === token); return value; }
function decode(text, max = 128) {
  need(typeof text === 'string' && text.length > 0 && text.length <= max && /^[1-9A-HJ-NP-Za-km-z]+$/.test(text));
  let value = 0n, zeros = 0; for (const ch of text) value = value * 58n + BigInt(ALPHABET.indexOf(ch));
  while (zeros < text.length && text[zeros] === '1') zeros++;
  const body = value ? Buffer.from(value.toString(16).padStart(Math.ceil(value.toString(16).length / 2) * 2, '0'), 'hex') : Buffer.alloc(0);
  return Buffer.concat([Buffer.alloc(zeros), body]);
}
const address = x => { need(decode(x, 44).length === 32); return x; };
const signature = x => { need(decode(x, 88).length === 64); return x; };
function boundsOk(bounds) { need(Array.isArray(bounds) && bounds.length === 4); bounds.forEach((n, i) => {
  uint(n); need(n >= ENVELOPE[0] && n < ENVELOPE[1] && (i === 0 || n > bounds[i - 1])); }); }
const fields = names => Object.fromEntries(names.split(' ').map(k => [k, true]));
function query(kind, index, current, bounds) {
  need(Number.isSafeInteger(index) && index >= 0 && index < (kind === 'data' ? 3 : 4));
  if (kind === 'resolver') return { kind, index, method: 'GET', url: `${BASE}timestamps/${DATES[index]}/block` };
  need(['header', 'data'].includes(kind)); boundsOk(bounds);
  const slot = kind === 'header' ? bounds[index] - (index === 3 ? 1 : 0) : uint(current);
  need(slot >= bounds[kind === 'data' ? index : 0] && slot < bounds[kind === 'data' ? index + 1 : 3]);
  const body = { type: 'solana', fromBlock: slot, toBlock: kind === 'header' ? slot : bounds[index + 1] - 1,
    includeAllBlocks: kind === 'header', fields: { block: fields('number hash parentNumber parentHash timestamp') } };
  if (kind === 'data') { body.fields.transaction = fields('transactionIndex signatures err');
    body.fields.instruction = fields('transactionIndex instructionAddress programId accounts data isCommitted error');
    body.instructions = [{ programId: [PROGRAM], d8: ['0xe992d18ecf6840bc'], transaction: true }]; }
  return { kind, index, slot, method: 'POST', url: BASE + 'finalized-stream', body };
}
function parsePair(bytes) { return [parse(bytes, false), parse(bytes, true)]; }
function validatedHeader(h, lex, q) {
  need(h && lex && typeof h === 'object' && !Array.isArray(h));
  const result = { number: integerLexeme(h.number, lex.number), hash: address(h.hash), parentNumber: integerLexeme(h.parentNumber, lex.parentNumber),
    parentHash: address(h.parentHash), timestamp: integerLexeme(h.timestamp, lex.timestamp) };
  need(result.parentNumber < result.number && result.number >= q.body.fromBlock && result.number <= q.body.toBlock);
  const start = q.index === 3 ? DATES[2] : DATES[q.index], end = q.kind === 'data' ? DATES[q.index + 1] : DATES[Math.min(q.index + 1, 3)];
  need(result.timestamp >= start && result.timestamp < end); return result;
}
function compare(a, b) { return a.slot - b.slot || a.transactionIndex - b.transactionIndex || comparePath(a.instructionPath, b.instructionPath) || a.signature.localeCompare(b.signature, 'en'); }
function comparePath(a, b) { for (let i = 0; i < Math.min(a.length, b.length); i++) if (a[i] !== b[i]) return a[i] - b[i]; return a.length - b.length; }
const locator = x => `${x.slot}:${x.transactionIndex}:${x.instructionPath.join('.')}:${x.signature}`;
function admit(bytes, q, bounds) {
  try {
    need(Buffer.isBuffer(bytes) && bytes.length <= LIMITS.response && q && ['resolver', 'header', 'data'].includes(q.kind));
    if (q.kind === 'resolver') { need(canonical(q) === canonical(query('resolver', q.index))); const [v, lex] = parsePair(bytes);
      const slot = integerLexeme(v?.block_number, lex?.block_number); need(slot >= ENVELOPE[0] && slot < ENVELOPE[1]);
      return { code: null, slot, creations: [], headers: [], counts: {} }; }
    need(canonical(q) === canonical(query(q.kind, q.index, q.slot, bounds)));
    const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes), lines = text.split('\n');
    const blocks = []; for (let i = 0; i < lines.length; i++) if (lines[i].trim()) { const [v, lex] = parsePair(Buffer.from(lines[i]));
      blocks.push({ v, lex, lineOrdinal: i + 1, h: validatedHeader(v.header, lex.header, q) }); }
    need(blocks.length > 0, 'NO_PROGRESS'); if (q.kind === 'header') need(blocks.length === 1);
    for (let i = 1; i < blocks.length; i++) { need(blocks[i].h.number > blocks[i - 1].h.number && blocks[i].h.timestamp >= blocks[i - 1].h.timestamp);
      if (blocks[i].h.parentNumber === blocks[i - 1].h.number) need(blocks[i].h.parentHash === blocks[i - 1].h.hash, 'IMMUTABLE_CONFLICT'); }
    if (q.kind === 'header') return { code: null, header: blocks[0].h, headers: [blocks[0].h], creations: [], counts: {} };
    const creations = new Map(), matchingHeaders = new Set(), counts = { instructions: 0, unknown: 0, success: 0, topLevel: 0, cpi: 0, depth: {}, reasons: {} }; let lastMatched = null;
    for (const { v, lex, h, lineOrdinal } of blocks) {
      const txs = v.transactions ?? [], instructions = v.instructions ?? []; need(Array.isArray(txs) && Array.isArray(instructions));
      need(counts.instructions + instructions.length <= LIMITS.pageInstructions, 'INSTRUCTION_LIMIT');
      const transactions = new Map(), ixSeen = new Map();
      for (let i = 0; i < txs.length; i++) { const tx = txs[i], index = integerLexeme(tx?.transactionIndex, lex.transactions[i]?.transactionIndex);
        need(!transactions.has(index)); if (tx.signatures !== undefined) { need(Array.isArray(tx.signatures)); tx.signatures.forEach(signature); }
        transactions.set(index, tx); }
      for (let i = 0; i < instructions.length; i++) {
        const ins = instructions[i], il = lex.instructions[i], index = integerLexeme(ins?.transactionIndex, il?.transactionIndex);
        need(Array.isArray(ins.instructionAddress) && ins.instructionAddress.length > 0);
        const ip = ins.instructionAddress.map((n, k) => integerLexeme(n, il.instructionAddress[k]));
        const id = `${index}:${ip.join('.')}`, contents = canonical(ins);
        if (ixSeen.has(id)) { need(ixSeen.get(id) === contents, 'IMMUTABLE_CONFLICT'); continue; } ixSeen.set(id, contents);
        counts.instructions++; const depth = ip.length - 1; counts.depth[depth] = (counts.depth[depth] ?? 0) + 1;
        need(Object.keys(counts.depth).length <= LIMITS.diagnostics, 'INSTRUCTION_LIMIT'); counts[depth ? 'cpi' : 'topLevel']++;
        lastMatched = h.number; matchingHeaders.add(h.number); const tx = transactions.get(index); let reason = null, data = null;
        if (!tx || !Array.isArray(tx.signatures) || tx.signatures.length === 0) reason = 'LINK_UNKNOWN';
        else if (ins.isCommitted !== true || !Object.hasOwn(ins, 'error') || ins.error !== null || !Object.hasOwn(tx, 'err') || tx.err !== null) reason = 'STATE_UNKNOWN';
        else if (ins.programId !== PROGRAM) reason = 'PROGRAM_UNKNOWN';
        else { try { data = decode(ins.data, 64000000); if (data.length < 8 || data.subarray(0, 8).toString('hex') !== CONFIG.idl.discriminator) reason = 'DISCRIMINATOR_UNKNOWN';
          else { need(Array.isArray(ins.accounts) && ins.accounts.length >= 5); ins.accounts.slice(0, 5).forEach(address); } }
          catch { reason = 'LAYOUT_UNKNOWN'; } }
        if (reason) { counts.unknown++; counts.reasons[reason] = (counts.reasons[reason] ?? 0) + 1; continue; }
        counts.success++; const creation = { status: 'OBSERVED_DECLARED_CREATE_POOL', slot: h.number, timestamp: h.timestamp,
          signature: tx.signatures[0], transactionIndex: index, instructionPath: ip, cpiDepth: depth, pool: ins.accounts[0],
          globalConfig: ins.accounts[1], creator: ins.accounts[2], baseMint: ins.accounts[3], quoteMint: ins.accounts[4],
          accountCount: ins.accounts.length, dataLength: data.length, rawHash: digest(bytes), lineOrdinal, layoutApplicability: 'UNVERIFIED' };
        const loc = locator(creation); if (creations.has(loc)) need(canonical(creations.get(loc)) === canonical(creation), 'IMMUTABLE_CONFLICT');
        else creations.set(loc, creation);
      }
    }
    return { code: null, headers: blocks.map(b => b.h), lastScanned: blocks.at(-1).h.number, lastMatched,
      creations: [...creations.values()].sort(compare), matchingHeaders: matchingHeaders.size, counts };
  } catch (e) { return { code: safe(e, 'RESPONSE_INVALID'), creations: [], headers: [], counts: {} }; }
}
function ancestors(file) { let current = path.dirname(file); while (true) { if (fs.existsSync(current)) {
  const stat = fs.lstatSync(current); need(stat.isDirectory() && !stat.isSymbolicLink() && fs.realpathSync.native(current).toLowerCase() === current.toLowerCase(), 'UNSAFE_PATH'); }
  const parent = path.dirname(current); if (parent === current) break; current = parent; } }
function readBounded(file, limit) { ancestors(file); const stat = fs.lstatSync(file);
  need(stat.isFile() && !stat.isSymbolicLink() && stat.size <= limit, 'INTEGRITY_ERROR'); const fd = fs.openSync(file, 'r');
  try { const size = fs.fstatSync(fd).size; need(size === stat.size, 'INTEGRITY_ERROR'); const out = Buffer.alloc(size + 1); let at = 0;
    while (at < out.length) { const n = fs.readSync(fd, out, at, out.length - at, null); if (!n) break; at += n; }
    need(at === size, 'INTEGRITY_ERROR'); return out.subarray(0, at); } finally { fs.closeSync(fd); } }
function verifySealed() {
  const manifest = readBounded(path.join(SEALED_ROOT, 'manifest.json'), LIMITS.metadata); need(digest(manifest) === SEAL.manifestHash, 'INTEGRITY_ERROR');
  const m = parse(manifest, false); need(Array.isArray(m.records), 'INTEGRITY_ERROR');
  for (const pin of SEAL.headers) { const matches = m.records.filter(r => r.raw === pin.name);
    need(matches.length === 1 && matches[0].hash === pin.hash && matches[0].size === pin.bytes && matches[0].code === null
      && matches[0].request.kind === 'header' && matches[0].request.from === pin.number && matches[0].request.to === pin.number
      && matches[0].request.body?.fromBlock === pin.number && matches[0].request.body?.toBlock === pin.number, 'INTEGRITY_ERROR');
    const raw = readBounded(path.join(SEALED_ROOT, pin.name), pin.bytes); need(raw.length === pin.bytes && digest(raw) === pin.hash, 'INTEGRITY_ERROR');
    const [v, lex] = parsePair(raw); need(integerLexeme(v.header?.number, lex.header?.number) === pin.number
      && integerLexeme(v.header?.timestamp, lex.header?.timestamp) === pin.timestamp, 'INTEGRITY_ERROR');
    address(v.header.hash); address(v.header.parentHash); need(integerLexeme(v.header.parentNumber, lex.header.parentNumber) < pin.number, 'INTEGRITY_ERROR'); }
  return { status: 'VERIFIED', manifestHash: SEAL.manifestHash, headers: SEAL.headers };
}
function provenance(deps) {
  const cwd = path.resolve(__dirname, '../../..'), source = deps.source ?? {
    commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd, encoding: 'utf8', timeout: 5000 }).trim(),
    dirty: execFileSync('git', ['status', '--porcelain'], { cwd, encoding: 'utf8', timeout: 5000 }).trim().length > 0 };
  need(/^[a-f0-9]{40}$/.test(source.commit) && typeof source.dirty === 'boolean', 'SOURCE_IDENTITY_INVALID');
  const runtime = deps.runtime ?? process.version; need(/^v(24|25)\.[0-9]+\.[0-9]+$/.test(runtime), 'RUNTIME_INVALID');
  const scripts = {}; for (const name of ['exploratory-probe.cjs', 'e2-census.cjs', 'e2-census-cli.cjs']) scripts[name] = digest(readBounded(path.join(__dirname, name), 1000000));
  need(scripts['exploratory-probe.cjs'] === CONFIG.helperHash, 'INTEGRITY_ERROR'); return { source, runtime, scripts };
}
function fileStore() {
  const check = () => { need(path.resolve(OUTPUT) === OUTPUT, 'UNSAFE_PATH'); ancestors(path.join(OUTPUT, 'manifest.json')); };
  const file = name => { need(['attempt.json', 'manifest.json', 'summary.json', 'creations.jsonl'].includes(name) || /^[0-9]{4}\.raw$/.test(name), 'UNSAFE_PATH'); check(); return path.join(OUTPUT, name); };
  return { absent() { check(); return !fs.existsSync(OUTPUT); },
    free() { check(); let dir = OUTPUT; while (!fs.existsSync(dir)) dir = path.dirname(dir);
      const stat = fs.statfsSync(dir, { bigint: true }), bytes = stat.bavail * stat.bsize; need(bytes <= BigInt(Number.MAX_SAFE_INTEGER), 'FREE_SPACE_LIMIT'); return Number(bytes); },
    create() { check(); need(!fs.existsSync(OUTPUT), 'ROOT_EXISTS'); fs.mkdirSync(path.dirname(OUTPUT), { recursive: true }); check(); fs.mkdirSync(OUTPUT); },
    write(name, bytes) { const fd = fs.openSync(file(name), 'wx'); try { let at = 0;
      while (at < bytes.length) { const n = fs.writeSync(fd, bytes, at, bytes.length - at); need(n > 0, 'STORAGE_ERROR'); at += n; } fs.fsyncSync(fd); } finally { fs.closeSync(fd); } },
    read(name) { return readBounded(file(name), name.endsWith('.raw') || name === 'creations.jsonl' ? LIMITS.response : LIMITS.metadata); },
    size(name) { try { return fs.lstatSync(file(name)).size; } catch (e) { if (e.code === 'ENOENT') return 0; throw e; } },
    list() { check(); return fs.readdirSync(OUTPUT); } };
}
function send(q, options) {
  return new Promise(resolve => { let received = 0, done = false, timer, req, status = null, chunks = [], retryAfter = null;
    const finish = (code, bytes) => { if (done) return; done = true; clearTimeout(timer); resolve({ code, bytes, received, status, retryAfter }); };
    try { need(q.url.startsWith(BASE) && !new URL(q.url).search && !new URL(q.url).username, 'INTEGRITY_ERROR');
      const body = q.body ? json(q.body) : null;
      req = https.request(q.url, { method: q.method, headers: body ? { 'Content-Type': 'application/json', 'Content-Length': body.length } : {} }, res => {
        status = res.statusCode; retryAfter = res.headers['retry-after'] ?? null;
        res.on('data', chunk => { if (done) return; received += chunk.length;
          try { options.onChunk(chunk.length); if (received > LIMITS.response) throw Error('RESPONSE_LIMIT'); }
          catch (e) { finish(safe(e, 'RESPONSE_LIMIT')); res.destroy(); req.destroy(); return; }
          if (status === 200) chunks.push(chunk); });
        res.on('end', () => finish(null, status === 200 ? Buffer.concat(chunks) : undefined));
        res.on('aborted', () => finish('PARTIAL_RESPONSE')); res.on('error', () => finish('PARTIAL_RESPONSE'));
      });
      timer = setTimeout(() => { finish('TIMEOUT'); req.destroy(); }, LIMITS.deadline);
      req.on('error', () => finish('NETWORK_ERROR')); req.end(body);
    } catch { finish('NETWORK_ERROR'); req?.destroy(); }
  });
}
function initial() { return { bounds: [], headers: new Map(), creations: new Map(), instructionCount: 0,
  streams: Array.from({ length: 3 }, (_, index) => ({ index, start: null, end: null, current: null, lastScanned: null,
    lastMatched: null, pages: 0, matchingHeaders: 0, instructions: 0, success: 0, unknown: 0, topLevel: 0, cpi: 0, depth: {}, reasons: {}, status: 'INCOMPLETE' })) }; }
function checkAdmitted(state, q, admitted) {
  if (q.kind === 'resolver') { need(state.bounds.length === q.index && (q.index === 0 || admitted.slot > state.bounds.at(-1)), 'RESPONSE_INVALID'); return; }
  for (const h of admitted.headers) { const prior = state.headers.get(h.number);
    if (prior) need(canonical(prior) === canonical(h), 'IMMUTABLE_CONFLICT');
    const parent = state.headers.get(h.parentNumber); if (parent) need(parent.hash === h.parentHash, 'IMMUTABLE_CONFLICT'); }
  if (q.kind !== 'data') return;
  need(state.instructionCount + admitted.counts.instructions <= LIMITS.instructions, 'INSTRUCTION_LIMIT');
  let newCount = 0; for (const creation of admitted.creations) { const prior = state.creations.get(locator(creation));
    if (prior) need(canonical(prior) === canonical(creation), 'IMMUTABLE_CONFLICT'); else newCount++; }
  need(state.creations.size + newCount <= LIMITS.creations, 'CREATION_LIMIT');
  const bytes = [...state.creations.values(), ...admitted.creations].reduce((n, v) => n + json(v).length, 0); need(bytes <= LIMITS.creationBytes, 'CREATION_LIMIT');
}
function applyAdmitted(state, q, admitted) {
  if (q.kind === 'resolver') { state.bounds.push(admitted.slot); if (state.bounds.length === 4) state.streams.forEach((s, i) => {
    s.start = s.current = state.bounds[i]; s.end = state.bounds[i + 1] - 1; }); return; }
  admitted.headers.forEach(h => state.headers.set(h.number, h)); if (q.kind !== 'data') return;
  const s = state.streams[q.index]; s.pages++; s.lastScanned = admitted.lastScanned; s.current = admitted.lastScanned + 1;
  if (admitted.lastMatched !== null) { s.lastMatched = admitted.lastMatched; s.matchingHeaders += admitted.matchingHeaders; }
  for (const k of ['instructions', 'success', 'unknown', 'topLevel', 'cpi']) s[k] += admitted.counts[k];
  for (const k of ['depth', 'reasons']) for (const [name, count] of Object.entries(admitted.counts[k])) s[k][name] = (s[k][name] ?? 0) + count;
  need(Object.keys(s.depth).length + Object.keys(s.reasons).length <= LIMITS.diagnostics, 'INSTRUCTION_LIMIT');
  state.instructionCount += admitted.counts.instructions; for (const c of admitted.creations) state.creations.set(locator(c), c);
  if (s.current > s.end) s.status = 'SCAN_COMPLETE';
}
function summary(state, code, lineage) { return { ...FLAGS, version: CONFIG.version, status: code ? 'INCOMPLETE' : 'SCAN_COMPLETE', code,
  streams: state.streams.map(({ current, ...s }) => s), creations: state.creations.size, decodedInstructions: state.instructionCount,
  fallback: 'NOT_RUN', retention: 'UNVERIFIED', expiry: EXPIRY, sourceCoverage: 'REFERENCE_BOUNDARIES_ONLY',
  gaps: ['DEPLOYED_VERSION_UNKNOWN', 'INNER_INGESTION_UNKNOWN', 'GLOBAL_EARLIEST_UNKNOWN', 'PIT_KNOWN_AT_UNKNOWN', 'RIGHTS_UNKNOWN'], lineage }; }
const summaryHash = s => fingerprint(CONFIG.canonicalVersion, s);
async function run(options = {}, deps = {}) {
  if (options.enabled !== true) return error('DISABLED');
  if (options.output !== OUTPUT || Object.keys(options).some(k => !['enabled', 'retainedBefore', 'output'].includes(k))) return error('UNSAFE_PATH');
  const now = deps.now ?? Date.now, utcNow = deps.utcNow ?? Date.now, wait = deps.wait ?? (ms => new Promise(r => setTimeout(r, ms)));
  const start = now(), state = initial(), records = [], files = [], accounting = { attempts: 0, retries: 0, distinct: 0, dataQueries: 0,
    received: 0, retained: 0, partialBytes: 0, retainedKnown: true, cashMicrousd: '0', checkpoints: [] };
  let store, lineage, code = null, created = false, lastStart = -Infinity, metadataBytes = 0;
  const checkpoint = () => { for (const [k, limit] of [['attempts', LIMITS.attempts], ['received', LIMITS.received], ['retained', LIMITS.retained]])
    if (accounting[k] * 5 >= limit * 4 && !accounting.checkpoints.includes(k)) accounting.checkpoints.push(k); };
  function write(name, bytes) {
    need(accounting.retained + bytes.length <= LIMITS.retained, 'DISK_LIMIT');
    if (!name.endsWith('.raw') && name !== 'creations.jsonl') { need(metadataBytes + bytes.length <= LIMITS.metadata, 'METADATA_LIMIT'); metadataBytes += bytes.length; }
    try { store.write(name, bytes); accounting.retained += bytes.length; files.push({ name, bytes: bytes.length, hash: digest(bytes) }); checkpoint(); }
    catch { try { const size = store.size(name); uint(size, 'STORAGE_ERROR'); accounting.retained += size; accounting.partialBytes += size;
        if (size) files.push({ name, bytes: size, hash: digest(store.read(name)), partial: true }); }
      catch { accounting.retainedKnown = false; } throw Error('STORAGE_ERROR'); }
  }
  function room(pause = 0) {
    need(now() - start + pause + LIMITS.deadline <= LIMITS.sourceMs, 'TIME_LIMIT');
    need(accounting.attempts < LIMITS.attempts, 'ATTEMPT_LIMIT'); need(accounting.received + LIMITS.response <= LIMITS.received, 'RECEIVED_LIMIT');
    need(accounting.retained + LIMITS.response + LIMITS.creationBytes + LIMITS.metadata <= LIMITS.retained, 'DISK_LIMIT');
    const free = store.free(); uint(free, 'FREE_SPACE_LIMIT'); need(free - LIMITS.response - LIMITS.creationBytes - LIMITS.metadata >= LIMITS.free, 'FREE_SPACE_LIMIT');
    need(now() - start + pause + LIMITS.deadline <= LIMITS.sourceMs, 'TIME_LIMIT');
  }
  async function request(q) {
    need(accounting.distinct < LIMITS.distinct, 'DISTINCT_LIMIT'); if (q.kind === 'data') need(accounting.dataQueries < LIMITS.dataQueries, 'DATA_QUERY_LIMIT');
    for (let retry = 0; retry <= 2; retry++) {
      const previous = records.at(-1), retryAfter = retry && previous?.retryAfterSeconds;
      const pause = retry ? Math.max(retry === 1 ? 15000 : 45000, (retryAfter ?? 0) * 1000) : Math.max(0, LIMITS.spacing - (now() - lastStart));
      room(pause); if (pause > 0) await wait(pause); room();
      const record = { ordinal: records.length, query: q, queryHash: fingerprint('e2-census-query-v1', q), retry,
        waitMs: pause, status: null, code: null, received: 0, raw: null, startMs: now() - start, endMs: null };
      if (retry) { need(accounting.retries < LIMITS.retries, 'ATTEMPT_LIMIT'); accounting.retries++; }
      else { accounting.distinct++; if (q.kind === 'data') accounting.dataQueries++; }
      accounting.attempts++; records.push(record); lastStart = now(); let response;
      const onChunk = n => { uint(n, 'NETWORK_ERROR'); record.received += n; accounting.received += n; checkpoint();
        need(record.received <= LIMITS.response, 'RESPONSE_LIMIT'); need(accounting.received <= LIMITS.received, 'RECEIVED_LIMIT'); };
      try { response = await (deps.transport ?? send)(q, { onChunk, deadline: LIMITS.deadline }); }
      catch (e) { throw Error(safe(e, 'NETWORK_ERROR')); } finally { record.endMs = now() - start; }
      need(response && typeof response === 'object', 'NETWORK_ERROR');
      const received = response.received ?? response.bytes?.length ?? record.received; uint(received, 'NETWORK_ERROR');
      if (record.received < received) onChunk(received - record.received); need(record.received === received, 'NETWORK_ERROR');
      record.status = response.status ?? null;
      need(now() - start <= LIMITS.sourceMs, 'TIME_LIMIT');
      if (response.code) throw Error(safe(response.code, 'NETWORK_ERROR'));
      if (record.status === 529) {
        const ra = response.retryAfter; record.retryAfterSeconds = typeof ra === 'string' && /^(0|[1-9][0-9]*)$/.test(ra) && Number.isSafeInteger(Number(ra)) ? Number(ra) : null;
        record.code = 'HTTP_ERROR'; if (retry === 2) throw Error('HTTP_529_EXHAUSTED'); continue;
      }
      need(record.status === 200, record.status === 204 ? 'NO_PROGRESS' : 'HTTP_ERROR');
      need(Buffer.isBuffer(response.bytes) && response.bytes.length === received, 'PARTIAL_RESPONSE');
      const admitted = admit(response.bytes, q, state.bounds); need(admitted.code === null, admitted.code); checkAdmitted(state, q, admitted);
      const name = `${String(record.ordinal + 1).padStart(4, '0')}.raw`; write(name, response.bytes);
      record.raw = name; record.rawHash = digest(response.bytes); record.rawBytes = response.bytes.length; applyAdmitted(state, q, admitted); return;
    }
  }
  try {
    need(typeof options.retainedBefore === 'string' && /^(0|[1-9][0-9]*)$/.test(options.retainedBefore), 'ARGUMENTS_INVALID');
    const retainedBefore = Number(options.retainedBefore); uint(retainedBefore, 'DISK_LIMIT'); need(retainedBefore + LIMITS.retained <= LIMITS.aggregate, 'DISK_LIMIT');
    need(utcNow() < Date.parse(EXPIRY), 'EXPIRY'); lineage = provenance(deps); (deps.verifySealed ?? verifySealed)();
    lineage = { ...lineage, seal: SEAL, configHash: fingerprint(CONFIG.canonicalVersion, CONFIG), priorAccounting: { source: 'MAIN_ATTESTED', bytes: retainedBefore } };
    store = deps.store ?? fileStore(); need(store.absent(), 'ROOT_EXISTS'); const free = store.free(); uint(free, 'FREE_SPACE_LIMIT');
    need(free - LIMITS.retained >= LIMITS.free, 'FREE_SPACE_LIMIT'); store.create(); created = true;
    write('attempt.json', json({ ...FLAGS, startedAt: new Date(utcNow()).toISOString(), lineage, expiry: EXPIRY,
      interruptedDisposition: 'No final manifest is INCOMPLETE; resume forbidden' }));
    for (let index = 0; index < 4; index++) await request(query('resolver', index));
    boundsOk(state.bounds); for (let index = 0; index < 4; index++) await request(query('header', index, null, state.bounds));
    while (state.streams.some(s => s.status !== 'SCAN_COMPLETE')) for (const s of state.streams)
      if (s.status !== 'SCAN_COMPLETE') await request(query('data', s.index, s.current, state.bounds));
  } catch (e) { code = safe(e); const last = records.at(-1); if (last && last.raw === null) last.code = code; }
  if (!created) return { ...error(code ?? 'INTEGRITY_ERROR'), accounting };
  let result = summary(state, code, lineage), hash = summaryHash(result);
  try {
    write('creations.jsonl', Buffer.concat([...state.creations.values()].sort(compare).map(json)));
    write('summary.json', json(result));
    const publicationOperational = { elapsedMs: now() - start, endedAt: new Date(utcNow()).toISOString() };
    const manifest = { ...FLAGS, version: CONFIG.version, configuration: CONFIG, lineage, records, files: [...files], code, status: result.status,
      summaryHash: hash, accounting: { ...accounting, manifestBytes: 0 }, operational: publicationOperational, publicationOperational,
      runBudget: now() - start > LIMITS.totalMs ? 'OVERRUN' : 'UNMEASURED' };
    let bytes = json(manifest), settled = false;
    for (let i = 0; i < 16; i++) {
      manifest.accounting.retained = accounting.retained + bytes.length; manifest.accounting.manifestBytes = bytes.length;
      manifest.accounting.checkpoints = [...accounting.checkpoints];
      if (manifest.accounting.retained * 5 >= LIMITS.retained * 4 && !manifest.accounting.checkpoints.includes('retained')) manifest.accounting.checkpoints.push('retained');
      const next = json(manifest); if (next.length === bytes.length) { bytes = next; settled = true; break; } bytes = next;
    }
    need(settled, 'INTEGRITY_ERROR'); accounting.manifestBytes = bytes.length; write('manifest.json', bytes);
  } catch (e) { code = safe(e, 'STORAGE_ERROR'); result = summary(state, code, lineage); hash = summaryHash(result); }
  return { ...FLAGS, code, status: code ? 'INCOMPLETE' : 'SCAN_COMPLETE', summary: result, summaryHash: hash, accounting,
    runBudget: now() - start > LIMITS.totalMs ? 'OVERRUN' : 'UNMEASURED' };
}
async function replay(store = fileStore()) {
  try {
    const manifestBytes = store.read('manifest.json'); need(manifestBytes.length <= LIMITS.metadata, 'INTEGRITY_ERROR'); const m = parse(manifestBytes, false);
    need(canonical(m.configuration) === canonical(CONFIG) && m.version === CONFIG.version && canonical(Object.fromEntries(Object.keys(FLAGS).map(k => [k, m[k]]))) === canonical(FLAGS), 'INTEGRITY_ERROR');
    need(m.lineage.configHash === fingerprint(CONFIG.canonicalVersion, CONFIG) && canonical(m.lineage.seal) === canonical(SEAL), 'INTEGRITY_ERROR');
    need(/^v(24|25)\.[0-9]+\.[0-9]+$/.test(m.lineage.runtime) && /^[a-f0-9]{40}$/.test(m.lineage.source.commit)
      && typeof m.lineage.source.dirty === 'boolean' && m.lineage.scripts['exploratory-probe.cjs'] === CONFIG.helperHash, 'INTEGRITY_ERROR');
    for (const name of ['e2-census.cjs', 'e2-census-cli.cjs']) need(m.lineage.scripts[name] === digest(readBounded(path.join(__dirname, name), 1000000)), 'INTEGRITY_ERROR');
    need(m.lineage.priorAccounting.source === 'MAIN_ATTESTED' && uint(m.lineage.priorAccounting.bytes) + LIMITS.retained <= LIMITS.aggregate, 'INTEGRITY_ERROR');
    need(Array.isArray(m.records) && m.records.length <= LIMITS.attempts && Array.isArray(m.files) && m.files.length <= LIMITS.distinct + 3, 'INTEGRITY_ERROR');
    const files = new Map(); let retained = manifestBytes.length, metadata = manifestBytes.length;
    for (const f of m.files) { need(!files.has(f.name) && (['attempt.json', 'summary.json', 'creations.jsonl'].includes(f.name) || /^[0-9]{4}\.raw$/.test(f.name)), 'INTEGRITY_ERROR');
      const bytes = store.read(f.name); need(bytes.length === f.bytes && digest(bytes) === f.hash, 'INTEGRITY_ERROR');
      retained += bytes.length; if (!f.name.endsWith('.raw') && f.name !== 'creations.jsonl') metadata += bytes.length; files.set(f.name, { ...f, data: bytes }); }
    need(retained <= LIMITS.retained && metadata <= LIMITS.metadata, 'INTEGRITY_ERROR');
    need(m.publicationOperational && Number.isSafeInteger(m.publicationOperational.elapsedMs) && m.publicationOperational.elapsedMs >= 0
      && typeof m.publicationOperational.endedAt === 'string' && new Date(m.publicationOperational.endedAt).toISOString() === m.publicationOperational.endedAt, 'INTEGRITY_ERROR');
    // Replay packaging/timing may differ; reconstruct the original publication's serialized manifest independently.
    const originalManifestBytes = json({ ...m, operational: m.publicationOperational }).length;
    need(m.accounting.manifestBytes === originalManifestBytes && m.accounting.retained === retained - manifestBytes.length + originalManifestBytes
      && m.accounting.retained <= LIMITS.retained && m.accounting.retainedKnown === true, 'INTEGRITY_ERROR');
    need(canonical(store.list().sort()) === canonical([...files.keys(), 'manifest.json'].sort()), 'INTEGRITY_ERROR');
    const attempt = parse(files.get('attempt.json')?.data, false); need(canonical(attempt.lineage) === canonical(m.lineage) && attempt.expiry === EXPIRY, 'INTEGRITY_ERROR');
    const state = initial(); let controls = 0, turn = 0, distinct = 0, retries = 0, dataQueries = 0, received = 0, previous = null;
    for (const [i, record] of m.records.entries()) {
      need(record.ordinal === i && Number.isSafeInteger(record.received) && record.received >= 0 && record.received <= LIMITS.response, 'INTEGRITY_ERROR'); received += record.received;
      need(received <= LIMITS.received && record.queryHash === fingerprint('e2-census-query-v1', record.query), 'INTEGRITY_ERROR');
      let expected;
      if (record.retry) { need(previous?.status === 529 && !previous.raw && record.retry === previous.retry + 1 && record.retry <= 2, 'INTEGRITY_ERROR');
        need(record.waitMs >= Math.max(record.retry === 1 ? 15000 : 45000, (previous.retryAfterSeconds ?? 0) * 1000), 'INTEGRITY_ERROR'); expected = previous.query; retries++; }
      else { distinct++; if (controls < 4) expected = query('resolver', controls);
        else if (controls < 8) expected = query('header', controls - 4, null, state.bounds);
        else { let checked = 0; while (state.streams[turn].status === 'SCAN_COMPLETE' && checked++ < 3) turn = (turn + 1) % 3;
          need(checked <= 3, 'INTEGRITY_ERROR'); expected = query('data', turn, state.streams[turn].current, state.bounds); dataQueries++; } }
      need(canonical(record.query) === canonical(expected), 'INTEGRITY_ERROR');
      if (record.raw) { need(record.code === null && record.status === 200 && record.retry <= 2, 'INTEGRITY_ERROR');
        const file = files.get(record.raw); need(file && !file.partial && file.hash === record.rawHash && file.bytes === record.rawBytes && file.bytes === record.received, 'INTEGRITY_ERROR');
        const admitted = admit(file.data, expected, state.bounds); need(admitted.code === null, 'INTEGRITY_ERROR'); checkAdmitted(state, expected, admitted); applyAdmitted(state, expected, admitted);
        if (controls < 8) controls++; else turn = (turn + 1) % 3;
      } else { need(CODES.has(record.code), 'INTEGRITY_ERROR');
        if (i < m.records.length - 1) need(record.status === 529 && record.retry < 2 && m.records[i + 1].retry === record.retry + 1, 'INTEGRITY_ERROR'); }
      previous = record;
    }
    need(distinct <= LIMITS.distinct && dataQueries <= LIMITS.dataQueries && retries <= LIMITS.retries, 'INTEGRITY_ERROR');
    need(m.accounting.attempts === m.records.length && m.accounting.distinct === distinct && m.accounting.retries === retries
      && m.accounting.dataQueries === dataQueries && m.accounting.received === received, 'INTEGRITY_ERROR');
    need(m.code === null || CODES.has(m.code), 'INTEGRITY_ERROR');
    if (m.code === null) need(controls === 8 && state.streams.every(s => s.status === 'SCAN_COMPLETE'), 'INTEGRITY_ERROR');
    const s = summary(state, m.code, m.lineage), hash = summaryHash(s); need(hash === m.summaryHash && files.get('summary.json')?.data.equals(json(s)), 'INTEGRITY_ERROR');
    const creations = Buffer.concat([...state.creations.values()].sort(compare).map(json)); need(creations.length <= LIMITS.creationBytes && files.get('creations.jsonl')?.data.equals(creations), 'INTEGRITY_ERROR');
    return { ...FLAGS, code: null, status: s.status, summary: s, summaryHash: hash, runBudget: 'UNMEASURED', manifestHash: digest(manifestBytes) };
  } catch { return error('INTEGRITY_ERROR'); }
}
module.exports = { OUTPUT, CONFIG, LIMITS, query, admit, run, replay, fileStore, error, verifySealed };
