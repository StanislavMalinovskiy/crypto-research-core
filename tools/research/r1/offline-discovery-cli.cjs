'use strict';
const { discover, CONFIG } = require('./offline-discovery.cjs');
function runCli(args = [], deps = {}) {
  if (!args.length) return { classification: 'EXPLORATORY', status: 'DISABLED', code: 'DISABLED', exitCode: 2, runAuthorized: false, d1Evidence: false, d1Passed: false };
  if (args.length !== 1 || args[0] !== '--enable-offline') return { classification: 'EXPLORATORY', status: 'DISCOVERY_INVALID', code: 'ARGUMENTS_INVALID', exitCode: 1 };
  return discover(CONFIG, deps);
}
if (require.main === module) { const report = runCli(process.argv.slice(2)); process.stdout.write(JSON.stringify(report) + '\n'); process.exitCode = report.exitCode; }
module.exports = { runCli };
