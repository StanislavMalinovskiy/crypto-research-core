'use strict';
const fs = require('node:fs'), path = require('node:path'), { execFileSync } = require('node:child_process');
const { parse, canonical, digest, fingerprint, CONFIG: SQD, query: sqdQuery } = require('./exploratory-probe.cjs');
const { mapBlock } = require('./pumpswap-mapper.cjs');
const BASE = 'C:\\crypto-research-evidence\\r1-d1';
const CONFIG = { version: 'offline-provisional-discovery-v1', base: BASE, from: 1775001600, cutoff: 1787961600,
  expiresAt: '2026-10-17T11:42:35.392Z', c3: { version: 'r1-c3-default-60s-v1', seconds: 60, label: 'MODELED' },
  scripts: { 'pumpswap-mapper.cjs': 'sha256:161783107d93225c3a658c4340c13e8db6a5476b96e13e489c920b8730269a1b',
    'exploratory-probe.cjs': 'sha256:8f8790026241de1bf3bcded6fef3fe99a6b954af34931184d04897ca393b0a47' },
  limits: { elapsedMs: 600000, manifests: 4000000, raw: 2000000, input: 25000000, records: 10000, rows: 250000, addresses: 10000, output: 32000000,
    invocations: 2, cumulativeInput: 50000000, capturedDisk: 80000000, cashMicrousd: '0', requests: 0, retries: 0 },
  sources: [
    { root: 'exploratory-sqd-v1', kind: 'SQD', count: 28, bytes: 5962659, manifestHash: 'sha256:fc1fe02982ec49885e7553b2e35331c9f5444e2fca5924327bd0e4d4eed74a4a' },
    { root: 'exploratory-sqd-v2', kind: 'SQD', count: 66, bytes: 317030, manifestHash: 'sha256:5e8dc296b2ce20c9a20e7b9728c202278153516d702bdbbe01762bc45cffb224' },
    { root: 'exploratory-helius-h1-v1', kind: 'HELIUS', count: 1, bytes: 224743, manifestHash: 'sha256:5cc326892ab985d09d2b7f287beaa28dff41ac7ae8a4ab00f3eab81033671910' },
    { root: 'exploratory-helius-v1', kind: 'HELIUS', count: 324, bytes: 3942470434, manifestHash: 'sha256:6e675fce49ddd0c0e3ee6b485e768710be3e634bf3c7db8bc68a4f242d4aa513' } ],
  historicalAudit: 'sha256:12b95d5476b50660f51f3043fe95943654f6d0bc2552879d99dbd14c2b8063da' };
const FLAGS = { classification: 'EXPLORATORY', d1Evidence: false, d1Passed: false, runAuthorized: false, section5CandidateUniverse: false,
  actualTraderConfirmed: false, reconstructedTradeCount: null, fullD1UpperBound: null, fullD1Fits: null };
