'use strict';
const fs = require('node:fs');
const { run } = require('./probe.cjs');
async function main(args) {
  if (!args.includes('--enable-public')) { console.log('DISABLED'); return; }
  const allowed = new Set(['--enable-public', '--requests', '--evidence-root']), values = {}; let valid = true;
  for (let i = 0; i < args.length; i++) {
    const key = args[i]; if (!allowed.has(key) || Object.hasOwn(values, key)) { valid = false; break; }
    values[key] = key === '--enable-public' ? true : args[++i]; if (!values[key]) valid = false;
  }
  if (!valid || !values['--requests']) throw Error('INPUT_INVALID');
  const file = values['--requests']; if (fs.statSync(file).size > 256000) throw Error('INPUT_INVALID');
  const plan = JSON.parse(fs.readFileSync(file, 'utf8'));
  const result = await run({ enabled: true, plan, root: values['--evidence-root'] });
  console.log(JSON.stringify({ reason: result.reason, attempts: result.attempts, received: result.received }));
  if (result.reason !== 'LIST_EXHAUSTED') process.exitCode = 1;
}
if (require.main === module) main(process.argv.slice(2)).catch(() => { console.error('INPUT_INVALID'); process.exitCode = 1; });
module.exports = { main };
