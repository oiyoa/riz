export const SALT_SIZE = 16;
export const ITERATIONS = 600000;
export const TAG_LENGTH_BYTES = 16;
export const TAG_LENGTH_BITS = 128;
export const NONCE_SIZE = 12;

export const PROTOCOL_VERSION = 0x01;
export const HEADER_SIZE = 1; // Only FLAGS(1) is needed in the encrypted header now
export const HKDF_INFO = new TextEncoder().encode("riz/v1/message-key");
export const MULTI_FILE_MAGIC = 0xFF;
