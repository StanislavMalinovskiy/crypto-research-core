'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const https = require('node:https');
const http = require('node:http');
const { execFileSync } = require('node:child_process');
const OUTPUT = 'C:\\crypto-research-evidence\\r1-d1\\exploratory-sqd-v1';
const BASE = 'https://portal.sqd.dev/datasets/solana-mainnet/';
const ANCHORS = Object.freeze(['2026-04-01T00:00:00Z', '2026-05-01T00:00:00Z', '2026-06-01T00:00:00Z',
  '2026-07-01T00:00:00Z', '2026-08-01T00:00:00Z', '2026-08-28T00:00:00Z']);
const PROGRAMS = Object.freeze(['6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P',
  'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA', '675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8']);
const LIMITS = Object.freeze({ attempts: 80, received: 25000000, disk: 50000000, response: 2000000,
  elapsed: 1800000, deadline: 30000, interval: 2000, concurrency: 1, retries: 0, cashMicrousd: '0', free: 30000000000 });
const FIELDS = Object.freeze({ block: 'number hash parentNumber parentHash timestamp',
  transaction: 'transactionIndex signatures accountKeys loadedAddresses version err',
  instruction: 'transactionIndex instructionAddress programId accounts data isCommitted error', balance: 'account pre post',
  tokenBalance: 'transactionIndex account preMint postMint preOwner postOwner preAmount postAmount preDecimals postDecimals' });
const API = Object.freeze({ title: 'SQD Portal API', version: '1.0.0', retrieved: '2026-10-03',
  url: 'https://docs.sqd.dev/openapi.json', bytes: 77881,
  sha256: 'sha256:0af342338e732a39e4251c31d45a82dfb264ebe80d1316bec6d70852f461129a' });
const CONFIG = Object.freeze({ queryVersion: 'exploratory-sqd-v1', summaryVersion: 'exploratory-counts-v1',
  canonicalizationVersion: 'exploratory-config-c14n-v1', runtime: 'v24.19.0', source: 'sqd-public-solana-mainnet',
  base: BASE, anchors: ANCHORS, programs: PROGRAMS, limits: LIMITS, fields: FIELDS, api: API,
  exclusion: ['2026-08-31T00:00:00Z', '2026-09-29T00:00:00Z'], windowSeconds: 60,
  includeAllBlocks: true, type: 'solana', instructionLinks: ['transaction', 'transactionInstructions', 'innerInstructions', 'transactionBalances', 'transactionTokenBalances'],
  output: OUTPUT, datasetRevision: null, retention: 'UNVERIFIED', paidFallback: false, metadataReserve: 1000000 });
const FLAGS = { classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false };
const CODES = new Set(['RESPONSE_INVALID', 'RESPONSE_LIMIT', 'HTTP_ERROR', 'PARTIAL_RESPONSE', 'TIMEOUT', 'NETWORK_ERROR',
  'ATTEMPT_LIMIT', 'RECEIVED_LIMIT', 'DISK_LIMIT', 'TIME_LIMIT', 'FREE_SPACE_LIMIT', 'STORAGE_ERROR', 'UNEXECUTED']);