const fail = code => { throw Error(code); }, demand = (ok, code) => { if (!ok) fail(code); };
const cmp = (a, b) => a < b ? -1 : a > b ? 1 : 0, sorted = list => list.sort((a, b) => cmp(canonical(a), canonical(b)));
const sha = value => typeof value === 'string' && /^sha256:[a-f0-9]{64}$/.test(value);
const int = value => { demand(typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value) && BigInt(value) <= BigInt(Number.MAX_SAFE_INTEGER), 'TIME_ADMISSION_INVALID'); return Number(value); };
function readFixed(root, name, cap, io = fs) {
  demand(root === '' ? Object.hasOwn(CONFIG.scripts, name) : CONFIG.sources.some(s => s.root === root) && /^(manifest\.json|[0-9]{3,4}\.raw)$/.test(name), 'UNSAFE_PATH');
  const file = root === '' ? path.join(__dirname, name) : path.join(BASE, root, name); let at = file;
  while (true) { const st = io.lstatSync(at); demand(!st.isSymbolicLink() && io.realpathSync.native(at).toLowerCase() === at.toLowerCase(), 'UNSAFE_PATH');
    const next = path.dirname(at); if (next === at) break; at = next; }
  let fd; try { fd = io.openSync(file, 'r'); const st = io.fstatSync(fd); demand(st.isFile() && Number.isSafeInteger(st.size) && st.size <= cap, 'READ_LIMIT');
    const b = Buffer.alloc(st.size + 1); let n = 0; while (n < b.length) { const received = io.readSync(fd, b, n, b.length - n, null); if (!received) break; n += received; }
    demand(n === st.size, 'READ_LIMIT'); return b.subarray(0, n);
  } finally { if (fd !== undefined) io.closeSync(fd); }
}
function sourceIdentity(remaining) {
  const cwd = path.resolve(__dirname, '../../..'), run = args => {
    const timeout = remaining(); demand(Number.isSafeInteger(timeout) && timeout > 0, 'TIME_LIMIT');
    try { return execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], timeout, killSignal: 'SIGKILL' }); }
    catch (error) { if (error?.code === 'ETIMEDOUT') fail('TIME_LIMIT'); throw error; }
  };
  return { commit: run(['rev-parse', 'HEAD']).trim(), dirty: run(['status', '--porcelain']).length > 0 };
}
function auditErrors(lossless, typed, counts) {
  const audit = (l, t, index = false) => {
    if (typeof t === 'number') { counts.numericErrTokens++; demand(typeof l === 'string' && /^(0|[1-9][0-9]*)$/.test(l) && BigInt(l) <= (index ? 255n : 4294967295n), 'ERR_NUMERIC_LEXEME_INVALID'); }
    else if (Array.isArray(t)) t.forEach((v, n) => audit(l[n], v, index && n === 0));
    else if (t && typeof t === 'object') for (const key of Object.keys(t)) audit(l[key], t[key], key === 'InstructionError');
  };
  const walk = (l, t) => {
    if (!t || typeof t !== 'object') return;
    if (Array.isArray(t)) t.forEach((v, n) => walk(l[n], v));
    else for (const key of Object.keys(t)) { if (key === 'err' && t[key] !== null && t[key] !== undefined) { counts.nonemptyErr++; audit(l[key], t[key]); }
      else walk(l[key], t[key]); }
  }; walk(lossless, typed);
}
function checkTime(b, config, q) {
  demand(b && b.header && typeof b.header.timestamp === 'string', 'TIME_ADMISSION_INVALID'); const timestamp = int(b.header.timestamp), slot = int(b.header.number);
  demand(timestamp >= config.from && timestamp < config.cutoff && timestamp + config.c3.seconds <= config.cutoff, 'TIME_ADMISSION_INVALID');
  demand(slot >= q.body.fromBlock && slot <= q.body.toBlock, 'QUERY_LINEAGE_INVALID'); return timestamp;
}
function checkQuery(r, source, m) {
  const q = r.request; demand(q && typeof q.kind === 'string' && typeof q.anchor === 'string' && Number.isFinite(Date.parse(q.anchor)), 'QUERY_LINEAGE_INVALID');
  demand(Date.parse(q.anchor) / 1000 >= CONFIG.from && Date.parse(q.anchor) / 1000 < CONFIG.cutoff, 'TIME_ADMISSION_INVALID');
  if (source.root === 'exploratory-sqd-v1') demand(canonical(q) === canonical(sqdQuery(q.kind, q.anchor, q.slot, q.program)), 'QUERY_LINEAGE_INVALID');
  else {
    demand(m.config.windows.includes(q.anchor) && ['resolver', 'header', 'census', 'A', 'B', 'C'].includes(q.kind), 'QUERY_LINEAGE_INVALID');
    if (q.kind === 'resolver') demand(q.method === 'GET' && Number.isSafeInteger(q.time) && q.url === SQD.base + 'timestamps/' + q.time + '/block' &&
      [Date.parse(q.anchor) / 1000, Date.parse(q.anchor) / 1000 + 600].includes(q.time), 'QUERY_LINEAGE_INVALID');
    else demand(q.method === 'POST' && q.url === SQD.base + 'finalized-stream' && q.body?.type === 'solana' && q.body.includeAllBlocks === true &&
      Number.isSafeInteger(q.body.fromBlock) && Number.isSafeInteger(q.body.toBlock) && q.body.fromBlock >= 0 && q.body.toBlock >= q.body.fromBlock &&
      q.body.toBlock - q.body.fromBlock < 32 && canonical(q.body.fields.block) === canonical(sqdQuery('header', SQD.anchors[0], 1).body.fields.block), 'QUERY_LINEAGE_INVALID');
  }
}
function discover(config = CONFIG, deps = {}) {
  const now = deps.now ?? Date.now, utcNow = deps.utcNow ?? Date.now, started = now(), read = deps.read ?? readFixed;
  const counters = { inputBytes: 0, rawFilesRead: 0, rawBytesRead: 0, childOccurrences: 0, mapperRows: 0, missingErr: 0, nonemptyErr: 0, numericErrTokens: 0,
    invocationOccurrences: 0, transferOccurrences: 0, tokenStateOccurrences: 0, nativeBalanceRows: 0, conflictingEvents: 0 };
  const children = [], sources = [], coverage = [], gaps = [], observations = new Map(), addressEvidence = new Map(), eventContents = new Map(), eventIdentities = new Map(), conflicts = new Set();
  let retainedReportBytes = 0;
  const chargeOutput = value => { retainedReportBytes += Buffer.byteLength(JSON.stringify(value)); demand(retainedReportBytes <= config.limits.output, 'OUTPUT_LIMIT'); };
  const tick = () => { const elapsed = now() - started; demand(Number.isSafeInteger(elapsed) && elapsed >= 0 && elapsed <= config.limits.elapsedMs, 'TIME_LIMIT'); };
  const expiry = () => demand(Number.isFinite(utcNow()) && utcNow() < Date.parse(config.expiresAt), 'RETENTION_EXPIRED');
  const readBytes = (root, name, cap) => { tick(); demand(counters.inputBytes < config.limits.input, 'INPUT_LIMIT'); const b = read(root, name, cap);
    demand(Buffer.isBuffer(b) && b.length <= cap, 'READ_LIMIT'); counters.inputBytes += b.length; demand(counters.inputBytes <= config.limits.input, 'INPUT_LIMIT'); tick(); return b; };
  const incomplete = (code, status = 'DISCOVERY_INVALID', context = null) => ({ ...FLAGS, status, exitCode: status === 'DISCOVERY_INVALID' ? 1 : 2,
    code, scanComplete: false, addresses: [], observations: [], counters, failure: context, runBudget: 'UNMEASURED' });
  try {
    expiry(); demand(config.base === BASE && config.version === CONFIG.version && config.sources.length === 4, 'CONFIG_INVALID');
    const runtime = deps.runtime ?? process.version, source = deps.source ?? sourceIdentity(() => {
      const elapsed = now() - started; demand(Number.isSafeInteger(elapsed) && elapsed >= 0, 'TIME_LIMIT'); return config.limits.elapsedMs - elapsed;
    });
    demand(/^v24\./.test(runtime) && /^[a-f0-9]{40}$/.test(source.commit) && typeof source.dirty === 'boolean', 'SOURCE_INTEGRITY');
    demand(Object.keys(config.scripts).length === 2 && Object.keys(CONFIG.scripts).every(n => sha(config.scripts[n])), 'SOURCE_INTEGRITY');
    for (const [name, hash] of Object.entries(config.scripts)) demand(digest(readBytes('', name, config.limits.raw)) === hash, 'SOURCE_INTEGRITY');
    const manifests = [];
    for (const [n, sourceContract] of config.sources.entries()) {
      demand(sourceContract.root === CONFIG.sources[n].root && sourceContract.kind === CONFIG.sources[n].kind, 'UNSAFE_PATH');
      const bytes = readBytes(sourceContract.root, 'manifest.json', config.limits.manifests); demand(digest(bytes) === sourceContract.manifestHash, 'MANIFEST_INTEGRITY');
      const m = parse(bytes, false); demand(m.classification === 'EXPLORATORY' && m.d1Evidence === false && m.d1Passed === false, 'MANIFEST_INTEGRITY');
      const provenance = sourceContract.kind === 'SQD' ? { source: m.source, runtime: m.runtime } : m.lineage;
      demand(provenance && /^[a-f0-9]{40}$/.test(provenance.source?.commit) && typeof provenance.source.dirty === 'boolean' && /^v24\./.test(provenance.runtime), 'MANIFEST_INTEGRITY');
      if (sourceContract.kind === 'SQD') { demand(m.config?.queryVersion === 'exploratory-sqd-v' + (n + 1) && m.config.source === SQD.source &&
        m.config.base === SQD.base && fingerprint(m.config.canonicalizationVersion, m.config) === m.configHash && Array.isArray(m.records), 'QUERY_LINEAGE_INVALID');
        if (n === 0) demand(canonical(m.config) === canonical(SQD), 'QUERY_LINEAGE_INVALID'); }
      else { demand(m.configuration?.queryVersion === (n === 2 ? 'helius-history-query-v1' : 'helius-full-history-query-v1') && Array.isArray(m.files) && Array.isArray(m.records), 'MANIFEST_INTEGRITY');
        for (const [ordinal, r] of m.records.entries()) { const q = r.query ?? m.query, settings = q?.body?.params?.[1], range = settings?.filters?.blockTime;
          demand(q?.method === 'POST' && q.url === 'https://mainnet.helius-rpc.com/' && q.body.method === 'getTransactionsForAddress' &&
            Number.isSafeInteger(range?.gte) && Number.isSafeInteger(range?.lt) && range.gte >= config.from && range.lt <= config.cutoff && range.gte < range.lt &&
            settings.commitment === 'finalized' && settings.filters.status === 'any' && settings.filters.tokenAccounts === (n === 2 ? 'none' : 'all'), 'QUERY_LINEAGE_INVALID');
          coverage.push({ root: sourceContract.root, ordinal, kind: 'HELIUS_METADATA_ONLY', range, attempted: r.attempted === true, status: r.status ?? null,
            code: r.code ?? null, disposition: 'FORMAT_NOT_ALLOCATED', raw: r.raw?.name ?? null, rawHash: r.raw?.sha256 ?? null, rawSize: r.raw?.size ?? null }); }
      }
      const listed = new Map();
      for (const [ordinal, r] of (sourceContract.kind === 'SQD' ? m.records : m.files).entries()) {
        if (sourceContract.kind === 'SQD') { checkQuery(r, sourceContract, m); coverage.push({ root: sourceContract.root, ordinal, kind: r.request.kind,
          anchor: r.request.anchor, attempted: r.attempted === true, status: r.status ?? null, code: r.code ?? null, raw: r.raw ?? null, rawHash: r.hash ?? null, rawSize: r.size ?? null,
          disposition: r.request.kind === 'resolver' ? 'RESOLVER_ONLY' : ['header', 'census'].includes(r.request.kind) ? 'HEADER_ONLY_NO_PAYLOAD' :
            r.request.kind === 'payload' ? 'PAYLOAD' : 'FORMAT_NOT_ALLOCATED' }); }
        const name = sourceContract.kind === 'SQD' ? r.raw : r.name; if (!name || (sourceContract.kind === 'HELIUS' && !name.endsWith('.raw'))) continue;
        demand(/^[0-9]{3,4}\.raw$/.test(name), 'UNSAFE_PATH'); const entry = { name, size: r.size, hash: sourceContract.kind === 'SQD' ? r.hash : r.sha256 };
        demand(Number.isSafeInteger(entry.size) && entry.size > 0 && entry.size <= (sourceContract.kind === 'SQD' ? config.limits.raw : 64000000) && sha(entry.hash), 'MANIFEST_INTEGRITY');
        if (listed.has(name)) demand(canonical(listed.get(name)) === canonical(entry), 'MANIFEST_INTEGRITY'); else listed.set(name, entry);
      }
      demand(listed.size === sourceContract.count && [...listed.values()].reduce((sum, e) => sum + e.size, 0) === sourceContract.bytes, 'MANIFEST_INTEGRITY');
      sources.push({ ...sourceContract, manifestSize: bytes.length, provenance, queryVersion: (m.config ?? m.configuration).queryVersion, datasetRevision: null,
        retention: 'UNVERIFIED', readFiles: 0, readBytes: 0, disposition: sourceContract.kind === 'HELIUS' ? 'FORMAT_NOT_ALLOCATED' : 'FILTERED_SPARSE_SAMPLE',
        historyContext: sourceContract.root === 'exploratory-helius-v1' ? { completeAddresses: 13, censoredAddresses: 23, proof: 'HISTORICAL_H3_RECEIPT_ONLY' } : null });
      manifests.push({ m, listed });
    }
    counters.sourceRecords = coverage.length; counters.missingPayloads = coverage.filter(r => r.disposition === 'PAYLOAD' && !r.raw).length;
    counters.skippedRecords = coverage.filter(r => r.disposition !== 'PAYLOAD' || r.status !== 200 || r.code !== null).length;
    for (const record of coverage) if (!record.attempted || record.code !== null || record.status !== 200) gaps.push({ code: !record.attempted ? 'SOURCE_REQUEST_UNEXECUTED' : 'SOURCE_REQUEST_FAILED',
      root: record.root, ordinal: record.ordinal, kind: record.kind, sourceCode: record.code, status: record.status });
    chargeOutput({ sources, coverage });
    for (const [n, { m, listed }] of manifests.entries()) {
      const sourceContract = config.sources[n], resultSource = sources[n]; if (sourceContract.kind !== 'SQD') continue;
      for (const entry of [...listed.values()].sort((a, b) => cmp(a.name, b.name))) {
        expiry(); const raw = readBytes(sourceContract.root, entry.name, config.limits.raw);
        demand(raw.length === entry.size && digest(raw) === entry.hash, 'RAW_INTEGRITY'); resultSource.readFiles++; resultSource.readBytes += raw.length;
        counters.rawFilesRead++; counters.rawBytesRead += raw.length;
        const parents = m.records.map((r, ordinal) => ({ r, ordinal })).filter(({ r }) => r.raw === entry.name);
        if (parents.every(({ r }) => r.request.kind === 'resolver')) { parse(raw, false); continue; }
        let offset = 0;
        while (offset < raw.length) {
          const lf = raw.indexOf(10, offset), end = lf < 0 ? raw.length : lf + 1, child = raw.subarray(offset, end), start = offset; offset = end;
          demand(child.length > 0 && child.length <= config.limits.raw, 'CHILD_INVALID'); expiry(); tick();
          demand(++counters.childOccurrences <= config.limits.records, 'RECORD_LIMIT');
          const lossless = parse(child, true), typed = parse(child, false); demand(lossless && !Array.isArray(lossless), 'CHILD_INVALID');
          for (const { r } of parents) checkTime(lossless, config, r.request);
          const lineage = { root: sourceContract.root, file: entry.name, parentHash: entry.hash, parentSize: entry.size, offset: start, length: child.length,
            childHash: digest(child), recordOrdinals: parents.map(p => p.ordinal), sourceManifestHash: sourceContract.manifestHash };
          const eligible = parents.filter(({ r }) => r.request.kind === 'payload' && r.status === 200 && r.code === null);
          if (!eligible.length) { gaps.push({ code: 'NO_ALLOCATED_PAYLOAD', childHash: lineage.childHash, root: sourceContract.root, file: entry.name }); continue; }
          children.push(lineage);
          chargeOutput(lineage);
          auditErrors(lossless, typed, counters);
          const mapped = mapBlock(child, lineage.childHash); tick();
          if (mapped.status === 'MAPPING_INVALID') return incomplete('MAPPER_INVALID', 'DISCOVERY_INCOMPLETE', { ...lineage, mapperCode: mapped.code });
          const rowCount = ['invocations', 'ownedDeltas', 'tokenStates', 'transfers', 'diagnostics'].reduce((v, k) => v + mapped[k].length, 0);
          counters.mapperRows += rowCount; demand(counters.mapperRows <= config.limits.rows, 'ROW_LIMIT');
          counters.invocationOccurrences += mapped.invocations.length; counters.transferOccurrences += mapped.transfers.length;
          counters.tokenStateOccurrences += mapped.tokenStates.length; counters.nativeBalanceRows += (lossless.balances ?? []).length;
          for (const tx of lossless.transactions ?? []) if (!Object.hasOwn(tx, 'err')) { counters.missingErr++; gaps.push({ code: 'MISSING_TRANSACTION_ERR', childHash: lineage.childHash, signature: tx.signatures?.[0] ?? null }); }
          const outsideDecoder = (lossless.instructions ?? []).filter(i => ![mapped.configuration.programs.pump, mapped.configuration.programs.token,
            mapped.configuration.programs.token2022, mapped.configuration.programs.system].includes(i.programId)).map(i => ({ programId: i.programId, instructionAddress: i.instructionAddress.map(int),
              signature: (lossless.transactions ?? []).find(tx => tx.transactionIndex === i.transactionIndex)?.signatures?.[0] ?? null }));
          if (outsideDecoder.length) gaps.push({ code: 'VENUE_NOT_ALLOCATED', childHash: lineage.childHash, count: outsideDecoder.length });
          for (const d of mapped.diagnostics) gaps.push({ ...d, childHash: lineage.childHash });
          const observation = { childHash: lineage.childHash, factsHash: mapped.factsHash, eventTime: int(lossless.header.timestamp), availableAt: int(lossless.header.timestamp) + config.c3.seconds,
            mapped, signatures: (lossless.transactions ?? []).flatMap(tx => tx.signatures ?? []), nativeBalanceRows: (lossless.balances ?? []).length,
            outsideDecoder, parents: eligible.map(({ ordinal }) => ({ ...lineage, recordOrdinal: ordinal })) };
          counters.mapperRows += outsideDecoder.length; demand(counters.mapperRows <= config.limits.rows, 'ROW_LIMIT');
          if (observations.has(lineage.childHash)) { chargeOutput(observation.parents); observations.get(lineage.childHash).parents.push(...observation.parents); }
          else { chargeOutput(observation); observations.set(lineage.childHash, observation); }
          for (const invocation of mapped.invocations) {
            if (invocation.identity === null) continue;
            const key = canonical(invocation.identity), related = mapped.ownedDeltas.filter(d => d.invocationIdentities.some(id => canonical(id) === key)); eventIdentities.set(key, invocation.identity);
            const content = digest(Buffer.from(canonical({ invocation, related: related.map(({ signature, owner, mint, decimals, ownedDelta, reason }) => ({ signature, owner, mint, decimals, ownedDelta, reason })) })));
            if (!eventContents.has(key)) eventContents.set(key, new Set());
            eventContents.get(key).add(content); if (eventContents.get(key).size > 1) conflicts.add(key);
            if (invocation.declaredTrader === null) continue;
            const exact = invocation.reason === null && related.length === 2 && related.every(d => d.ownedDelta !== null && d.reason === null) && invocation.vaultChecks.every(v => v.reason === null);
            const evidence = { event: invocation.identity, childHash: lineage.childHash, factsHash: mapped.factsHash, exact, reason: invocation.reason,
              layoutApplicability: invocation.layoutApplicability };
            if (!addressEvidence.has(invocation.declaredTrader)) addressEvidence.set(invocation.declaredTrader, new Map());
            addressEvidence.get(invocation.declaredTrader).set(canonical([key, lineage.childHash]), evidence);
          }
        }
      }
    }
    for (const event of [...conflicts].sort()) { counters.conflictingEvents++; gaps.push({ code: 'CONFLICTING_OBSERVATIONS', event: eventIdentities.get(event) }); }
    const addresses = [...addressEvidence].sort(([a], [b]) => cmp(a, b)).map(([address, evidenceMap]) => {
      const evidence = sorted([...evidenceMap.values()]); const conflicting = evidence.some(e => conflicts.has(canonical(e.event)));
      return { address, status: !conflicting && evidence.every(e => e.exact) ? 'DECLARED_OWNER_WITH_EXACT_DELTAS' : 'DECLARED_ADDRESS_UNRESOLVED',
        evidence, conflicting, actualTraderConfirmed: false, reconstructedTradeCount: null, layoutApplicability: 'DECLARED_UNVERIFIED', eligibility: 'UNVERIFIED', knownAtCompleteness: 'UNVERIFIED' };
    }); demand(addresses.length <= config.limits.addresses, 'ADDRESS_LIMIT');
    const orderedObservations = [...observations.values()].sort((a, b) => cmp(a.childHash, b.childHash)); for (const o of orderedObservations) sorted(o.parents);
    counters.reasons = {}; for (const gap of gaps) counters.reasons[gap.code] = (counters.reasons[gap.code] ?? 0) + 1;
    const allEvents = new Set(orderedObservations.flatMap(o => [...o.mapped.invocations, ...o.mapped.transfers].filter(r => r.identity !== null).map(r => canonical(r.identity))));
    for (const observation of orderedObservations) for (const row of observation.outsideDecoder) if (row.signature !== null) allEvents.add(canonical({ chain: observation.mapped.configuration.chain,
      signature: row.signature, instructionAddress: row.instructionAddress }));
    const semantic = { ...FLAGS, version: config.version, status: 'PROVISIONAL_DISCOVERY_PARTIAL', code: null, scanComplete: true,
      configuration: config, configHash: fingerprint(config.version, config), source, runtime, historicalAudit: { hash: config.historicalAudit, scope: 'PRIOR_419_FILE_AUDIT_NOT_FRESH_HELIUS_VALIDATION' },
      availability: config.c3, sources, coverage, children: sorted(children), observations: orderedObservations, addresses, counters,
      gaps: sorted(gaps), distinct: { signatures: new Set(orderedObservations.flatMap(o => o.signatures)).size,
        events: allEvents.size, observations: observations.size, addresses: addresses.length }, unknowns: ['filtered sparse universe', 'other venues', 'quote eligibility',
        'fee split', 'tips', 'SOL/USD', 'observed visibility', 'full native transfer history', 'wallet eligibility', 'first funder', 'layout deployment'] };
    const report = { ...semantic, semanticHash: fingerprint(config.version, semantic), exitCode: 2, runBudget: 'UNMEASURED', operational: { elapsedMs: now() - started } };
    demand(Buffer.byteLength(JSON.stringify(report) + '\n') <= config.limits.output, 'OUTPUT_LIMIT'); tick(); return report;
  } catch (error) { const allowed = new Set(['CONFIG_INVALID', 'SOURCE_INTEGRITY', 'UNSAFE_PATH', 'READ_LIMIT', 'INPUT_LIMIT', 'TIME_LIMIT', 'RETENTION_EXPIRED',
    'MANIFEST_INTEGRITY', 'RAW_INTEGRITY', 'QUERY_LINEAGE_INVALID', 'TIME_ADMISSION_INVALID', 'ERR_NUMERIC_LEXEME_INVALID', 'CHILD_INVALID', 'RECORD_LIMIT', 'ROW_LIMIT', 'ADDRESS_LIMIT', 'OUTPUT_LIMIT']);
    return incomplete(allowed.has(error?.message) ? error.message : 'READ_OR_PARSE_INVALID'); }
}
module.exports = { CONFIG, discover, readFixed };
