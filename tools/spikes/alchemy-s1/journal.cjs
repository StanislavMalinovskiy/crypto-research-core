'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { hash, fail, exact } = require('./core.cjs');
const MAX = 16 * 1024 * 1024;
function durable(file, bytes, flags = 'w') {
  const fd = fs.openSync(file, flags);
  try { let pos = 0; while (pos < bytes.length) { const n = fs.writeSync(fd, bytes, pos, bytes.length - pos); if (!n) fail('WRITE_FAILED'); pos += n; } fs.fsyncSync(fd); }
  finally { fs.closeSync(fd); }
}
function atomic(file, value) { const tmp = `${file}.tmp`; durable(tmp, Buffer.from(JSON.stringify(value))); fs.renameSync(tmp, file); }
function size(dir) {
  let total = 0; for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name); if (entry.isSymbolicLink()) fail('UNSAFE_PATH');
    total += entry.isDirectory() ? size(file) : fs.statSync(file).size;
  } return total;
}
function free(dir) { const stat = fs.statfsSync(dir, { bigint: true }); return Number(stat.bavail * stat.bsize); }
function findIdentity(file, signature) {
  if (!fs.existsSync(file)) return null;
  const fd = fs.openSync(file, 'r'), bytes = Buffer.alloc(65536); let tail = '';
  try {
    for (;;) {
      const count = fs.readSync(fd, bytes, 0, bytes.length, null); if (!count) break;
      const lines = (tail + bytes.toString('utf8', 0, count)).split('\n'); tail = lines.pop();
      if (tail.length > 4096) fail('CORRUPT_EVIDENCE');
      for (const line of lines) { const record = JSON.parse(line); if (record.signature === signature) return record; }
    }
    if (tail) fail('CORRUPT_EVIDENCE'); return null;
  } finally { fs.closeSync(fd); }
}
class Journal {
  constructor(dir, options = {}) {
    this.dir = path.resolve(dir); this.options = options; this.guard = options.guard || (() => {});
    fs.mkdirSync(dir, { recursive: true }); this.raw = path.join(dir, 'raw.bin'); this.check = path.join(dir, 'checkpoint.json');
    if (options.resume) {
      this.state = JSON.parse(fs.readFileSync(this.check)); this.verify();
    } else {
      if (fs.existsSync(this.raw) || fs.existsSync(this.check)) fail('PATH_REUSE');
      this.state = { anchor: exact(options.anchor || '0'), complete: null, offset: 0, ranges: [], unresolved: [], incomplete: false };
      durable(this.raw, Buffer.alloc(0), 'wx');
    }
    if (!options.readOnly) {
      fs.mkdirSync(path.join(dir, 'slots'), { recursive: true }); fs.mkdirSync(path.join(dir, 'identities'), { recursive: true }); this.checkpoint();
    }
  }
  verify() {
    const fd = fs.openSync(this.raw, 'r'), length = fs.fstatSync(fd).size; let offset = 0;
    try {
      while (offset < length) {
        const header = Buffer.alloc(8); if (fs.readSync(fd, header, 0, 8, offset) < 8) break;
        const metaLength = header.readUInt32BE(0), bodyLength = header.readUInt32BE(4);
        if (metaLength > 65536 || bodyLength > MAX) fail('CORRUPT_EVIDENCE');
        if (offset + 8 + metaLength + bodyLength > length) break;
        const meta = Buffer.alloc(metaLength), body = Buffer.alloc(bodyLength);
        fs.readSync(fd, meta, 0, metaLength, offset + 8); fs.readSync(fd, body, 0, bodyLength, offset + 8 + metaLength);
        let info; try { info = JSON.parse(meta); } catch { fail('CORRUPT_EVIDENCE'); }
        if (info.sha256 !== hash(body) || info.length !== bodyLength) fail('CORRUPT_EVIDENCE');
        offset += 8 + metaLength + bodyLength;
      }
      if (this.state.offset > offset) fail('CORRUPT_EVIDENCE');
      if (offset !== length || this.state.offset !== length) this.state.incomplete = true;
    } finally { fs.closeSync(fd); }
  }
  append(raw, metadata) {
    if (this.state.incomplete) fail('INCOMPLETE_TAIL'); if (raw.length > MAX) fail('RAW_LIMIT');
    const meta = Buffer.from(JSON.stringify({ ...metadata, length: raw.length, sha256: hash(raw) }));
    if (meta.length > 65536) fail('RAW_LIMIT'); const header = Buffer.alloc(8);
    header.writeUInt32BE(meta.length); header.writeUInt32BE(raw.length, 4);
    const bytes = Buffer.concat([header, meta, raw]); this.guard(bytes.length);
    try { durable(this.raw, bytes, 'a'); }
    catch (e) { this.state.incomplete = true; throw e; }
    this.state.offset += bytes.length; return this.state.offset;
  }
  checkpoint() {
    if (this.options.beforeCheckpoint) this.options.beforeCheckpoint();
    this.guard(Buffer.byteLength(JSON.stringify(this.state)) + 1024); atomic(this.check, this.state);
  }
  event(event) {
    const bytes = Buffer.from(`${JSON.stringify(event)}\n`); this.guard(bytes.length); durable(path.join(this.dir, 'events.jsonl'), bytes, 'a');
  }
  signatures(slot) {
    const file = path.join(this.dir, 'slots', `${exact(slot)}.jsonl`);
    if (!fs.existsSync(file)) return [];
    if (fs.statSync(file).size > MAX / 16) fail('INDEX_LIMIT');
    const result = fs.readFileSync(file, 'utf8').trim(); return result ? result.split('\n').map(line => JSON.parse(line)) : [];
  }
  transaction({ slot, signature, index, hash: contentHash }) {
    slot = exact(slot); index = exact(index);
    if (typeof signature !== 'string' || !signature || signature.length > 128 || typeof contentHash !== 'string') fail('UNSUPPORTED');
    const bucket = path.join(this.dir, 'identities', `${hash(signature).slice(0, 2)}.jsonl`);
    const global = findIdentity(bucket, signature);
    if (global && (global.slot !== slot || global.index !== index || global.hash !== contentHash)) fail('IMMUTABLE_CONFLICT');
    const records = this.signatures(slot), old = records.find(item => item.signature === signature);
    if (old) { if (old.index !== index || old.hash !== contentHash) fail('IMMUTABLE_CONFLICT'); return 'duplicate'; }
    const bytes = Buffer.from(`${JSON.stringify({ signature, index, hash: contentHash })}\n`);
    const file = path.join(this.dir, 'slots', `${slot}.jsonl`);
    if ((fs.existsSync(file) ? fs.statSync(file).size : 0) + bytes.length > MAX / 16) fail('INDEX_LIMIT');
    if (!global) {
      const identity = Buffer.from(`${JSON.stringify({ signature, slot, index, hash: contentHash })}\n`);
      this.guard(identity.length); durable(bucket, identity, 'a');
    }
    this.guard(bytes.length); durable(file, bytes, 'a'); return 'new';
  }
  verifyRange(from, to, mismatches) {
    if (mismatches.length) return false;
    this.state.ranges.push({ from: exact(from), to: exact(to) });
    this.state.ranges.sort((a, b) => BigInt(a.from) < BigInt(b.from) ? -1 : 1);
    let next = this.state.complete === null ? BigInt(this.state.anchor) : BigInt(this.state.complete) + 1n;
    this.state.ranges = this.state.ranges.filter(range => {
      if (BigInt(range.from) > next) return true;
      if (BigInt(range.to) >= next) next = BigInt(range.to) + 1n; return false;
    });
    if (next > BigInt(this.state.anchor)) this.state.complete = String(next - 1n);
    if (this.state.ranges.length > 64) fail('INDEX_LIMIT'); this.checkpoint(); return true;
  }
  close() {}
}
module.exports = { Journal, atomic, durable, size, free };
