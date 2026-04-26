import { MULTI_FILE_MAGIC } from './constants';

/**
 * Packs multiple files into a single binary format.
 */
export function packMultipleFiles(filesArray) {
  const enc = new TextEncoder();
  const entries = filesArray.map(f => {
    const nameBytes = enc.encode(f.name);
    if (nameBytes.length > 254) throw new Error('Filename too long: ' + f.name);
    return { nameBytes, data: f.data };
  });

  let total = 3;
  for (const e of entries) total += 1 + e.nameBytes.length + 4 + e.data.length;

  const out = new Uint8Array(total);
  const view = new DataView(out.buffer);
  out[0] = MULTI_FILE_MAGIC;
  view.setUint16(1, entries.length, false);

  let offset = 3;
  for (const e of entries) {
    out[offset] = e.nameBytes.length;
    out.set(e.nameBytes, offset + 1);
    view.setUint32(offset + 1 + e.nameBytes.length, e.data.length, false);
    out.set(e.data, offset + 1 + e.nameBytes.length + 4);
    offset += 1 + e.nameBytes.length + 4 + e.data.length;
  }
  return out;
}

/**
 * Unpacks binary data into multiple file objects.
 */
export function unpackMultipleFiles(dec) {
  const view = new DataView(dec.buffer, dec.byteOffset, dec.byteLength);
  const count = view.getUint16(1, false);
  const files = [];
  let offset = 3;
  const decod = new TextDecoder();

  for (let i = 0; i < count; i++) {
    const nameLen = dec[offset];
    const name = decod.decode(dec.subarray(offset + 1, offset + 1 + nameLen));
    const dataLen = view.getUint32(offset + 1 + nameLen, false);
    const data = dec.subarray(offset + 1 + nameLen + 4, offset + 1 + nameLen + 4 + dataLen);
    offset += 1 + nameLen + 4 + dataLen;
    files.push({ name, data });
  }
  return files;
}
