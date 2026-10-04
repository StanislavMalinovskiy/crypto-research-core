'use strict';
const fs = require('node:fs'), path = require('node:path'), { execFileSync } = require('node:child_process');
const h = require('./helius-probe.cjs'), { parse, canonical, digest, fingerprint, integer } = require('./exploratory-probe.cjs');
const FLAGS = { classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false, cohortAdmitted: false, actualTraderConfirmed: false, economicReconstructed: false };
const OUTPUT = 'C:\\crypto-research-evidence\\r1-e2\\exploratory-path-v1';
const SEEDS = [
  { pool: '8S4phy4nn4AvKdeXXAeYEWRHHvVzXNyo3Rk41xw13sSg', raw: '004.raw', slot: 410195947, index: 18, path: [4], bytes: 537582, hash: 'sha256:c1595f12107d76dd511df1e12dc4189868d434b73038ce025ff013a02e1b602f' },
  { pool: '4WYZCnTMv1SHZoEbmHjFf2HmaoPXZwGnE2ayigaLJ1LR', raw: '009.raw', slot: 416762082, index: 16, path: [3], bytes: 616790, hash: 'sha256:0a34ebacef46a7335e5a61e582935b9ca23ab57e19b23f32ddcb2289a3cfa3a1' },
  { pool: '7ddsEETgdtoipMmshQgQigwtgp9cNxW93h9oFCspPVmr', raw: '014.raw', slot: 423478907, index: 499, path: [8], bytes: 1025412, hash: 'sha256:c83d0a757d882d9122080da8d1a8282a12612ca4acb943fa4c81833421a5de95' }
];
const error = code => ({ ...FLAGS, status: 'INCOMPLETE', code });
const SEED_ROOT = 'C:\\crypto-research-evidence\\r1-d1\\exploratory-sqd-v1';
const PUMP = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA', SYSTEM = '11111111111111111111111111111111';
const TOKEN = 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA', TOKEN2022 = 'TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb';
const ASSOCIATED = 'ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL', alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
const PIN = { 'helius-probe.cjs': 'sha256:bcf3fe32aa99e9c51ed94be1be60dbe3e41daf0a6554ab3544f22628f74473f2',
  'exploratory-probe.cjs': 'sha256:8f8790026241de1bf3bcded6fef3fe99a6b954af34931184d04897ca393b0a47' };
const QUOTA = { version: 'e2-conditional-quota-v1', decision: '0a1b5e93-58e6-437d-b893-531b3bb5cbec',
  delegatedSession: 'a023e2d7-364f-4163-9c4e-9ea3df5cc4c9', date: '2026-10-03', formula: '1000000-10-39800=960190',
  assumption: 'NO_UNRELATED_ACCOUNT_USAGE', unrelatedUsage: 'UNVERIFIED', autoTransition: 'UNVERIFIED', actualH3Debit: 'UNKNOWN',
  measuredCurrentBalance: false, currentFreeGuaranteed: false, noPaidTariffProved: false, invoiceGuaranteed: false, reservation: 900 };
const CONFIG = { version: 'e2-path-v1', seedVersion: 'e2-historical-convenience-v1', canonicalVersion: 'e2-path-semantic-v1',
  seeds: SEEDS, seedManifestHash: 'sha256:fc1fe02982ec49885e7553b2e35331c9f5444e2fca5924327bd0e4d4eed74a4a',
  helpers: PIN, schema: { commit: '82dacacf15ca93dc0444ab38714f2226210a0a3d', version: '0.1.0', bytes: 130535,
    hash: 'sha256:5a15060f412974e53068bae7e89aa6004defbb70ef0c56e3902ce75d124accb6', create: 'e992d18ecf6840bc', event: 'b1310cd2a076a774',
    instructionBytes: 60, accounts: 18, eventBytes: 334, applicability: 'DECLARED_UNVERIFIED' },
  range: { from: 1775001600, toExclusive: 1782777600 }, queryVersion: 'helius-full-history-query-v1', output: OUTPUT, quota: QUOTA,
  limits: { attempts: 9, retries: 0, pages: 3, credits: 900, response: 64000000, received: 576000000, disk: 1000000000,
    metadata: 64000000, report: 16000000, rows: 9000, observations: 100000, diagnostics: 1000, deadlineMs: 60000,
    sourceCutoffMs: 900000, publicationReserveMs: 300000, nominalMs: 1200000, spacingMs: 250, concurrency: 1,
    aggregate: 50000000000, free: 30000000000 },
  retention: 'UNVERIFIED', expiresAt: '2026-10-17T11:42:35.392Z', expectedFreeMicrousd: '0', actualInvoice: 'UNKNOWN' };
const fail = code => { throw code; }, need = (v, code = 'RESPONSE_INVALID') => { if (!v) fail(code); };
const SAFE = new Set(['DISABLED', 'ARGUMENTS_INVALID', 'INTEGRITY_ERROR', 'INPUT_LIMIT', 'UNSAFE_PATH', 'PREPARATION_LIMIT', 'PREFLIGHT_ERROR',
  'OUTPUT_EXISTS', 'RUNTIME_INVALID', 'SOURCE_IDENTITY_INVALID', 'ACCESS_UNREVIEWED', 'RETENTION_EXPIRED', 'HTTP_ERROR', 'NETWORK_ERROR', 'TIMEOUT',
  'PARTIAL_RESPONSE', 'RESPONSE_LIMIT', 'RECEIVED_LIMIT', 'RESPONSE_INVALID', 'RPC_ERROR', 'IMMUTABLE_CONFLICT', 'CURSOR_INVALID',
  'SECRET_EXPOSURE', 'SECRET_UNAVAILABLE', 'STORAGE_ERROR', 'TIME_LIMIT', 'TRANSPORT_ERROR', 'ATTEMPT_LIMIT', 'CREDIT_LIMIT', 'DISK_LIMIT',
  'FREE_SPACE_LIMIT', 'OBSERVATION_LIMIT', 'REPORT_LIMIT', 'BILLING_REFUSAL']);
