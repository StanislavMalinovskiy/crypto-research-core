'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const p = require('../exploratory-probe-v2.cjs');
const { runCli } = require('../exploratory-probe-v2-cli.cjs');
const anchor = '2026-04-01T00:00:00Z', unix = Date.parse(anchor) / 1000;
const h = (number, timestamp = unix, parentNumber = number - 1) => ({ number, timestamp, hash: `h${number}`, parentNumber, parentHash: `h${parentNumber}` });
const bytes = rows => Buffer.from(rows.map(x => JSON.stringify(x)).join('\n') + '\n');
function base58(buffer) {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; let n = BigInt('0x' + buffer.toString('hex')), result = '';
  while (n) { result = alphabet[Number(n % 58n)] + result; n /= 58n; }
  for (const byte of buffer) { if (byte !== 0) break; result = '1' + result; } return result;
}
const swap = (programId, hex, address = [0], extra = {}) => ({ transactionIndex: 0, instructionAddress: address, programId, data: base58(Buffer.from(hex, 'hex')), isCommitted: true, error: null, ...extra });
const tx = (index = 0, signature = 'fullSignature') => ({ transactionIndex: index, signatures: [signature], feePayer: 'payer', fee: '9007199254740993123', computeUnitsConsumed: '123', err: null });
function memory() { const files = new Map(); let made = false; return { files, free: () => 100000000000,
  create() { if (made) throw Error('OUTPUT_EXISTS'); made = true; }, write(name, b) { if (files.has(name)) throw Error('OUTPUT_EXISTS'); files.set(name, Buffer.from(b)); },
  read(name) { if (!files.has(name)) throw Error('FILE_MISSING'); return files.get(name); } }; }
