'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const p = require('../offline-discovery.cjs'), { runCli } = require('../offline-discovery-cli.cjs');
const helper = require('../exploratory-probe.cjs'), { digest, fingerprint } = helper;
const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function b58(bytes) { let n = BigInt('0x' + bytes.toString('hex')), out = ''; while (n) { out = alphabet[Number(n % 58n)] + out; n /= 58n; }
  for (const v of bytes) { if (v) break; out = '1' + out; } return out; }
const address = n => b58(Buffer.alloc(32, n)), signature = b58(Buffer.alloc(64, 7));
const P = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA', T = 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA';
function block() {
  const a = Array.from({ length: 21 }, (_, n) => address(n + 1)), data = Buffer.alloc(24); Buffer.from('33e685a4017f83ad', 'hex').copy(data);
  a[11] = a[12] = T; a[13] = '11111111111111111111111111111111'; a[14] = 'ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL'; a[16] = P;
  const token = (account, mint, owner, pre, post) => ({ transactionIndex: 5, account, preMint: mint, postMint: mint, preOwner: owner,
    postOwner: owner, preAmount: pre, postAmount: post, preDecimals: 9, postDecimals: 9 });
  const transfer = Buffer.alloc(9); transfer[0] = 3; transfer.writeBigUInt64LE(9007199254740993123n, 1);
  return { header: { number: 410195947, parentNumber: 410195946, hash: address(28), parentHash: address(29), timestamp: 1775001600 },
    transactions: [{ transactionIndex: 5, signatures: [signature], accountKeys: [address(30)], err: null }],
    instructions: [{ transactionIndex: 5, instructionAddress: [4], programId: P, accounts: a, data: b58(data), isCommitted: true, error: null },
      { transactionIndex: 5, instructionAddress: [9], programId: T, accounts: [a[5], a[7], a[1]], data: b58(transfer), isCommitted: true, error: null }],
    tokenBalances: [token(a[5], a[3], a[1], '9007199254740993123', '9007199254740993120'), token(a[6], a[4], a[1], '1', '3'),
      token(a[7], a[3], a[0], '0', '3'), token(a[8], a[4], a[0], '3', '1')], balances: [{ account: address(30), pre: '9007199254740993123', post: '9007199254740993120' }] };
}
const bytes = b => Buffer.from(JSON.stringify(b) + '\r\n');
function fixture(raw = bytes(block())) {
  const config = structuredClone(p.CONFIG), files = new Map(), reads = [], manifests = [];
  config.scripts = Object.fromEntries(Object.keys(config.scripts).map(n => { files.set(n, Buffer.from(n)); return [n, digest(files.get(n))]; }));
  for (const [n, source] of config.sources.entries()) {
    const cfg = n === 0 ? helper.CONFIG : { queryVersion: 'exploratory-sqd-v2', canonicalizationVersion: 'exploratory-config-c14n-v2',
      source: helper.CONFIG.source, base: helper.CONFIG.base, windows: [helper.ANCHORS[0]], datasetRevision: null };
    const data = n === 0 ? raw : n === 1 ? Buffer.from(JSON.stringify({ header: block().header }) + '\n') : Buffer.alloc(0);
    const request = helper.query(n === 0 ? 'payload' : 'header', helper.ANCHORS[0], 410195947, P);
    if (n === 1) { request.time = 1775001600; delete request.slot; }
    const m = n < 2 ? { classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false, status: 'INCOMPLETE', config: cfg,
      configHash: fingerprint(cfg.canonicalizationVersion, cfg), source: { commit: 'a'.repeat(40), dirty: false }, runtime: 'v24.19.0',
      records: [{ request, raw: n === 0 ? '004.raw' : '0001.raw', hash: digest(data), size: data.length, status: 200, code: null, attempted: true }] } :
      { classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false, files: [{ name: '000.raw', size: 1, sha256: digest(Buffer.from('x')) }],
        records: [], configuration: { queryVersion: n === 2 ? 'helius-history-query-v1' : 'helius-full-history-query-v1' }, lineage: { source: { commit: 'a'.repeat(40), dirty: false }, runtime: 'v24.19.0' } };
    manifests.push(m); if (n < 2) files.set(source.root + '/' + m.records[0].raw, data);
  }
  let time = 0;
  const seal = () => { for (const [n, source] of config.sources.entries()) { const m = manifests[n]; const entries = n < 2 ? [...new Map(m.records.filter(r => r.raw).map(r => [r.raw, r])).values()] : m.files;
    source.count = entries.length; source.bytes = entries.reduce((v, r) => v + r.size, 0); const b = Buffer.from(JSON.stringify(m));
    source.manifestHash = digest(b); files.set(source.root + '/manifest.json', b); } };
  const deps = { source: { commit: 'b'.repeat(40), dirty: false }, runtime: 'v24.19.0', now: () => time,
    utcNow: () => Date.parse('2026-10-03T00:00:00Z'), read: (root, name, cap) => { reads.push(root + '/' + name);
      const b = files.get(root ? root + '/' + name : name); if (!b) throw Error('READ_ERROR'); assert.ok(b.length <= cap); return b; } };
  seal(); return { config, files, reads, manifests, deps, seal, advance: n => time += n };
}
const run = f => p.discover(f.config, f.deps);
test('exact contiguous children retain byte offsets hashes balances transfers and only declared provisional addresses', () => {
  const child = bytes(block()), f = fixture(Buffer.concat([child, child])), r = run(f);
  assert.equal(r.status, 'PROVISIONAL_DISCOVERY_PARTIAL'); assert.equal(r.exitCode, 2); assert.equal(r.scanComplete, true);
  assert.equal(r.children.length, 2); assert.deepEqual(r.children.map(c => [c.offset, c.length, c.childHash]), [[0, child.length, digest(child)], [child.length, child.length, digest(child)]]);
  assert.equal(r.observations.length, 1); assert.equal(r.observations[0].parents.length, 2);
  assert.equal(r.observations[0].mapped.tokenStates[0].preAmount, '9007199254740993123');
  assert.equal(r.observations[0].mapped.transfers[0].amount, '9007199254740993123'); assert.equal(r.observations[0].mapped.transfers[0].associatedInvocation, null);
  assert.equal(r.addresses.length, 1); assert.equal(r.addresses[0].address, block().instructions[0].accounts[1]);
  assert.equal(r.addresses[0].status, 'DECLARED_OWNER_WITH_EXACT_DELTAS'); assert.equal(r.addresses.some(a => a.address === address(30)), false);
  assert.equal(r.availability.label, 'MODELED'); assert.equal(r.fullD1UpperBound, null); assert.equal(r.fullD1Fits, null);
  assert.equal(r.section5CandidateUniverse, false); assert.equal(r.addresses[0].actualTraderConfirmed, false);
  assert.ok(f.reads.every(n => !n.includes('helius') || n.endsWith('manifest.json')));
  assert.equal(r.sources.filter(s => s.kind === 'HELIUS').every(s => s.readFiles === 0 && s.disposition === 'FORMAT_NOT_ALLOCATED'), true);
});
test('every new child audits numeric err lexemes before unchanged mapper and returns no provisional rows on violation', () => {
  for (const literal of ['1.0000000000000001', '1e0', '-1', '4294967296', '4294967295.0000001']) {
    const b = block(); b.transactions[0].err = { InstructionError: [0, { Custom: '__number__' }] };
    const raw = Buffer.from(JSON.stringify(b).replace('"__number__"', literal) + '\n'), r = run(fixture(raw));
    assert.equal(r.status, 'DISCOVERY_INVALID'); assert.equal(r.code, 'ERR_NUMERIC_LEXEME_INVALID'); assert.deepEqual(r.addresses, []); assert.deepEqual(r.observations, []);
  }
  const b = block(); b.transactions[0].err = { InstructionError: [256, { Custom: 1 }] };
  assert.equal(run(fixture(bytes(b))).code, 'ERR_NUMERIC_LEXEME_INVALID');
  for (const index of ['1.0000000000000001', '1e0']) { const text = JSON.stringify(b).replace('[256,', '[' + index + ',');
    assert.equal(run(fixture(Buffer.from(text + '\n'))).code, 'ERR_NUMERIC_LEXEME_INVALID'); }
  b.transactions[0].err = { InstructionError: ['256', { Custom: '4294967296' }] };
  const quoted = run(fixture(bytes(b))); assert.equal(quoted.status, 'PROVISIONAL_DISCOVERY_PARTIAL'); assert.equal(quoted.addresses.length, 0);
});
test('missing err and non-DEX native rows remain actionable UNKNOWN gaps not payer candidates or false activity absence', () => {
  const b = block(); delete b.transactions[0].err; const r = run(fixture(bytes(b)));
  assert.equal(r.status, 'PROVISIONAL_DISCOVERY_PARTIAL'); assert.equal(r.counters.missingErr, 1); assert.equal(r.addresses.length, 0);
  assert.equal(r.observations[0].mapped.transfers.length, 1); assert.equal(r.observations[0].nativeBalanceRows, 1);
  assert.ok(r.gaps.some(g => g.code === 'MISSING_TRANSACTION_ERR'));
  const plain = block(); plain.instructions = []; const noDex = run(fixture(bytes(plain)));
  assert.equal(noDex.scanComplete, true); assert.equal(noDex.addresses.length, 0); assert.equal(noDex.observations.length, 1);
});
test('manifest raw hash unsafe path query/time provenance and expiry rejection precede provisional publication', () => {
  const wrong = fixture(); wrong.config.sources[0].manifestHash = digest(Buffer.from('wrong')); assert.equal(run(wrong).code, 'MANIFEST_INTEGRITY');
  const raw = fixture(); raw.files.set('exploratory-sqd-v1/004.raw', Buffer.from('wrong')); assert.equal(run(raw).code, 'RAW_INTEGRITY');
  const escape = fixture(); escape.manifests[0].records[0].raw = '../004.raw'; escape.seal(); assert.equal(run(escape).code, 'UNSAFE_PATH');
  const query = fixture(); delete query.manifests[0].records[0].request.body.fields.tokenBalance; query.seal(); assert.equal(run(query).code, 'QUERY_LINEAGE_INVALID');
  for (const timestamp of [null, 1787961600, 1788134400, 1775001599]) { const b = block(); b.header.timestamp = timestamp;
    const r = run(fixture(bytes(b))); assert.equal(r.code, 'TIME_ADMISSION_INVALID'); assert.deepEqual(r.addresses, []); }
  const available = block(); available.header.timestamp = p.CONFIG.cutoff - 59;
  assert.equal(run(fixture(bytes(available))).code, 'TIME_ADMISSION_INVALID');
  const expired = fixture(); expired.deps.utcNow = () => Date.parse(p.CONFIG.expiresAt);
  assert.equal(run(expired).code, 'RETENTION_EXPIRED'); assert.equal(expired.reads.length, 0);
  const script = fixture(); script.config.scripts['pumpswap-mapper.cjs'] = digest(Buffer.from('changed')); assert.equal(run(script).code, 'SOURCE_INTEGRITY');
});
test('distinct child conflicts are unresolved and semantic ordering duplicates/provenance remain reproducible', () => {
  const first = block(), second = block(); second.tokenBalances[0].postAmount = '9007199254740993119';
  const f = fixture(Buffer.concat([bytes(first), bytes(second)])), r = run(f);
  assert.equal(r.addresses.length, 1); assert.equal(r.addresses[0].status, 'DECLARED_ADDRESS_UNRESOLVED'); assert.ok(r.gaps.some(g => g.code === 'CONFLICTING_OBSERVATIONS'));
  const reverse = fixture(Buffer.concat([bytes(second), bytes(first)])), q = run(reverse);
  assert.deepEqual(q.addresses.map(a => [a.address, a.status]), r.addresses.map(a => [a.address, a.status])); assert.equal(q.observations.length, 2);
  const a = fixture(), z = fixture(); z.manifests[0].records.push(structuredClone(z.manifests[0].records[0])); z.seal();
  const original = run(a), duplicate = run(z); assert.equal(duplicate.observations.length, original.observations.length);
  assert.equal(duplicate.observations[0].parents.length, 2); assert.notEqual(duplicate.semanticHash, original.semanticHash);
  const again = run(a); assert.equal(again.semanticHash, original.semanticHash);
  const revised = fixture(); revised.deps.source.dirty = true; assert.notEqual(run(revised).semanticHash, original.semanticHash);
});
test('caps and mapper-invalid children stop without partial addresses or silent truncation', () => {
  for (const [key, value] of [['rows', 1], ['records', 0], ['addresses', 0], ['output', 100], ['input', 1]]) {
    const f = fixture(); f.config.limits[key] = value; const r = run(f); assert.equal(r.status, 'DISCOVERY_INVALID'); assert.deepEqual(r.addresses, []);
  }
  const slow = fixture(), read = slow.deps.read; slow.deps.read = (...args) => { const b = read(...args); slow.advance(600001); return b; };
  assert.equal(run(slow).code, 'TIME_LIMIT');
  const bad = block(); bad.instructions[0].accounts[0] = '0'; const rejected = run(fixture(bytes(bad)));
  assert.equal(rejected.status, 'DISCOVERY_INCOMPLETE'); assert.equal(rejected.exitCode, 2); assert.deepEqual(rejected.addresses, []);
});
test('closed CLI defaults are read/key/network/write-free and filesystem reader rejects symlink/escape', () => {
  let reads = 0; const d = { read: () => { reads++; assert.fail('read forbidden'); } };
  assert.equal(runCli([], d).code, 'DISABLED'); assert.equal(reads, 0);
  for (const args of [['--enable-offline', '--path', 'secret'], ['--enable-offline', '--enable-offline']]) assert.equal(runCli(args, d).code, 'ARGUMENTS_INVALID');
  assert.throws(() => p.readFixed('../outside', 'manifest.json', 10), /UNSAFE_PATH/);
  const io = { lstatSync: () => ({ isSymbolicLink: () => true }) };
  assert.throws(() => p.readFixed('exploratory-sqd-v1', 'manifest.json', 10, io), /UNSAFE_PATH/);
});
test('nonempty H1 and H3 metadata use nested tokenAccounts filters without reading Helius raw', () => {
  const f = fixture();
  for (const n of [2, 3]) { const m = f.manifests[n], q = { method: 'POST', url: 'https://mainnet.helius-rpc.com/',
    body: { jsonrpc: '2.0', id: 1, method: 'getTransactionsForAddress', params: [address(30), { commitment: 'finalized',
      transactionDetails: n === 2 ? 'signatures' : 'full', filters: { blockTime: { gte: 1775001600, lt: 1775002200 }, status: 'any', tokenAccounts: n === 2 ? 'none' : 'all' } }] } };
    if (n === 2) m.query = q;
    m.records = [{ ...(n === 3 ? { query: q } : {}), attempted: true, status: 200, code: null, raw: m.files[0] }]; }
  f.seal(); const r = run(f); assert.equal(r.status, 'PROVISIONAL_DISCOVERY_PARTIAL'); assert.equal(r.scanComplete, true);
  const metadata = r.coverage.filter(c => c.kind === 'HELIUS_METADATA_ONLY'); assert.equal(metadata.length, 2);
  assert.ok(metadata.every(c => c.disposition === 'FORMAT_NOT_ALLOCATED' && c.status === 200));
  assert.ok(f.reads.every(n => !n.includes('helius') || n.endsWith('manifest.json'))); assert.equal(r.counters.rawFilesRead, 2);
  f.manifests[3].records[0].query.body.params[1].filters.tokenAccounts = 'none'; f.seal();
  const invalid = run(f); assert.equal(invalid.code, 'QUERY_LINEAGE_INVALID'); assert.equal(invalid.counters.rawFilesRead, 0);
});
test('Git provenance subprocesses receive remaining deadline and stop on timeout before evidence reads', () => {
  const cp = require('node:child_process'), id = require.resolve('../offline-discovery.cjs');
  const invoke = (f, exec) => { const original = cp.execFileSync, cached = require.cache[id]; let module;
    try { cp.execFileSync = exec; delete require.cache[id]; module = require(id); }
    finally { cp.execFileSync = original; require.cache[id] = cached; }
    delete f.deps.source; return module.discover(f.config, f.deps); };
  const f = fixture(), calls = [], r = invoke(f, (file, args, options) => {
    calls.push({ file, args, timeout: options.timeout }); f.advance(25); return args[0] === 'rev-parse' ? 'b'.repeat(40) + '\n' : ''; });
  assert.deepEqual(calls.map(c => c.timeout), [600000, 599975]); assert.equal(r.status, 'PROVISIONAL_DISCOVERY_PARTIAL');
  assert.deepEqual(calls.map(c => c.args), [['rev-parse', 'HEAD'], ['status', '--porcelain']]); assert.ok(calls.every(c => c.file === 'git'));
  const exhausted = fixture(); let count = 0; const stopped = invoke(exhausted, () => { count++; exhausted.advance(600000); return 'b'.repeat(40); });
  assert.equal(count, 1); assert.equal(stopped.code, 'TIME_LIMIT'); assert.equal(exhausted.reads.length, 0);
  const timeout = fixture(), timed = invoke(timeout, () => { const error = Error('unsafe Git diagnostic'); error.code = 'ETIMEDOUT'; throw error; });
  assert.equal(timed.code, 'TIME_LIMIT'); assert.equal(timeout.reads.length, 0); assert.equal(JSON.stringify(timed).includes('unsafe Git diagnostic'), false);
});
