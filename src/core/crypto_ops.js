import { 
  SALT_SIZE, NONCE_SIZE, TAG_LENGTH_BITS, TAG_LENGTH_BYTES, 
  PROTOCOL_VERSION, HEADER_SIZE, MULTI_FILE_MAGIC 
} from './constants';
import { compressData, decompressData } from './compression';
import { getMasterKey, deriveMessageKey } from './kdf';
import { unpackMultipleFiles } from './packing';

/**
 * Encrypts data using AES-GCM with key derivation and compression.
 */
export async function encryptData(data, passphrase, filename = "", flags = 0x01) {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_SIZE));
  const masterKey = await getMasterKey(passphrase, salt);
  const nonce = crypto.getRandomValues(new Uint8Array(NONCE_SIZE));
  const messageKey = await deriveMessageKey(masterKey, nonce); // Use nonce as msg salt
  
  const enc = new TextEncoder();
  const nameBytes = enc.encode(filename);
  const payload = new Uint8Array(1 + nameBytes.length + data.length);
  payload[0] = nameBytes.length;
  payload.set(nameBytes, 1);
  payload.set(data, 1 + nameBytes.length);

  const compressed = await compressData(payload);
  
  const header = new Uint8Array(HEADER_SIZE);
  header[0] = flags;

  const fullPlaintext = new Uint8Array(HEADER_SIZE + compressed.length);
  fullPlaintext.set(header, 0);
  fullPlaintext.set(compressed, HEADER_SIZE);

  const prefix = new Uint8Array(1 + NONCE_SIZE + SALT_SIZE);
  prefix[0] = PROTOCOL_VERSION;
  prefix.set(nonce, 1);
  prefix.set(salt, 1 + NONCE_SIZE);

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

/**
 * Decrypts data and handles decompression and multi-file unpacking.
 */
export async function decryptData(packed, passphrase) {
  const PROTO_VER = packed[0];
  if (PROTO_VER !== PROTOCOL_VERSION) throw new Error('Unsupported protocol version: ' + PROTO_VER);

  const PREFIX_LEN = 1 + NONCE_SIZE + SALT_SIZE;
  const MIN_LEN = PREFIX_LEN + HEADER_SIZE + TAG_LENGTH_BYTES;
  if (packed.length < MIN_LEN) throw new Error('Invalid ciphertext length');
  
  const nonce = packed.subarray(1, 1 + NONCE_SIZE);
  const headerMasterSalt = packed.subarray(1 + NONCE_SIZE, PREFIX_LEN);
  const prefix = packed.subarray(0, PREFIX_LEN);
  const enc = packed.subarray(PREFIX_LEN);
  
  const masterKey = await getMasterKey(passphrase, headerMasterSalt);
  const messageKey = await deriveMessageKey(masterKey, nonce); // Use nonce as msg salt
  
  const decryptedBuffer = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: nonce, tagLength: TAG_LENGTH_BITS, additionalData: prefix }, 
    messageKey, 
    enc
  );
  
  const fullDecrypted = new Uint8Array(decryptedBuffer);
  const dec = await decompressData(fullDecrypted.subarray(HEADER_SIZE));

  if (dec[0] === MULTI_FILE_MAGIC) {
    return { multiFile: true, files: unpackMultipleFiles(dec) };
  }

  const nameLen = dec[0];
  const filename = new TextDecoder().decode(dec.subarray(1, 1 + nameLen));
  const data = dec.subarray(1 + nameLen);
  return { multiFile: false, data, filename };
}

/**
 * Checks if a buffer likely contains encrypted data from this app.
 */
export function isEncryptedBuffer(buffer) {
  if (!buffer || buffer.length < (1 + NONCE_SIZE + SALT_SIZE + HEADER_SIZE + TAG_LENGTH_BYTES)) {
    return false;
  }
  return buffer[0] === PROTOCOL_VERSION;
}