function deps(alter, store = memory()) {
  let time = 0; const calls = [];
  return { store, calls, source: { commit: 'a'.repeat(40), dirty: false }, runtime: 'v24.99.1', now: () => time, sleep: async ms => { time += ms; },
    transport: async (q, options) => {
      calls.push(q); const start = Date.parse(q.anchor) / 1000;
      const headers = [h(100, start), h(102, start + 300, 100), h(103, start + 599, 102), h(104, start + 600, 103)];
      let b = q.kind === 'resolver' ? Buffer.from(JSON.stringify({ block_number: q.time === start ? 100 : 104 }))
        : bytes(headers.filter(x => x.number >= q.from && x.number <= q.to).map(header => q.kind === 'A' || q.kind === 'C'
          ? { header, transactions: [tx()], instructions: [swap(p.PROGRAMS[0], '66063d1201daebea')], tokenBalances: [], balances: [] } : { header }));
      const response = alter?.(q, b, headers); if (response) { options.onChunk(response.received || 0); return response; }
      options.onChunk(b.length); return { code: null, status: 200, bytes: b, received: b.length };
    } };
}
test('Node24 patches and strict disabled/root guards perform zero I/O', async () => {
  const d = deps(); assert.equal((await runCli([], d)).code, 'DISABLED'); assert.equal(d.calls.length, 0); assert.equal(d.store.files.size, 0);
  assert.equal((await runCli(['--enable-public', '--output', 'secret'], d)).code, 'ARGUMENTS_INVALID');
  assert.equal((await p.runProbe({ enabled: true, output: p.OUTPUT }, { ...d, runtime: 'v25.0.0' })).code, 'RUNTIME_INVALID');
  assert.equal((await p.runProbe({ enabled: true, output: p.OUTPUT }, d)).status, 'COMPLETE');
  assert.equal((await p.runProbe({ enabled: true, output: p.OUTPUT }, d)).code, 'OUTPUT_EXISTS');
  assert.throws(() => p.fileStore('C:\\git\\target'), /UNSAFE_PATH/);
});
test('fixed OR profiles, census skipped slots and cursor continuation preserve distinct coverage', async () => {
  const q = p.query('A', anchor, 100, 131); assert.equal(q.body.toBlock, 131); assert.deepEqual(q.body.instructions[0].programId, p.PROGRAMS);
  assert.equal(q.body.instructions[0].transactionBalances, undefined); assert.equal(p.query('C', anchor, 100, 100).body.instructions[0].transactionBalances, true);
  const d = deps((q, b) => q.kind === 'A' && q.from === 100 ? { code: null, status: 200, bytes: bytes([{ header: h(100, Date.parse(q.anchor) / 1000), transactions: [tx()], instructions: [] }]), received: 1 } : null);
  const r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d); assert.equal(r.status, 'COMPLETE');
  assert.equal(r.summary.windows[0].skippedPositions, 1); assert.equal(r.summary.windows[0].A.processedSlots, 3);
  assert.equal(r.summary.windows[0].A.coveredSeconds, 600); assert.equal(r.summary.windows[0].C.processedSlots, 3);
  assert.ok(d.calls.some(x => x.kind === 'A' && x.from === 101)); assert.equal(r.d1Passed, false);
  const replay = await p.replay(d.store); assert.equal(replay.code, null); assert.equal(replay.summaryHash, r.summaryHash);
  assert.ok([...d.store.files.values()].every(b => b.length <= 16000000));
  const raw = [...d.store.files.keys()].find(x => x.endsWith('.raw')); d.store.files.set(raw, Buffer.from('changed'));
  assert.equal((await p.replay(d.store)).code, 'INTEGRITY_ERROR');
});
test('duplicate, holdout, missing census and mismatched payload reject before raw counts', async () => {
  const good = JSON.stringify(h(100)), forbidden = JSON.stringify(h(100, 1790553600));
  for (const b of [Buffer.from(`{"header":${forbidden},"header":${good}}\n`), bytes([{ header: h(100, 1790553600) }]), bytes([{ header: {} }])])
    assert.equal(p.admit(b, { kind: 'header', anchor, from: 100, to: 100, time: unix }).code, 'RESPONSE_INVALID');
  const d = deps(q => q.kind === 'census' ? { code: null, status: 200, bytes: bytes([{ header: h(100, Date.parse(q.anchor) / 1000) }, { header: h(104, Date.parse(q.anchor) / 1000 + 600, 103) }]), received: 50 } : null);
  const r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d); assert.equal(r.status, 'INCOMPLETE'); assert.equal(d.calls.filter(q => ['A', 'B', 'C'].includes(q.kind)).length, 0);
  assert.ok(![...d.store.files.values()].some(b => b.includes(Buffer.from('1790553600'))));
  const d2 = deps(q => q.kind === 'A' ? { code: null, status: 200, bytes: bytes([{ header: h(100, 1790553600), transactions: [tx()] }]), received: 20 } : null);
  const r2 = await p.runProbe({ enabled: true, output: p.OUTPUT }, d2); assert.equal(r2.summary.windows[0].A.processedSlots, 0);
});
test('cap bisection is left-first, exact budgets and network failures never retry', async () => {
  const d = deps(q => q.kind === 'A' && q.to > q.from ? { code: 'RESPONSE_LIMIT', received: 16000001 } : null);
  const r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d); assert.equal(r.status, 'COMPLETE');
  const a = d.calls.filter(x => x.kind === 'A' && x.anchor === anchor); assert.deepEqual(a.slice(0, 3).map(x => [x.from, x.to]), [[100, 103], [100, 101], [100, 100]]);
  assert.ok(r.summary.windows[0].A.gaps.some(x => x.code === 'CAP_SPLIT')); assert.ok(r.accounting.received >= 16000001);
  const n = deps(q => q.kind === 'A' ? { code: 'NETWORK_ERROR', received: 3 } : null); const nr = await p.runProbe({ enabled: true, output: p.OUTPUT }, n);
  assert.equal(nr.status, 'INCOMPLETE'); assert.equal(n.calls.filter(x => x.kind === 'A' && x.anchor === anchor).length, 1);
  assert.equal(p.CONFIG.limits.response, 16000000); assert.equal(p.CONFIG.limits.received, 1000000000); assert.equal(p.CONFIG.limits.retries, 0);
  for (const [limits, code] of [[{ attempts: 1 }, 'ATTEMPT_LIMIT'], [{ received: 32000001 }, 'RECEIVED_LIMIT'], [{ disk: 32000001 }, 'DISK_LIMIT'], [{ elapsed: 1 }, 'TIME_LIMIT']]) {
    const budget = p.budget(limits, 0); assert.equal(budget.reserve(0, 100000000000), null); budget.charge(16000001, 16000001);
    assert.equal(budget.reserve(2, 100000000000), code);
  }
  const checkpoint = p.budget({ attempts: 5 }, 0); for (let i = 0; i < 4; i++) assert.equal(checkpoint.reserve(i, 100000000000), null);
  assert.ok(checkpoint.state.checkpoints.includes('attempts'));
});
test('exact System layouts and cross-profile signatures preserve transfer/fee lexemes', () => {
  const b = Buffer.alloc(12); b.writeUInt32LE(2); b.writeBigUInt64LE(9007199254740993123n, 4);
  const instruction = { ...swap('11111111111111111111111111111111', b.toString('hex')), accounts: ['sender', p.JITO[0]] };
  assert.equal(p.transfer(instruction).lamports, '9007199254740993123');
  const seed = Buffer.alloc(55); seed.writeUInt32LE(11); seed.writeBigUInt64LE(7n, 4); seed.writeBigUInt64LE(3n, 12); seed.write('abc', 20);
  assert.equal(p.transfer({ ...instruction, data: base58(seed), accounts: ['sender', 'base', p.JITO[0]] }).lamports, '7');
  assert.equal(p.transfer({ ...instruction, data: base58(seed.subarray(0, 54)) }).code, 'MALFORMED');
  const r = p.inspect([{ header: h(100), transactions: [tx(8)], instructions: [{ ...instruction, transactionIndex: 8 }] }], 'B', new Set(['100:fullSignature']));
  assert.equal(r.associatedTransfers, 1); assert.deepEqual(r.lamportExamples, ['9007199254740993123']); assert.equal(r.exactFeeInputs, 1);
  assert.equal(p.transfer({ ...instruction, data: '0' }).code, 'MALFORMED');
});
test('recognized swaps separate other failed CPI and rich denominators with exact shares', () => {
  const instructions = [swap(p.PROGRAMS[0], '66063d1201daebea'), swap(p.PROGRAMS[3], 'f8c69e91e17587c8', [1]),
    swap(p.PROGRAMS[3], 'f8c69e91e17587c8', [1, 0]), swap(p.PROGRAMS[0], '0000000000000000', [2]),
    swap(p.PROGRAMS[0], '66063d1201daebea', [3], { error: 'failed' }), swap(p.PROGRAMS[0], '66063d1201daebea', [4], { isCommitted: false }),
    swap(p.PROGRAMS[0], '00', [5], { data: '0' }), swap(p.PROGRAMS[0], '66063d1201daebea')];
  const r = p.inspect([{ header: h(100), transactions: [tx()], instructions }], 'A');
  assert.equal(r.recognizedTotal, 3); assert.deepEqual(r.missingDepthDiagnosticShare, { numerator: '2', denominator: '3' });
  assert.equal(r.other, 1); assert.equal(r.failed, 1); assert.equal(r.uncommitted, 1); assert.equal(r.malformed, 1); assert.equal(r.uniqueTransactions, 1);
  assert.equal(p.inspect([{ header: h(100), transactions: [tx()], instructions }], 'C').recognizedTotal, 0);
});
test('exact low central high projections and unavailable rich denominator are never invented', () => {
  const samples = [{ A: { complete: true, uniqueTransactions: 600, bytes: 6000, elapsedMs: 60000 }, B: { complete: true, bytes: 1200, elapsedMs: 60000 }, C: { uniqueTransactions: 3, bytes: 3000 } }];
  const r = p.sensitivity(samples); const t = BigInt(p.CONFIG.targetSeconds);
  assert.equal(r.scenarios[0].richBytes, ((t * 1000n + 99n) / 100n).toString()); assert.equal(r.scenarios[1].richBytes, (t * 300n).toString());
  assert.equal(r.scenarios[2].richBytes, (t * 10000n).toString()); assert.equal(r.scenarios[0].cashMicrousd, '0');
  assert.equal(r.scenarios[0].lightABytes, (t * 10n).toString()); assert.equal(r.fullD1UpperBound, null);
  const unavailable = p.sensitivity([{ ...samples[0], C: { bytes: 0, uniqueTransactions: 0 } }]); assert.equal(unavailable.scenarios[0].richBytes, null);
  assert.equal(p.sensitivity([{ ...samples[0], A: { ...samples[0].A, complete: false } }]).scenarios[0].richBytes, null);
});
test('partial writes are truthful and offline replay reads pages incrementally', async () => {
  const store = memory(), write = store.write; store.write = (name, b) => { if (name.endsWith('.raw')) { store.files.set(name, b.subarray(0, 5)); throw Error('STORAGE_ERROR'); } write(name, b); };
  const d = deps(null, store), r = await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
  assert.equal(r.accounting.retained, [...store.files.values()].reduce((n, b) => n + b.length, 0)); assert.equal(r.status, 'INCOMPLETE');
  assert.equal(d.calls.length, 1); assert.equal((await p.replay(store)).code, 'INTEGRITY_ERROR');
});
test('actual finite loopback transport accepts >v1 cap, caps overflow and redacts timeout', async t => {
  const s = http.createServer((req, res) => { res.write(Buffer.alloc(2100000, 97)); res.end('z'); }); await new Promise(r => s.listen(0, '127.0.0.1', r));
  t.after(() => new Promise(r => { s.closeAllConnections(); s.close(r); })); const loopback = `http://127.0.0.1:${s.address().port}`;
  let n = 0; const r = await p.send(p.query('resolver', anchor, undefined, undefined, unix), { loopback, onChunk: x => { n += x; } });
  assert.equal(r.code, null); assert.equal(r.bytes.length, 2100001); assert.equal(n, 2100001);
  const cap = await p.send(p.query('resolver', anchor, undefined, undefined, unix), { loopback, responseLimit: 10 }); assert.equal(cap.code, 'RESPONSE_LIMIT'); assert.equal(cap.received, 11); assert.equal(cap.bytes, undefined);
  const idle = http.createServer((req, res) => res.write('secret')); await new Promise(r => idle.listen(0, '127.0.0.1', r));
  t.after(() => new Promise(r => { idle.closeAllConnections(); idle.close(r); }));
  const timeout = await p.send(p.query('resolver', anchor, undefined, undefined, unix), { loopback: `http://127.0.0.1:${idle.address().port}`, deadlineMs: 20 });
  assert.equal(timeout.code, 'TIMEOUT'); assert.equal(timeout.received, 6); assert.equal(timeout.bytes, undefined); assert.ok(!JSON.stringify(timeout).includes('secret'));
});
test('conflicting immutable instruction identities reject both orders before retention or counts', async () => {
  const recognized = swap(p.PROGRAMS[0], '66063d1201daebea'), other = { ...recognized, data: base58(Buffer.from('0000000000000000', 'hex')) };
  for (const instructions of [[recognized, other], [other, recognized]]) {
    const row = { header: h(100), transactions: [tx()], instructions }, q = p.query('A', anchor, 100, 100);
    assert.equal(p.admit(bytes([row]), q, [h(100)]).code, 'RESPONSE_INVALID');
    const d = deps((query, b, headers) => query.kind === 'A' ? { code: null, status: 200,
      bytes: bytes([{ ...row, header: headers[0] }]), received: 500 } : null);
    const result = await p.runProbe({ enabled: true, output: p.OUTPUT }, d);
    assert.equal(result.summary.windows[0].A.processedSlots, 0); assert.equal(result.summary.windows[0].A.recognizedTotal, 0);
    const manifest = JSON.parse(d.store.read('manifest.json'));
    assert.ok(manifest.records.filter(r => r.request.kind === 'A').every(r => r.code === 'RESPONSE_INVALID' && !r.raw));
  }
  const equal = p.inspect([{ header: h(100), transactions: [tx(), tx()], instructions: [recognized, { ...recognized }] }], 'A');
  assert.equal(equal.uniqueTransactions, 1); assert.equal(equal.recognizedTotal, 1);
});
test('conflicting immutable transaction identities reject permutations while ambiguous links stay unknown', () => {
  for (const transactions of [[tx(), { ...tx(), fee: '42' }], [{ ...tx(), fee: '42' }, tx()]]) {
    const row = { header: h(100), transactions, instructions: [swap(p.PROGRAMS[0], '66063d1201daebea')] };
    assert.equal(p.admit(bytes([row]), p.query('A', anchor, 100, 100), [h(100)]).code, 'RESPONSE_INVALID');
  }
  const unknown = p.inspect([{ header: h(100), transactions: [tx(), tx(0, 'differentSignature')],
    instructions: [swap(p.PROGRAMS[0], '66063d1201daebea')] }], 'A');
  assert.equal(unknown.recognizedTotal, 0); assert.ok(unknown.unknownSignatureLinks > 0);
});
test('light data projections exclude rejected cap traffic but retain transport accounting and elapsed cost', async () => {
  const A = { complete: true, uniqueTransactions: 600, admittedBytes: 600, bytes: 16000601, elapsedMs: 60000 },
    B = { complete: true, admittedBytes: 1200, bytes: 16001201, elapsedMs: 60000 }, C = { uniqueTransactions: 3, bytes: 3000 };
  const result = p.sensitivity([{ A, B, C }]), target = BigInt(p.CONFIG.targetSeconds);
  assert.equal(result.scenarios[0].lightABytes, target.toString()); assert.equal(result.scenarios[0].lightBBytes, (target * 2n).toString());
  assert.equal(result.scenarios[0].lightARequestsCapacityOnly, ((target + 15999999n) / 16000000n).toString());
  assert.equal(result.scenarios[0].measuredLightRuntimeMs, (target * 100n).toString());
  const d = deps(q => q.kind === 'A' && q.to > q.from ? { code: 'RESPONSE_LIMIT', received: 16000001 } : null);
  const run = await p.runProbe({ enabled: true, output: p.OUTPUT }, d), stats = run.summary.windows[0].A;
  assert.equal(stats.rejectedBytes, stats.bytes - stats.admittedBytes); assert.ok(stats.rejectedBytes >= 16000001);
  assert.ok(run.accounting.received > stats.admittedBytes); assert.equal((await p.replay(d.store)).summaryHash, run.summaryHash);
});
