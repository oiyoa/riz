import { ITERATIONS, HKDF_INFO } from './constants';

let cachedMasterKey = null;
let cachedPassphrase = null;
let cachedSalt = null;
let pendingMasterKeyPromise = null;

/**
 * Derives or retrieves the cached master key from passphrase and salt.
 */
export async function getMasterKey(passphrase, salt) {
  if (cachedPassphrase === passphrase && cachedSalt && salt.every((v, i) => v === cachedSalt[i])) {
    return cachedMasterKey;
  }

  if (pendingMasterKeyPromise) return pendingMasterKeyPromise;

  pendingMasterKeyPromise = (async () => {
    try {
      const enc = new TextEncoder();
      const pk = await crypto.subtle.importKey('raw', enc.encode(passphrase), 'PBKDF2', false, ['deriveBits']);
      const rawMaster = await crypto.subtle.deriveBits(
        { name: 'PBKDF2', salt, iterations: ITERATIONS, hash: 'SHA-256' }, pk, 256
      );
      
      const masterKey = await crypto.subtle.importKey('raw', rawMaster, 'HKDF', false, ['deriveKey']);
      
      cachedMasterKey = masterKey;
      cachedPassphrase = passphrase;
      cachedSalt = salt;
      
      return masterKey;
    } finally {
      pendingMasterKeyPromise = null;
    }
  })();

  return pendingMasterKeyPromise;
}

/**
 * Derives a message-specific key from the master key.
 */
export async function deriveMessageKey(masterKey, messageSalt) {
  return await crypto.subtle.deriveKey(
    { name: 'HKDF', salt: messageSalt, info: HKDF_INFO, hash: 'SHA-256' },
    masterKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

