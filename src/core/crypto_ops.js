import {
  SALT_SIZE, NONCE_SIZE, TAG_LENGTH_BITS, TAG_LENGTH_BYTES,
  PROTOCOL_VERSION, PREFIX_SIZE, INNER_HEADER_SIZE, TIMESTAMP_SIZE,
  LEGACY_PROTOCOL_VERSION, LEGACY_PREFIX_SIZE, LEGACY_INNER_HEADER_SIZE,
  FLAG_COMPRESSED, MULTI_FILE_MAGIC,
} from './constants';
import { compressData, decompressData } from './compression';
import { getMasterKey, deriveMessageKey } from './kdf';
import { unpackMultipleFiles } from './packing';

const MIN_CIPHERTEXT_SIZE = PREFIX_SIZE + INNER_HEADER_SIZE + TAG_LENGTH_BYTES;
const ENTROPY_THRESHOLD = 7.5;
const ENTROPY_SAMPLE_BYTES = 4096;

export async function encryptData(data, passphrase, filename = "", flags = FLAG_COMPRESSED) {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_SIZE));
  const masterKey = await getMasterKey(passphrase, salt);
  const nonce = crypto.getRandomValues(new Uint8Array(NONCE_SIZE));
  const messageKey = await deriveMessageKey(masterKey, nonce);

  const enc = new TextEncoder();
  const nameBytes = enc.encode(filename);
  if (nameBytes.length > 254) throw new Error('Filename too long');

  const payload = new Uint8Array(1 + nameBytes.length + data.length);
  payload[0] = nameBytes.length;
  payload.set(nameBytes, 1);
  payload.set(data, 1 + nameBytes.length);

  const compressed = await compressData(payload);

  const fullPlaintext = new Uint8Array(INNER_HEADER_SIZE + compressed.length);
  const innerView = new DataView(fullPlaintext.buffer);
  fullPlaintext[0] = PROTOCOL_VERSION;
  fullPlaintext[1] = flags;
  innerView.setBigInt64(2, BigInt(Date.now()), false);
  fullPlaintext.set(compressed, INNER_HEADER_SIZE);

  const prefix = new Uint8Array(PREFIX_SIZE);
  prefix.set(nonce, 0);
  prefix.set(salt, NONCE_SIZE);

  const encrypted = new Uint8Array(await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: nonce, tagLength: TAG_LENGTH_BITS, additionalData: prefix },
    messageKey,
    fullPlaintext
  ));

  const out = new Uint8Array(prefix.length + encrypted.length);
  out.set(prefix, 0);
  out.set(encrypted, prefix.length);
  return out;
}

export async function decryptData(packed, passphrase) {
  try {
    return await decryptV3(packed, passphrase);
  } catch (err) {
    // Fall back to legacy v1 only when the first byte looks like that format's version tag.
    if (packed.length >= LEGACY_PREFIX_SIZE && packed[0] === LEGACY_PROTOCOL_VERSION) {
      return await decryptLegacyV1(packed, passphrase);
    }
    throw err;
  }
}

async function decryptV3(packed, passphrase) {
  if (packed.length < MIN_CIPHERTEXT_SIZE) {
    throw new Error('Invalid ciphertext length');
  }

  const nonce = packed.subarray(0, NONCE_SIZE);
  const salt = packed.subarray(NONCE_SIZE, PREFIX_SIZE);
  const prefix = packed.subarray(0, PREFIX_SIZE);
  const enc = packed.subarray(PREFIX_SIZE);

  const masterKey = await getMasterKey(passphrase, salt);
  const messageKey = await deriveMessageKey(masterKey, nonce);

  const decryptedBuffer = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: nonce, tagLength: TAG_LENGTH_BITS, additionalData: prefix },
    messageKey,
    enc
  );
  const fullDecrypted = new Uint8Array(decryptedBuffer);

  if (fullDecrypted[0] !== PROTOCOL_VERSION) {
    throw new Error('Unrecognized inner version: ' + fullDecrypted[0]);
  }

  const innerView = new DataView(fullDecrypted.buffer, fullDecrypted.byteOffset, fullDecrypted.byteLength);
  const createdAt = Number(innerView.getBigInt64(2, false));

  const dec = await decompressData(fullDecrypted.subarray(INNER_HEADER_SIZE));
  return parsePlaintext(dec, createdAt);
}

async function decryptLegacyV1(packed, passphrase) {
  const minLen = LEGACY_PREFIX_SIZE + LEGACY_INNER_HEADER_SIZE + TAG_LENGTH_BYTES;
  if (packed.length < minLen) throw new Error('Invalid ciphertext length');

  const nonce = packed.subarray(1, 1 + NONCE_SIZE);
  const salt = packed.subarray(1 + NONCE_SIZE, LEGACY_PREFIX_SIZE);
  const prefix = packed.subarray(0, LEGACY_PREFIX_SIZE);
  const enc = packed.subarray(LEGACY_PREFIX_SIZE);

  const masterKey = await getMasterKey(passphrase, salt);
  const messageKey = await deriveMessageKey(masterKey, nonce);

  const decryptedBuffer = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: nonce, tagLength: TAG_LENGTH_BITS, additionalData: prefix },
    messageKey,
    enc
  );
  const fullDecrypted = new Uint8Array(decryptedBuffer);
  const dec = await decompressData(fullDecrypted.subarray(LEGACY_INNER_HEADER_SIZE));
  return parsePlaintext(dec, null);
}

function parsePlaintext(dec, createdAt) {
  if (dec[0] === MULTI_FILE_MAGIC) {
    return { multiFile: true, files: unpackMultipleFiles(dec), createdAt };
  }
  const nameLen = dec[0];
  const filename = new TextDecoder().decode(dec.subarray(1, 1 + nameLen));
  const data = dec.subarray(1 + nameLen);
  return { multiFile: false, data, filename, createdAt };
}

// Heuristic for the action-priority swap, not a security gate. v3 blobs are
// indistinguishable from random by header alone, so we fall back to entropy.
export function isEncryptedBuffer(buffer) {
  if (!buffer) return false;
  if (buffer.length < MIN_CIPHERTEXT_SIZE) return false;
  if (buffer[0] === LEGACY_PROTOCOL_VERSION) return true;
  const sampleLen = Math.min(ENTROPY_SAMPLE_BYTES, buffer.length);
  return shannonEntropy(buffer, sampleLen) >= ENTROPY_THRESHOLD;
}

function shannonEntropy(data, len) {
  const freq = new Uint32Array(256);
  for (let i = 0; i < len; i++) freq[data[i]]++;
  let h = 0;
  const ln2 = Math.log(2);
  for (let i = 0; i < 256; i++) {
    const f = freq[i];
    if (f === 0) continue;
    const p = f / len;
    h -= p * (Math.log(p) / ln2);
  }
  return h;
}