const fail = code => { throw Error(code); };
const digest = bytes => 'sha256:' + crypto.createHash('sha256').update(bytes).digest('hex');
function canonical(value) {
  const atom = (tag, v) => { const text = String(v); return `${tag}${Buffer.byteLength(text)}:${text}`; };
  if (value === null) return 'z0:';
  if (typeof value === 'string') return atom('s', value);
  if (typeof value === 'boolean') return atom('b', value ? '1' : '0');
  if (typeof value === 'number' && Number.isSafeInteger(value)) return atom('n', value);
  if (Array.isArray(value)) return atom('a', value.map(canonical).join(''));
  if (value && typeof value === 'object') return atom('o', Object.keys(value).sort().map(k => atom('k', k) + canonical(value[k])).join(''));
  fail('INTEGRITY_ERROR');
}
const fingerprint = (version, value) => digest(Buffer.from(canonical({ version, value }), 'utf8'));
const configHash = () => fingerprint(CONFIG.canonicalizationVersion, CONFIG);
const flagsError = code => ({ ...FLAGS, status: 'INCOMPLETE', code });
const fields = kind => Object.fromEntries(FIELDS[kind].split(' ').map(name => [name, true]));
function query(kind, anchor, slot, program) {
  if (!ANCHORS.includes(anchor) || !['resolver', 'header', 'payload'].includes(kind)) fail('QUERY_INVALID');
  if (kind === 'resolver') return { kind, anchor, method: 'GET', url: `${BASE}timestamps/${Date.parse(anchor) / 1000}/block` };
  if (!Number.isSafeInteger(slot) || slot < 0 || (kind === 'payload' && !PROGRAMS.includes(program))) fail('QUERY_INVALID');
  const body = { type: 'solana', fromBlock: slot, toBlock: slot, includeAllBlocks: true, fields: { block: fields('block') } };
  if (kind === 'payload') {
    for (const k of ['transaction', 'instruction', 'balance', 'tokenBalance']) body.fields[k] = fields(k);
    body.instructions = [{ programId: [program], ...Object.fromEntries(CONFIG.instructionLinks.map(k => [k, true])) }];
  }
  return { kind, anchor, slot, ...(program ? { program } : {}), method: 'POST', url: BASE + 'finalized-stream', body };
}
function validQuery(spec) {
  try { return canonical(spec) === canonical(query(spec.kind, spec.anchor, spec.slot, spec.program)); } catch { return false; }
}
// Preserve numeric lexemes before JSON decoding: quantities never pass through Number.
function parse(bytes, preserveNumbers = true) {
  const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  let encoded = '', at = 0; const objects = [];
  while (at < text.length) {
    if (text[at] === '"') {
      const start = at++;
      while (at < text.length) { if (text[at] === '\\') { at += 2; continue; } if (text[at++] === '"') break; }
      const token = text.slice(start, at);
      if (text.slice(at).match(/^\s*:/)) {
        const keys = objects.at(-1), key = JSON.parse(token);
        if (!(keys instanceof Set) || keys.has(key)) fail('RESPONSE_INVALID'); keys.add(key);
      }
      encoded += token;
    } else if (text[at] === '-' || /[0-9]/.test(text[at])) {
      const match = text.slice(at).match(/^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/);
      if (!match) fail('RESPONSE_INVALID'); encoded += preserveNumbers ? JSON.stringify(match[0]) : match[0]; at += match[0].length;
    } else {
      if (text[at] === '{') objects.push(new Set()); else if (text[at] === '[') objects.push(null);
      else if (text[at] === '}' || text[at] === ']') objects.pop();
      encoded += text[at++];
    }
  }
  return JSON.parse(encoded);
}
function integer(value) {
  if (!/^(0|[1-9][0-9]*)$/.test(String(value))) fail('RESPONSE_INVALID');
  const n = Number(value); if (!Number.isSafeInteger(n)) fail('RESPONSE_INVALID'); return n;
}
function safeHeader(h, context) {
  if (!h || typeof h !== 'object' || Array.isArray(h)) fail('RESPONSE_INVALID');
  const result = { number: integer(h.number), hash: h.hash, parentNumber: integer(h.parentNumber), parentHash: h.parentHash, timestamp: integer(h.timestamp) };
  if (result.number !== context.slot || typeof h.hash !== 'string' || !/^[A-Za-z0-9]{1,128}$/.test(h.hash)
    || typeof h.parentHash !== 'string' || !/^[A-Za-z0-9]{1,128}$/.test(h.parentHash)
    || result.parentNumber >= result.number || result.timestamp < Date.parse(context.anchor) / 1000
    || result.timestamp >= Date.parse(context.anchor) / 1000 + 60 || result.timestamp >= 1788134400) fail('RESPONSE_INVALID');
  if (context.header && canonical(result) !== canonical(context.header)) fail('RESPONSE_INVALID');
  return result;
}
function admit(bytes, context) {
  try {
    if (!Buffer.isBuffer(bytes) || bytes.length > LIMITS.response || !ANCHORS.includes(context.anchor)) fail('RESPONSE_INVALID');
    if (context.kind === 'resolver') {
      const value = parse(bytes); if (!value || Object.keys(value).join() !== 'block_number') fail('RESPONSE_INVALID');
      return { code: null, slot: integer(value.block_number), rows: 0 };
    }
    const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    const lines = text.trim().split('\n'); if (lines.length !== 1 || !lines[0]) fail('RESPONSE_INVALID');
    const value = parse(bytes), header = safeHeader(value.header, context);
    if (context.kind === 'header') return { code: null, header, rows: 0 };
    if (context.kind !== 'payload') fail('RESPONSE_INVALID');
    const counts = {}; let rows = 0;
    for (const [collection, kind] of [['transactions', 'transaction'], ['instructions', 'instruction'], ['balances', 'balance'], ['tokenBalances', 'tokenBalance']]) {
      const list = value[collection] === undefined ? [] : value[collection];
      if (!Array.isArray(list)) fail('RESPONSE_INVALID');
      const stats = { rows: list.length, collectionPresent: Object.hasOwn(value, collection), fields: {} };
      for (const field of FIELDS[kind].split(' ')) stats.fields[field] = { present: 0, null: 0, missing: 0 };
      for (const row of list) {
        if (!row || typeof row !== 'object' || Array.isArray(row)) fail('RESPONSE_INVALID');
        for (const field of FIELDS[kind].split(' ')) {
          const s = stats.fields[field]; if (!Object.hasOwn(row, field)) s.missing++; else { s.present++; if (row[field] === null) s.null++; }
        }
      }
      counts[collection] = stats; rows += list.length;
    }
    return { code: null, header, rows, counts };
  } catch { return { code: 'RESPONSE_INVALID', rows: 0 }; }
}
function budget(overrides = {}, start = 0) {
  const caps = { ...LIMITS, ...overrides };
  const state = { attempts: 0, received: 0, disk: 0, elapsed: 0, cashMicrousd: '0', checkpoints: [] };
  const check = () => { for (const k of ['attempts', 'received', 'disk', 'elapsed'])
    if (state[k] * 5 >= caps[k] * 4 && !state.checkpoints.includes(k)) state.checkpoints.push(k); };
  return { state, reserve(now, free) {
    state.elapsed = Math.max(0, now - start); check();
    if (state.attempts >= caps.attempts) return 'ATTEMPT_LIMIT';
    if (state.received + caps.response > caps.received) return 'RECEIVED_LIMIT';
    if (state.disk + caps.response > caps.disk) return 'DISK_LIMIT';
    if (state.elapsed >= caps.elapsed) return 'TIME_LIMIT';
    if (!Number.isSafeInteger(free) || free - caps.response < caps.free) return 'FREE_SPACE_LIMIT';
    state.attempts++; check(); return null;
  }, charge(received, disk) { state.received += received; state.disk += disk; check(); },
  tick(now) { state.elapsed = Math.max(0, now - start); check(); } };
}
function send(spec, options = {}) {
  if (!validQuery(spec)) return Promise.resolve({ code: 'QUERY_INVALID', received: 0 });
  return sendBounded(spec, options, LIMITS);
}
// V2 supplies its own closed query validator before entering this finite I/O seam.
function sendBounded(spec, options = {}, caps = LIMITS) {
  if (!spec || !['GET', 'POST'].includes(spec.method) || !spec.url?.startsWith(BASE)
    || ![BASE + 'finalized-stream'].includes(spec.url) && !new RegExp('^' + BASE.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + 'timestamps/[0-9]+/block$').test(spec.url))
    return Promise.resolve({ code: 'QUERY_INVALID', received: 0 });
  let url = spec.url, client = https;
  if (options.loopback) {
    let local; try { local = new URL(options.loopback); } catch { return Promise.resolve({ code: 'QUERY_INVALID', received: 0 }); }
    if (local.protocol !== 'http:' || local.hostname !== '127.0.0.1' || local.username || local.password || local.search || local.hash || local.pathname !== '/')
      return Promise.resolve({ code: 'QUERY_INVALID', received: 0 });
    url = local.origin + new URL(spec.url).pathname; client = http;
  }
  const limit = Math.min(options.responseLimit ?? caps.response, caps.response);
  const deadline = Math.min(options.deadlineMs ?? caps.deadline, caps.deadline);
  return new Promise(resolve => {
    let done = false, received = 0, status = null, res, timer; const chunks = [];
    const finish = (code, bytes) => {
      if (done) return; done = true; clearTimeout(timer);
      if (code) { res?.destroy(); req.destroy(); }
      resolve({ code, status, received, ...(code ? {} : { bytes }) });
    };
    const req = client.request(url, { method: spec.method, agent: false,
      headers: { Accept: 'application/json', ...(spec.body ? { 'Content-Type': 'application/json' } : {}) } }, response => {
      res = response; status = res.statusCode;
      res.on('readable', () => {
        if (done) return;
        try {
          while (res.readableLength > 0) {
            const chunk = res.read(Math.min(res.readableLength, limit + 1 - received)); if (!chunk) break;
            received += chunk.length; options.onChunk?.(chunk.length);
            if (received > limit) { finish('RESPONSE_LIMIT'); return; }
            if (status === 200) chunks.push(chunk);
          }
          // Reading zero after draining allows the finite stream's end event to fire.
          res.read(0);
        } catch { finish('NETWORK_ERROR'); }
      });
      res.on('end', () => finish(status === 200 ? (res.complete ? null : 'PARTIAL_RESPONSE') : 'HTTP_ERROR', Buffer.concat(chunks)));
      res.on('aborted', () => finish('PARTIAL_RESPONSE'));
      res.on('error', () => finish('PARTIAL_RESPONSE'));
    });
    req.on('error', () => finish('NETWORK_ERROR'));
    timer = setTimeout(() => finish('TIMEOUT'), deadline);
    if (spec.body) req.write(JSON.stringify(spec.body)); req.end();
  });
}
function checkedPath(output, requireDirectory = false, expected = OUTPUT) {
  if (output !== expected || process.platform !== 'win32') fail('UNSAFE_PATH');
  let ancestor = path.resolve(output);
  while (true) {
    try {
      const stat = fs.lstatSync(ancestor);
      if (stat.isSymbolicLink() || !stat.isDirectory() || fs.realpathSync.native(ancestor).toLowerCase() !== ancestor.toLowerCase()) fail('UNSAFE_PATH');
    } catch (e) { if (e.code !== 'ENOENT') throw e; if (requireDirectory && ancestor === output) fail('INTEGRITY_ERROR'); }
    const parent = path.dirname(ancestor); if (parent === ancestor) break; ancestor = parent;
  }
  return output;
}
function fileStore(output) { return exclusiveStore(output, OUTPUT, LIMITS.response, 1000000, 3); }
function exclusiveStore(output, expected, responseCap, metadataCap, digits) {
  if (expected !== OUTPUT && expected !== 'C:\\crypto-research-evidence\\r1-d1\\exploratory-sqd-v2') fail('UNSAFE_PATH');
  const check = required => checkedPath(output, required, expected); check(false);
  const nameOk = name => ['manifest.json', 'summary.json', 'attempt.json'].includes(name) || new RegExp(`^\\d{${digits}}\\.raw$`).test(name);
  return {
    create() { check(false); if (fs.existsSync(output)) fail('OUTPUT_EXISTS'); fs.mkdirSync(path.dirname(output), { recursive: true }); check(false); fs.mkdirSync(output); },
    free() { check(false); let ancestor = output; while (!fs.existsSync(ancestor)) ancestor = path.dirname(ancestor);
      const stat = fs.statfsSync(ancestor, { bigint: true }); const free = stat.bavail * stat.bsize;
      return Number(free > BigInt(Number.MAX_SAFE_INTEGER) ? BigInt(Number.MAX_SAFE_INTEGER) : free); },
    write(name, bytes) {
      if (!nameOk(name)) fail('UNSAFE_PATH'); check(true); let fd;
      try { fd = fs.openSync(path.join(output, name), 'wx'); let at = 0;
        while (at < bytes.length) { const n = fs.writeSync(fd, bytes, at, bytes.length - at); if (!n) fail('STORAGE_ERROR'); at += n; } fs.fsyncSync(fd);
      } finally { if (fd !== undefined) fs.closeSync(fd); }
    },
    read(name) {
      if (!nameOk(name)) fail('INTEGRITY_ERROR'); check(true); const file = path.join(output, name);
      if (fs.lstatSync(file).isSymbolicLink()) fail('INTEGRITY_ERROR'); const fd = fs.openSync(file, 'r');
      try { const stat = fs.fstatSync(fd), cap = name.endsWith('.raw') ? responseCap : metadataCap;
        if (!stat.isFile() || stat.size > cap) fail('INTEGRITY_ERROR'); const bytes = Buffer.alloc(cap + 1); let at = 0;
        while (at < bytes.length) { const n = fs.readSync(fd, bytes, at, bytes.length - at, null); if (!n) break; at += n; }
        if (at > cap) fail('INTEGRITY_ERROR'); return bytes.subarray(0, at);
      } finally { fs.closeSync(fd); }
    }
  };
}
function summary(records, raws) {
  const strata = ANCHORS.flatMap(anchor => PROGRAMS.map(program => ({ anchor, program, status: 'UNEXECUTED', code: 'UNEXECUTED', rows: 0 })));
  const headers = new Map(), resolved = new Map(), seen = new Set(); const gaps = []; let lastAnchor = -1;
  for (const r of records) {
    const index = ANCHORS.indexOf(r.request.anchor), identity = `${r.request.anchor}:${r.request.kind}:${r.request.program || ''}`;
    if (index < lastAnchor || seen.has(identity)) fail('INTEGRITY_ERROR'); lastAnchor = index; seen.add(identity);
    if (r.request.kind === 'header' && resolved.get(r.request.anchor) !== r.request.slot) fail('INTEGRITY_ERROR');
    if (r.request.kind === 'payload' && (!headers.has(r.request.anchor) || headers.get(r.request.anchor).number !== r.request.slot)) fail('INTEGRITY_ERROR');
    if (r.code) { gaps.push({ anchor: r.request.anchor, kind: r.request.kind, ...(r.request.program ? { program: r.request.program } : {}), code: r.code }); }
    let observed;
    if (r.raw) {
      observed = admit(raws.get(r.raw), { ...r.request, header: r.request.kind === 'payload' ? headers.get(r.request.anchor) : undefined });
      if (observed.code) fail('INTEGRITY_ERROR');
      if (r.request.kind === 'resolver') resolved.set(r.request.anchor, observed.slot);
      if (r.request.kind === 'header') headers.set(r.request.anchor, observed.header);
    }
    if (r.request.kind === 'payload') {
      const s = strata.find(s => s.anchor === r.request.anchor && s.program === r.request.program);
      Object.assign(s, observed ? { status: observed.rows ? 'POPULATED' : 'EMPTY', code: null, rows: observed.rows, counts: observed.counts,
        header: observed.header, received: r.received, rawHash: r.hash } : { status: 'INCOMPLETE', code: r.code, rows: 0 });
    }
  }
  return { ...FLAGS, version: CONFIG.summaryVersion, configHash: configHash(), strata, gaps, fullD1UpperBound: null,
    unknowns: ['candidate-wallet/token/pool populations', 'discovery and related histories', 'CLMM/DLMM state', '200 independent trade checks',
      'tips', 'exact SOL/USD', 'observed visibility', 'September excluded', 'tail costs', 'full-D1 retention/tariffs/capacity', 'temporal gaps and depth denominators'] };
}
function sourceIdentity() {
  const cwd = path.resolve(__dirname, '../../..');
  try { return { commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim(),
    dirty: execFileSync('git', ['status', '--porcelain'], { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).length > 0 }; }
  catch { return { commit: null, dirty: null }; }
}
async function runProbe(options = {}, injected = {}) {
  if (options.enabled !== true) return flagsError('DISABLED');
  if (options.output !== OUTPUT || Object.keys(options).some(k => !['enabled', 'output'].includes(k))) return flagsError('UNSAFE_PATH');
  if ((injected.runtime ?? process.version) !== CONFIG.runtime) return flagsError('RUNTIME_INVALID');
  const now = injected.now ?? Date.now, sleep = injected.sleep ?? (ms => new Promise(r => setTimeout(r, ms)));
  const start = now(), b = budget({}, start), records = [], raws = new Map(); let store, lastStart = null, stopped = false, retainedKnown = true;
  const transport = injected.transport ?? send;
  const metaReserve = CONFIG.metadataReserve;
  const source = injected.source ?? sourceIdentity();
  const attemptBytes = Buffer.from(JSON.stringify({ ...FLAGS, status: 'INCOMPLETE', startedAt: new Date(start).toISOString(),
    configHash: configHash(), source, runtime: process.version, plannedMaximumAttempts: 30,
    interruptedDisposition: 'No final manifest means interrupted/incomplete; reuse and resume forbidden' }) + '\n');
  try { store = injected.store ?? fileStore(options.output); if (store.free() - metaReserve - LIMITS.response < LIMITS.free) return flagsError('FREE_SPACE_LIMIT'); store.create(); }
  catch (e) { return flagsError(e.message === 'OUTPUT_EXISTS' ? 'OUTPUT_EXISTS' : e.message === 'UNSAFE_PATH' ? 'UNSAFE_PATH' : 'STORAGE_ERROR'); }
  try { store.write('attempt.json', attemptBytes); b.charge(0, attemptBytes.length); } catch { return flagsError('STORAGE_ERROR'); }
  async function request(spec, expectedHeader) {
    if (stopped) return null;
    if (lastStart !== null) await sleep(Math.max(0, LIMITS.interval - (now() - lastStart)));
    let code; try { code = b.reserve(now(), store.free() - metaReserve); } catch { code = 'STORAGE_ERROR'; }
    if (code) { records.push({ request: spec, code, received: 0, attempted: false, status: null }); stopped = true; return null; }
    lastStart = now(); const record = { request: spec, received: 0, attempted: true, status: null, code: null }; records.push(record);
    let response;
    try { response = await transport(spec, { deadlineMs: Math.min(LIMITS.deadline, LIMITS.elapsed - (now() - start)), responseLimit: LIMITS.response,
      onChunk(n) { record.received += n; b.charge(n, 0); } }); } catch { response = { code: 'NETWORK_ERROR' }; }
    record.status = Number.isInteger(response.status) ? response.status : null; b.tick(now());
    if (response.code || b.state.elapsed >= LIMITS.elapsed) {
      record.code = b.state.elapsed >= LIMITS.elapsed ? 'TIME_LIMIT' : CODES.has(response.code) ? response.code : 'NETWORK_ERROR'; stopped = true; return null;
    }
    const observed = admit(response.bytes, { ...spec, header: expectedHeader });
    if (observed.code) { record.code = observed.code; return null; }
    const name = `${String(records.length).padStart(3, '0')}.raw`;
    try {
      store.write(name, response.bytes);
      record.raw = name; record.hash = digest(response.bytes); record.size = response.bytes.length; raws.set(name, response.bytes); b.charge(0, response.bytes.length);
    } catch {
      record.code = 'STORAGE_ERROR'; stopped = true;
      try { const partial = store.read(name); b.charge(0, partial.length);
        record.failedRaw = { name, size: partial.length, hash: digest(partial) };
      } catch (e) { if (e.code !== 'ENOENT' && e.message !== 'FILE_MISSING') retainedKnown = false; }
      return null;
    }
    return observed;
  }
  for (const anchor of ANCHORS) {
    if (stopped) break;
    const resolved = await request(query('resolver', anchor)); if (!resolved) continue;
    const admitted = await request(query('header', anchor, resolved.slot)); if (!admitted) continue;
    for (const program of PROGRAMS) { if (stopped) break; await request(query('payload', anchor, resolved.slot, program), admitted.header); }
  }
  try {
    b.tick(now()); const result = summary(records, raws), summaryHash = fingerprint(CONFIG.summaryVersion, result);
    const status = result.strata.every(s => s.status === 'POPULATED') && !stopped && /^[a-f0-9]{40,64}$/.test(source.commit || '') && typeof source.dirty === 'boolean' ? 'COMPLETE' : 'INCOMPLETE';
    const summaryBytes = Buffer.from(JSON.stringify(result) + '\n');
    const scriptHashes = Object.fromEntries(['exploratory-probe.cjs', 'exploratory-probe-cli.cjs'].map(name => [name, digest(fs.readFileSync(path.join(__dirname, name)))]));
    const accounting = { ...b.state, retained: b.state.disk, retainedKnown, plannedMaximumAttempts: 30, retries: 0, concurrency: 1 };
    const manifest = { ...FLAGS, status, config: CONFIG, configHash: configHash(), summaryHash, summaryFileHash: digest(summaryBytes),
      records, accounting, startedAt: new Date(start).toISOString(), endedAt: new Date(now()).toISOString(),
      source, scriptHashes, runtime: process.version, attemptFileHash: digest(attemptBytes),
      command: `node tools/research/r1/exploratory-probe-cli.cjs --enable-public --output ${OUTPUT}`, protocolVersion: '1.0.0', freezeEntry: 1,
      change: 'establish-r1-d1-data-gate', operator: 'local-owner-authorized-probe' };
    // Metadata has its own reserved headroom and is included in final retained accounting.
    let manifestBytes;
    for (let i = 0; i < 5; i++) {
      manifestBytes = Buffer.from(JSON.stringify(manifest) + '\n'); accounting.retained = b.state.disk + summaryBytes.length + manifestBytes.length;
      accounting.disk = accounting.retained;
      if (accounting.disk * 5 >= LIMITS.disk * 4 && !accounting.checkpoints.includes('disk')) accounting.checkpoints.push('disk');
    }
    manifestBytes = Buffer.from(JSON.stringify(manifest) + '\n');
    if (summaryBytes.length + manifestBytes.length > metaReserve || accounting.retained > LIMITS.disk || store.free() - summaryBytes.length - manifestBytes.length < LIMITS.free) fail('STORAGE_ERROR');
    store.write('summary.json', summaryBytes); store.write('manifest.json', manifestBytes);
    return { ...FLAGS, status, accounting, summary: result, summaryHash, manifestHash: digest(manifestBytes) };
  } catch { return { ...flagsError('STORAGE_ERROR'), accounting: { ...b.state, retainedKnown: false },
    attempts: records.map(r => ({ kind: r.request.kind, anchor: r.request.anchor, code: r.code, received: r.received, status: r.status })) }; }
}
function replay(store) {
  try {
    const manifestBytes = store.read('manifest.json'); if (manifestBytes.length > 1000000) fail('INTEGRITY_ERROR');
    const m = parse(manifestBytes, false);
    if (canonical(m.config) !== canonical(CONFIG) || m.configHash !== configHash() || m.classification !== 'EXPLORATORY'
      || m.d1Evidence !== false || m.d1Passed !== false || !Array.isArray(m.records) || m.records.length > 30) fail('INTEGRITY_ERROR');
    if (digest(store.read('attempt.json')) !== m.attemptFileHash || !['COMPLETE', 'INCOMPLETE'].includes(m.status)) fail('INTEGRITY_ERROR');
    const raws = new Map(), seen = new Set(); let total = 0;
    for (const r of m.records) {
      if (!validQuery(r.request) || (r.code !== null && !CODES.has(r.code)) || r.code === 'STORAGE_ERROR' || r.failedRaw) fail('INTEGRITY_ERROR');
      if (r.raw) {
        if (r.code || !/^\d{3}\.raw$/.test(r.raw) || seen.has(r.raw)) fail('INTEGRITY_ERROR'); seen.add(r.raw);
        const bytes = store.read(r.raw); total += bytes.length;
        if (total > LIMITS.received || bytes.length !== r.size || digest(bytes) !== r.hash) fail('INTEGRITY_ERROR'); raws.set(r.raw, bytes);
      } else if (!r.code) fail('INTEGRITY_ERROR');
    }
    const result = summary(m.records, raws), summaryHash = fingerprint(CONFIG.summaryVersion, result);
    const bytes = store.read('summary.json');
    if (summaryHash !== m.summaryHash || digest(bytes) !== m.summaryFileHash || bytes.toString('utf8') !== JSON.stringify(result) + '\n') fail('INTEGRITY_ERROR');
    return { ...FLAGS, status: m.status, code: null, summaryHash, manifestHash: digest(manifestBytes), summary: result };
  } catch { return flagsError('INTEGRITY_ERROR'); }
}
module.exports = { OUTPUT, ANCHORS, PROGRAMS, CONFIG, query, admit, budget, send, fileStore, runProbe, replay,
  parse, canonical, digest, fingerprint, integer, sourceIdentity, sendBounded, exclusiveStore };
