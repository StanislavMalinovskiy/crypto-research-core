'use strict';
const fs = require('node:fs'), path = require('node:path'), https = require('node:https');
const { execFileSync, spawnSync } = require('node:child_process');
const crypto = require('node:crypto');
const { parse, canonical, digest, fingerprint, integer } = require('./exploratory-probe.cjs');
const census = require('./e2-offline-mint-count.cjs'), { mapHeliusTransaction } = require('./pumpswap-mapper-v2.cjs');
const VERSION = 'e2-cohort-d1-window-v1', ROOT = 'C:\\crypto-research-evidence\\r1-e2\\d1-window-v1-20261004';
const REPO = path.resolve(__dirname,'../../..');
const STOP = Date.parse('2026-10-05T04:42:41Z'), START = Date.parse('2026-10-04T16:42:41Z');
const LIMITS = Object.freeze({ credits: 450000, tokenCredits: 1400, pages: 14, history: 4200, ancillary: 30000,
  response: 64000000, timeout: 60000, publication: 300000, spacing: 250, retained: 50000000000, free: 30000000000,
  metadata: 32000000, received: 50000000000, workerHeapMb:1024, workerRss:1750000000, offlineMs:300000, signatures:4200000 });
const FLAGS = Object.freeze({ classification: 'EXPLORATORY', deployment: 'UNVERIFIED', cohortAdmitted: false, d1Passed: false });
const need = (v, code = 'INTEGRITY_ERROR') => { if (!v) throw Error(code); };
const uint = v => Number.isSafeInteger(v) && v >= 0;
const hash = h => typeof h === 'string' && /^sha256:[0-9a-f]{64}$/.test(h);
const seal = (kind, body) => ({ ...body, hash: fingerprint(VERSION + '-' + kind, body) });
function verify(kind, value) { const { hash: h, ...body } = value; need(hash(h) && h === fingerprint(VERSION + '-' + kind, body)); return body; }
const json = v => Buffer.from(JSON.stringify(v) + '\n');
// Exact keys and content digests, compact in bounded chunks; hash collisions probe
// and compare the full signature. This replaces only the replay's in-memory Map.
class SignatureIndex {
  constructor(){this.size=0;this.table=null;this.chunks=[];}
  locate(signature){
    need(typeof signature === 'string' && signature.length>0 && signature.length<=88);
    const key=Buffer.from(signature,'ascii');let slot=crypto.createHash('sha256').update(key).digest().readUInt32LE(0)&8388607;
    if(!this.table)return {key,slot,id:0};
    while(this.table[slot]){const id=this.table[slot]-1,b=this.chunks[Math.floor(id/10000)],at=(id%10000)*121;
      if(b[at]===key.length&&b.subarray(at+1,at+1+key.length).equals(key))return {key,slot,id:id+1,b,at};slot=(slot+1)&8388607;}
    return {key,slot,id:0};
  }
  set(signature,content){need(hash(content));const p=this.locate(signature),value=Buffer.from(content.slice(7),'hex');
    if(p.id){need(p.b.subarray(p.at+89,p.at+121).equals(value),'IMMUTABLE_CONFLICT');return this;}
    need(this.size<LIMITS.signatures,'INPUT_LIMIT');this.table??=new Uint32Array(8388608);
    const chunk=Math.floor(this.size/10000),at=(this.size%10000)*121;this.chunks[chunk]??=Buffer.alloc(1210000);
    const b=this.chunks[chunk];b[at]=p.key.length;p.key.copy(b,at+1);value.copy(b,at+89);this.table[p.slot]=++this.size;return this;
  }
}
function resourceCheck(start=performance.now()){
  need(process.memoryUsage().rss<=LIMITS.workerRss,'RESOURCE_LIMIT');need(performance.now()-start<LIMITS.offlineMs,'TIME_LIMIT');
}
function query(address, cursor = null) {
  census.decode(address, 32); need(cursor === null || typeof cursor === 'string' && cursor.length <= 40 && /^(0|[1-9][0-9]*):(0|[1-9][0-9]*)$/.test(cursor), 'CURSOR_INVALID');
  if(cursor !== null)cursor.split(':').forEach(integer);
  return { method: 'POST', url: 'https://mainnet.helius-rpc.com/', body: { jsonrpc: '2.0', id: 1, method: 'getTransactionsForAddress',
    params: [address, { commitment: 'finalized', transactionDetails: 'full', encoding: 'json', maxSupportedTransactionVersion: 1,
      sortOrder: 'asc', limit: 1000, filters: { blockTime: { gte: 1775001600, lt: 1788134400 }, status: 'any', tokenAccounts: 'none' },
      ...(cursor === null ? {} : { paginationToken: cursor }) }] } };
}
function position(row) { return [integer(row.slot), row.transactionIndex === undefined ? null : integer(row.transactionIndex)]; }
// Locate original JSON value slices; numeric lexemes and transaction errors stay
// exactly as supplied. This is source framing, not an economic parser.
function rawRows(bytes) {
  const text = new TextDecoder('utf-8', { fatal:true }).decode(bytes); let at = 0;
  const ws = () => { while (/\s/.test(text[at] ?? '') && at < text.length) at++; };
  const skip = () => { ws(); const start = at, ch = text[at++];
    if (ch === '"') { while (at < text.length) { const c = text[at++]; if(c === '\\') at++; else if(c === '"') break; } }
    else if(ch === '{' || ch === '[') { const end = ch === '{' ? '}' : ']'; ws();
      while(text[at] !== end) { skip(); ws(); if(text[at] === ':' || text[at] === ',') at++; else need(text[at] === end); ws(); } at++; }
    else { while(at < text.length && !/[\s,\]}]/.test(text[at])) at++; }
    return [start,at]; };
  const property = name => { ws(); need(text[at++] === '{'); ws(); while(text[at] !== '}') {
      const [a,b] = skip(), key = JSON.parse(text.slice(a,b)); ws(); need(text[at++] === ':'); ws();
      if(key === name) return; skip(); ws(); if(text[at] === ',') at++; else need(text[at] === '}'); ws(); }
    throw Error('INTEGRITY_ERROR'); };
  property('result'); property('data'); need(text[at++] === '['); const rows = []; ws();
  while(text[at] !== ']') { const [a,b] = skip(); rows.push(Buffer.from(text.slice(a,b))); ws(); if(text[at] === ',') at++; else need(text[at] === ']'); ws(); }
  return rows;
}
function admit(bytes, q) {
  try {
    need(Buffer.isBuffer(bytes) && bytes.length <= LIMITS.response, 'RESPONSE_LIMIT');
    need(canonical(q) === canonical(query(q.body.params[0], q.body.params[1].paginationToken ?? null)), 'QUERY_INVALID');
    const r = parse(bytes); need(r.jsonrpc === '2.0' && integer(r.id) === 1); need(!Object.hasOwn(r, 'error'), 'RPC_ERROR');
    need(r.result && Array.isArray(r.result.data) && r.result.data.length <= 1000);
    const seen = new Map(), rows = [], raw = rawRows(bytes), rowBytes = []; need(raw.length === r.result.data.length);
    for (const [index,row] of r.result.data.entries()) {
      const time = integer(row.blockTime); need(time >= 1775001600 && time < 1788134400, 'FORBIDDEN_TIME');
      need(row.transaction && typeof row.transaction === 'object' && Array.isArray(row.transaction.signatures));
      const sig = row.transaction.signatures[0]; census.decode(sig, 64); position(row);
      if (row.signature !== undefined) need(row.signature === sig, 'IMMUTABLE_CONFLICT');
      need(row.confirmationStatus === undefined || row.confirmationStatus === 'finalized', 'FINALITY_INVALID');
      const content = canonical(row); if (seen.has(sig)) need(seen.get(sig) === content, 'IMMUTABLE_CONFLICT');
      else { seen.set(sig, content); rows.push(row); rowBytes.push(raw[index]); }
    }
    const token = r.result.paginationToken; need(token === null || typeof token === 'string' && token.length <= 40 && /^(0|[1-9][0-9]*):(0|[1-9][0-9]*)$/.test(token), 'CURSOR_INVALID');
    const prior = q.body.params[1].paginationToken ?? null;
    if(prior){const c=prior.split(':').map(integer);for(const row of rows){const p=position(row);
      need(p[0]>c[0]||p[0]===c[0]&&p[1]!==null&&p[1]>c[1],'CURSOR_INVALID');}}
    const compareCursor = (a, b) => { const x = a.split(':').map(integer), y = b.split(':').map(integer); return x[0] - y[0] || x[1] - y[1]; };
    if (token !== null) { need(rows.length > 0 && (!prior || compareCursor(token, prior) > 0), 'CURSOR_INVALID');
      const p = position(rows.at(-1)), c = token.split(':').map(integer); need(c[0] === p[0] && (p[1] === null || c[1] === p[1]), 'CURSOR_INVALID'); }
    else if(prior && rows.length) { const p=position(rows.at(-1)),c=prior.split(':').map(integer);
      need(p[0] > c[0] || p[0] === c[0] && (p[1] === null || p[1] > c[1]),'CURSOR_INVALID'); }
    for (let i = 1; i < rows.length; i++) { const a = position(rows[i - 1]), b = position(rows[i]); need(b[0] >= a[0] && (b[0] !== a[0] || a[1] === null || b[1] === null || b[1] >= a[1]), 'ORDER_INVALID'); }
    return { code: null, rows, rowBytes, coverage:rows.some(r=>r.transactionIndex === undefined) ? 'UNVERIFIED_POSITION' : 'INDEXED_RANGE_TERMINATED_IF_NULL',paginationToken: token, returnedRows: r.result.data.length };
  } catch (e) { return { code: cleanCode(e), rows: [] }; }
}
// Exact census row/metadata semantics come from the unchanged count tool.
function collector(roots = census.ROOTS) {
  const reducer = new census.Reducer(roots), pools = new Map();
  return { add(row, at) {
    reducer.add(row, at);
    if (row.status !== 'OBSERVED_DECLARED_CREATE_POOL' || row.accountCount !== 18 || ![59, 60].includes(row.dataLength)) return;
    const oriented = census.orient(row); if (!oriented.mint) return;
    const item = pools.get(oriented.mint) ?? new Set(); item.add(row.pool); pools.set(oriented.mint, item);
  }, finish(inputs) {
    need(Array.isArray(inputs) && inputs.length <= 15 && inputs.every(i => uint(i.bytes) && hash(i.hash)) && inputs.reduce((n, i) => n + i.bytes, 0) <= 200000000, 'INPUT_LIMIT');
    const groups = [[], [], []], eligible = [];
    for (const [mint, [creation]] of reducer.mints) {
      const stratum = census.DATES.findIndex((t, i) => i < 3 && creation.timestamp >= t && creation.timestamp < census.DATES[i + 1]);
      need(stratum >= 0); const decoded = census.decode(mint, 32), mintHash = digest(decoded);
      const item = { mint, mintHash, month: ['april', 'may', 'june'][stratum], creation, addresses: [...pools.get(mint)].sort((a, b) => Buffer.compare(census.decode(a, 32), census.decode(b, 32))) };
      groups[stratum].push(item); eligible.push(item);
    }
    const compare = (a, b) => Buffer.compare(Buffer.from(a.mintHash.slice(7), 'hex'), Buffer.from(b.mintHash.slice(7), 'hex')) || Buffer.compare(census.decode(a.mint, 32), census.decode(b.mint, 32));
    groups.forEach(g => g.sort(compare)); eligible.sort((a, b) => Buffer.compare(census.decode(a.mint, 32), census.decode(b.mint, 32)));
    need(groups.every(g => g.length >= 100), 'INSUFFICIENT_STRATA');
    return seal('selection', { ...FLAGS, version: VERSION, algorithm: 'SHA256_RAW32_UNSIGNED_DIGEST_THEN_MINT', eligibility: 'OBSERVED_DOCUMENTARY_18_ACCOUNTS_59_OR60_BYTES',
      limitations: census.UNKNOWN, censusCounts: reducer.result(), counts: groups.map(g => g.length), inputs,
      inputHash: fingerprint(VERSION + '-inputs', inputs), eligibilityHash: fingerprint(VERSION + '-eligibility', eligible), tokens: groups.flatMap(g => g.slice(0, 100)) });
  } };
}
function selectPinned() {
  const began=performance.now();resourceCheck(began);
  need(Date.now() < STOP,'TIME_LIMIT');
  const c = collector();
  const result = census.inspect({ reader(root, name) {
    const r = census.reader(root, name), at = census.ROOTS.findIndex(x => x.suffix === root.suffix);
    const lines = name === 'creations.jsonl' ? new census.Lines(b => c.add(census.parsedRow(b), at)) : null;
    return { size: r.size, read() { resourceCheck(began);need(Date.now() < STOP,'TIME_LIMIT');const bytes = r.read(); if (lines && bytes.length) lines.feed(bytes); return bytes; }, close() { try { lines?.finish(); } finally { r.close(); } } };
  } });
  need(result.code === null && result.status === 'QUALIFIED_OFFLINE_COUNT', result.code ?? 'INTEGRITY_ERROR'); return c.finish(result.inputs);
}
function validSelection(s) {
  verify('selection', s); need(s.version === VERSION && s.tokens.length === 300 && s.classification === 'EXPLORATORY' && s.cohortAdmitted === false);
  need(s.algorithm === 'SHA256_RAW32_UNSIGNED_DIGEST_THEN_MINT' && s.eligibility === 'OBSERVED_DOCUMENTARY_18_ACCOUNTS_59_OR60_BYTES');
  const mints = new Set(); s.tokens.forEach((t, i) => { need(!mints.has(t.mint)); mints.add(t.mint); need(t.mintHash === digest(census.decode(t.mint, 32)));
    const m = Math.floor(i / 100); need(t.creation.timestamp >= census.DATES[m] && t.creation.timestamp < census.DATES[m + 1]);
    need(t.month === ['april', 'may', 'june'][m] && Array.isArray(t.addresses) && t.addresses.length > 0 && new Set(t.addresses).size === t.addresses.length && !t.addresses.includes(t.mint));
    t.addresses.forEach(a => census.decode(a, 32));
    if (i % 100) need(Buffer.compare(Buffer.from(s.tokens[i - 1].mintHash.slice(7), 'hex'), Buffer.from(t.mintHash.slice(7), 'hex')) <= 0);
  }); return s;
}
function initial(selection, attestation, lineage) {
  validSelection(selection);
  return { ...FLAGS, version: VERSION, selection, attestation, lineage, phase: 'FIRST_FIVE', code: null, qualification: null,
    creditsReserved: attestation.creditsReservedBefore, actualCredits: null, historyStarts: 0, actualHistoryStarts: 0,
    ancillaryStarts: attestation.ancillaryStartsBefore, publicStarts: attestation.publicStartsBefore, received: attestation.receivedBefore, retained: 0, lastStart: null,
    openingCredits: { ownerReportedUsed:31620,limit:1000000,noUnrelatedUsage:'UNVERIFIED',actualDebit:'DASHBOARD_RECONCILIATION_REQUIRED' },
    tokens: selection.tokens.map(t => ({ mint: t.mint, pages: 0, credits: 0, addressIndex: 0, cursor: null, status: 'NOT_STARTED', rows: 0 })), records: [], ancillary: [], crossCheck: null };
}
function reserve(state, tokenIndex, kind = 'history', credits = 100) {
  need(uint(credits) && credits >= (kind === 'history' ? 100 : 1), 'TARIFF_INVALID');
  need(state.creditsReserved + credits <= LIMITS.credits, 'CREDIT_LIMIT');
  if (kind === 'history') {
    const t = state.tokens[tokenIndex]; need(t && t.pages < LIMITS.pages && t.credits + credits <= LIMITS.tokenCredits, 'TOKEN_CAP');
    need(state.historyStarts < LIMITS.history, 'HISTORY_CAP'); t.pages++; t.credits += credits; state.historyStarts++;
  } else { need(kind === 'ancillary' && state.ancillaryStarts < LIMITS.ancillary, 'ANCILLARY_CAP'); state.ancillaryStarts++; }
  state.creditsReserved += credits;
}
function capacity(attestation, now, extraBytes = 0, received = 0) {
  need(uint(now) && now >= START && now + LIMITS.timeout + LIMITS.publication <= STOP, 'TIME_LIMIT');
  need(attestation && attestation.plan === 'Free' && attestation.entitlement === 'VERIFIED' && attestation.historyCredits === 100 && hash(attestation.tariffEvidenceHash), 'TARIFF_INVALID');
  need(attestation.poolIndexCoverage === 'DOCUMENTED_INDEXED_POOL_ADDRESSES' && hash(attestation.poolIndexEvidenceHash), 'PREFLIGHT_INVALID');
  need(uint(attestation.creditsReservedBefore) && attestation.creditsReservedBefore <= LIMITS.credits && uint(attestation.ancillaryStartsBefore)
    && attestation.ancillaryStartsBefore <= LIMITS.ancillary && attestation.creditsReservedBefore >= attestation.ancillaryStartsBefore
    && uint(attestation.publicStartsBefore) && attestation.publicStartsBefore <= 300 && uint(attestation.receivedBefore), 'PREFLIGHT_INVALID');
  need(uint(attestation.retainedBytes) && uint(attestation.freeBytes) && uint(extraBytes) && uint(received), 'PREFLIGHT_INVALID');
  need(now >= Date.parse(attestation.observedAt) && now <= Date.parse(attestation.validUntil) && now + LIMITS.timeout + LIMITS.publication < Date.parse(attestation.expiry), 'EXPIRY');
  const reservation = LIMITS.response + LIMITS.metadata;
  need(attestation.retainedBytes + extraBytes + reservation < LIMITS.retained, 'DISK_LIMIT');
  need(attestation.freeBytes - extraBytes - reservation >= LIMITS.free, 'FREE_SPACE_LIMIT');
  need(received + LIMITS.response <= LIMITS.received, 'RECEIVED_LIMIT');
  return attestation.retainedBytes + extraBytes + reservation >= LIMITS.retained * 0.8 ? 'CHECKPOINT_80' : 'WITHIN_LIMIT';
}
function applyPage(state, at, admitted) {
  need(admitted.code === null); const t = state.tokens[at]; t.rows += admitted.returnedRows;
  if(admitted.coverage === 'UNVERIFIED_POSITION')t.coverageUnknown=true;
  t.cursor = admitted.paginationToken;
  if (t.cursor === null) t.addressIndex++;
  t.status = t.addressIndex === state.selection.tokens[at].addresses.length ? t.coverageUnknown ? 'INCOMPLETE' : 'COMPLETE' : t.pages >= LIMITS.pages ? 'INCOMPLETE' : 'IN_PROGRESS';
}
const errors = new Set(['DISABLED','ARGUMENTS_INVALID','INPUT_LIMIT','INSUFFICIENT_STRATA','INTEGRITY_ERROR','FORBIDDEN_TIME','RESPONSE_LIMIT','QUERY_INVALID','RPC_ERROR','CURSOR_INVALID','FINALITY_INVALID','ORDER_INVALID','IMMUTABLE_CONFLICT','TOKEN_CAP','CREDIT_LIMIT','HISTORY_CAP','ANCILLARY_CAP','TARIFF_INVALID','PREFLIGHT_INVALID','TIME_LIMIT','EXPIRY','DISK_LIMIT','FREE_SPACE_LIMIT','RECEIVED_LIMIT','UNSAFE_PATH','OUTPUT_EXISTS','STORAGE_ERROR','SECRET_UNAVAILABLE','SECRET_EXPOSURE','NETWORK_ERROR','TIMEOUT','HTTP_ERROR','PARTIAL_RESPONSE','QUALIFICATION_REQUIRED','QUALIFICATION_FAILED','SOURCE_STOPPED','CLOCK_INVALID','FREEZE_INVALID','LINEAGE_CHANGED','INCOMPLETE_START']);
errors.add('RESOURCE_LIMIT');
function cleanCode(e) { const c = typeof e === 'string' ? e : e?.message; return errors.has(c) ? c : 'INTEGRITY_ERROR'; }
function ancestors(file, directory = false) {
  let current = path.resolve(file), first = true;
  for (;;) { const s = fs.lstatSync(current); need(!s.isSymbolicLink() && fs.realpathSync.native(current).toLowerCase() === current.toLowerCase(), 'UNSAFE_PATH');
    need(first && !directory ? s.isFile() : s.isDirectory(), 'UNSAFE_PATH'); first = false; const next = path.dirname(current); if (next === current) break; current = next; }
}
function store() {
  const parent = path.dirname(ROOT), allowed = /^(manifest\.json|manifest\.tmp|selection\.json|summary\.json|lock|ancillary-[0-9]{5}(\.receipt)?\.json|[0-9]{5}\.(start\.json|receipt\.json|raw))$/;
  const file = name => { need(allowed.test(name), 'UNSAFE_PATH'); return path.join(ROOT, name); };
  return {
    create() { ancestors(parent, true); try { fs.mkdirSync(ROOT); } catch (e) { throw Error(e.code === 'EEXIST' ? 'OUTPUT_EXISTS' : 'STORAGE_ERROR'); } },
    read(name) { const p = file(name); ancestors(p); need(fs.statSync(p).size <= (name.endsWith('.raw') ? LIMITS.response : LIMITS.metadata), 'INPUT_LIMIT'); return fs.readFileSync(p); },
    write(name, bytes, replace = false) { ancestors(ROOT, true); need(bytes.length <= (name.endsWith('.raw') ? LIMITS.response : LIMITS.metadata), 'INPUT_LIMIT');
      const p = file(name); if (fs.existsSync(p)) ancestors(p); const fd = fs.openSync(p, replace ? 'w' : 'wx'); try { fs.writeFileSync(fd, bytes); fs.fsyncSync(fd); } finally { fs.closeSync(fd); } },
    publish(value) { this.write('manifest.tmp', json(seal('manifest', value))); fs.renameSync(file('manifest.tmp'), file('manifest.json')); },
    list() { ancestors(ROOT, true); return fs.readdirSync(ROOT); },
    size() { return this.list().filter(n=>n !== 'lock').reduce((n, name) => { const p = file(name); ancestors(p); return n + fs.statSync(p).size; }, 0); },
    free() { ancestors(parent, true); const s = fs.statfsSync(parent, { bigint: true }); return Number(s.bavail * s.bsize); },
    lock() { this.write('lock', json({ pid: process.pid })); }, unlock() { fs.unlinkSync(file('lock')); }
  };
}
function readSmall(file, limit = 1000000) { ancestors(file); need(fs.statSync(file).size <= limit, 'INPUT_LIMIT'); return parse(fs.readFileSync(file), false); }
function lineage() { return { runtime: process.version, source: { commit: execFileSync('git', ['rev-parse','HEAD'], { cwd:REPO,encoding: 'utf8', timeout: 5000 }).trim(),
  dirty: execFileSync('git', ['status','--porcelain'], { cwd:REPO,encoding: 'utf8', timeout: 5000 }).trim().length > 0 },
  scripts: Object.fromEntries(['e2-cohort-d1-window.cjs','pumpswap-mapper-v2.cjs','e2-offline-mint-count.cjs','exploratory-probe.cjs'].map(n => [n, digest(fs.readFileSync(path.join(__dirname,n)))])) };
}
function freezeProof(commit) {
  need(typeof commit === 'string' && /^[0-9a-f]{40}$/.test(commit), 'FREEZE_INVALID');
  const read = name => execFileSync('git', ['cat-file','blob', `${commit}:docs/research/${name}`], { cwd:REPO,timeout: 5000, maxBuffer: 1000000 });
  const protocol = read('R1_E2_RESEARCH_PROTOCOL.md'), record = read('R1_E2_PROTOCOL_FREEZE.md').toString('utf8');
  const sha = digest(protocol); need(record.includes('`' + sha.slice(7) + '`') && protocol.toString('utf8').includes('## 10. Owner-approved bounded D1 window'), 'FREEZE_INVALID');
  need(execFileSync('git', ['merge-base','--is-ancestor',commit,'HEAD'], { cwd:REPO,timeout: 5000 }).length === 0, 'FREEZE_INVALID');
  return { commit, protocolHash: sha, recordHash: digest(Buffer.from(record)) };
}
function secret() {
  try {
  let value = process.env.CRYPTO_HELIUS_API_KEY;
  if (!value) { const file = path.resolve(__dirname,'../../../config/application-managed-secrets.properties');
    ancestors(file); need(fs.statSync(file).size <= 1000000,'SECRET_UNAVAILABLE');
    const text = fs.readFileSync(file, 'utf8'); need(Buffer.byteLength(text) <= 1000000, 'SECRET_UNAVAILABLE');
    const lines = text.split(/\r?\n/).filter(l => /^\s*CRYPTO_HELIUS_API_KEY\s*[=:]/.test(l)); need(lines.length === 1, 'SECRET_UNAVAILABLE');
    value = lines[0].replace(/^\s*CRYPTO_HELIUS_API_KEY\s*[=:]\s*/, '').trim(); }
  need(/^[A-Za-z0-9_-]{8,256}$/.test(value), 'SECRET_UNAVAILABLE'); return value;
  } catch {throw Error('SECRET_UNAVAILABLE');}
}
function send(q, key) {
  return new Promise(resolve => {
    let done = false, received = 0, status = null, res, req, timer; const chunks = [];
    const finish = (code, bytes = null) => { if (done) return; done = true; clearTimeout(timer); if (code) { res?.destroy(); req?.destroy(); }
      resolve({ code, status, received, bytes }); };
    try {
      req = https.request(q.url + '?api-key=' + encodeURIComponent(key), { method: 'POST', agent: false, headers: { 'Content-Type': 'application/json' } }, response => {
        res = response; status = res.statusCode;
        res.on('readable', () => { if(done) return; while(res.readableLength > 0) {
          const b=res.read(Math.min(res.readableLength,LIMITS.response + 1 - received)); if(!b) break;
          received+=b.length; if(received > LIMITS.response) return finish('RESPONSE_LIMIT'); chunks.push(b);
        } res.read(0); });
        res.on('end', () => { const bytes = Buffer.concat(chunks); if (bytes.includes(Buffer.from(key))) return finish('SECRET_EXPOSURE');
          // Decode escaped secret strings too; never persist provider error bodies.
          try { if (JSON.stringify(parse(bytes)).includes(key)) return finish('SECRET_EXPOSURE'); } catch { /* admission checks JSON */ }
          finish(status === 200 ? res.complete ? null : 'PARTIAL_RESPONSE' : 'HTTP_ERROR', status === 200 ? bytes : null); });
        res.on('aborted', () => finish('PARTIAL_RESPONSE')); res.on('error', () => finish('PARTIAL_RESPONSE'));
      });
      req.on('error', () => finish('NETWORK_ERROR')); timer = setTimeout(() => finish('TIMEOUT'), LIMITS.timeout); req.end(JSON.stringify(q.body));
    } catch { finish('NETWORK_ERROR'); }
  });
}
function mappingCounts(rows, rowBytes = rows.map(json), check = () => {}) {
  const counts = { transactions: rows.length, invalid: 0, pumpInvocations: 0, ownedFacts: 0, pathUnknown: 0, recoverableKeys: 0, recoverableOwners: 0, recoverableCpi: 0, reconstructed: 0 };
  for (const [index,row] of rows.entries()) {
    check(); const raw = rowBytes[index], r = mapHeliusTransaction(raw, digest(raw));
    if (r.status === 'MAPPING_INVALID') { counts.invalid++; continue; }
    counts.recoverableKeys++; if (r.pathStatus === 'RESOLVED' && r.sourceFacts.innerInstructionsStatus !== 'UNKNOWN') counts.recoverableCpi++;
    counts.pumpInvocations += r.invocations.length; counts.ownedFacts += r.ownedDeltas.filter(t => t.ownedDelta !== null).length;
    if (r.tokenStates.some(t => t.preOwner !== null && t.postOwner !== null && t.preOwner === t.postOwner)) counts.recoverableOwners++;
    if (r.pathStatus === 'PATH_UNKNOWN') counts.pathUnknown++;
    // Existing mapper reports owned facts, not complete fee/tip/route economic actions.
  } return counts;
}
function publish(s,io) {
  // The digest length is fixed. Settle the final manifest's own byte count too.
  for(let i=0;i<4;i++) { io.publish(s); const bytes=io.size(); if(s.retained === bytes) return; s.retained=bytes; }
  throw Error('STORAGE_ERROR');
}
async function runStage(options, deps = {}) {
  if (options.enabled !== true) return { ...FLAGS, code: 'DISABLED' };
  const io = deps.store ?? store(), now = deps.now ?? Date.now, wait = deps.wait ?? (ms => new Promise(r => setTimeout(r,ms)));
  let s, locked = false, baselineSize = 0, signatures = new SignatureIndex();
  try {
    const a = options.attestation; capacity(a, now()); const currentLineage = deps.lineage ?? lineage();
    need(a.freeze && hash(a.freeze.protocolHash), 'FREEZE_INVALID');
    need(currentLineage.source?.dirty === false && /^[a-f0-9]{40}$/.test(currentLineage.source?.commit) && /^v24\./.test(currentLineage.runtime),'LINEAGE_CHANGED');
    const key = deps.transport ? null : secret(), transport = deps.transport ?? (q => send(q,key));
    if (options.phase === 'first-five') {
      s = initial(options.selection, a, currentLineage); io.create(); io.lock(); locked = true; io.write('selection.json', json(options.selection)); publish(s,io);
    } else {
      need(options.phase === 'continue', 'ARGUMENTS_INVALID');
      const manifest = parse(io.read('manifest.json'), false); s = verify('manifest', manifest); need(manifest.hash === options.head, 'INTEGRITY_ERROR');
      need(s.phase === 'QUALIFICATION_PENDING' && s.code === null, 'SOURCE_STOPPED');
      need(canonical(a.freeze) === canonical(s.attestation.freeze),'FREEZE_INVALID');
      need(a.creditsReservedBefore === s.creditsReserved && a.ancillaryStartsBefore === s.ancillaryStarts && a.publicStartsBefore === s.publicStarts
        && a.receivedBefore === s.received,'PREFLIGHT_INVALID');
      need(canonical(currentLineage.scripts) === canonical(s.lineage.scripts), 'LINEAGE_CHANGED');
      need(s.ancillary.every(r=>r.externalReceipt),'QUALIFICATION_REQUIRED');
      const priorReplay=replay(io, options.head, deps);need(priorReplay.recordedTiming.status==='PASS','TIME_LIMIT');
      signatures = priorReplay.signatures; const q = options.qualification;
      need(q && q.status === 'APPROVE' && q.manifestHash === options.head && hash(q.reviewHash) && q.technicalUsable === true, 'QUALIFICATION_REQUIRED');
      const technical = qualify(s); need(technical.technicalUsable === true, 'QUALIFICATION_FAILED');
      io.lock(); locked = true; s.qualification = q; s.phase = 'CONTINUING';
    }
    baselineSize = options.phase === 'first-five' ? 0 : io.size(); s.attestations = [...(s.attestations ?? []), a];
    let lastClock = now(); const clock = () => { const t = now(); need(uint(t) && t >= lastClock, 'CLOCK_INVALID'); lastClock = t; return t; };
    const end = options.phase === 'first-five' ? 5 : 300;
    for (let at = 0; at < end; at++) {
      const t = s.tokens[at]; if (options.phase === 'continue' && at < 5) continue;
      while (!['COMPLETE','INCOMPLETE'].includes(t.status)) {
        if (t.pages >= LIMITS.pages) { t.status = 'INCOMPLETE'; break; }
        if (s.lastStart !== null) { const until = s.lastStart + LIMITS.spacing; let n = clock();
          while (n < until) { await wait(until - n); const next = clock(); need(next > n, 'CLOCK_INVALID'); n = next; } }
        // Capacity is measured first, then time rechecked immediately before the reserved start.
        const extra = Math.max(0, io.size() - baselineSize); need(io.free() - LIMITS.response - LIMITS.metadata >= LIMITS.free, 'FREE_SPACE_LIMIT');
        s.checkpoint = capacity(a, clock(), extra, s.received);
        const address = s.selection.tokens[at].addresses[t.addressIndex], q = query(address,t.cursor), ordinal = s.records.length + 1;
        const name = String(ordinal).padStart(5,'0'); reserve(s, at);
        const record = { ordinal, tokenIndex: at, addressIndex: t.addressIndex, query: q, creditsReserved: 100, actualCredits: null, reservedAt: clock(), start: null, outcome: 'START_RESERVED' };
        s.records.push(record);
        io.write(name + '.start.json', json(record)); publish(s,io); capacity(a, clock(), Math.max(0,io.size() - baselineSize),s.received);
        const startExtra=Math.max(0,io.size()-baselineSize);resourceCheck();
        record.start = clock();capacity(a,record.start,startExtra,s.received);
        s.lastStart = record.start; record.transportStarted = true; s.actualHistoryStarts++;
        const response = await transport(q); record.end = clock(); record.received = response.received;
        need(uint(response.received) && response.received <= LIMITS.response + 1); s.received += response.received;
        record.code = response.code ?? null; record.status = response.status ?? null;
        if(response.bytes)record.responseHash=digest(response.bytes);
        if (!record.code) {
          need(response.status === 200 && Buffer.isBuffer(response.bytes) && response.bytes.length === response.received);
          const admitted = admit(response.bytes,q); record.code = admitted.code;
          if (!record.code) {
            const before=signatures.size;
            for(const row of admitted.rows) signatures.set(row.transaction.signatures[0],fingerprint(VERSION + '-tx',row));
            record.equalScopedRows=admitted.rows.length-(signatures.size-before);resourceCheck();
            record.rawHash = digest(response.bytes); record.rawBytes = response.bytes.length;
            need(clock() + LIMITS.publication <= STOP,'TIME_LIMIT');
            io.write(name + '.raw',response.bytes); record.mapping = mappingCounts(admitted.rows,admitted.rowBytes,() => need(clock() < STOP,'TIME_LIMIT'));
            record.returnedRows = admitted.returnedRows; record.cursor = admitted.paginationToken;
            applyPage(s,at,admitted);
          }
        }
        record.outcome = record.code ? 'STOPPED' : 'RETAINED'; io.write(name + '.receipt.json',json(record));
        if (record.code) { s.code = record.code; t.status = 'INCOMPLETE'; throw Error(record.code); }
        publish(s,io);
      }
    }
    s.phase = options.phase === 'first-five' ? 'QUALIFICATION_PENDING' : 'SOURCE_COMPLETE'; publish(s,io);
    return { ...FLAGS, code: null, phase: s.phase, manifestHash: seal('manifest',s).hash, qualification: qualify(s), creditsReserved: s.creditsReserved, actualCredits: s.actualCredits };
  } catch (e) {
    const code = cleanCode(e); if (s && locked) { s.code = code; s.phase = 'STOPPED'; try { publish(s,io); } catch { /* immutable reservation survives */ } }
    return { ...FLAGS, code, phase: s?.phase ?? 'NOT_STARTED', creditsReserved: s?.creditsReserved ?? 0, actualCredits: s?.actualCredits ?? null };
  } finally { if (locked) try { io.unlock(); } catch { /* retained lock blocks unsafe continuation */ } }
}
function qualify(s) {
  const totals = { transactions: 0, pumpInvocations: 0, recoverableKeys: 0, recoverableOwners: 0, recoverableCpi: 0, invalid: 0, pathUnknown: 0 };
  for (const r of s.records.filter(x => x.tokenIndex < 5 && x.mapping)) for (const k of Object.keys(totals)) totals[k] += r.mapping[k];
  const systematic = totals.transactions > 0 && (totals.recoverableKeys === 0 || totals.recoverableCpi === 0 || totals.pumpInvocations > 0 && totals.recoverableOwners === 0);
  const finished = s.tokens.slice(0,5).every(t => ['COMPLETE','INCOMPLETE'].includes(t.status));
  return { status: !finished ? 'NOT_READY' : systematic ? 'STOP_REPORT' : 'REVIEW_REQUIRED', technicalUsable: finished && !systematic && s.code === null,
    measured: totals, histories: s.tokens.slice(0,5).map(t => ({ mint: t.mint,status: t.status,pages: t.pages,rows: t.rows })), criteria: 'TECHNICAL_RECOVERABILITY_NO_PERCENT_OR_ACTIVITY_THRESHOLD' };
}
function replay(io = store(), expectedHead, deps = {}) {
  const began=performance.now();resourceCheck(began);
  const manifest = parse(io.read('manifest.json'),false), s = verify('manifest',manifest); if (expectedHead) need(expectedHead === manifest.hash);
  need(s.version === VERSION && canonical(s.lineage.scripts) === canonical((deps.lineage ?? lineage()).scripts), 'LINEAGE_CHANGED');
  validSelection(s.selection); need(canonical(parse(io.read('selection.json'),false)) === canonical(s.selection));
  const rebuilt = initial(s.selection,s.attestation,s.lineage), names = new Set(['manifest.json','selection.json']);
  let lastStart = null, signatures = new SignatureIndex();const timing=[];
  need(Array.isArray(s.ancillary) && s.ancillary.length <= LIMITS.ancillary);
  const applyAncillary = after => { for(const [i,r] of s.ancillary.entries()) if(r.afterHistoryRecords === after) {
    const name='ancillary-'+String(i+1).padStart(5,'0')+'.json', {externalReceipt,...reserved}=r;names.add(name);need(canonical(parse(io.read(name),false)) === canonical(reserved));
    need(r.ordinal === i+1 && uint(r.reservedAt) && r.reservedAt + LIMITS.timeout + LIMITS.publication <= STOP && hash(r.queryHash));
    need([0,1].includes(r.publicStartsReserved)&&rebuilt.publicStarts+r.publicStartsReserved<=300);
    reserve(rebuilt,0,'ancillary',r.credits);rebuilt.ancillary.push(r);
    if(externalReceipt){const receiptName=name.replace('.json','.receipt.json');names.add(receiptName);const bytes=io.read(receiptName);
      const receipt=externalReceiptBody(bytes,r);need(digest(bytes)===externalReceipt.hash&&canonical(receipt)===canonical(externalReceipt.body));
      rebuilt.received+=receipt.received;rebuilt.publicStarts+=receipt.publicStarts;need(rebuilt.received<=LIMITS.received&&rebuilt.publicStarts<=300);
      if(receipt.start<r.reservedAt||receipt.start+LIMITS.timeout+LIMITS.publication>STOP||receipt.end-receipt.start>LIMITS.timeout||receipt.end+LIMITS.publication>STOP)
        timing.push({ancillaryOrdinal:r.ordinal,code:'ANCILLARY_TIMING'});
    }
  } };
  applyAncillary(0);
  need(s.records.length <= LIMITS.history); const check = () => {resourceCheck(began);need((deps.now ?? Date.now)() < STOP,'TIME_LIMIT');};
  for (const r of s.records) {
    check();
    need(r.ordinal === rebuilt.records.length + 1 && uint(r.start));
    if(r.start<START||r.start+LIMITS.timeout+LIMITS.publication>STOP)timing.push({ordinal:r.ordinal,code:'START_RESERVATION'});
    if(lastStart!==null&&r.start-lastStart<LIMITS.spacing)timing.push({ordinal:r.ordinal,code:'START_SPACING'});lastStart=r.start;
    const t = rebuilt.tokens[r.tokenIndex], address = s.selection.tokens[r.tokenIndex].addresses[t.addressIndex];
    need(canonical(r.query) === canonical(query(address,t.cursor)) && r.addressIndex === t.addressIndex && r.creditsReserved === 100);
    reserve(rebuilt,r.tokenIndex); const name = String(r.ordinal).padStart(5,'0'); names.add(name + '.start.json');
    need(uint(r.reservedAt) && r.start >= r.reservedAt);
    const start = parse(io.read(name + '.start.json'),false); need(canonical(start) === canonical({ ordinal:r.ordinal,tokenIndex:r.tokenIndex,addressIndex:r.addressIndex,query:r.query,creditsReserved:100,actualCredits:null,reservedAt:r.reservedAt,start:null,outcome:'START_RESERVED' }));
    need(r.outcome !== 'START_RESERVED','INCOMPLETE_START'); names.add(name + '.receipt.json'); need(canonical(parse(io.read(name + '.receipt.json'),false)) === canonical(r));
    need(uint(r.end) && r.end >= r.start && uint(r.received) && r.transportStarted === true); rebuilt.actualHistoryStarts++; rebuilt.received += r.received;
    need(r.received<=LIMITS.response+1&&rebuilt.received<=LIMITS.received);
    if(r.end-r.start>LIMITS.timeout||r.end+LIMITS.publication>STOP)timing.push({ordinal:r.ordinal,code:'RESPONSE_PUBLICATION'});
    if (r.rawHash) {
      names.add(name + '.raw'); const raw = io.read(name + '.raw'); need(raw.length === r.rawBytes && raw.length === r.received && digest(raw) === r.rawHash);
      const admitted = admit(raw,r.query); need(admitted.code === null && r.code === null && canonical(mappingCounts(admitted.rows,admitted.rowBytes,check)) === canonical(r.mapping));
      need(r.returnedRows === admitted.returnedRows && r.cursor === admitted.paginationToken);
      const before=signatures.size;for(const row of admitted.rows)signatures.set(row.transaction.signatures[0],fingerprint(VERSION+'-tx',row));
      need(r.equalScopedRows===admitted.rows.length-(signatures.size-before));
      applyPage(rebuilt,r.tokenIndex,admitted);
    } else need(r.code !== null && r.outcome === 'STOPPED');
    rebuilt.records.push(r);
    applyAncillary(rebuilt.records.length);
  }
  const actualNames = io.list().filter(n => n !== 'lock'); need(actualNames.length === names.size && actualNames.every(n => names.has(n)));
  need(s.retained === io.size());
  need(s.historyStarts === rebuilt.historyStarts && s.actualHistoryStarts === rebuilt.actualHistoryStarts && s.creditsReserved === rebuilt.creditsReserved
    && s.ancillaryStarts === rebuilt.ancillaryStarts && s.publicStarts === rebuilt.publicStarts && s.received === rebuilt.received && s.actualCredits === null);
  need(rebuilt.ancillary.length === s.ancillary.length);
  for (let i = 0; i < 300; i++) { const t = rebuilt.tokens[i], actual = s.tokens[i]; if (t.pages >= 14 && t.status !== 'COMPLETE') t.status = 'INCOMPLETE';
    if (s.code && actual.status === 'INCOMPLETE' && t.status !== 'COMPLETE') t.status = 'INCOMPLETE'; need(canonical(t) === canonical(actual)); }
  return { ...FLAGS, code: null, status: 'SEMANTIC_REPLAY_VALID', phase:s.phase, manifestHash:manifest.hash, creditsReserved:s.creditsReserved,
    semanticIntegrity:'PASS',budgetValidity:'PASS',recordedTiming:{status:timing.length?'FAIL':'PASS',violations:timing},uniqueTransactions:signatures.size,
    actualCredits:s.actualCredits, received:s.received, rawFiles:s.records.filter(r=>r.rawHash).length, qualification:qualify(s), verdict:summary(s), state:s, signatures };
}
function reserveAncillary(options, deps = {}) {
  if(options.enabled !== true) return {...FLAGS,code:'DISABLED'};
  const io=deps.store ?? store(), now=deps.now ?? Date.now;let locked=false;
  try {
    const a=options.attestation;capacity(a,now());const m=parse(io.read('manifest.json'),false),s=verify('manifest',m);
    need(options.receiptBytes===undefined,'ARGUMENTS_INVALID');
    need(m.hash === options.head && canonical(s.lineage.scripts) === canonical((deps.lineage ?? lineage()).scripts),'LINEAGE_CHANGED');
    need(s.code === null && ['QUALIFICATION_PENDING','SOURCE_COMPLETE'].includes(s.phase),'SOURCE_STOPPED');
    need(s.ancillary.every(r=>r.externalReceipt),'QUALIFICATION_REQUIRED');
    need(canonical(a.freeze) === canonical(s.attestation.freeze) && a.creditsReservedBefore === s.creditsReserved && a.ancillaryStartsBefore === s.ancillaryStarts
      && a.receivedBefore===s.received&&a.publicStartsBefore===s.publicStarts,'PREFLIGHT_INVALID');
    need(hash(options.queryHash) && options.purpose === 'independent-preholdout-receipt','ARGUMENTS_INVALID');
    need(io.free() - LIMITS.response - LIMITS.metadata >= LIMITS.free,'FREE_SPACE_LIMIT');capacity(a,now(),0,s.received);
    const publicStartsReserved=options.publicStarts??0;need([0,1].includes(publicStartsReserved)&&s.publicStarts+publicStartsReserved<=300,'PREFLIGHT_INVALID');
    io.lock();locked=true; const r={ordinal:s.ancillary.length+1,afterHistoryRecords:s.records.length,reservedAt:now(),credits:options.credits,publicStartsReserved,
      queryHash:options.queryHash,purpose:options.purpose,actualStarts:null,actualCredits:null,disposition:'MAIN_OWNS_CALL_AND_EXTERNAL_RECEIPT_NO_REFUND'};
    reserve(s,0,'ancillary',options.credits);io.write('ancillary-'+String(r.ordinal).padStart(5,'0')+'.json',json(r));s.ancillary.push(r);publish(s,io);
    return {...FLAGS,code:null,manifestHash:seal('manifest',s).hash,creditsReserved:s.creditsReserved,ancillaryStartsReserved:s.ancillaryStarts,reservation:r};
  } catch(e) {return {...FLAGS,code:cleanCode(e)};} finally {if(locked)io.unlock();}
}
function externalReceiptBody(bytes,reservation){
  need(Buffer.isBuffer(bytes)&&bytes.length<=4096,'INPUT_LIMIT');const r=parse(bytes,false);
  need(Object.keys(r).sort().join()===['ordinal','queryHash','start','end','actualStarts','publicStarts','received','status','rawHash','actualCredits'].sort().join(),'ARGUMENTS_INVALID');
  need(r.ordinal===reservation.ordinal&&r.queryHash===reservation.queryHash&&hash(r.rawHash)&&r.actualCredits===null,'IMMUTABLE_CONFLICT');
  need(r.publicStarts<=reservation.publicStartsReserved,'PREFLIGHT_INVALID');
  need(uint(r.start)&&uint(r.end)&&r.end>=r.start&&[0,1].includes(r.actualStarts)&&[0,1].includes(r.publicStarts)
    &&r.actualStarts+r.publicStarts>0&&uint(r.received)&&r.received<=LIMITS.response+1&&uint(r.status)&&r.status<=599,'ARGUMENTS_INVALID');return r;
}
function attachExternal(s,r,bytes,io,now){
  const body=externalReceiptBody(bytes,r),receiptHash=digest(bytes);
  if(r.externalReceipt){need(r.externalReceipt.hash===receiptHash,'IMMUTABLE_CONFLICT');return;}
  need(body.start>=r.reservedAt&&body.end<=now&&body.start+LIMITS.timeout+LIMITS.publication<=STOP&&body.end-body.start<=LIMITS.timeout&&body.end+LIMITS.publication<=STOP,'TIME_LIMIT');
  need(s.received+body.received<=LIMITS.received,'RECEIVED_LIMIT');need(s.publicStarts+body.publicStarts<=300,'PREFLIGHT_INVALID');
  io.write('ancillary-'+String(r.ordinal).padStart(5,'0')+'.receipt.json',bytes);
  r.externalReceipt={hash:receiptHash,body};s.received+=body.received;s.publicStarts+=body.publicStarts;publish(s,io);
}
function reconcileAncillary(options,deps={}){
  if(options.enabled!==true)return {...FLAGS,code:'DISABLED'};const io=deps.store??store(),now=deps.now??Date.now;let locked=false;
  try{const a=options.attestation;capacity(a,now());const m=parse(io.read('manifest.json'),false),s=verify('manifest',m);
    need(m.hash===options.head&&canonical(s.lineage.scripts)===canonical((deps.lineage??lineage()).scripts),'LINEAGE_CHANGED');
    need(canonical(a.freeze)===canonical(s.attestation.freeze)&&a.creditsReservedBefore===s.creditsReserved&&a.ancillaryStartsBefore===s.ancillaryStarts
      &&a.receivedBefore===s.received&&a.publicStartsBefore===s.publicStarts,'PREFLIGHT_INVALID');
    const r=s.ancillary[options.ordinal-1];need(r&&r.ordinal===options.ordinal,'ARGUMENTS_INVALID');capacity(a,now(),0,s.received);
    need(io.free()-LIMITS.response-LIMITS.metadata>=LIMITS.free,'FREE_SPACE_LIMIT');io.lock();locked=true;
    attachExternal(s,r,options.receiptBytes,io,now());return {...FLAGS,code:null,manifestHash:seal('manifest',s).hash,received:s.received,publicStarts:s.publicStarts,creditsReserved:s.creditsReserved,actualCredits:null};
  }catch(e){return {...FLAGS,code:cleanCode(e)};}finally{if(locked)io.unlock();}
}
function crossCheck(checks, requiredStrata) {
  // A threshold reducer for independently reviewed actual receipt comparisons.
  // Downloads/mapping counts never manufacture these boolean measurement facts.
  need(Array.isArray(checks) && checks.length <= 200 && Array.isArray(requiredStrata) && requiredStrata.length > 0);
  const seen = new Set(), strata = new Set(); let agree = 0, doubledVolume = 0;
  for (const c of checks) { need(typeof c.signature === 'string' && Array.isArray(c.instructionPath) && typeof c.venue === 'string' && typeof c.month === 'string' && hash(c.rawHash) && hash(c.independentReceiptHash));
    const id = canonical([c.signature,c.instructionPath]); need(!seen.has(id),'IMMUTABLE_CONFLICT'); seen.add(id); strata.add(c.venue + '/' + c.month);
    need(typeof c.agrees === 'boolean' && typeof c.doubleVolume === 'boolean'); if(c.agrees) agree++; if(c.doubleVolume) doubledVolume++;
  }
  return { checked:checks.length,agree,doubledVolume,missingStrata:requiredStrata.filter(s=>!strata.has(s)), status:checks.length === 200 && agree >= 190 && doubledVolume === 0 && requiredStrata.every(s=>strata.has(s)) ? 'PASS' : 'UNMET' };
}
function summary(s) {
  const history = { complete:s.tokens.filter(t=>t.status === 'COMPLETE').length,incomplete:s.tokens.filter(t=>t.status === 'INCOMPLETE').length,notLoaded:s.tokens.filter(t=>t.status === 'NOT_STARTED').length };
  return { ...FLAGS, status:'INSUFFICIENT',hypothesis:'INCONCLUSIVE/data insufficient',history,creditsReserved:s.creditsReserved,actualCredits:s.actualCredits,
    counters:{historyStartsReserved:s.historyStarts,historyStartsActual:s.actualHistoryStarts,ancillaryStartsReserved:s.ancillaryStarts,
      ancillaryStartsActual:s.attestation.ancillaryStartsBefore===0&&s.ancillary.every(r=>r.externalReceipt)?s.ancillary.reduce((n,r)=>n+r.externalReceipt.body.actualStarts,0):null,
      ancillaryStartsObserved:s.ancillary.reduce((n,r)=>n+(r.externalReceipt?.body.actualStarts??0),0),ancillaryUnreconciled:s.ancillary.filter(r=>!r.externalReceipt).length,
      publicStartsBefore:s.publicStarts,receivedBytes:s.received,retainedBytes:s.retained},
    thresholds:{ depthPercent:90,reconstructedPercent:90,entryAndHorizonPercent:90,lossOfExitPercent:90,maxVenueGapPercent:2,aggregateChecks:200,agreementPercent:95,doubledVolume:0 },
    reconstructed:{ numerator:0,denominator:null,status:'UNKNOWN_ECONOMIC_ROUTE_FEES_TIPS' }, crossCheck:s.crossCheck ?? { status:'UNMET',checked:0,required:200,
      eligibleEconomicTrades:0,reason:'EXISTING_MAPPER_OWNED_FACTS_ARE_NOT_COMPLETE_ECONOMIC_TRADES',missingStrata:'UNMEASURED' },
    criteria:{ depth:'UNAVAILABLE',entryAndHorizonInputs:'UNAVAILABLE',lossOfExit:'UNAVAILABLE',venueEnvelopeGaps:'UNMEASURED',walletLookback:'INCOMPLETE',noncohortAssets:'UNAVAILABLE',
      transfers:'INCOMPLETE',price:'UNAVAILABLE',tip:'UNKNOWN',visibility:'UNAVAILABLE',mintFreezeAuthority:'UNAVAILABLE',protectedPeriodAndTail:'UNMEASURED',
      pointInTimeUniverse:'UNVERIFIED',identityAndLabels:'UNVERIFIED',retention:'UNVERIFIED',deployment:'UNVERIFIED',manifest:'SCOPED_WINDOW_LINEAGE_NOT_FULL_ENVELOPE' },
    measurements:{ depth:{numerator:null,denominator:null},entryAndHorizon:{numerator:null,denominator:null},lossOfExit:{numerator:null,denominator:null},venueGaps:{unresolvedMs:null,envelopeMs:null} },selectionHash:s.selection.hash };
}
async function runCli(args) {
  try {
    need(args.length > 0,'DISABLED'); const command = args[0], flags = new Map();
    for(let i=1;i<args.length;i+=2) { need(args[i]?.startsWith('--') && typeof args[i+1] === 'string' && !flags.has(args[i]),'ARGUMENTS_INVALID'); flags.set(args[i],args[i+1]); }
    const exact = keys => need([...flags.keys()].sort().join() === keys.sort().join(),'ARGUMENTS_INVALID');
    if(command === '--select') { exact(['--freeze-commit']); const freeze = freezeProof(flags.get('--freeze-commit')); return { ...selectPinned(), freeze }; }
    if(command === '--replay' || command === '--summary' || command === '--qualify') { exact(['--head']); const r = replay(store(),flags.get('--head')); delete r.state; delete r.signatures; return r; }
    if(command === '--reserve-ancillary') {
      exact(['--enable-free','--attestation','--head','--credits','--query-hash',...(flags.has('--public-starts')?['--public-starts']:[])]);need(flags.get('--enable-free') === 'true','DISABLED');
      need(!flags.has('--public-starts')||['0','1'].includes(flags.get('--public-starts')),'ARGUMENTS_INVALID');
      need(/^(0|[1-9][0-9]*)$/.test(flags.get('--credits')),'ARGUMENTS_INVALID');const a=readSmall(flags.get('--attestation'));a.freeze=freezeProof(a.freezeCommit);
      return reserveAncillary({enabled:true,attestation:a,head:flags.get('--head'),credits:Number(flags.get('--credits')),publicStarts:Number(flags.get('--public-starts')??0),queryHash:flags.get('--query-hash'),purpose:'independent-preholdout-receipt'});
    }
    if(command === '--reconcile-ancillary'){
      exact(['--enable-free','--attestation','--head','--ordinal','--receipt']);need(flags.get('--enable-free')==='true','DISABLED');
      need(/^[1-9][0-9]*$/.test(flags.get('--ordinal')),'ARGUMENTS_INVALID');const a=readSmall(flags.get('--attestation'));a.freeze=freezeProof(a.freezeCommit);
      const file=flags.get('--receipt');ancestors(file);need(fs.statSync(file).size<=4096,'INPUT_LIMIT');
      return reconcileAncillary({enabled:true,attestation:a,head:flags.get('--head'),ordinal:Number(flags.get('--ordinal')),receiptBytes:fs.readFileSync(file)});
    }
    need(command === '--first-five' || command === '--continue','ARGUMENTS_INVALID');
      exact(command === '--first-five' ? ['--enable-free','--attestation','--selection','--selection-hash'] : ['--enable-free','--attestation','--head','--qualification']);
    need(flags.get('--enable-free') === 'true','DISABLED');
    const a = readSmall(flags.get('--attestation')); a.freeze = freezeProof(a.freezeCommit); capacity(a,Date.now());
    const options = { enabled:true,attestation:a,phase:command === '--first-five' ? 'first-five' : 'continue' };
    if(command === '--first-five') { const selection = readSmall(flags.get('--selection'),LIMITS.metadata); const {freeze,...s} = selection;
      need(canonical(freeze) === canonical(a.freeze),'FREEZE_INVALID');need(flags.get('--selection-hash') === s.hash,'INTEGRITY_ERROR'); options.selection = validSelection(s); }
    else { options.head = flags.get('--head'); options.qualification = readSmall(flags.get('--qualification')); }
    return await runStage(options);
  } catch(e) { return { ...FLAGS,code:cleanCode(e),status:'STOPPED' }; }
}
if(require.main===module){const args=process.argv.slice(2);
  const output=r=>{process.stdout.write(JSON.stringify(r)+'\n');process.exitCode=r.code?1:2;};
  if(args[0]==='--bounded-worker'){
    // V8's total includes young-generation space: this Node24 host reports
    // 1024MiB old space + 3*64MiB young-generation capacity = 1216MiB.
    const oldSpace=process.execArgv.filter(a=>a.startsWith('--max-old-space-size='));
    const totalHeapLimit=(LIMITS.workerHeapMb+3*64)*1024*1024;
    if(oldSpace.length!==1||oldSpace[0]!=='--max-old-space-size='+LIMITS.workerHeapMb||require('node:v8').getHeapStatistics().heap_size_limit>totalHeapLimit)
      output({...FLAGS,code:'RESOURCE_LIMIT',status:'STOPPED'});
    else runCli(args.slice(1)).then(output);
  }else{
    const ms=Math.min(['--first-five','--continue'].includes(args[0])?STOP-Date.now()-LIMITS.publication:LIMITS.offlineMs,STOP-Date.now()-LIMITS.publication);
    if(ms<=0)output({...FLAGS,code:'TIME_LIMIT',status:'STOPPED'});
    else{const child=spawnSync(process.execPath,['--max-old-space-size='+LIMITS.workerHeapMb,__filename,'--bounded-worker',...args],{timeout:ms,maxBuffer:LIMITS.metadata,windowsHide:true});
      try{need(!child.error&&[1,2].includes(child.status)&&child.stdout.length<=LIMITS.metadata,'RESOURCE_LIMIT');output(JSON.parse(child.stdout.toString('utf8')));}
      catch{output({...FLAGS,code:child.error?.code==='ETIMEDOUT'?'TIME_LIMIT':'RESOURCE_LIMIT',status:'STOPPED'});}
    }
  }
}
module.exports = { VERSION,ROOT,STOP,START,LIMITS,FLAGS,query,admit,collector,selectPinned,validSelection,initial,reserve,capacity,applyPage,mappingCounts,runStage,qualify,replay,crossCheck,summary,runCli,seal,verify,rawRows,reserveAncillary,reconcileAncillary,lineage,resourceCheck };