const safe = e => SAFE.has(e) ? e : 'TRANSPORT_ERROR';
const json = v => Buffer.from(JSON.stringify(v) + '\n');
const cmp = (a, b) => a < b ? -1 : a > b ? 1 : 0;
function decode(s, limit = 512) {
  need(typeof s === 'string' && s.length > 0 && s.length <= limit); let n = 0n, zeros = 0;
  for (const c of s) { const at = alphabet.indexOf(c); need(at >= 0); n = n * 58n + BigInt(at); }
  while (s[zeros] === '1') zeros++; const hex = n.toString(16), b = n ? Buffer.from(hex.length % 2 ? '0' + hex : hex, 'hex') : Buffer.alloc(0);
  return Buffer.concat([Buffer.alloc(zeros), b]);
}
function encode(b) { let n = BigInt('0x' + b.toString('hex')), s = '', zeros = 0;
  while (zeros < b.length && b[zeros] === 0) zeros++; while (n) { s = alphabet[Number(n % 58n)] + s; n /= 58n; } return '1'.repeat(zeros) + s; }
const address = v => { need(decode(v, 44).length === 32); return v; };
function checkAncestors(file) { let dir = path.dirname(file); while (true) { const s = fs.lstatSync(dir);
  need(s.isDirectory() && !s.isSymbolicLink() && fs.realpathSync.native(dir).toLowerCase() === dir.toLowerCase(), 'UNSAFE_PATH');
  const up = path.dirname(dir); if (up === dir) break; dir = up; } }
function readBounded(file, limit) { checkAncestors(file); need(!fs.lstatSync(file).isSymbolicLink(), 'UNSAFE_PATH'); let fd;
  try { fd = fs.openSync(file, 'r'); const s = fs.fstatSync(fd); need(s.isFile() && s.size <= limit, 'INPUT_LIMIT');
    const b = Buffer.alloc(s.size + 1); let n = 0; while (n < b.length) { const read = fs.readSync(fd, b, n, b.length - n, n); if (!read) break; n += read; }
    need(n === s.size && n <= limit, 'INTEGRITY_ERROR'); return b.subarray(0, n);
  } finally { if (fd !== undefined) fs.closeSync(fd); } }
function verifySeeds(inputs, now = Date.now) {
  const start = now(); need(Buffer.isBuffer(inputs?.manifest) && inputs.manifest.length <= 4000000 && digest(inputs.manifest) === CONFIG.seedManifestHash, 'INTEGRITY_ERROR');
  need(inputs.raws && canonical(Object.keys(inputs.raws).sort()) === canonical(SEEDS.map(s => s.raw).sort()), 'INTEGRITY_ERROR');
  const proof = [];
  for (const s of SEEDS) { const raw = inputs.raws[s.raw]; need(Buffer.isBuffer(raw) && raw.length === s.bytes && raw.length <= 1500000 && digest(raw) === s.hash, 'INTEGRITY_ERROR');
    const b = parse(raw); need(integer(b.header?.number) === s.slot && Array.isArray(b.instructions) && b.instructions.length <= 20000 && Array.isArray(b.transactions), 'INTEGRITY_ERROR');
    const matches = b.instructions.filter(i => integer(i.transactionIndex) === s.index && canonical(i.instructionAddress.map(integer)) === canonical(s.path));
    const txs = b.transactions.filter(t => integer(t.transactionIndex) === s.index); need(matches.length === 1 && txs.length === 1, 'INTEGRITY_ERROR');
    const i = matches[0], t = txs[0], bytes = decode(i.data); need(t.err === null && i.isCommitted === true && (i.error === null || i.error === undefined) && i.programId === PUMP, 'INTEGRITY_ERROR');
    const variants = { '66063d1201daebea': [25, 23], c62e1552b4d9e870: [25, 23], '33e685a4017f83ad': [24, 21] }, v = variants[bytes.subarray(0, 8).toString('hex')];
    need(v && bytes.length === v[0] && i.accounts.length === v[1] && i.accounts[0] === s.pool && Array.isArray(t.signatures) && t.signatures.length > 0, 'INTEGRITY_ERROR');
    const signature = t.signatures[0]; need(decode(signature, 88).length === 64, 'INTEGRITY_ERROR');
    proof.push({ ...s, signature, baseMint: address(i.accounts[3]), quoteMint: address(i.accounts[4]) });
    need(now() - start >= 0 && now() - start <= 10000, 'PREPARATION_LIMIT');
  } return proof;
}
function prepare(now) { const manifest = readBounded(path.join(SEED_ROOT, 'manifest.json'), 4000000), raws = {};
  for (const s of SEEDS) raws[s.raw] = readBounded(path.join(SEED_ROOT, s.raw), 1500000); return verifySeeds({ manifest, raws }, now); }
