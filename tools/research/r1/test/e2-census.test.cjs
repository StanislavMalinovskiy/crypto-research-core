'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const c = require('../e2-census.cjs');
const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function b58(bytes) { let n = BigInt('0x' + bytes.toString('hex')), out = '', z = 0;
  while (z < bytes.length && bytes[z] === 0) z++; while (n) { out = alphabet[Number(n % 58n)] + out; n /= 58n; } return '1'.repeat(z) + out; }
const key = n => b58(Buffer.alloc(32, n)), signature = n => b58(Buffer.alloc(64, n));
const program = 'pAMMBay6oceH9fJKBRHGP5D4bD4sWpmSwMn52FMfXEA';
const bounds = [410195947, 416000000, 423000000, 429000000], dates = [1775001600, 1777593600, 1780272000, 1782604800];
const header = (number, timestamp) => ({ number, hash: key(1), parentNumber: number - 1, parentHash: key(2), timestamp });
const ix = (address = [0], extra = {}) => ({ transactionIndex: 2, instructionAddress: address, programId: program,
  accounts: [key(3), key(4), key(5), key(6), key(7), key(8), key(9)],
  data: b58(Buffer.from('e992d18ecf6840bc000102', 'hex')), isCommitted: true, error: null, ...extra });
const block = (number = bounds[0], timestamp = dates[0], instructions = [ix()]) => ({ header: header(number, timestamp),
  transactions: [{ transactionIndex: 2, signatures: [signature(1)], err: null }], instructions });
const nd = blocks => Buffer.from(blocks.map(b => JSON.stringify(b)).join('\n') + '\n');
const dq = () => c.query('data', 0, bounds[0], bounds);
function memory() { const files = new Map(); let created = false; return { files, free: () => 100000000000,
  absent: () => !created, create() { if (created) throw Error('ROOT_EXISTS'); created = true; },
  write(n, b) { assert.equal(files.has(n), false); files.set(n, Buffer.from(b)); },
  read(n) { assert.ok(files.has(n)); return files.get(n); }, size: n => files.get(n)?.length ?? 0, list: () => [...files.keys()] }; }
