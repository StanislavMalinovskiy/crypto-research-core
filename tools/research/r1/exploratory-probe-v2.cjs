'use strict';
const fs = require('node:fs');
const path = require('node:path');
const v1 = require('./exploratory-probe.cjs');
const { parse, canonical, digest, fingerprint, integer } = v1;
const OUTPUT = 'C:\\crypto-research-evidence\\r1-d1\\exploratory-sqd-v2';
const WINDOWS = Object.freeze(['2026-04-01T00:00:00Z', '2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z', '2026-08-01T00:00:00Z']);
const PROGRAMS = Object.freeze([...v1.PROGRAMS, 'whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc',
  'CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK', 'LBUZKhRxPF3XUpBCjp4YzTKgLccjZhTSDM9YuVaPwxo']);
const JITO = Object.freeze(['96gYZGLnJYVFmbjzopPSU6QiEV5fGqZNyN9nmNhvrZU5', 'HFqU5x63VTqvQss8hp11i4wVV8bD44PvwucfZ2bU7gRe',
  'Cw8CFyM9FkoMi7K7Crf6HNQqf4uEMzpKw6QNghXLvLkY', 'ADaUMid9yfUytqMBgopwjb2DTLSokTSzL1zt6iGPaS49',
  'DfXygSm4jCyNCybVYYK6DwvWqjKee8pbDmJGcLWNDXjh', 'ADuUkR4vqLUMWXxW9gh6D6L8pMSawimctcNZ5pGwDcEt',
  'DttWaMuVvTiduZRnguLF7jNxTgiMBZ1hyAumKUiL2KRL', '3AVi9Tg9Uo68tJfuvoKvqKNWKkC5wPdSSdeBnizKZ6jT']);
const PREFIXES = Object.freeze([
  ['66063d1201daebea', '38fc74089edfcd5f', 'b817ee6167c5d33d', 'c2ab1c46684d5b2f', '33e685a4017f83ad', '5df6823ce7e940b2'],
  ['66063d1201daebea', 'c62e1552b4d9e870', '33e685a4017f83ad'], ['09', '0b'],
  ['f8c69e91e17587c8', '2b04ed0b1ac91e62', 'c360ed6c44a2dbe6', 'ba8fd11dfe02c275'],
  ['f8c69e91e17587c8', '2b04ed0b1ac91e62'],
  ['f8c69e91e17587c8', '414b3f4ceb5b5b88', 'fa49652126cf4bb8', '2bd7f784893cf351', '38ade6d0ade49ccd', '4a62c0d6b1334b33']]);
const SOURCES = Object.freeze(['https://raw.githubusercontent.com/pump-fun/pump-public-docs/main/idl/pump.json',
  'https://raw.githubusercontent.com/pump-fun/pump-public-docs/main/idl/pump_amm.json',
  'https://raw.githubusercontent.com/raydium-io/raydium-amm/master/program/src/instruction.rs',
  'https://raw.githubusercontent.com/orca-so/whirlpools/main/programs/whirlpool/src/lib.rs',
  'https://raw.githubusercontent.com/raydium-io/raydium-clmm/master/programs/amm/src/lib.rs',
  'https://raw.githubusercontent.com/MeteoraAg/dlmm-sdk/main/idls/dlmm.json', 'https://www.anchor-lang.com/docs/basics/idl']);
const LIMITS = Object.freeze({ attempts: 1000, received: 1000000000, disk: 1100000000, response: 16000000,
  elapsed: 7200000, deadline: 45000, interval: 2000, concurrency: 1, retries: 0, cashMicrousd: '0', free: 30000000000 });
const FIELDS = Object.freeze({ block: v1.CONFIG.fields.block, transaction: 'transactionIndex signatures feePayer fee computeUnitsConsumed err',
  instruction: 'transactionIndex instructionAddress programId data isCommitted error',
  richTransaction: v1.CONFIG.fields.transaction + ' feePayer fee computeUnitsConsumed', richInstruction: v1.CONFIG.fields.instruction,
  balance: v1.CONFIG.fields.balance, tokenBalance: v1.CONFIG.fields.tokenBalance });
const OMITTED = Object.freeze(['candidate wallet/token/pool population and mapping', 'discovery and subscriptions', 'other venues',
  'related history joins', 'account/tick/bin state', 'independent checks', 'SOL/USD', 'observed visibility',
  'base/priority fee separation', 'non-Jito and separate-bundle tips', 'historical Jito label availability',
  'retention and continued free capacity', 'tail/regime variation']);