function provenance(deps) { const cwd = path.resolve(__dirname, '../../..'), source = deps.source ?? {
  commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd, encoding: 'utf8', timeout: 5000 }).trim(),
  dirty: execFileSync('git', ['status', '--porcelain'], { cwd, encoding: 'utf8', timeout: 5000 }).trim().length > 0 };
  need(/^[a-f0-9]{40}$/.test(source.commit) && typeof source.dirty === 'boolean', 'SOURCE_IDENTITY_INVALID');
  const runtime = deps.runtime ?? process.version; need(/^v(24|25)\.[0-9]+\.[0-9]+$/.test(runtime), 'RUNTIME_INVALID');
  const scripts = {}; for (const name of [...Object.keys(PIN), 'e2-path-probe.cjs', 'e2-path-probe-cli.cjs']) {
    scripts[name] = digest(readBounded(path.join(__dirname, name), 1000000)); if (PIN[name]) need(scripts[name] === PIN[name], 'INTEGRITY_ERROR'); }
  return { source, runtime, scripts };
}
function scanPreflight(now = Date.now) {
  const base = 'C:\\crypto-research-evidence', began = now(), roots = [...new Set([...h.SCAN_ROOTS,
    ...fs.readdirSync(base).filter(n => /^r1-(d1|e2)(-|$)/.test(n)).map(n => path.join(base, n))])].sort();
  let entries = 0, total = 0; const files = new Set(), records = [];
  function inspect(file, depth) { need(++entries <= 100000 && depth <= 12 && now() - began <= 15000, 'PREFLIGHT_ERROR');
    const s = fs.lstatSync(file); need(!s.isSymbolicLink() && fs.realpathSync.native(file).toLowerCase() === file.toLowerCase(), 'UNSAFE_PATH');
    if (s.isDirectory()) { const names = fs.readdirSync(file); need(names.length <= 100000 - entries, 'PREFLIGHT_ERROR'); for (const n of names.sort()) inspect(path.join(file, n), depth + 1); }
    else { need(s.isFile() && Number.isSafeInteger(s.size) && s.size >= 0, 'PREFLIGHT_ERROR'); const id = file.toLowerCase();
      if (!files.has(id)) { total += s.size; files.add(id); need(total <= CONFIG.limits.aggregate, 'DISK_LIMIT'); } }
  }
  for (const root of roots) { let absent = false; try { fs.lstatSync(root); } catch (e) { if (e.code === 'ENOENT') absent = true; else fail('PREFLIGHT_ERROR'); }
    const before = total; if (!absent) inspect(root, 0); records.push({ root, status: absent ? 'ABSENT' : 'PRESENT', bytes: total - before }); }
  let outputAbsent = false; try { fs.lstatSync(OUTPUT); } catch (e) { if (e.code === 'ENOENT') outputAbsent = true; else fail('PREFLIGHT_ERROR'); }
  const procedure = readBounded(path.resolve(__dirname, '../../../docs/research/R1_OFFLINE_RESEARCH_PROCEDURE.md'), 1000000), text = procedure.toString('utf8');
  return { retainedBytes: total, reconciled: true, roots: records, entries, outputAbsent,
    accessReviewed: text.includes('e2-path-probe-cli.cjs') && text.includes(QUOTA.decision), procedureHash: digest(procedure),
    scope: 'FIXED_D1_E2_ROOTS_GATES_KNOWN_CHECKPOINTS_STAT_ONLY_MAIN_RECONCILIATION_REQUIRED' };
}
function fileStore() {
  const root = path.resolve(OUTPUT), parent = path.dirname(root), base = path.dirname(parent), names = /^(manifest\.json|summary\.json|attempt\.json|000[0-8]\.raw)$/;
  const file = name => { need(names.test(name), 'UNSAFE_PATH'); return path.join(root, name); };
  const check = () => { checkAncestors(path.join(root, 'manifest.json')); const s = fs.lstatSync(root); need(s.isDirectory() && !s.isSymbolicLink(), 'UNSAFE_PATH'); };
  return { free() { checkAncestors(path.join(base, 'probe-placeholder')); const s = fs.statfsSync(base, { bigint: true }), n = s.bavail * s.bsize;
      need(n <= BigInt(Number.MAX_SAFE_INTEGER), 'FREE_SPACE_LIMIT'); return Number(n); },
    create() { checkAncestors(path.join(base, 'probe-placeholder')); try { fs.mkdirSync(parent); } catch (e) { if (e.code !== 'EEXIST') fail('STORAGE_ERROR'); }
      checkAncestors(path.join(parent, 'placeholder')); try { fs.mkdirSync(root); } catch (e) { fail(e.code === 'EEXIST' ? 'OUTPUT_EXISTS' : 'STORAGE_ERROR'); } check(); },
    write(name, bytes) { check(); let fd; try { fd = fs.openSync(file(name), 'wx'); fs.writeFileSync(fd, bytes); fs.fsyncSync(fd); }
      catch { fail('STORAGE_ERROR'); } finally { if (fd !== undefined) fs.closeSync(fd); } },
    read(name) { check(); return readBounded(file(name), name.endsWith('.raw') ? CONFIG.limits.response : CONFIG.limits.metadata); },
    list() { check(); return fs.readdirSync(root); }, size(name) { check(); const s = fs.lstatSync(file(name)); need(s.isFile() && !s.isSymbolicLink(), 'INTEGRITY_ERROR'); return s.size; } };
}
function accountKeys(r) { const staticKeys = r.transaction.message.accountKeys;
  need(Array.isArray(staticKeys) && staticKeys.length <= 256); const loaded = r.meta.loadedAddresses;
  const keys = staticKeys.map(k => address(typeof k === 'string' ? k : k?.pubkey));
  if (loaded !== undefined) { need(loaded && Array.isArray(loaded.writable) && Array.isArray(loaded.readonly)); keys.push(...loaded.writable.map(address), ...loaded.readonly.map(address)); }
  need(keys.length <= 256); return keys;
}
function instructions(r, keys) {
  const out = [], outer = r.transaction.message.instructions; need(Array.isArray(outer) && outer.length <= 20000);
  const add = (ix, locator) => { try { const program = keys[integer(ix.programIdIndex)]; need(program); need(Array.isArray(ix.accounts) && ix.accounts.length <= 256);
      const accounts = ix.accounts.map(n => { const k = keys[integer(n)]; need(k); return k; }); out.push({ program, accounts, data: ix.data, locator });
    } catch { out.push({ program: null, accounts: [], data: null, locator }); } };
  outer.forEach((ix, i) => add(ix, [i])); const groups = r.meta.innerInstructions;
  if (Array.isArray(groups)) for (const group of groups) { need(Array.isArray(group.instructions) && out.length + group.instructions.length <= 20000);
    const parent = integer(group.index); need(parent < outer.length); group.instructions.forEach((ix, i) => add(ix, [parent, i])); }
  return out;
}
function creation(r, seed, ixes, rawHash) {
  const unknown = reason => ({ status: 'CREATION_UNKNOWN', reason, layoutApplicability: 'DECLARED_UNVERIFIED', earliestEver: false, monthConfirmed: false });
  if (r.meta.err !== null) return unknown('NON_SUCCESS_TRANSACTION');
  const candidates = ixes.filter(i => i.program === PUMP && i.accounts[0] === seed.pool);
  const logs = r.meta.logMessages; if (!Array.isArray(logs) || logs.length > 20000) return unknown('MISSING_LINKED_EVENT');
  // Pair each invocation's logs with the ordered compiled invocations; never trust an unlinked Program data line.
  let ordinal = 0; const stack = [], events = [];
  for (const line of logs) { if (typeof line !== 'string' || line.length > 200000) return unknown('UNSUPPORTED_LOG_LAYOUT');
    const invoke = /^Program ([1-9A-HJ-NP-Za-km-z]{32,44}) invoke \[([0-9]+)\]$/.exec(line);
    if (invoke) { const program = invoke[1], ix = program === PUMP ? ixes.filter(i => i.program === PUMP)[ordinal++] : null;
      stack.push({ program, ix, data: [] }); continue; }
    const finish = /^Program ([1-9A-HJ-NP-Za-km-z]{32,44}) (success|failed:.*)$/.exec(line);
    if (finish) { const frame = stack.pop(); if (!frame || frame.program !== finish[1]) return unknown('UNLINKED_LOG_STACK');
      if (finish[2] === 'success' && frame.program === PUMP && frame.ix) for (const data of frame.data) events.push({ ix: frame.ix, data }); continue; }
    if (line.startsWith('Program data: ') && stack.at(-1)?.program === PUMP) stack.at(-1).data.push(line.slice(14));
  }
  if (stack.length) return unknown('INCOMPLETE_LOG_STACK');
  for (const i of candidates) { let data; try { data = decode(i.data); } catch { continue; }
    if (data.length !== 60 || data.subarray(0, 8).toString('hex') !== CONFIG.schema.create || data[58] > 1 || data[59] > 1 || i.accounts.length !== 18) continue;
    const a = i.accounts; if (a[11] !== SYSTEM || a[12] !== TOKEN2022 || ![TOKEN, TOKEN2022].includes(a[13]) || ![TOKEN, TOKEN2022].includes(a[14]) || a[15] !== ASSOCIATED || a[17] !== PUMP || a[3] === a[4]) continue;
    if ((seed.baseMint && a[3] !== seed.baseMint) || (seed.quoteMint && a[4] !== seed.quoteMint)) continue;
    for (const linked of events.filter(e => e.ix === i)) { const text = linked.data;
      if (!/^[A-Za-z0-9+/]+={0,2}$/.test(text)) continue; const e = Buffer.from(text, 'base64');
      if (e.length !== 334 || e.toString('base64') !== text || e.subarray(0, 8).toString('hex') !== CONFIG.schema.event || e[333] > 1) continue;
      const k = offset => encode(e.subarray(offset, offset + 32));
      if (k(18) !== a[2] || k(50) !== a[3] || k(82) !== a[4] || k(173) !== seed.pool || k(205) !== a[5] || k(237) !== a[6] || k(269) !== a[7] ||
        !e.subarray(301, 333).equals(data.subarray(26, 58)) || e[333] !== data[58] || e.readUInt16LE(16) !== data.readUInt16LE(8) || e.readBigInt64LE(8) !== BigInt(integer(r.blockTime))) continue;
      return { status: 'OBSERVED_DECLARED_CREATION', layoutApplicability: 'DECLARED_UNVERIFIED', earliestEver: false, monthConfirmed: false,
        signature: r.transaction.signatures[0], instructionAddress: i.locator, rawHash, pool: seed.pool, baseMint: a[3], quoteMint: a[4], blockTime: integer(r.blockTime),
        schemaHash: CONFIG.schema.hash, eventDiscriminator: CONFIG.schema.event };
    }
  } return unknown('MISSING_OR_UNSUPPORTED_LINKED_CREATION_LAYOUT');
}
function diagnose(r, seed, rawHash) {
  const observations = [], gaps = {}, gap = code => { gaps[code] = (gaps[code] ?? 0) + 1; };
  const payload = { nativePreCount: Array.isArray(r.meta.preBalances) ? r.meta.preBalances.length : 0,
    nativePostCount: Array.isArray(r.meta.postBalances) ? r.meta.postBalances.length : 0,
    tokenPreCount: Array.isArray(r.meta.preTokenBalances) ? r.meta.preTokenBalances.length : 0,
    tokenPostCount: Array.isArray(r.meta.postTokenBalances) ? r.meta.postTokenBalances.length : 0,
    innerGroupCount: Array.isArray(r.meta.innerInstructions) ? r.meta.innerInstructions.length : 0, nativeInstructionCount: 0, splInstructionCount: 0,
    rawHash, signature: r.transaction.signatures[0] };
  gap('FEE_ROUTE_TIP_UNRESOLVED'); gap('FULL_WALLET_HISTORY_UNMEASURED'); gap('VAULT_STATE_AND_DEPLOYED_LAYOUT_UNVERIFIED');
  if (!Array.isArray(r.meta.innerInstructions)) gap('INNER_INSTRUCTIONS_MISSING');
  if (!payload.nativePreCount || !payload.nativePostCount) gap('NATIVE_BALANCES_MISSING');
  if (r.meta.fee === undefined) gap('FEE_MISSING');
  let keys, ixes = [], created = { status: 'CREATION_UNKNOWN', reason: 'MISSING_ACCOUNT_KEYS', layoutApplicability: 'DECLARED_UNVERIFIED', earliestEver: false, monthConfirmed: false };
  try { keys = accountKeys(r); ixes = instructions(r, keys); created = creation(r, seed, ixes, rawHash); }
  catch { gap('KEY_OR_INSTRUCTION_LAYOUT_UNKNOWN'); }
  payload.nativeInstructionCount = ixes.filter(i => i.program === SYSTEM).length;
  payload.splInstructionCount = ixes.filter(i => [TOKEN, TOKEN2022].includes(i.program)).length;
  if (r.meta.err !== null) { gap(r.meta.err === undefined ? 'TRANSACTION_SUCCESS_UNKNOWN' : 'NON_SUCCESS_TRANSACTION'); return { observations, gaps, creation: created, payload }; }
  if (!keys) return { observations, gaps, creation: created, payload };
  const sides = [];
  for (const name of ['preTokenBalances', 'postTokenBalances']) { const list = r.meta[name];
    if (!Array.isArray(list) || list.length > 256) { gap('TOKEN_BALANCE_STATE_MISSING_OR_UNSUPPORTED'); sides.push(new Map()); continue; }
    const m = new Map(); for (const t of list) { try { const index = integer(t.accountIndex); need(index < keys.length);
        need(!m.has(index)); const amount = t.uiTokenAmount?.amount, decimals = integer(t.uiTokenAmount?.decimals);
        need(typeof amount === 'string' && /^(0|[1-9][0-9]{0,19})$/.test(amount) && BigInt(amount) <= 18446744073709551615n && decimals <= 255);
        m.set(index, { owner: address(t.owner), mint: address(t.mint), amount, decimals });
      } catch { gap('OWNERSHIP_AMOUNT_DECIMAL_OR_INDEX_UNKNOWN'); try { m.set(integer(t.accountIndex), null); } catch { /* Counted unsupported locator. */ } } }
    sides.push(m);
  }
  for (const index of [...new Set([...sides[0].keys(), ...sides[1].keys()])].sort((a, b) => a - b)) {
    const before = sides[0].get(index), after = sides[1].get(index); if (!before || !after) { gap('MISSING_BALANCE_SIDE_UNKNOWN'); continue; }
    if (before.owner !== after.owner || before.mint !== after.mint || before.decimals !== after.decimals) { gap('AMBIGUOUS_OWNERSHIP_OR_MINT_OR_DECIMALS'); continue; }
    const account = keys[index], linked = ixes.some(i => i.accounts.includes(account)); if (!linked) gap('UNLINKED_OWNED_ACCOUNT');
    observations.push({ status: 'OWNED_ACCOUNT_OBSERVATION', signature: r.transaction.signatures[0], accountIndex: index, account,
      owner: before.owner, mint: before.mint, decimals: before.decimals, preAmount: before.amount, postAmount: after.amount,
      delta: (BigInt(after.amount) - BigInt(before.amount)).toString(), instructionLinked: linked, rawHash });
  } return { observations, gaps, creation: created, payload };
}
function initial(seeds) { return { streams: seeds.map((s, id) => ({ id, pool: s.pool, cursor: null, pages: 0, rows: 0, returnedRows: 0,
    received: 0, estimatedCredits: 0, status: 'UNQUERIED', creation: { status: 'CREATION_UNKNOWN', reason: 'UNQUERIED', layoutApplicability: 'DECLARED_UNVERIFIED', earliestEver: false, monthConfirmed: false }, rawReferences: [] })),
    unique: new Map(), observations: new Map(), payloads: new Map(), gaps: {}, membership: seeds.map(() => new Set()) }; }
