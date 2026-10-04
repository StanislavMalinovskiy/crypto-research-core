'use strict';
const c = require('./e2-monthly-census.cjs');
async function runCli(args, deps = {}) {
  if (!Array.isArray(args)) return c.error('ARGUMENTS_INVALID'); if (!args.length) return c.error('DISABLED');
  if (args.length === 3 && args[0] === '--replay' && args[1] === '--month' && c.MONTHS.includes(args[2])) return c.replay(args[2], deps.store);
  if (args.length === 9 && args[0] === '--enable-public' && args[1] === '--month' && args[3] === '--retained-before'
    && args[5] === '--attempts-before' && args[7] === '--retries-before' && c.MONTHS.includes(args[2]))
    return c.run({ enabled: true, month: args[2], retainedBefore: args[4], attemptsBefore: args[6], retriesBefore: args[8] }, deps);
  return c.error('ARGUMENTS_INVALID');
}
if (require.main === module) runCli(process.argv.slice(2)).then(r => {
  const { summary, ...receipt } = r, bytes = Buffer.from(JSON.stringify(receipt) + '\n');
  if (bytes.length > c.LIMITS.stdout) { process.stdout.write(JSON.stringify(c.error('METADATA_LIMIT')) + '\n'); process.exitCode = 1; }
  else { process.stdout.write(bytes); process.exitCode = c.exitCode(r); }
}).catch(() => { process.stdout.write(JSON.stringify(c.error('INTEGRITY_ERROR')) + '\n'); process.exitCode = 1; });
module.exports = { runCli };