const CONFIG = Object.freeze({ queryVersion: 'exploratory-sqd-v2', summaryVersion: 'exploratory-counts-v2',
  canonicalizationVersion: 'exploratory-config-c14n-v2', projectionVersion: 'exploratory-workload-sensitivity-v1',
  runtimeMajor: 24, source: v1.CONFIG.source, base: v1.CONFIG.base, api: v1.CONFIG.api, output: OUTPUT, windows: WINDOWS,
  windowSeconds: 600, maximumSlots: 3000, pageSlots: 32, splitDepth: 5, richOffsets: [0, 300, 599], programs: PROGRAMS,
  jito: JITO, jitoVersion: 'jito-document-list-2026-10-03', jitoSource: 'https://docs.jito.wtf/lowlatencytxnsend/#gettipaccounts',
  systemSource: 'https://raw.githubusercontent.com/solana-labs/solana/master/sdk/program/src/system_instruction.rs',
  systemProgram: '11111111111111111111111111111111', prefixVersion: 'exploratory-swap-prefixes-v1', prefixes: PREFIXES, prefixSources: SOURCES,
  fields: FIELDS, links: v1.CONFIG.instructionLinks, limits: LIMITS, exclusion: v1.CONFIG.exclusion, metadataCap: 8000000,
  metadataReserve: 16000000, base58Characters: 512, decodedBytes: 384, datasetRevision: null, retention: 'UNVERIFIED',
  envelope: ['2026-04-01T00:00:00Z', '2026-09-29T04:02:00Z'], targetSeconds: '15652920',
  scenarios: [{ name: 'low', fraction: ['1', '100'], ancillary: '1', tariffUsdPerGB: '0' },
    { name: 'central', fraction: ['1', '10'], ancillary: '3', tariffUsdPerGB: '1' },
    { name: 'high', fraction: ['1', '1'], ancillary: '10', tariffUsdPerGB: '10' }],
  formulas: { rich: 'ceil(T*transactions/second*richBytes/transaction*fraction*ancillary)', light: 'ceil(T*bytes/second)',
    requests: 'ceil(projectedBytes/16000000):capacity-only lower estimate', runtime: 'ceil(T*measuredPageElapsedMs/coveredSeconds)',
    cash: 'ceil(projectedRichBytes*tariffUsdPerGB*1000000/1000000000)', rounding: 'ceiling only' }, omissions: OMITTED });