function processPage(state, stream, page, seed, raw) {
  // Stage all diagnostic changes and bounds before committing any coverage from this page.
  const facts = [];
  for (const item of page.rows) { need(!state.unique.has(item.signature) || state.unique.get(item.signature) === item.content, 'IMMUTABLE_CONFLICT');
    if (!state.unique.has(item.signature)) facts.push({ item, fact: diagnose(item.row, seed, raw.sha256) }); }
  need(state.observations.size + facts.reduce((n, f) => n + f.fact.observations.length, 0) <= CONFIG.limits.observations, 'OBSERVATION_LIMIT');
  for (const { item, fact } of facts) { state.unique.set(item.signature, item.content); state.payloads.set(item.signature, fact.payload);
    for (const o of fact.observations) state.observations.set(canonical([o.signature, o.accountIndex, o.owner, o.mint, o.decimals]), o);
    for (const [k, n] of Object.entries(fact.gaps)) state.gaps[k] = (state.gaps[k] ?? 0) + n; }
  for (const item of page.rows) { if (!state.membership[stream.id].has(item.signature)) { state.membership[stream.id].add(item.signature); stream.rows++; }
    const c = diagnose(item.row, seed, raw.sha256).creation; if (c.status === 'OBSERVED_DECLARED_CREATION') stream.creation = c; }
  need(Object.keys(state.gaps).length <= CONFIG.limits.diagnostics, 'REPORT_LIMIT'); stream.pages++; stream.returnedRows += page.returnedRows;
  stream.cursor = page.paginationToken; stream.estimatedCredits += 10 * Math.max(1, Math.ceil(page.returnedRows / 100)); stream.rawReferences.push(raw);
  stream.status = stream.creation.status === 'OBSERVED_DECLARED_CREATION' ? 'CREATION_OBSERVED' : page.paginationToken === null ? 'INTERVAL_COMPLETE' : 'CENSORED';
}
function summary(state, code, lineage, seeds) {
  const observations = [...state.observations.values()].sort((a, b) => cmp(a.signature, b.signature) || a.accountIndex - b.accountIndex || cmp(canonical(a), canonical(b)));
  return { ...FLAGS, version: CONFIG.version, status: code ? 'INCOMPLETE' : 'COMPLETE', code, lineage, seeds, configurationHash: fingerprint(CONFIG.canonicalVersion, CONFIG),
    streams: state.streams, rows: state.unique.size, observations, payloads: [...state.payloads.values()].sort((a, b) => cmp(a.signature, b.signature)), gaps: state.gaps,
    fullD1UpperBound: null, actualCredits: 'UNKNOWN', expectedFreeMicrousd: '0', actualInvoice: 'UNKNOWN',
    walletExpansion: { keysAndOwnershipObserved: observations.length > 0, authorized: false, prerequisites: ['COMPLETE_NATIVE_SPL_NONCOHORT_HISTORY', 'EXACT_HISTORY_COST_AND_RETENTION', 'FEES_TIPS_ROUTES_IDENTITY'] },
    census: { complete: false, prerequisites: ['FULL_MONTH_CREATE_EVENT_COVERAGE', 'DEAD_POOLS', 'ALL_POOLS_PER_MINT', 'GLOBAL_EARLIEST_CREATION'] } };
}
const semantics = (s, records, quota) => fingerprint(CONFIG.canonicalVersion, { configuration: CONFIG, summary: s, quota,
  records: records.map(({ startMs, endMs, ...r }) => r) });
