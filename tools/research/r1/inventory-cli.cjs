'use strict';
const { validateInventory } = require('./inventory.cjs');
const fs = require('node:fs');
const INPUT_LIMIT = 1000000, REPORT_LIMIT = 262144;
const FILE_CODES = new Set(['FILE_METADATA_ERROR', 'FILE_READ_ERROR', 'INPUT_LIMIT', 'JSON_INVALID']);
function invalid(code) {
  return { exitCode: 1, report: { classification: 'INVENTORY_INVALID', code, runAuthorized: false, d1Passed: false } };
}
function runCli(args, io = { fs }) {
  if (!Array.isArray(args) || args.length !== 2 || args[0] !== '--inventory' ||
      typeof args[1] !== 'string' || !args[1].trim() || /[\u0000-\u001f]/u.test(args[1])) return invalid('ARGUMENTS_INVALID');
  let fd, result;
  try {
    fd = io.fs.openSync(args[1], 'r');
    let metadata; try { metadata = io.fs.fstatSync(fd); } catch { throw 'FILE_METADATA_ERROR'; }
    if (!metadata.isFile() || !Number.isSafeInteger(metadata.size) || metadata.size < 0) throw 'FILE_METADATA_ERROR';
    if (metadata.size > INPUT_LIMIT) throw 'INPUT_LIMIT';
    const buffer = Buffer.alloc(INPUT_LIMIT + 1);
    let offset = 0;
    while (offset < buffer.length) {
      const count = io.fs.readSync(fd, buffer, offset, Math.min(65536, buffer.length - offset), offset);
      if (count === 0) break;
      if (!Number.isSafeInteger(count) || count < 0 || count > buffer.length - offset) throw 'FILE_READ_ERROR';
      offset += count;
    }
    if (offset > INPUT_LIMIT) throw 'INPUT_LIMIT';
    let input;
    try { input = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(buffer.subarray(0, offset))); }
    catch { throw 'JSON_INVALID'; }
    const report = validateInventory(input);
    result = { exitCode: report.classification === 'INVENTORY_COMPLETE' ? 0 : report.classification === 'INVENTORY_BLOCKED' ? 2 : 1, report };
    if (Buffer.byteLength(JSON.stringify(report) + '\n') > REPORT_LIMIT) result = invalid('REPORT_LIMIT');
  } catch (code) { result = invalid(FILE_CODES.has(code) ? code : 'FILE_READ_ERROR'); }
  finally {
    if (fd !== undefined) {
      try { io.fs.closeSync(fd); } catch { result = invalid('FILE_READ_ERROR'); }
    }
  }
  return result;
}
if (require.main === module) {
  const result = runCli(process.argv.slice(2));
  process.stdout.write(JSON.stringify(result.report) + '\n');
  process.exitCode = result.exitCode;
}
module.exports = { runCli };
