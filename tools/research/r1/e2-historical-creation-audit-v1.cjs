'use strict';
// Read-only relationships over existing creations; never a raw replay, admission or selection runner.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const { digest, parseExact, canonical, uint, numericPath, signature, address, boundedLimits } = require('./e2-census-parser-v2.cjs');
const STOP = '2026-10-05T04:42:41Z';
const LIMITS = Object.freeze({ timeMs: 1800000, outputBytes: 50000000, retainedBytes: 50000000000,
  freeBytes: 30000000000, rows: 1000000, lineBytes: 262144, stateBytes: 128000000, diagnostics: 1000, inputs: 64 });
const need = (ok, code = 'INPUT_INVALID') => { if (!ok) throw Error(code); };
const COVERAGE = Object.freeze({ scope: 'ONLY_SUPPLIED_CREATION_ROWS', rawReplayed: false, transactionPopulationChecked: false,
  globalCensusProven: false, deploymentProven: false, selectionPerformed: false, outcomesRead: false });
function relationshipState(limits = LIMITS) {
  const signatures = new Map(), paths = new Map(), pathCounts = new Map(); let stateBytes = 0;
  const report = { rowsExamined: 0, distinctSignatures: 0, distinctPaths: 0, equalRetries: 0,
    multiplePathSignatures: 0, conflicts: 0, signatureConflicts: 0, pathConflicts: 0, diagnostics: [], coverage: { ...COVERAGE } };
  const reserve = (k, v) => { const bytes = Buffer.byteLength(k) + Buffer.byteLength(v) + 128;
    need(stateBytes + bytes <= limits.stateBytes, 'STATE_LIMIT'); stateBytes += bytes; };
  function add(c, ref) {
    need(report.rowsExamined < limits.rows, 'ROW_LIMIT'); need(c && typeof c === 'object' && !Array.isArray(c));
    const sig = signature(c.signature), slot = uint(c.slot), index = uint(c.transactionIndex), ip = numericPath(c.instructionPath);
    uint(c.timestamp); need(uint(c.cpiDepth) === ip.length - 1);
    for (const k of ['pool', 'globalConfig', 'creator', 'baseMint', 'quoteMint']) address(c[k]);
    uint(c.accountCount); uint(c.dataLength); uint(c.lineOrdinal);
    need(c.status === 'OBSERVED_DECLARED_CREATE_POOL' && /^sha256:[0-9a-f]{64}$/.test(c.rawHash)
      && typeof c.layoutApplicability === 'string');
    const mapping = canonical([slot, index]), key = canonical([sig, ip]);
    const contents = Object.fromEntries(Object.entries(c).filter(([k]) => !['rawHash', 'lineOrdinal'].includes(k)));
    const contentHash = digest(Buffer.from(canonical(contents))), issues = [];
    if (signatures.has(sig) && signatures.get(sig).content !== mapping) issues.push({ kind: 'SIGNATURE_MAPPING_CONFLICT', prior: signatures.get(sig).ref });
    if (paths.has(key) && paths.get(key).content !== contentHash) issues.push({ kind: 'SIGNATURE_PATH_CONTENT_CONFLICT', prior: paths.get(key).ref });
    const diagnostics = issues.map(issue => ({ ...issue, current: ref, signature: sig, instructionPath: ip }));
    need(report.diagnostics.length + diagnostics.length <= limits.diagnostics, 'DIAGNOSTIC_LIMIT');
    // Preflight all storage reservations before applying any row counts/maps.
    const requiredBytes = (!signatures.has(sig) ? Buffer.byteLength(sig) + Buffer.byteLength(mapping) + 128 : 0)
      + (!paths.has(key) ? Buffer.byteLength(key) + Buffer.byteLength(contentHash) + 128 : 0)
      + diagnostics.reduce((n, diagnostic) => n + Buffer.byteLength(JSON.stringify(diagnostic)), 0);
    need(stateBytes + requiredBytes <= limits.stateBytes, 'STATE_LIMIT');
    if (!signatures.has(sig)) { reserve(sig, mapping); signatures.set(sig, { content: mapping, ref }); report.distinctSignatures++; }
    if (!paths.has(key)) { reserve(key, contentHash); paths.set(key, { content: contentHash, ref }); report.distinctPaths++;
      const count = (pathCounts.get(sig) ?? 0) + 1; pathCounts.set(sig, count); if (count === 2) report.multiplePathSignatures++; }
    else if (!issues.length) report.equalRetries++;
    for (const diagnostic of diagnostics) {
      stateBytes += Buffer.byteLength(JSON.stringify(diagnostic)); report.diagnostics.push(diagnostic); report.conflicts++;
      if (diagnostic.kind === 'SIGNATURE_MAPPING_CONFLICT') report.signatureConflicts++; else report.pathConflicts++; }
    report.rowsExamined++;
  }
  return { add, report };
}
function auditRows(rows, options = {}) {
  const limits = boundedLimits(options.limits, LIMITS), state = relationshipState(limits); let ordinal = 0;
  for (const row of rows) state.add(row, { input: 'synthetic', line: ++ordinal });
  return state.report;
}
function checkedPath(file, mustExist = true) {
  need(typeof file === 'string' && path.isAbsolute(file), 'UNSAFE_PATH'); const resolved = path.resolve(file);
  need(resolved === file, 'UNSAFE_PATH'); let at = mustExist ? file : path.dirname(file);
  while (true) { if (fs.existsSync(at)) { const stat = fs.lstatSync(at);
      need(!stat.isSymbolicLink() && fs.realpathSync.native(at).toLowerCase() === at.toLowerCase(), 'UNSAFE_PATH'); }
    const parent = path.dirname(at); if (parent === at) break; at = parent; }
  return resolved;
}
function outsideGit(file) { let at = path.dirname(file); while (true) {
  need(!fs.existsSync(path.join(at, '.git')), 'UNSAFE_PATH'); const parent = path.dirname(at); if (parent === at) break; at = parent; } }