async function run(options = {}, deps = {}) {
  if (!options.enabled) return error('DISABLED');
  if (options.enabled !== true || options.output !== OUTPUT || Object.keys(options).some(k => !['enabled', 'creditsRemaining', 'output'].includes(k)) ||
    typeof options.creditsRemaining !== 'string' || !/^(0|[1-9][0-9]{0,6})$/.test(options.creditsRemaining) || BigInt(options.creditsRemaining) > 960190n) return error('ARGUMENTS_INVALID');
  if (BigInt(options.creditsRemaining) < 900n) return error('CREDIT_LIMIT');
  const caps = CONFIG.limits, now = deps.now ?? Date.now, utc = deps.utcNow ?? Date.now, began = now(), wait = deps.wait ?? (ms => new Promise(resolve => setTimeout(resolve, ms)));
  let preflight, store, lineage, seeds;
  try { lineage = provenance(deps); need(utc() < Date.parse(CONFIG.expiresAt), 'RETENTION_EXPIRED');
    const prepStart = now(); seeds = (deps.prepare ?? (() => prepare(now)))(); need(now() - prepStart >= 0 && now() - prepStart <= 10000, 'PREPARATION_LIMIT');
    need(Array.isArray(seeds) && seeds.length === 3 && seeds.every((s, i) => SEEDS[i].pool === s.pool && SEEDS[i].hash === s.hash), 'INTEGRITY_ERROR');
    preflight = deps.preflight ?? scanPreflight(now); need(preflight.reconciled === true && Number.isSafeInteger(preflight.retainedBytes) && preflight.retainedBytes >= 0, 'PREFLIGHT_ERROR');
    need(preflight.outputAbsent === true, 'OUTPUT_EXISTS'); need(preflight.accessReviewed === true, 'ACCESS_UNREVIEWED');
    need(preflight.retainedBytes + caps.disk <= caps.aggregate, 'DISK_LIMIT'); store = deps.store ?? fileStore();
    need(store.free() - caps.disk >= caps.free, 'FREE_SPACE_LIMIT'); need(now() - began >= 0 && now() - began + caps.deadlineMs <= caps.sourceCutoffMs, 'TIME_LIMIT'); store.create();
  } catch (e) { return error(safe(typeof e === 'string' ? e : e?.code === 'ENOENT' ? 'INTEGRITY_ERROR' : 'PREFLIGHT_ERROR')); }
  const state = initial(seeds), files = [], partialFiles = [], records = [], checkpoints = []; let received = 0, retained = 0, partialBytes = 0, attempts = 0, creditsReserved = 0, lastStart = -Infinity, code = null;
  const write = (name, bytes) => { need(retained + bytes.length <= caps.disk, 'DISK_LIMIT');
    try { store.write(name, bytes); retained += bytes.length; const f = { name, size: bytes.length, sha256: digest(bytes) }; files.push(f); return f; }
    catch { let size = null; try { size = store.size(name); need(Number.isSafeInteger(size) && size >= 0, 'STORAGE_ERROR'); } catch { partialFiles.push({ name, size: 'UNKNOWN' }); fail('STORAGE_ERROR'); }
      retained += size; partialBytes += size; if (size) partialFiles.push({ name, size }); fail('STORAGE_ERROR'); } };
  const checkpoint = () => { for (const [name, value, cap] of [['attempts', attempts, caps.attempts], ['credits', creditsReserved, caps.credits], ['received', received, caps.received],
    ['disk', retained, caps.disk], ['aggregate', preflight.retainedBytes + retained, caps.aggregate], ['elapsed', now() - began, caps.nominalMs]])
    if (value >= cap * 0.8 && !checkpoints.some(c => c.name === name)) checkpoints.push({ name, value, atAttempt: attempts }); };
  const capacity = () => { need(attempts < caps.attempts, 'ATTEMPT_LIMIT'); need(creditsReserved + 100 <= caps.credits, 'CREDIT_LIMIT');
    need(received + caps.response <= caps.received, 'RECEIVED_LIMIT'); need(retained + caps.response + caps.metadata <= caps.disk && preflight.retainedBytes + retained + caps.response + caps.metadata <= caps.aggregate, 'DISK_LIMIT');
    need(store.free() - caps.response - caps.metadata >= caps.free, 'FREE_SPACE_LIMIT'); };
  const timeFits = delay => { const elapsed = now() - began; need(elapsed >= 0 && elapsed + delay + caps.deadlineMs <= caps.sourceCutoffMs, 'TIME_LIMIT'); };
  try { write('attempt.json', json({ version: CONFIG.version, configurationHash: fingerprint(CONFIG.canonicalVersion, CONFIG), lineage, seeds, quota: QUOTA }));
    for (let pageOrdinal = 0; pageOrdinal < caps.pages; pageOrdinal++) for (const stream of state.streams) {
      if (stream.pages && (stream.cursor === null || stream.creation.status === 'OBSERVED_DECLARED_CREATION')) continue;
      const delay = Math.max(0, lastStart + caps.spacingMs - now()); capacity(); timeFits(delay);
      if (delay) { const before = now(); await wait(delay); need(now() - before >= delay, 'TIME_LIMIT'); }
      capacity(); timeFits(0); const q = h.query3(stream.pool, 0, stream.cursor), startMs = now() - began;
      const record = { ordinal: records.length, stream: stream.id, queryHash: fingerprint('e2-fixed-query-v1', q), cursor: stream.cursor, startMs, endMs: null,
        code: null, status: null, attempted: false, received: 0, creditsReserved: 100, raw: null }; records.push(record); creditsReserved += 100;
      let response, chunks = 0; lastStart = now(); timeFits(0); // Full pending timeout is reserved after the capacity stat and immediately before transport.
      try { response = await (deps.transport ?? h.send)(q, { responseLimit: caps.response, deadlineMs: caps.deadlineMs,
          onChunk: n => { need(Number.isSafeInteger(n) && n >= 0, 'RECEIVED_LIMIT'); chunks += n; received += n;
            need(chunks <= caps.response + 1, 'RESPONSE_LIMIT'); need(received <= caps.received, 'RECEIVED_LIMIT'); } });
        record.attempted = response.attempted !== false; record.code = response.code ? safe(response.code) : null;
        record.status = Number.isSafeInteger(response.status) ? response.status : null; need(response.received === chunks, 'RESPONSE_INVALID');
      } catch (e) { record.attempted = response?.attempted !== false; record.code = safe(e); }
      if (record.attempted) attempts++; record.received = chunks; record.endMs = now() - began; stream.received += chunks; checkpoint();
      need(record.endMs >= startMs && record.endMs <= caps.sourceCutoffMs, 'TIME_LIMIT'); if (record.code) fail(record.code);
      need(record.status === 200 && Buffer.isBuffer(response.bytes) && response.bytes.length === chunks && chunks <= caps.response, 'RESPONSE_INVALID');
      const admitted = h.admit3(response.bytes, q); if (admitted.code) fail(admitted.code);
      for (const item of admitted.rows) need(!state.unique.has(item.signature) || state.unique.get(item.signature) === item.content, 'IMMUTABLE_CONFLICT');
      need(state.streams.reduce((n, s) => n + s.returnedRows, 0) + admitted.returnedRows <= caps.rows, 'REPORT_LIMIT');
      const raw = { name: String(record.ordinal).padStart(4, '0') + '.raw', size: response.bytes.length, sha256: digest(response.bytes) };
      // Check complete proposed report before raw retention or mutable coverage publication.
      const staged = structuredClone(state); processPage(staged, staged.streams[stream.id], admitted, seeds[stream.id], raw);
      need(json(summary(staged, null, lineage, seeds)).length <= caps.report, 'REPORT_LIMIT'); write(raw.name, response.bytes); record.raw = raw;
      processPage(state, stream, admitted, seeds[stream.id], raw);
    }
  } catch (e) { code = safe(e); const last = records.at(-1); if (last && last.code === null && last.raw === null) last.code = code; }
  checkpoint(); const s = summary(state, code, lineage, seeds), semanticHash = semantics(s, records, QUOTA);
  const accounting = { attempts, retries: 0, received, creditsReserved, actualCredits: 'UNKNOWN', retained, partialBytes, aggregateBefore: preflight.retainedBytes,
    aggregateAfter: preflight.retainedBytes + retained, elapsedScope: 'PRE_PUBLICATION', elapsedMs: now() - began, expectedFreeMicrousd: '0', actualInvoice: 'UNKNOWN' };
  const m = { ...FLAGS, version: 'e2-path-manifest-v1', configuration: CONFIG, lineage, seeds, preflight, quota: QUOTA,
    records, files, partialFiles, checkpoints, summaryHash: digest(json(s)), semanticHash, code, accounting, runBudget: 'UNMEASURED',
    operational: { elapsedScope: 'PRE_PUBLICATION', elapsedMs: now() - began, expiresAt: CONFIG.expiresAt } };
  try { need(json(s).length <= caps.report, 'REPORT_LIMIT'); write('summary.json', json(s));
    m.accounting.retained = retained; m.accounting.aggregateAfter = preflight.retainedBytes + retained;
    // Preserve original publication size independently of replay's mutable elapsed/log presentation.
    m.accounting.originalManifestBytes = 0;
    for (let i = 0; i < 6; i++) { m.accounting.originalManifestBytes = json(m).length;
      m.accounting.retained = retained + m.accounting.originalManifestBytes; m.accounting.aggregateAfter = preflight.retainedBytes + m.accounting.retained; }
    need(m.accounting.originalManifestBytes === json(m).length && m.accounting.retained === retained + json(m).length && json(m).length <= caps.metadata && m.accounting.retained <= caps.disk, 'DISK_LIMIT');
    write('manifest.json', json(m));
  } catch { return { ...error('STORAGE_ERROR'), summary: s, accounting: { ...accounting, retained, partialBytes, accountingComplete: false }, semanticHash: null }; }
  return { ...FLAGS, status: s.status, code, summary: s, semanticHash, accounting: m.accounting, runBudget: 'UNMEASURED' };
}
async function replay(store = fileStore()) {
  try { const m = parse(store.read('manifest.json'), false), caps = CONFIG.limits;
    need(m.version === 'e2-path-manifest-v1' && canonical(m.configuration) === canonical(CONFIG) && canonical(m.quota) === canonical(QUOTA), 'INTEGRITY_ERROR');
    need(canonical(m.seeds.map(({ signature, baseMint, quoteMint, ...s }) => s)) === canonical(SEEDS), 'INTEGRITY_ERROR');
    need(m.lineage && /^v(24|25)\.[0-9]+\.[0-9]+$/.test(m.lineage.runtime) && /^[a-f0-9]{40}$/.test(m.lineage.source.commit) && typeof m.lineage.source.dirty === 'boolean', 'INTEGRITY_ERROR');
    for (const [name, hash] of Object.entries(PIN)) need(m.lineage.scripts[name] === hash, 'INTEGRITY_ERROR');
    const current = provenance({ source: m.lineage.source, runtime: m.lineage.runtime }); need(canonical(current.scripts) === canonical(m.lineage.scripts), 'INTEGRITY_ERROR');
    need(Array.isArray(m.records) && m.records.length <= 9 && Array.isArray(m.files) && m.files.length <= 11 && m.runBudget === 'UNMEASURED', 'INTEGRITY_ERROR');
    const currentManifestBytes = store.read('manifest.json').length;
    need(Number.isSafeInteger(m.accounting.originalManifestBytes) && m.accounting.originalManifestBytes > 0 && m.accounting.originalManifestBytes <= caps.metadata && currentManifestBytes <= caps.metadata, 'INTEGRITY_ERROR');
    const names = new Set(), bytes = new Map(); let retained = m.accounting.originalManifestBytes;
    for (const f of m.files) { need(/^(attempt\.json|summary\.json|000[0-8]\.raw)$/.test(f.name) && !names.has(f.name), 'INTEGRITY_ERROR'); names.add(f.name);
      const b = store.read(f.name); need(b.length === f.size && digest(b) === f.sha256, 'INTEGRITY_ERROR'); bytes.set(f.name, b); retained += b.length; }
    for (const f of m.partialFiles) { need(Number.isSafeInteger(f.size) && f.size > 0 && /^000[0-8]\.raw$/.test(f.name) && !names.has(f.name), 'INTEGRITY_ERROR');
      const b = store.read(f.name); need(b.length === f.size, 'INTEGRITY_ERROR'); names.add(f.name); retained += f.size; }
    need(canonical(store.list().sort()) === canonical([...names, 'manifest.json'].sort()), 'INTEGRITY_ERROR');
    need(canonical(parse(bytes.get('attempt.json'), false)) === canonical({ version: CONFIG.version, configurationHash: fingerprint(CONFIG.canonicalVersion, CONFIG), lineage: m.lineage, seeds: m.seeds, quota: QUOTA }), 'INTEGRITY_ERROR');
    const state = initial(m.seeds); let attempts = 0, received = 0, credits = 0, priorStream = -1, round = 0;
    for (const [i, r] of m.records.entries()) { need(r.ordinal === i && Number.isSafeInteger(r.stream) && r.stream >= 0 && r.stream < 3, 'INTEGRITY_ERROR');
      const stream = state.streams[r.stream]; need(stream.pages < 3 && (stream.pages === 0 || (stream.cursor !== null && stream.creation.status !== 'OBSERVED_DECLARED_CREATION')), 'INTEGRITY_ERROR');
      if (r.stream <= priorStream) round++; need(round <= 2 && stream.pages === round, 'INTEGRITY_ERROR'); priorStream = r.stream;
      const q = h.query3(stream.pool, 0, stream.cursor); need(r.cursor === stream.cursor && r.queryHash === fingerprint('e2-fixed-query-v1', q) && r.creditsReserved === 100 &&
        Number.isSafeInteger(r.received) && r.received >= 0 && r.received <= caps.response + 1 && typeof r.attempted === 'boolean', 'INTEGRITY_ERROR');
      if (r.attempted) attempts++; received += r.received; credits += 100; stream.received += r.received;
      if (r.raw !== null) { need(r.code === null && r.status === 200 && r.attempted && bytes.has(r.raw.name) && r.raw.size === r.received, 'INTEGRITY_ERROR');
        const f = m.files.find(f => f.name === r.raw.name); need(f && canonical(f) === canonical(r.raw), 'INTEGRITY_ERROR');
        const page = h.admit3(bytes.get(r.raw.name), q); need(page.code === null, 'INTEGRITY_ERROR'); processPage(state, stream, page, m.seeds[r.stream], r.raw);
      } else need(i === m.records.length - 1 && r.code !== null && safe(r.code) === r.code && m.code !== null, 'INTEGRITY_ERROR');
    }
    need(attempts === m.accounting.attempts && received === m.accounting.received && credits === m.accounting.creditsReserved && credits <= 900 && received <= caps.received &&
      retained === m.accounting.retained && retained <= caps.disk && retained - m.accounting.originalManifestBytes + currentManifestBytes <= caps.disk &&
      m.accounting.aggregateBefore === m.preflight.retainedBytes && m.accounting.aggregateAfter === m.preflight.retainedBytes + retained &&
      m.preflight.retainedBytes + caps.disk <= caps.aggregate && m.accounting.retries === 0 && m.accounting.actualCredits === 'UNKNOWN', 'INTEGRITY_ERROR');
    const s = summary(state, m.code, m.lineage, m.seeds); need(bytes.has('summary.json') && digest(bytes.get('summary.json')) === m.summaryHash && canonical(parse(bytes.get('summary.json'), false)) === canonical(s), 'INTEGRITY_ERROR');
    const semanticHash = semantics(s, m.records, QUOTA); need(semanticHash === m.semanticHash, 'INTEGRITY_ERROR');
    return { ...FLAGS, status: s.status, code: m.code, summary: s, semanticHash, accounting: m.accounting, runBudget: 'UNMEASURED' };
  } catch { return error('INTEGRITY_ERROR'); }
}
module.exports = { OUTPUT, SEEDS, FLAGS, CONFIG, error, run, replay, diagnose, verifySeeds, fileStore, scanPreflight };