const FLAGS = Object.freeze({ classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false });
const fail = code => { throw Error(code); };
const invalid = code => ({ ...FLAGS, status: 'INCOMPLETE', code });
const cfgHash = () => fingerprint(CONFIG.canonicalizationVersion, CONFIG);
const selection = names => Object.fromEntries(names.split(' ').map(k => [k, true]));
function query(kind, anchor, from, to, time) {
  if (!WINDOWS.includes(anchor) || !['resolver', 'header', 'census', 'A', 'B', 'C'].includes(kind)) fail('QUERY_INVALID');
  const start = Date.parse(anchor) / 1000;
  if (kind === 'resolver') {
    if (![start, start + 600].includes(time)) fail('QUERY_INVALID');
    return { kind, anchor, time, method: 'GET', url: `${CONFIG.base}timestamps/${time}/block` };
  }
  if (!Number.isSafeInteger(from) || !Number.isSafeInteger(to) || from < 0 || to < from || to - from >= 32
    || (['header', 'C'].includes(kind) && from !== to)) fail('QUERY_INVALID');
  const body = { type: 'solana', fromBlock: from, toBlock: to, includeAllBlocks: true, fields: { block: selection(FIELDS.block) } };
  if (['A', 'B', 'C'].includes(kind)) {
    body.fields.transaction = selection(kind === 'C' ? FIELDS.richTransaction : FIELDS.transaction);
    body.fields.instruction = selection(kind === 'C' ? FIELDS.richInstruction : FIELDS.instruction + (kind === 'B' ? ' accounts' : ''));
    const filter = { programId: kind === 'A' ? PROGRAMS : kind === 'B' ? [CONFIG.systemProgram] : PROGRAMS.slice(0, 3), transaction: true };
    if (kind === 'B') filter.mentionsAccount = JITO;
    if (kind === 'C') { for (const link of CONFIG.links) filter[link] = true;
      body.fields.balance = selection(FIELDS.balance); body.fields.tokenBalance = selection(FIELDS.tokenBalance); }
    body.instructions = [filter];
  }
  if (kind === 'header' && ![start, start + 600].includes(time)) fail('QUERY_INVALID');
  return { kind, anchor, from, to, ...(kind === 'header' ? { time } : {}), method: 'POST', url: CONFIG.base + 'finalized-stream', body };
}
function validQuery(q) { try { return canonical(q) === canonical(query(q.kind, q.anchor, q.from, q.to, q.time)); } catch { return false; } }
function safeHeader(value, q) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail('RESPONSE_INVALID');
  const h = { number: integer(value.number), hash: value.hash, parentNumber: integer(value.parentNumber), parentHash: value.parentHash, timestamp: integer(value.timestamp) };
  const start = Date.parse(q.anchor) / 1000;
  if (h.number < q.from || h.number > q.to || h.parentNumber >= h.number || h.timestamp < start || h.timestamp >= start + 660
    || h.timestamp >= 1788134400 || !/^[A-Za-z0-9]{1,128}$/.test(h.hash || '') || !/^[A-Za-z0-9]{1,128}$/.test(h.parentHash || '')) fail('RESPONSE_INVALID');
  if (q.kind === 'header' && (h.timestamp < q.time || h.timestamp >= q.time + 60)) fail('RESPONSE_INVALID');
  return h;
}
function admit(bytes, q, expected, previous) {
  try {
    if (!Buffer.isBuffer(bytes) || bytes.length > LIMITS.response || !WINDOWS.includes(q.anchor)) fail('RESPONSE_INVALID');
    if (q.kind === 'resolver') { const value = parse(bytes); if (!value || Object.keys(value).join() !== 'block_number') fail('RESPONSE_INVALID');
      return { code: null, slot: integer(value.block_number) }; }
    const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes).trim(); if (!text) fail('RESPONSE_INVALID');
    const rows = text.split('\n').map(line => parse(Buffer.from(line))), headers = [];
    if (q.kind === 'header' && rows.length !== 1) fail('RESPONSE_INVALID');
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i], h = safeHeader(row.header, q), prev = headers.at(-1) || previous;
      if (prev && (h.number <= prev.number || h.timestamp < prev.timestamp || h.parentNumber !== prev.number || h.parentHash !== prev.hash)) fail('RESPONSE_INVALID');
      if (expected && (!expected[i] || canonical(h) !== canonical(expected[i]))) fail('RESPONSE_INVALID');
      if (['A', 'B', 'C'].includes(q.kind) && h.timestamp >= Date.parse(q.anchor) / 1000 + 600) fail('RESPONSE_INVALID');
      for (const name of ['transactions', 'instructions', 'balances', 'tokenBalances']) {
        if (row[name] !== undefined && (!Array.isArray(row[name]) || row[name].some(x => !x || typeof x !== 'object' || Array.isArray(x)))) fail('RESPONSE_INVALID');
      }
      identityLinks(row);
      row.header = h; headers.push(h);
    }
    return { code: null, rows, headers };
  } catch { return { code: 'RESPONSE_INVALID' }; }
}
function decode58(text) {
  if (typeof text !== 'string' || !text.length || text.length > 512) fail('MALFORMED');
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'; let n = 0n, leading = 0;
  for (const char of text) { const v = alphabet.indexOf(char); if (v < 0) fail('MALFORMED'); n = n * 58n + BigInt(v); }
  while (text[leading] === '1') leading++;
  const hex = n.toString(16), body = n ? Buffer.from(hex.length % 2 ? '0' + hex : hex, 'hex') : Buffer.alloc(0);
  if (leading + body.length > 384) fail('MALFORMED'); return Buffer.concat([Buffer.alloc(leading), body]);
}
function transfer(i) {
  try {
    if (i.programId !== CONFIG.systemProgram) return { code: 'OTHER' };
    const b = decode58(i.data); if (b.length < 4) fail('MALFORMED'); const tag = b.readUInt32LE(0); let recipient;
    if (tag === 2) { if (b.length !== 12) fail('MALFORMED'); recipient = 1; }
    else if (tag === 11) {
      if (b.length < 52) fail('MALFORMED'); const length = b.readBigUInt64LE(12);
      if (length > 332n || BigInt(b.length) !== 52n + length) fail('MALFORMED');
      new TextDecoder('utf-8', { fatal: true }).decode(b.subarray(20, 20 + Number(length))); recipient = 2;
    } else return { code: 'OTHER' };
    if (!Array.isArray(i.accounts) || !JITO.includes(i.accounts[recipient])) return { code: 'OTHER' };
    return { code: null, lamports: b.readBigUInt64LE(4).toString(), recipient: i.accounts[recipient] };
  } catch { return { code: 'MALFORMED' }; }
}
const share = (n, d) => d ? { numerator: String(n), denominator: String(d) } : null;
// A canonical identity may repeat equal content, never conflicting immutable content.
function identityLinks(row) {
  const links = new Map(), transactions = new Map(), instructions = new Map();
  const observe = (seen, key, value) => { const content = canonical(value);
    if (seen.has(key) && seen.get(key) !== content) fail('RESPONSE_INVALID'); seen.set(key, content); };
  for (const tx of row.transactions || []) {
    let index; try { index = integer(tx.transactionIndex); } catch { continue; }
    const signature = Array.isArray(tx.signatures) && typeof tx.signatures[0] === 'string' && tx.signatures[0].length > 0
      && tx.signatures[0].length <= 256 ? tx.signatures[0] : null;
    if (signature) observe(transactions, signature, tx);
    if (!signature || links.has(index) && links.get(index) !== signature) links.set(index, null); else links.set(index, signature);
  }
  for (const instruction of row.instructions || []) {
    let signature, address; try { signature = links.get(integer(instruction.transactionIndex));
      if (!Array.isArray(instruction.instructionAddress) || !instruction.instructionAddress.length || instruction.instructionAddress.length > 64) continue;
      address = instruction.instructionAddress.map(integer).join('.');
    } catch { continue; }
    if (signature) observe(instructions, `${signature}:${address}`, instruction);
  }
  return links;
}
function emptyStats() {
  return { processedSlots: 0, bytes: 0, requests: 0, elapsedMs: 0, complete: false, coveredSeconds: 0, gaps: [],
    populatedBytes: 0, populatedTransactions: 0, admittedBytes: 0, rejectedBytes: 0,
    uniqueTransactions: 0, uniqueInstructions: 0, uniqueFeePayerProxies: 0, distinctMintProxies: 0, distinctAccountProxies: 0,
    exactFeeInputs: 0, unknownSignatureLinks: 0, matchedTotal: 0, matched: PROGRAMS.map(() => 0), recognizedTotal: 0, recognized: PROGRAMS.map(() => 0),
    other: 0, failed: 0, uncommitted: 0, malformed: 0, transfers: 0, associatedTransfers: 0, unknownTransfers: 0, lamportExamples: [], collections: {} };
}
function inspect(rows, kind, association = new Set()) {
  const r = emptyStats(), txs = new Set(), ids = new Set(), payers = new Set(), mints = new Set(), accounts = new Set();
  for (const row of rows) {
    identityLinks(row);
    const slot = row.header.number, indexMap = new Map(), transactions = row.transactions || [];
    for (const tx of transactions) {
      let index; try { index = integer(tx.transactionIndex); } catch { r.unknownSignatureLinks++; continue; }
      const signature = Array.isArray(tx.signatures) && typeof tx.signatures[0] === 'string' && tx.signatures[0].length <= 256 ? tx.signatures[0] : null;
      if (!signature || indexMap.has(index) && indexMap.get(index) !== signature) { indexMap.set(index, null); r.unknownSignatureLinks++; }
      else indexMap.set(index, signature);
    }
    for (const tx of transactions) {
      let signature; try { signature = indexMap.get(integer(tx.transactionIndex)); } catch { continue; }
      if (!signature) continue; const id = `${slot}:${signature}`; if (txs.has(id)) continue; txs.add(id);
      if (typeof tx.feePayer === 'string') payers.add(tx.feePayer);
      if (typeof tx.fee === 'string' && /^(0|[1-9][0-9]*)$/.test(tx.fee)) r.exactFeeInputs++;
      for (const key of tx.accountKeys || []) if (typeof key === 'string') accounts.add(key);
    }
    for (const [name, fieldNames] of [['transactions', kind === 'C' ? FIELDS.richTransaction : FIELDS.transaction],
      ['instructions', kind === 'C' ? FIELDS.richInstruction : FIELDS.instruction + (kind === 'B' ? ' accounts' : '')],
      ...(kind === 'C' ? [['balances', FIELDS.balance], ['tokenBalances', FIELDS.tokenBalance]] : [])]) {
      const stats = r.collections[name] ||= { rows: 0, blocksPresent: 0, fields: {} }; const list = row[name] || [];
      stats.rows += list.length; if (Object.hasOwn(row, name)) stats.blocksPresent++;
      for (const field of fieldNames.split(' ')) { const s = stats.fields[field] ||= { present: 0, null: 0, missing: 0 };
        for (const value of list) { if (!Object.hasOwn(value, field)) s.missing++; else { s.present++; if (value[field] === null) s.null++; } } }
    }
    for (const i of row.instructions || []) {
      let signature, address; try { signature = indexMap.get(integer(i.transactionIndex));
        if (!Array.isArray(i.instructionAddress) || !i.instructionAddress.length || i.instructionAddress.length > 64) fail('MALFORMED'); address = i.instructionAddress.map(integer).join('.');
      } catch { r.unknownSignatureLinks++; continue; }
      if (!signature) { r.unknownSignatureLinks++; continue; }
      const txId = `${slot}:${signature}`, id = `${txId}:${address}`; if (ids.has(id)) continue; ids.add(id);
      if (kind === 'A') {
        const program = PROGRAMS.indexOf(i.programId); if (program < 0) { r.other++; continue; }
        r.matched[program]++; r.matchedTotal++;
        if (i.error !== null && i.error !== undefined) { r.failed++; continue; }
        if (i.isCommitted !== true) { r.uncommitted++; continue; }
        let data; try { data = decode58(i.data); } catch { r.malformed++; continue; }
        const prefix = data.subarray(0, program === 2 ? 1 : 8).toString('hex');
        if (PREFIXES[program].includes(prefix)) { r.recognized[program]++; r.recognizedTotal++; } else r.other++;
      } else if (kind === 'B') {
        if (i.isCommitted !== true || i.error !== null && i.error !== undefined) { r.unknownTransfers++; continue; }
        const value = transfer(i); if (value.code) { r.unknownTransfers++; if (value.code === 'MALFORMED') r.malformed++; else r.other++; }
        else { r.transfers++; if (association.has(txId)) r.associatedTransfers++;
          if (r.lamportExamples.length < 3) r.lamportExamples.push(value.lamports); }
      }
    }
    if (kind === 'C') for (const tb of row.tokenBalances || []) { for (const k of ['preMint', 'postMint']) if (typeof tb[k] === 'string') mints.add(tb[k]);
      if (typeof tb.account === 'string') accounts.add(tb.account); }
  }
  r.processedSlots = rows.length; r.uniqueTransactions = txs.size; r.uniqueInstructions = ids.size;
  r.uniqueFeePayerProxies = payers.size; r.distinctMintProxies = mints.size; r.distinctAccountProxies = accounts.size;
  r.matchedShares = r.matched.map(n => share(n, r.matchedTotal)); r.recognizedShares = r.recognized.map(n => share(n, r.recognizedTotal));
  r.missingDepthDiagnosticShare = share(r.recognized.slice(3).reduce((a, b) => a + b, 0), r.recognizedTotal);
  return Object.assign(r, { transactionIdentities: [...txs], payerIdentities: [...payers], mintIdentities: [...mints], accountIdentities: [...accounts] });
}
const ceil = (n, d) => ((n + d - 1n) / d).toString();
function rate(items, numerator, denominator) {
  const values = items.filter(x => denominator(x) > 0).map(x => [BigInt(numerator(x)), BigInt(denominator(x))]);
  if (!values.length) return [null, null, null]; values.sort((a, b) => a[0] * b[1] < b[0] * a[1] ? -1 : a[0] * b[1] > b[0] * a[1] ? 1 : 0);
  return [values[0], values.reduce((a, b) => [a[0] + b[0], a[1] + b[1]], [0n, 0n]), values.at(-1)];
}
function sensitivity(windows) {
  const a = windows.map(w => w.A).filter(x => x.complete), b = windows.map(w => w.B).filter(x => x.complete), c = windows.map(w => w.C).filter(x => (x.populatedTransactions ?? x.uniqueTransactions) > 0);
  const tr = rate(a, x => x.uniqueTransactions, () => 600), rich = rate(c, x => x.populatedBytes ?? x.bytes, x => x.populatedTransactions ?? x.uniqueTransactions);
  const ar = rate(a, x => x.admittedBytes ?? x.bytes, () => 600), br = rate(b, x => x.admittedBytes ?? x.bytes, () => 600);
  const runtime = rate(a, x => x.elapsedMs, () => 600); const T = BigInt(CONFIG.targetSeconds);
  const scenarios = CONFIG.scenarios.map((s, i) => {
    const project = r => r ? ceil(T * r[0], r[1]) : null;
    const richBytes = tr[i] && rich[i] ? ceil(T * tr[i][0] * rich[i][0] * BigInt(s.fraction[0]) * BigInt(s.ancillary), tr[i][1] * rich[i][1] * BigInt(s.fraction[1])) : null;
    return { ...s, richBytes, lightABytes: project(ar[i]), lightBBytes: project(br[i]),
      richRequestsCapacityOnly: richBytes === null ? null : ceil(BigInt(richBytes), 16000000n),
      lightARequestsCapacityOnly: project(ar[i]) === null ? null : ceil(BigInt(project(ar[i])), 16000000n),
      lightBRequestsCapacityOnly: project(br[i]) === null ? null : ceil(BigInt(project(br[i])), 16000000n),
      measuredLightRuntimeMs: project(runtime[i]), cashMicrousd: richBytes === null ? null : ceil(BigInt(richBytes) * BigInt(s.tariffUsdPerGB) * 1000000n, 1000000000n),
      above10GB: richBytes === null ? null : BigInt(richBytes) > 10000000000n,
      above14Days: project(runtime[i]) === null ? null : BigInt(project(runtime[i])) > 1209600000n,
      aboveUSD100: richBytes === null ? null : BigInt(ceil(BigInt(richBytes) * BigInt(s.tariffUsdPerGB) * 1000000n, 1000000000n)) > 100000000n };
  });
  return { version: CONFIG.projectionVersion, fullD1UpperBound: null, targetSeconds: CONFIG.targetSeconds, scenarios, assumptions: CONFIG.formulas,
    rateFractions: { transaction: tr.map(x => x?.map(String) || null), richBytes: rich.map(x => x?.map(String) || null) }, omitted: OMITTED, actualCashMicrousd: '0' };
}
function budget(overrides = {}, start = 0) { return v1.budget({ ...LIMITS, response: LIMITS.response + 1, ...overrides }, start); }
function send(q, options = {}) { return validQuery(q) ? v1.sendBounded(q, options, LIMITS) : Promise.resolve({ code: 'QUERY_INVALID', received: 0 }); }
const fileStore = output => v1.exclusiveStore(output, OUTPUT, LIMITS.response, CONFIG.metadataCap, 4);
function merge(target, stats, identities) {
  for (const key of ['processedSlots', 'exactFeeInputs', 'unknownSignatureLinks', 'uniqueInstructions', 'matchedTotal', 'recognizedTotal', 'other', 'failed', 'uncommitted', 'malformed', 'transfers', 'associatedTransfers', 'unknownTransfers']) target[key] += stats[key];
  for (const key of ['matched', 'recognized']) target[key] = target[key].map((n, i) => n + stats[key][i]);
  target.lamportExamples = target.lamportExamples.concat(stats.lamportExamples).slice(0, 3);
  for (const [collection, value] of Object.entries(stats.collections)) { const t = target.collections[collection] ||= { rows: 0, blocksPresent: 0, fields: {} };
    t.rows += value.rows; t.blocksPresent += value.blocksPresent;
    for (const [field, values] of Object.entries(value.fields)) { const f = t.fields[field] ||= { present: 0, null: 0, missing: 0 }; for (const k of Object.keys(f)) f[k] += values[k]; } }
  for (const [key, count, input] of [['tx', 'uniqueTransactions', 'transactionIdentities'], ['payer', 'uniqueFeePayerProxies', 'payerIdentities'],
    ['mint', 'distinctMintProxies', 'mintIdentities'], ['account', 'distinctAccountProxies', 'accountIdentities']]) {
    for (const id of stats[input]) identities[key].add(id); target[count] = identities[key].size;
  }
}
async function collect(request) {
  const windows = [];
  for (const anchor of WINDOWS) {
    const w = { anchor, fixedSeconds: 600, censusSlots: 0, skippedPositions: null, censusComplete: false, gaps: [], A: emptyStats(), B: emptyStats(), C: emptyStats() }; windows.push(w);
    const start = Date.parse(anchor) / 1000, startQ = query('resolver', anchor, undefined, undefined, start), endQ = query('resolver', anchor, undefined, undefined, start + 600);
    const first = await request(startQ, b => admit(b, startQ)), end = await request(endQ, b => admit(b, endQ));
    if (first.code || end.code || end.slot <= first.slot || end.slot - first.slot > 3000) { w.gaps.push({ code: first.code || end.code || 'RANGE_INVALID' }); continue; }
    const fq = query('header', anchor, first.slot, first.slot, start), eq = query('header', anchor, end.slot, end.slot, start + 600);
    const fh = await request(fq, b => admit(b, fq)), eh = await request(eq, b => admit(b, eq));
    if (fh.code || eh.code) { w.gaps.push({ code: fh.code || eh.code }); continue; }
    const census = []; let bad = false;
    async function censusPage(from, to, depth = 0) {
      let cursor = from;
      while (cursor <= to) {
        const q = query('census', anchor, cursor, to), prev = census.at(-1);
        const r = await request(q, b => {
          const result = admit(b, q, undefined, prev);
          if (!result.code && ((!prev && canonical(result.headers[0]) !== canonical(fh.headers[0]))
            || result.headers.some(h => h.number === end.slot && canonical(h) !== canonical(eh.headers[0])))) return { code: 'RESPONSE_INVALID' };
          return result;
        });
        if (r.code === 'RESPONSE_LIMIT' && cursor < to && depth < 5) { const mid = Math.floor((cursor + to) / 2);
          w.gaps.push({ from: cursor, to, code: 'CAP_SPLIT' }); return await censusPage(cursor, mid, depth + 1) && await censusPage(mid + 1, to, depth + 1); }
        if (r.code) { w.gaps.push({ from: cursor, to, code: r.code }); return false; }
        census.push(...r.headers); cursor = r.headers.at(-1).number + 1;
      }
      return true;
    }
    for (let from = first.slot; from <= end.slot; from += 32) { if (!await censusPage(from, Math.min(from + 31, end.slot))) { bad = true; break; } }
    if (bad || census.at(-1)?.number !== end.slot || canonical(census.at(-1)) !== canonical(eh.headers[0])) { w.gaps.push({ code: 'CENSUS_INCOMPLETE' }); continue; }
    const inside = census.slice(0, -1); w.censusComplete = true; w.censusSlots = inside.length; w.skippedPositions = end.slot - first.slot - inside.length;
    const lightTransactions = new Set();
    for (const kind of ['A', 'B', 'C']) {
      const stats = w[kind], admitted = new Set(), identities = { tx: new Set(), payer: new Set(), mint: new Set(), account: new Set() }; let terminal = false;
      async function page(from, to, depth = 0) {
        let cursor = from;
        while (cursor <= to) {
          const expected = inside.filter(h => h.number >= cursor && h.number <= to); if (!expected.length) return true;
          const q = query(kind, anchor, cursor, to), r = await request(q, b => admit(b, q, expected));
          stats.requests += r.attempted ? 1 : 0; stats.bytes += r.received || 0; stats.elapsedMs += r.elapsedMs || 0;
          if (r.code === 'RESPONSE_LIMIT' && cursor < to && depth < 5) {
            stats.gaps.push({ from: cursor, to, code: 'CAP_SPLIT' }); const mid = Math.floor((cursor + to) / 2);
            const left = await page(cursor, mid, depth + 1); if (terminal) return false; const right = await page(mid + 1, to, depth + 1); return left && right;
          }
          if (r.code) { stats.gaps.push({ from: cursor, to, code: r.code }); if (r.code !== 'RESPONSE_LIMIT') terminal = true; return false; }
          for (const h of r.headers) { if (admitted.has(h.number)) fail('INTEGRITY_ERROR'); admitted.add(h.number); }
          const measured = inspect(r.rows, kind, lightTransactions); merge(stats, measured, identities);
          stats.admittedBytes += r.size || 0;
          if (kind === 'C' && measured.uniqueTransactions) { stats.populatedBytes += r.size || 0; stats.populatedTransactions += measured.uniqueTransactions; }
          if (kind === 'A') for (const id of measured.transactionIdentities) lightTransactions.add(id);
          cursor = r.headers.at(-1).number + 1;
        }
        return true;
      }
      if (kind === 'C') {
        const slots = [...new Set(CONFIG.richOffsets.map(offset => inside.find(h => h.timestamp >= start + offset)?.number).filter(n => n !== undefined))];
        stats.plannedSlots = slots; for (const slot of slots) { if (terminal) break; await page(slot, slot); } stats.complete = slots.length === admitted.size;
      } else {
        for (let from = first.slot; from < end.slot; from += 32) { if (terminal) break; await page(from, Math.min(from + 31, end.slot - 1)); }
        stats.complete = admitted.size === inside.length; stats.coveredSeconds = stats.complete ? 600 : 0;
      }
      stats.unexecutedSlots = (kind === 'C' ? stats.plannedSlots.length : inside.length) - admitted.size;
      stats.rejectedBytes = stats.bytes - stats.admittedBytes;
      stats.matchedShares = stats.matched.map(n => share(n, stats.matchedTotal)); stats.recognizedShares = stats.recognized.map(n => share(n, stats.recognizedTotal));
      stats.missingDepthDiagnosticShare = share(stats.recognized.slice(3).reduce((a, b) => a + b, 0), stats.recognizedTotal);
    }
  }
  return { ...FLAGS, version: CONFIG.summaryVersion, configHash: cfgHash(), windows, fullD1UpperBound: null, sensitivity: sensitivity(windows),
    richDisposition: 'sparse rich-size/input samples, never continuous rich coverage', tipDisposition: 'current-document attribution only', unknowns: OMITTED };
}
const codes = new Set(['RESPONSE_LIMIT', 'RESPONSE_INVALID', 'HTTP_ERROR', 'PARTIAL_RESPONSE', 'TIMEOUT', 'NETWORK_ERROR', 'ATTEMPT_LIMIT',
  'RECEIVED_LIMIT', 'DISK_LIMIT', 'TIME_LIMIT', 'FREE_SPACE_LIMIT', 'STORAGE_ERROR', 'UNEXECUTED']);