function deps(respond) { let time = 0; const store = memory(), calls = [];
  return { store, calls, now: () => time, utcNow: () => Date.parse('2026-10-04T10:00:00Z'), wait: async ms => { time += ms; },
    source: { commit: 'a'.repeat(40), dirty: true }, runtime: 'v24.19.0', verifySealed: () => ({ status: 'VERIFIED' }),
    transport: async (q, o) => { calls.push({ q, start: time }); let bytes;
      if (q.kind === 'resolver') bytes = Buffer.from(JSON.stringify({ block_number: bounds[q.index] }));
      else if (q.kind === 'header') bytes = nd([{ header: header(q.slot, q.index === 3 ? dates[3] - 1 : dates[q.index]) }]);
      else bytes = nd([{ header: header(q.body.toBlock, q.index === 2 ? dates[3] - 1 : dates[q.index]) }]);
      const r = respond?.(q, calls.length) ?? { status: 200, code: null, bytes, received: bytes.length };
      o.onChunk(r.received ?? r.bytes?.length ?? 0); return r; } };
}
const options = () => ({ enabled: true, retainedBefore: '1000000', output: c.OUTPUT });
test('Census literal sparse NDJSON admits direct and CPI creation without exact account tail', () => {
  const raw = nd([block(bounds[0], dates[0], [ix([0]), ix([4, 1, 0])]), { header: header(bounds[1] - 1, dates[1] - 1) }]);
  const r = c.admit(raw, dq(), bounds);
  assert.equal(r.code, null, 'sealed explicit linked creation and sparse terminal boundary are admitted');
  assert.equal(r.lastScanned, bounds[1] - 1); assert.equal(r.lastMatched, bounds[0]); assert.equal(r.creations.length, 2);
  assert.deepEqual(r.creations.map(x => x.cpiDepth), [0, 2]); assert.equal(r.creations[0].accountCount, 7);
  assert.equal(r.creations[0].pool, key(3)); assert.equal(r.creations[0].creator, key(5)); assert.equal(r.creations[0].dataLength, 11);
  assert.equal(r.creations[1].status, 'OBSERVED_DECLARED_CREATE_POOL'); assert.equal(r.creations[1].lineOrdinal, 1);
});
test('Census query literal allowlist and controls never select broad relations', () => {
  const q = dq(); assert.deepEqual(q.body, { type: 'solana', fromBlock: bounds[0], toBlock: bounds[1] - 1, includeAllBlocks: false,
    fields: { block: { number: true, hash: true, parentNumber: true, parentHash: true, timestamp: true },
      transaction: { transactionIndex: true, signatures: true, err: true }, instruction: { transactionIndex: true, instructionAddress: true,
        programId: true, accounts: true, data: true, isCommitted: true, error: true } },
    instructions: [{ programId: [program], d8: ['0xe992d18ecf6840bc'], transaction: true }] });
  assert.equal(q.url, 'https://portal.sqd.dev/datasets/solana-mainnet/finalized-stream');
  for (let i = 0; i < 4; i++) assert.equal(c.query('resolver', i).url, `https://portal.sqd.dev/datasets/solana-mainnet/timestamps/${dates[i]}/block`);
  assert.throws(() => c.query('data', 0, 429980965, bounds));
});
test('Census unsafe full page duplicate keys lexemes links and holdout reject before counts', () => {
  const cases = [nd([block(), block(bounds[0] + 1, 1788134400)]), Buffer.from('{"header":{"number":1,"number":2}}\n'),
    Buffer.from('{"blocks":[]}\n'), Buffer.from(''), nd([block(bounds[0], dates[0], [ix([])])]),
    nd([block(bounds[0], dates[0], [ix([0], { transactionIndex: -1 })])])];
  for (const replace of [['410195947', '410195947.0'], ['410195947', '4.10195947e8'], ['410195947', '"410195947"']]) cases.push(Buffer.from(nd([block()]).toString().replace(...replace)));
  for (const raw of cases) { const r = c.admit(raw, dq(), bounds); assert.notEqual(r.code, null); assert.equal(r.creations.length, 0); }
  for (const number of [0, 410195946, 429980965, 9007199254740992]) assert.notEqual(c.admit(Buffer.from(JSON.stringify({ block_number: number })), c.query('resolver', 0)).code, null);
  const duplicate = block(); duplicate.transactions.push({ ...duplicate.transactions[0] }); assert.notEqual(c.admit(nd([duplicate]), dq(), bounds).code, null);
});
test('Census unsupported successful state prefix and missing linkage remain counted UNKNOWN', () => {
  const variants = [ix([0], { isCommitted: false }), ix([1], { error: {} }), ix([2], { accounts: [key(3)] }),
    ix([3], { transactionIndex: 99 }), ix([4], { data: b58(Buffer.alloc(8)) })];
  const missing = ix([5]); delete missing.error; variants.push(missing);
  const r = c.admit(nd([block(bounds[0], dates[0], variants)]), dq(), bounds); assert.equal(r.code, null);
  assert.equal(r.creations.length, 0); assert.equal(r.counts.unknown, 6); assert.equal(r.counts.instructions, 6);
  const failed = block(); failed.transactions[0].err = 'AccountInUse'; assert.equal(c.admit(nd([failed]), dq(), bounds).creations.length, 0);
});
test('Census zero matches use scanned boundary and runner round robins finite ranges', async () => {
  const d = deps(), r = await c.run(options(), d); assert.equal(r.code, null); assert.equal(r.status, 'SCAN_COMPLETE');
  assert.equal(d.calls.length, 11); assert.deepEqual(d.calls.slice(8).map(x => x.q.index), [0, 1, 2]);
  for (let i = 1; i < d.calls.length; i++) assert.ok(d.calls[i].start - d.calls[i - 1].start >= 250);
  assert.equal(r.summary.creations, 0); assert.ok(r.summary.streams.every(s => s.status === 'SCAN_COMPLETE' && s.lastMatched === null));
  for (const f of ['d1Evidence', 'd1Passed', 'cohortAdmitted', 'authoritativeCensusComplete']) assert.equal(r.summary[f], false);
  assert.equal(r.summary.fullD1UpperBound, null); assert.equal(r.summary.fullD1Fits, null); assert.equal(r.summary.fallback, 'NOT_RUN');
});
test('Census HTTP529 alone retries identical query twice with scheduled and RetryAfter waits', async () => {
  const d = deps((q, n) => n <= 2 ? { status: 529, code: null, received: 7, retryAfter: '20', bytes: Buffer.from('private') } : undefined);
  const r = await c.run(options(), d); assert.equal(r.code, null); assert.equal(d.calls.length, 13);
  assert.deepEqual(d.calls[0].q, d.calls[1].q); assert.deepEqual(d.calls[1].q, d.calls[2].q);
  assert.equal(d.calls[1].start - d.calls[0].start, 20000); assert.equal(d.calls[2].start - d.calls[1].start, 45000);
  assert.equal(r.accounting.retries, 2); assert.equal(r.accounting.attempts, 13);
  assert.equal([...d.store.files.values()].some(b => b.toString().includes('private')), false);
  const exhausted = deps(() => ({ status: 529, received: 3 })), er = await c.run(options(), exhausted);
  assert.equal(er.code, 'HTTP_529_EXHAUSTED'); assert.equal(exhausted.calls.length, 3); assert.equal(er.status, 'INCOMPLETE');
  for (const status of [204, 301, 401, 403, 429, 500]) { const x = deps(() => ({ status, received: 5 })), y = await c.run(options(), x);
    assert.notEqual(y.code, null); assert.equal(x.calls.length, 1); assert.equal(y.accounting.received, 5); }
});
test('Census time after capacity and retry wait reservation prevent actual start', async () => {
  const late = deps(); let n = 0; late.store.free = () => { if (++n === 2) late.wait(6840001); return 100000000000; };
  assert.equal((await c.run(options(), late)).code, 'TIME_LIMIT'); assert.equal(late.calls.length, 0);
  const wait = deps(() => ({ status: 529, received: 4, retryAfter: '7000' }));
  assert.equal((await c.run(options(), wait)).code, 'TIME_LIMIT'); assert.equal(wait.calls.length, 1);
});
test('Census preflight root expiry scalar capacity and sealed header failures make no calls', async () => {
  for (const value of ['01', '+1', '1e6', '1.0', '-1', '50000000001', '45900000001']) {
    const d = deps(); assert.notEqual((await c.run({ ...options(), retainedBefore: value }, d)).code, null); assert.equal(d.calls.length, 0); }
  const free = deps(); free.store.free = () => 34099999999; assert.equal((await c.run(options(), free)).code, 'FREE_SPACE_LIMIT'); assert.equal(free.calls.length, 0);
  const used = deps(); used.store.absent = () => false; assert.equal((await c.run(options(), used)).code, 'ROOT_EXISTS'); assert.equal(used.calls.length, 0);
  const expired = deps(); expired.utcNow = () => Date.parse('2026-10-18T00:00:00Z'); assert.equal((await c.run(options(), expired)).code, 'EXPIRY'); assert.equal(expired.calls.length, 0);
  const pin = deps(); pin.verifySealed = () => { throw Error('INTEGRITY_ERROR'); }; assert.equal((await c.run(options(), pin)).code, 'INTEGRITY_ERROR'); assert.equal(pin.calls.length, 0);
});
test('Census invalid resolver and header or sparse progress stops without raw lineage', async () => {
  const bad = deps(q => q.kind === 'resolver' ? { status: 200, bytes: Buffer.from('{"block_number":429980965}'), received: 26 } : undefined);
  const r = await c.run(options(), bad); assert.notEqual(r.code, null); assert.equal(bad.calls.length, 1); assert.equal(r.accounting.received, 26);
  assert.equal([...bad.store.files.keys()].some(n => n.endsWith('.raw')), false);
  for (const raw of [Buffer.from(''), nd([block(bounds[0], dates[0] - 1)]), nd([block(bounds[0] - 1)])]) {
    const d = deps(q => q.kind === 'data' ? { status: 200, bytes: raw, received: raw.length } : undefined), result = await c.run(options(), d);
    assert.notEqual(result.code, null); assert.equal(d.calls.length, 9); assert.equal(result.summary.creations, 0); assert.equal(result.summary.streams[0].pages, 0); }
  const broken = deps(() => ({ code: 'PARTIAL_RESPONSE', status: 200, received: 19 }));
  assert.equal((await c.run(options(), broken)).code, 'PARTIAL_RESPONSE'); assert.equal(broken.calls.length, 1);
});
test('Census raw publication and transport exceptions retain partial byte facts and stop', async () => {
  const d = deps(); d.store.write = (n, b) => { if (n.endsWith('.raw')) { d.store.files.set(n, b.subarray(0, 7)); throw Error('STORAGE_ERROR'); }
    assert.equal(d.store.files.has(n), false); d.store.files.set(n, Buffer.from(b)); };
  const r = await c.run(options(), d); assert.equal(r.code, 'STORAGE_ERROR'); assert.equal(d.calls.length, 1); assert.equal(r.accounting.partialBytes, 7);
  const x = deps(); x.transport = async (q, o) => { x.calls.push(q); o.onChunk(17); throw Error('private secret transport text'); };
  const y = await c.run(options(), x); assert.equal(y.code, 'NETWORK_ERROR'); assert.equal(y.accounting.received, 17); assert.equal(x.calls.length, 1);
  assert.equal(JSON.stringify(y).includes('private'), false);
});
test('Census independent replay verifies raw query controls summary identity and cannot write', async () => {
  const d = deps(q => q.kind === 'data' && q.index === 0 ? { status: 200,
    bytes: nd([block(bounds[0], dates[0], [ix([1, 2])]), { header: header(bounds[1] - 1, dates[1] - 1) }]) } : undefined);
  const r = await c.run(options(), d); assert.equal(r.code, null);
  const ro = { read: d.store.read, list: d.store.list }, replayed = await c.replay(ro);
  assert.equal(replayed.code, null); assert.equal(replayed.summaryHash, r.summaryHash); assert.equal(replayed.runBudget, 'UNMEASURED');
  assert.equal(replayed.summary.creations, 1); const manifestBytes = d.store.read('manifest.json'), m = JSON.parse(manifestBytes);
  m.operational.elapsedMs += 900; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m)));
  assert.equal((await c.replay(ro)).summaryHash, r.summaryHash);
  m.records[0].query.url = 'https://other.invalid/head'; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m)));
  assert.equal((await c.replay(ro)).code, 'INTEGRITY_ERROR'); d.store.files.set('manifest.json', manifestBytes);
  const raw = [...d.store.files.keys()].find(n => n.endsWith('.raw')); d.store.files.set(raw, Buffer.from('changed'));
  assert.equal((await c.replay(ro)).code, 'INTEGRITY_ERROR');
});
test('Census parser deterministic locator order deduplicates equal content and rejects conflict', () => {
  const a = c.admit(nd([block(bounds[0], dates[0], [ix([3]), ix([0, 1])])]), dq(), bounds);
  const b = c.admit(nd([block(bounds[0], dates[0], [ix([0, 1]), ix([3])])]), dq(), bounds);
  assert.equal(a.code, null); assert.deepEqual(a.creations.map(x => x.instructionPath), b.creations.map(x => x.instructionPath));
  assert.equal(c.admit(nd([block(bounds[0], dates[0], [ix(), ix()])]), dq(), bounds).creations.length, 1);
  const conflict = ix([0], { accounts: [key(10), key(4), key(5), key(6), key(7)] });
  assert.equal(c.admit(nd([block(bounds[0], dates[0], [ix(), conflict])]), dq(), bounds).code, 'IMMUTABLE_CONFLICT');
});
test('Census matched header count includes UNKNOWN instructions without successful creations', async () => {
  const d = deps(q => q.kind === 'data' && q.index === 0 ? { status: 200,
    bytes: nd([block(bounds[0], dates[0], [ix([0], { isCommitted: false }), ix([1], { error: {} })]),
      { header: header(bounds[1] - 1, dates[1] - 1) }]) } : undefined);
  const r = await c.run(options(), d); assert.equal(r.code, null); assert.equal(r.summary.streams[0].unknown, 2);
  assert.equal(r.summary.creations, 0);
  assert.equal(r.summary.streams[0].matchingHeaders, 1, 'all selected instruction headers count even if every creation is UNKNOWN');
  assert.equal(r.summary.streams[0].lastMatched, bounds[0]); assert.equal(r.summary.streams[0].lastScanned, bounds[1] - 1);
});
test('Census manifest retained accounting includes its own bytes and replay rejects false physical totals', async () => {
  const d = deps(), r = await c.run(options(), d); assert.equal(r.code, null);
  const physical = [...d.store.files.values()].reduce((n, b) => n + b.length, 0);
  const m = JSON.parse(d.store.read('manifest.json'));
  assert.equal(m.accounting.retained, physical, 'persisted retained bytes include final manifest and every physical output');
  assert.equal(r.accounting.retained, physical);
  assert.equal((await c.replay({ read: d.store.read, list: d.store.list })).code, null);
  m.accounting.retained = 0; d.store.files.set('manifest.json', Buffer.from(JSON.stringify(m)));
  assert.equal((await c.replay({ read: d.store.read, list: d.store.list })).code, 'INTEGRITY_ERROR');
});
test('Census CLI preflight disk and free space failures exit one before any attempt', () => {
  const { spawnSync } = require('node:child_process'), path = require('node:path');
  const cli = path.join(__dirname, '..', 'e2-census-cli.cjs');
  const disk = spawnSync(process.execPath, [cli, '--enable-public', '--retained-before', '45900000001'], { encoding: 'utf8', timeout: 10000 });
  assert.equal(disk.error, undefined); const receipt = JSON.parse(disk.stdout); assert.equal(receipt.code, 'DISK_LIMIT');
  assert.equal(receipt.accounting.attempts, 0);
  assert.equal(disk.status, 1, 'preflight disk failure has integrity/preflight exit code one');
  const wrapper = `const c=require(${JSON.stringify(path.join(__dirname, '..', 'e2-census.cjs'))});` +
    `c.run=async()=>({...c.error('FREE_SPACE_LIMIT'),accounting:{attempts:0}});` +
    `process.argv=['node',${JSON.stringify(cli)},'--enable-public','--retained-before','0'];` +
    `require('node:module')._load(${JSON.stringify(cli)},null,true);`;
  const free = spawnSync(process.execPath, ['-e', wrapper], { encoding: 'utf8', timeout: 10000 });
  assert.equal(free.error, undefined); assert.equal(JSON.parse(free.stdout).code, 'FREE_SPACE_LIMIT');
  assert.equal(free.status, 1, 'preflight free-space failure has integrity/preflight exit code one');
});
