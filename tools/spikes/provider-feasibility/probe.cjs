'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const https = require('node:https');
const caps = Object.freeze({ requests: 80, response: 2000000, received: 25000000, evidence: 50000000, disk: 10000000000, free: 30000000000, requestMs: 15000, batchMs: 1800000, spacingMs: 2000 });
const fail = code => { const error = new Error(code); error.safeCode = code; throw error; };
const keys = (object, allowed) => { if (!object || typeof object !== 'object' || Array.isArray(object) || Object.keys(object).some(k => !allowed.includes(k))) fail('REQUEST_INVALID'); };
const slot = n => { if (!Number.isSafeInteger(n) || n < 0) fail('REQUEST_INVALID'); };
const address = value => { if (typeof value !== 'string' || !/^[1-9A-HJ-NP-Za-km-z]{32,44}$/.test(value)) fail('REQUEST_INVALID'); };
const finality = (value, allowed = ['commitment']) => { keys(value, allowed); if (value.commitment !== 'finalized') fail('REQUEST_INVALID'); };
function validate(plan) {
  keys(plan, ['referenceEnd', 'requests']);
  if (typeof plan.referenceEnd !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z$/.test(plan.referenceEnd)
    || !Number.isFinite(Date.parse(plan.referenceEnd)) || new Date(plan.referenceEnd).toISOString() !== plan.referenceEnd || !Array.isArray(plan.requests) || !plan.requests.length || plan.requests.length > caps.requests
    || Buffer.byteLength(JSON.stringify(plan)) > 256000) fail('REQUEST_INVALID');
  const mints = new Map();
  for (const request of plan.requests) {
    keys(request, ['url', 'method', 'body']); let url;
    try { url = new URL(request.url); } catch { fail('REQUEST_INVALID'); }
    if (url.protocol !== 'https:' || url.username || url.password || url.port || url.hash || url.href !== request.url) fail('REQUEST_INVALID');
    if (request.url === 'https://portal.sqd.dev/datasets/solana-mainnet/metadata') {
      if (request.method !== 'GET' || request.body !== undefined) fail('REQUEST_INVALID');
    } else if (request.url === 'https://portal.sqd.dev/datasets/solana-mainnet/finalized-stream') {
      const b = request.body; keys(b, ['type', 'fromBlock', 'toBlock', 'includeAllBlocks', 'fields', 'instructions']);
      if (request.method !== 'POST' || b.type !== 'solana' || b.includeAllBlocks !== true) fail('REQUEST_INVALID');
      slot(b.fromBlock); slot(b.toBlock); if (b.toBlock < b.fromBlock || b.toBlock - b.fromBlock >= 100) fail('REQUEST_INVALID');
      const fields = { block: 'number hash parentNumber parentHash timestamp', transaction: 'transactionIndex signatures accountKeys loadedAddresses version err',
        instruction: 'transactionIndex instructionAddress programId accounts data isCommitted error', balance: 'account pre post',
        tokenBalance: 'transactionIndex account preMint postMint preOwner postOwner preAmount postAmount preDecimals postDecimals' };
      keys(b.fields, Object.keys(fields));
      for (const [kind, selectors] of Object.entries(b.fields)) { keys(selectors, fields[kind].split(' ')); if (Object.values(selectors).some(v => v !== true)) fail('REQUEST_INVALID'); }
      if (b.instructions !== undefined) {
        if (!Array.isArray(b.instructions) || b.instructions.length > 3) fail('REQUEST_INVALID');
        for (const filter of b.instructions) {
          keys(filter, ['programId', 'transaction', 'transactionInstructions', 'innerInstructions', 'transactionBalances', 'transactionTokenBalances']);
          if (!Array.isArray(filter.programId) || !filter.programId.length || filter.programId.length > 3) fail('REQUEST_INVALID'); filter.programId.forEach(address);
          for (const [k, v] of Object.entries(filter)) if (k !== 'programId' && v !== true) fail('REQUEST_INVALID');
        }
      }
    } else if (url.origin + url.pathname === 'https://api.gopluslabs.io/api/v1/solana/token_security') {
      if (request.method !== 'GET' || request.body !== undefined || [...url.searchParams.keys()].join() !== 'contract_addresses') fail('REQUEST_INVALID');
      const mint = url.searchParams.get('contract_addresses'); address(mint); mints.set(mint, (mints.get(mint) || 0) + 1);
      if (mints.size > 3 || mints.get(mint) > 2) fail('REQUEST_INVALID');
    } else if (request.url === 'https://api.mainnet-beta.solana.com/') {
      const b = request.body; keys(b, ['jsonrpc', 'id', 'method', 'params']);
      if (request.method !== 'POST' || b.jsonrpc !== '2.0' || !Number.isSafeInteger(b.id) || !Array.isArray(b.params)) fail('REQUEST_INVALID');
      const p = b.params;
      switch (b.method) {
        case 'getSlot': if (p.length !== 1) fail('REQUEST_INVALID'); finality(p[0]); break;
        case 'getBlockTime': if (p.length !== 1) fail('REQUEST_INVALID'); slot(p[0]); break;
        case 'getBlocks':
          if (p.length !== 3) fail('REQUEST_INVALID'); slot(p[0]); slot(p[1]); finality(p[2]); if (p[1] < p[0] || p[1] - p[0] >= 100) fail('REQUEST_INVALID'); break;
        case 'getBlock':
          if (p.length !== 2) fail('REQUEST_INVALID'); slot(p[0]); finality(p[1], ['commitment', 'encoding', 'transactionDetails', 'rewards', 'maxSupportedTransactionVersion']);
          if (p[1].encoding !== 'json' || p[1].transactionDetails !== 'full' || p[1].rewards !== false || p[1].maxSupportedTransactionVersion !== 1) fail('REQUEST_INVALID'); break;
        case 'getAccountInfo':
          if (p.length !== 2) fail('REQUEST_INVALID'); address(p[0]); finality(p[1], ['commitment', 'encoding']); if (!['base64', 'jsonParsed'].includes(p[1].encoding)) fail('REQUEST_INVALID'); break;
        case 'getTokenSupply': case 'getTokenLargestAccounts':
          if (p.length !== 2) fail('REQUEST_INVALID'); address(p[0]); finality(p[1]); break;
        default: fail('REQUEST_INVALID');
      }
    } else fail('REQUEST_INVALID');
  }
  return plan;
}
function used(dir) {
  if (!fs.existsSync(dir)) return 0; let total = 0;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.isSymbolicLink()) fail('UNSAFE_PATH'); const file = path.join(dir, entry.name);
    total += entry.isDirectory() ? used(file) : fs.statSync(file).size;
  } return total;
}
function canonical(root) {
  const resolved = path.resolve(root), repo = fs.realpathSync(path.resolve(__dirname, '../../..')).toLowerCase();
  let ancestor = resolved; const suffix = [];
  while (!fs.existsSync(ancestor)) { suffix.unshift(path.basename(ancestor)); ancestor = path.dirname(ancestor); }
  const actual = path.join(fs.realpathSync(ancestor), ...suffix), lower = actual.toLowerCase();
  if (lower === repo || lower.startsWith(repo + path.sep) || /(?:^|[\\/])target(?:[\\/]|$)/i.test(actual)) fail('UNSAFE_PATH'); return actual;
}
function durable(file, bytes, flags) {
  const fd = fs.openSync(file, flags);
  try { let at = 0; while (at < bytes.length) { const count = fs.writeSync(fd, bytes, at, bytes.length - at); if (!count) fail('STORAGE_ERROR'); at += count; } fs.fsyncSync(fd); }
  finally { fs.closeSync(fd); }
}
function send(spec, { signal, onChunk, onHeaders }) {
  return new Promise((resolve, reject) => {
    let response, done = false, remaining = caps.response;
    const finish = (error, value) => { if (done) return; done = true; signal.removeEventListener('abort', abort); if (error) { req.destroy(); reject(error); } else resolve(value); };
    const abort = () => finish(Object.assign(Error('CANCELLED'), { safeCode: 'CANCELLED' }));
    const req = https.request(spec.url, { method: spec.method, agent: false, headers: { Accept: 'application/json', ...(spec.body ? { 'Content-Type': 'application/json' } : {}) } }, res => {
      response = res;
      try { onHeaders(res.statusCode); } catch (e) { finish(e); return; }
      res.on('readable', () => {
        try {
          // Drain available bytes without waiting for a full read-sized chunk or EOF.
          let bytes; while (!done && res.readableLength > 0 && (bytes = res.read(Math.min(65536, remaining, res.readableLength))) !== null) {
            remaining = onChunk(bytes); if (!remaining) fail('RESPONSE_LIMIT');
          }
          // An empty buffer still needs a read to let the stream emit its pending end event.
          if (!done) res.read(0);
        } catch (e) { finish(e); }
      });
      res.on('end', () => finish(null, { ended: true })); res.on('error', error => finish(error));
      res.on('close', () => { if (!res.complete) finish(null, { ended: false }); });
    });
    req.on('error', error => finish(error)); signal.addEventListener('abort', abort, { once: true });
    if (signal.aborted) abort(); else req.end(spec.body ? JSON.stringify(spec.body) : undefined);
  });
}
async function run(options) {
  const result = { reason: 'DISABLED', attempts: 0, received: 0, records: [] }; if (!options.enabled) return result;
  const clock = options.clock || { now: Date.now, sleep: ms => new Promise(resolve => setTimeout(resolve, ms)), setTimeout, clearTimeout };
  const space = options.free || (dir => { const s = fs.statfsSync(dir, { bigint: true }); return Number(s.bavail * s.bsize); });
  const bytesUsed = options.used || used, write = options.write || durable; let root, dir, started, deadline, lastStart;
  const code = error => error?.safeCode || 'STORAGE_ERROR';
  const guard = bytes => {
    if (space(root) - bytes < caps.free) fail('FREE_SPACE_LIMIT');
    if (bytesUsed(root) + bytes > caps.disk) fail('DISK_LIMIT');
    if (dir && bytesUsed(dir) + bytes > caps.evidence) fail('EVIDENCE_LIMIT');
  };
  const save = (name, bytes, flags = 'wx') => { guard(bytes.length); try { write(path.join(dir, name), bytes, flags); } catch (e) { fail(code(e)); } };
  const json = (name, value) => save(name, Buffer.from(JSON.stringify(value)));
  try {
    try { validate(options.plan); } catch { fail('REQUEST_INVALID'); }
    root = canonical(options.root || 'C:/crypto-research-evidence'); dir = path.join(root, 'provider-feasibility');
    if (fs.existsSync(dir)) fail('OUTPUT_REUSE');
    if (fs.existsSync(path.join(root, 'alchemy-s1', 'active.lock'))) fail('S1_ACTIVE');
    fs.mkdirSync(root, { recursive: true }); guard(caps.evidence); fs.mkdirSync(dir);
    started = clock.now(); deadline = started + caps.batchMs;
    json('manifest.json', { incomplete: true, referenceEnd: options.plan.referenceEnd, requests: options.plan.requests, caps, startedAt: new Date(started).toISOString(),
      deadline: new Date(deadline).toISOString(), evidenceReservation: caps.evidence, sourceSha256: crypto.createHash('sha256').update(fs.readFileSync(__filename)).digest('hex'), node: process.version,
      interpretation: 'Completion describes response transport/syntax only, not dataset coverage. Raw quantities retained unchanged. No historical correctness or provider-selection verdict.' });
    for (const spec of options.plan.requests) {
      if (clock.now() >= deadline) fail('BATCH_DEADLINE');
      if (lastStart !== undefined) await clock.sleep(Math.max(0, Math.min(caps.spacingMs - (clock.now() - lastStart), deadline - clock.now())));
      if (clock.now() >= deadline) fail('BATCH_DEADLINE');
      if (result.received + caps.response > caps.received) fail('RECEIVED_LIMIT'); guard(caps.response + 65536);
      const index = result.attempts + 1, name = String(index).padStart(3, '0'), raw = name + '.raw'; save(raw, Buffer.alloc(0));
      const sent = clock.now(), requestDeadline = Math.min(deadline, sent + caps.requestMs);
      const record = { index, request: spec, raw, sentAt: new Date(sent).toISOString(), receivedAt: null, bytes: 0, complete: false, reason: 'IN_PROGRESS' };
      json(name + '.request.json', { ...record, reservedResponse: caps.response, requestDeadline });
      result.attempts++; lastStart = sent; const controller = new AbortController(), digest = crypto.createHash('sha256'), chunks = []; let timer, failure, ended = false;
      const expired = () => clock.now() >= deadline ? 'BATCH_DEADLINE' : 'REQUEST_DEADLINE';
      try {
        const timed = new Promise((resolve, reject) => { timer = clock.setTimeout(() => { failure = expired(); controller.abort(); reject(Object.assign(Error(failure), { safeCode: failure })); }, Math.max(0, requestDeadline - clock.now())); });
        const response = await Promise.race([timed, (options.transport || send)(spec, { signal: controller.signal,
          onHeaders: status => { record.status = status; }, onChunk: chunk => {
            if (controller.signal.aborted) fail(failure || 'CANCELLED');
            if (clock.now() >= requestDeadline) fail(expired());
            const bytes = chunk.subarray(0, caps.response - record.bytes); record.bytes += bytes.length; result.received += bytes.length;
            save(raw, bytes, 'a'); digest.update(bytes); chunks.push(bytes);
            if (record.bytes >= caps.response) fail('RESPONSE_LIMIT'); return caps.response - record.bytes;
          } })]); ended = response.ended === true;
      } catch (error) { failure ||= error.safeCode || 'NETWORK_ERROR'; controller.abort(); }
      finally { clock.clearTimeout(timer); }
      record.receivedAt = new Date(clock.now()).toISOString(); record.sha256 = digest.digest('hex');
      // A failed filesystem write can retain only a prefix. Hash what actually reached the file.
      try { const retained = fs.readFileSync(path.join(dir, raw)); record.retainedBytes = retained.length; record.sha256 = crypto.createHash('sha256').update(retained).digest('hex'); }
      catch { failure = 'STORAGE_ERROR'; }
      if (!failure) {
        if (record.status >= 300 && record.status < 400) failure = 'REDIRECT';
        else if ([401, 403].includes(record.status)) failure = 'AUTH_DENIED';
        else if (!(record.status >= 200 && record.status < 300)) failure = 'HTTP_ERROR';
        else if (!ended) failure = 'PARTIAL'; else if (!record.bytes) failure = 'EMPTY';
        else {
          // Parsing only classifies syntax/provider errors. Raw bytes are authoritative; never reserialize numeric facts.
          try {
            const text = Buffer.concat(chunks).toString('utf8'); let values;
            try { values = [JSON.parse(text)]; } catch { values = text.trim().split(/\r?\n/).map(line => JSON.parse(line)); }
            if (values.every(v => v === null || (Array.isArray(v) && !v.length))) failure = 'EMPTY';
            if (values.some(v => v && typeof v === 'object' && (v.error || (spec.url.includes('gopluslabs.io') && v.code !== undefined && v.code !== 1)))) failure = 'PROVIDER_ERROR';
          } catch { failure = 'MALFORMED'; }
        }
      }
      record.reason = failure || 'COMPLETE_RESPONSE'; record.complete = !failure; result.records.push(record);
      json(name + '.result.json', record); if (failure) fail(failure);
    }
    result.reason = 'LIST_EXHAUSTED';
  } catch (error) { result.reason = code(error); }
  if (started !== undefined) {
    result.endedAt = new Date(clock.now()).toISOString();
    try { json('summary.json', result); } catch (error) { result.reason = code(error); }
  }
  return result;
}
module.exports = { run, validate, caps };