function complete(summary) { return summary.windows.every(w => w.censusComplete && ['A', 'B', 'C'].every(k => w[k].complete)); }
async function runProbe(options = {}, injected = {}) {
  if (options.enabled !== true) return invalid('DISABLED');
  if (options.output !== OUTPUT || Object.keys(options).some(k => !['enabled', 'output'].includes(k))) return invalid('UNSAFE_PATH');
  if (!/^v24\./.test(injected.runtime ?? process.version)) return invalid('RUNTIME_INVALID');
  const now = injected.now ?? Date.now, sleep = injected.sleep ?? (ms => new Promise(r => setTimeout(r, ms))), transport = injected.transport ?? send;
  const start = now(), b = budget({}, start), records = [], source = injected.source ?? v1.sourceIdentity(); let store, stopped = false, lastStart = null, retainedKnown = true;
  const attempt = Buffer.from(JSON.stringify({ ...FLAGS, status: 'INCOMPLETE', configHash: cfgHash(), startedAt: new Date(start).toISOString(),
    source, runtime: process.version, interruptedDisposition: 'No final manifest means incomplete; reuse/resume forbidden' }) + '\n');
  try { store = injected.store ?? fileStore(options.output); if (store.free() - CONFIG.metadataReserve - LIMITS.response - 1 < LIMITS.free) return invalid('FREE_SPACE_LIMIT'); store.create(); }
  catch (e) { return invalid(['OUTPUT_EXISTS', 'UNSAFE_PATH'].includes(e.message) ? e.message : 'STORAGE_ERROR'); }
  function write(name, bytes) {
    try { store.write(name, bytes); b.charge(0, bytes.length); return null; }
    catch { let partial = null; try { const raw = store.read(name); b.charge(0, raw.length); partial = { name, size: raw.length, hash: digest(raw) }; }
      catch (e) { if (e.code !== 'ENOENT' && e.message !== 'FILE_MISSING') retainedKnown = false; } return partial || { name, size: 0, hash: null }; }
  }
  if (write('attempt.json', attempt)) return { ...invalid('STORAGE_ERROR'), accounting: { ...b.state, retained: b.state.disk, retainedKnown } };
  const request = async (q, validate) => {
    if (stopped) return { code: 'UNEXECUTED', attempted: false };
    const requestBegan = now();
    if (lastStart !== null) await sleep(Math.max(0, LIMITS.interval - (now() - lastStart)));
    let code; try { code = b.reserve(now(), store.free() - CONFIG.metadataReserve); } catch { code = 'STORAGE_ERROR'; }
    if (code) { records.push({ request: q, code, attempted: false, received: 0, elapsedMs: 0, status: null }); stopped = true; return { code, attempted: false }; }
    lastStart = now(); const r = { request: q, code: null, attempted: true, received: 0, elapsedMs: 0, status: null }; records.push(r);
    let response; try { response = await transport(q, { responseLimit: LIMITS.response, deadlineMs: Math.min(LIMITS.deadline, LIMITS.elapsed - (now() - start)),
      onChunk(n) { if (!Number.isSafeInteger(n) || n < 0 || r.received + n > LIMITS.response + 1) fail('NETWORK_ERROR'); r.received += n; b.charge(n, 0); } }); }
    catch { response = { code: 'NETWORK_ERROR' }; }
    r.elapsedMs = Math.max(0, now() - requestBegan); r.status = Number.isInteger(response.status) ? response.status : null; b.tick(now());
    if (b.state.elapsed >= LIMITS.elapsed) r.code = 'TIME_LIMIT';
    else if (response.code) r.code = codes.has(response.code) ? response.code : 'NETWORK_ERROR';
    let observed; if (!r.code) { observed = validate(response.bytes); r.code = observed.code; }
    if (r.code) { if (['TIME_LIMIT', 'STORAGE_ERROR'].includes(r.code)) stopped = true; return { ...r }; }
    const name = `${String(records.length).padStart(4, '0')}.raw`, failed = write(name, response.bytes);
    if (failed) { r.code = 'STORAGE_ERROR'; r.failedRaw = failed; stopped = true; return { ...r }; }
    r.raw = name; r.hash = digest(response.bytes); r.size = response.bytes.length;
    return { ...observed, attempted: true, received: r.received, elapsedMs: r.elapsedMs, size: r.size };
  };
  try {
    const summary = await collect(request); b.tick(now());
    const status = complete(summary) && !stopped && /^[a-f0-9]{40,64}$/.test(source.commit || '') && typeof source.dirty === 'boolean' ? 'COMPLETE' : 'INCOMPLETE';
    const summaryBytes = Buffer.from(JSON.stringify(summary) + '\n'), summaryHash = fingerprint(CONFIG.summaryVersion, summary);
    const scriptHashes = Object.fromEntries(['exploratory-probe.cjs', 'exploratory-probe-v2.cjs', 'exploratory-probe-v2-cli.cjs'].map(name => [name, digest(fs.readFileSync(path.join(__dirname, name)))]));
    const accounting = { ...b.state, retained: b.state.disk, retainedKnown, concurrency: 1, retries: 0 };
    const manifest = { ...FLAGS, status, config: CONFIG, configHash: cfgHash(), summaryHash, summaryFileHash: digest(summaryBytes),
      attemptFileHash: digest(attempt), records, accounting, source, scriptHashes, runtime: process.version,
      startedAt: new Date(start).toISOString(), endedAt: new Date(now()).toISOString(), protocolVersion: '1.0.0', freezeEntry: 1,
      change: 'establish-r1-d1-data-gate', command: `node tools/research/r1/exploratory-probe-v2-cli.cjs --enable-public --output ${OUTPUT}` };
    let manifestBytes; for (let i = 0; i < 8; i++) { manifestBytes = Buffer.from(JSON.stringify(manifest) + '\n');
      accounting.retained = b.state.disk + summaryBytes.length + manifestBytes.length; accounting.disk = accounting.retained;
      if (accounting.disk * 5 >= LIMITS.disk * 4 && !accounting.checkpoints.includes('disk')) accounting.checkpoints.push('disk'); }
    manifestBytes = Buffer.from(JSON.stringify(manifest) + '\n');
    if (summaryBytes.length > CONFIG.metadataCap || manifestBytes.length > CONFIG.metadataCap || accounting.retained > LIMITS.disk
      || store.free() - summaryBytes.length - manifestBytes.length < LIMITS.free) fail('STORAGE_ERROR');
    if (write('summary.json', summaryBytes) || write('manifest.json', manifestBytes)) fail('STORAGE_ERROR');
    return { ...FLAGS, status, code: null, summary, summaryHash, manifestHash: digest(manifestBytes), accounting };
  } catch { return { ...invalid('STORAGE_ERROR'), accounting: { ...b.state, retained: b.state.disk, retainedKnown } }; }
}
async function replay(store) {
  try {
    const manifestBytes = store.read('manifest.json'); if (manifestBytes.length > CONFIG.metadataCap) fail('INTEGRITY_ERROR'); const m = parse(manifestBytes, false);
    if (canonical(m.config) !== canonical(CONFIG) || m.configHash !== cfgHash() || m.classification !== 'EXPLORATORY' || m.d1Evidence !== false || m.d1Passed !== false
      || !['COMPLETE', 'INCOMPLETE'].includes(m.status) || !Array.isArray(m.records) || m.records.length > 1001 || m.accounting.retainedKnown !== true
      || digest(store.read('attempt.json')) !== m.attemptFileHash) fail('INTEGRITY_ERROR');
    let index = 0, received = 0, retained = store.read('attempt.json').length; const seen = new Set();
    const summary = await collect(async (q, validate) => {
      const r = m.records[index]; if (!r) return { code: 'UNEXECUTED', attempted: false };
      if (canonical(r.request) !== canonical(q) || !validQuery(q) || !Number.isSafeInteger(r.received) || r.received < 0 || r.received > LIMITS.response + 1
        || r.failedRaw || r.code === 'STORAGE_ERROR' || r.code !== null && !codes.has(r.code)) fail('INTEGRITY_ERROR'); index++; received += r.received;
      if (!r.raw) { if (!r.code) fail('INTEGRITY_ERROR'); return { ...r }; }
      if (r.code || !/^\d{4}\.raw$/.test(r.raw) || seen.has(r.raw)) fail('INTEGRITY_ERROR'); seen.add(r.raw);
      const bytes = store.read(r.raw); retained += bytes.length;
      if (bytes.length > LIMITS.response || bytes.length !== r.size || digest(bytes) !== r.hash) fail('INTEGRITY_ERROR');
      const observed = validate(bytes); if (observed.code) fail('INTEGRITY_ERROR'); return { ...observed, attempted: r.attempted, received: r.received, elapsedMs: r.elapsedMs, size: r.size };
    });
    const summaryBytes = store.read('summary.json'), summaryHash = fingerprint(CONFIG.summaryVersion, summary); retained += summaryBytes.length + manifestBytes.length;
    if (index !== m.records.length || received > LIMITS.received || retained > LIMITS.disk || received !== m.accounting.received || retained !== m.accounting.retained
      || summaryBytes.length > CONFIG.metadataCap || digest(summaryBytes) !== m.summaryFileHash || summaryHash !== m.summaryHash
      || summaryBytes.toString('utf8') !== JSON.stringify(summary) + '\n' || m.status === 'COMPLETE' && !complete(summary)) fail('INTEGRITY_ERROR');
    return { ...FLAGS, status: m.status, code: null, summaryHash, manifestHash: digest(manifestBytes), summary };
  } catch { return invalid('INTEGRITY_ERROR'); }
}
module.exports = { OUTPUT, WINDOWS, PROGRAMS, JITO, CONFIG, query, admit, transfer, inspect, sensitivity, budget, send, fileStore, runProbe, replay };
