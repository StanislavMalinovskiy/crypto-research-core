'use strict';
const p = require('./exploratory-probe-v3.cjs');
const invalid = code => ({ classification: 'EXPLORATORY', status: 'INCOMPLETE', code, d1Evidence: false, d1Passed: false });
async function runCli(args, injected) {
  if (!Array.isArray(args)) return invalid('ARGUMENTS_INVALID'); if (!args.length) return invalid('DISABLED');
  if (args.length === 3 && args[0] === '--enable-public' && args[1] === '--output' && args[2] === p.OUTPUT)
    return p.runProbe({ enabled: true, output: p.OUTPUT }, injected);
  if (args.length === 2 && args[0] === '--replay' && args[1] === p.OUTPUT + '\\manifest.json') {
    if (!/^v24\./.test(process.version)) return invalid('RUNTIME_INVALID');
    try { return await p.replay(injected?.store ?? p.fileStore(p.OUTPUT)); } catch { return invalid('INTEGRITY_ERROR'); }
  }
  return invalid('ARGUMENTS_INVALID');
}
if (require.main === module) runCli(process.argv.slice(2)).then(result => {
  const { summary, ...receipt } = result; process.stdout.write(JSON.stringify(receipt) + '\n');
  process.exitCode = result.code ? (result.code === 'DISABLED' ? 2 : 1) : result.status === 'COMPLETE' ? 0 : 2;
}).catch(() => { process.stdout.write(JSON.stringify(invalid('STORAGE_ERROR')) + '\n'); process.exitCode = 1; });
module.exports = { runCli };