function scan(file, limit, check, consume) {
  checkedPath(file); const stat = fs.lstatSync(file); need(stat.isFile() && stat.size <= limit, 'INPUT_LIMIT');
  const fd = fs.openSync(file, 'r'), buffer = Buffer.alloc(65536); let total = 0;
  try { need(fs.fstatSync(fd).size === stat.size, 'INPUT_CHANGED');
    while (true) { check(); const n = fs.readSync(fd, buffer, 0, buffer.length, null); if (!n) break;
      total += n; need(total <= limit, 'INPUT_LIMIT'); consume(buffer.subarray(0, n)); }
    check(); return { bytes: total, initialBytes: stat.size };
  } finally { fs.closeSync(fd); }
}
function hashFile(file, limit, check) { const hash = crypto.createHash('sha256');
  const result = scan(file, limit, check, bytes => hash.update(bytes));
  return { ...result, hash: 'sha256:' + hash.digest('hex') }; }
function streamRows(file, limits, check, consume) {
  let pending = Buffer.alloc(0), line = 0;
  const emit = bytes => { need(bytes.length <= limits.lineBytes, 'LINE_LIMIT'); line++;
    if (!bytes.toString('utf8').trim()) return; check();
    try { consume(parseExact(bytes, { maxBytes: limits.lineBytes, check }), line); }
    catch (e) { e.auditRef = { input: file, line }; throw e; } };
  scan(file, limits.retainedBytes, check, chunk => {
    let at = 0; while (at < chunk.length) { const found = chunk.indexOf(10, at), end = found < 0 ? chunk.length : found;
      const piece = chunk.subarray(at, end); need(pending.length + piece.length <= limits.lineBytes, 'LINE_LIMIT');
      pending = Buffer.concat([pending, piece]); if (found < 0) break;
      emit(pending); pending = Buffer.alloc(0); at = end + 1; }
  }); if (pending.length) emit(pending);
}
function run(options = {}) {
  let report = { version: 'e2-historical-creation-audit-v1', status: 'INCOMPLETE', code: null,
    d1Evidence: false, d1Passed: false, cohortAdmitted: false, authoritativeCensusComplete: false,
    inputs: [], coverage: { ...COVERAGE }, absoluteStop: STOP }, output, limits, check, created = false;
  try {
    limits = boundedLimits(options.limits, LIMITS); need(limits.freeBytes === LIMITS.freeBytes, 'LIMIT_INVALID'); report.limits = limits;
    const now = options.now ?? (() => performance.now()), utcNow = options.utcNow ?? Date.now, started = now(); let last = started;
    check = () => { const t = now(), utc = utcNow();
      need(Number.isFinite(t) && t >= last && t - started <= limits.timeMs, 'TIME_LIMIT'); last = t;
      need(Number.isFinite(utc) && utc < Date.parse(STOP), 'ABSOLUTE_STOP'); };
    check(); need(Array.isArray(options.inputs) && options.inputs.length > 0 && options.inputs.length <= limits.inputs);
    need(typeof options.retainedBefore === 'string' && /^(0|[1-9][0-9]*)$/.test(options.retainedBefore), 'RETAINED_INVALID');
    const retained = BigInt(options.retainedBefore); need(retained + BigInt(limits.outputBytes) <= BigInt(limits.retainedBytes), 'RETAINED_LIMIT');
    report.retainedBefore = options.retainedBefore;
    const inputs = options.inputs.map(file => checkedPath(file));
    need(new Set(inputs.map(f => f.toLowerCase())).size === inputs.length, 'DUPLICATE_INPUT');
    let sourceBytes = 0n;
    for (const file of inputs) { need(path.basename(file) === 'creations.jsonl', 'INPUT_KIND_INVALID'); const s = fs.lstatSync(file);
      need(s.isFile(), 'UNSAFE_PATH'); sourceBytes += BigInt(s.size); }
    need(sourceBytes <= retained, 'RETAINED_INVALID'); report.sourceBytes = String(sourceBytes);
    output = checkedPath(options.output, false); outsideGit(output); need(!fs.existsSync(output), 'ROOT_EXISTS');
    need(!inputs.some(f => f === output || f.startsWith(output + path.sep)), 'UNSAFE_PATH');
    let ancestor = path.dirname(output); while (!fs.existsSync(ancestor)) ancestor = path.dirname(ancestor);
    const free = () => options.freeBytes ? BigInt(options.freeBytes()) : (() => { const s = fs.statfsSync(ancestor, { bigint: true }); return s.bavail * s.bsize; })();
    const freeCheck = () => { check(); need(free() >= BigInt(limits.freeBytes) + BigInt(limits.outputBytes), 'FREE_SPACE_LIMIT'); };
    freeCheck();
    // Hash all supplied creation inputs before parsing, using bounded streaming reads.
    let hashedBytes = 0n;
    for (const file of inputs) { const h = hashFile(file, limits.retainedBytes, check);
      need(h.bytes === h.initialBytes, 'INPUT_CHANGED'); hashedBytes += BigInt(h.bytes);
      need(hashedBytes <= retained, 'RETAINED_INVALID');
      report.inputs.push({ path: file, bytesBefore: h.bytes, hashBefore: h.hash, hashAfter: null }); }
    need(hashedBytes === sourceBytes, 'INPUT_CHANGED');
    const state = relationshipState(limits); let parseError = null;
    try { for (const file of inputs) streamRows(file, limits, check, (row, line) => {
      state.add(row, { input: file, line }); if (options.onRow) options.onRow(row); }); }
    catch (e) { parseError = e.message; report.stoppedAt = e.auditRef ?? null; }
    Object.assign(report, state.report);
    // A failed/partial relationship scan still gets source hashes when remaining budget permits it.
    for (const input of report.inputs) { const h = hashFile(input.path, limits.retainedBytes, check);
      input.hashAfter = h.hash; input.bytesAfter = h.bytes;
      if (h.bytes !== input.bytesBefore || h.hash !== input.hashBefore) parseError = 'INPUT_CHANGED'; }
    if (parseError) report.code = parseError;
    else { report.status = report.conflicts ? 'RELATIONSHIP_CONFLICTS' : 'RELATIONSHIPS_CONSISTENT'; report.code = null; }
    report.coverage.relationshipRowsComplete = parseError === null;
    freeCheck();
    const bytes = Buffer.from(JSON.stringify(report, null, 2) + '\n'), outputHash = digest(bytes);
    const receipt = Buffer.from(JSON.stringify({ version: report.version, output: 'report.json', bytes: bytes.length, sha256: outputHash,
      inputs: report.inputs, d1Evidence: false }) + '\n');
    need(bytes.length + receipt.length <= limits.outputBytes, 'OUTPUT_LIMIT');
    // Exclusive root creation and exclusive writes; existing evidence is never changed.
    fs.mkdirSync(path.dirname(output), { recursive: true }); checkedPath(output, false); outsideGit(output); freeCheck();
    fs.mkdirSync(output); created = true;
    for (const [name, contents] of [['report.json', bytes], ['receipt.json', receipt]]) { check(); checkedPath(path.join(output, name), false);
      const fd = fs.openSync(path.join(output, name), 'wx'); try { fs.writeFileSync(fd, contents); fs.fsyncSync(fd); } finally { fs.closeSync(fd); } }
    check(); return { ...report, outputHash, outputBytes: bytes.length + receipt.length, output };
  } catch (e) { return { ...report, status: 'INCOMPLETE', code: e.message, outputCreated: created }; }
}
function cli(args) { const options = { inputs: [] }, seen = new Set();
  for (let i = 0; i < args.length; i += 2) { const flag = args[i], value = args[i + 1];
    need(value && ['--input', '--output', '--retained-before'].includes(flag), 'ARGUMENTS_INVALID');
    if (flag === '--input') options.inputs.push(value);
    else { need(!seen.has(flag), 'ARGUMENTS_INVALID'); seen.add(flag); options[flag === '--output' ? 'output' : 'retainedBefore'] = value; } }
  return run(options);
}
if (require.main === module) { let report; try { report = cli(process.argv.slice(2)); } catch (e) { report = { status: 'INCOMPLETE', code: e.message }; }
  process.stdout.write(JSON.stringify({ status: report.status, code: report.code, rowsExamined: report.rowsExamined,
    conflicts: report.conflicts, output: report.output, outputHash: report.outputHash, inputs: report.inputs,
    coverage: report.coverage, stoppedAt: report.stoppedAt }) + '\n');
  process.exitCode = report.status === 'RELATIONSHIPS_CONSISTENT' ? 0 : 2;
}
module.exports = { run, cli, auditRows, LIMITS, STOP };
