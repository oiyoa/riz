export const SALT_SIZE = 16;
export const ITERATIONS = 600000;
export const TAG_LENGTH_BYTES = 16;
export const TAG_LENGTH_BITS = 128;
export const NONCE_SIZE = 12;

// v3 wire format; matches Android byte-for-byte.
export const PROTOCOL_VERSION = 0x03;
export const PREFIX_SIZE = NONCE_SIZE + SALT_SIZE;
export const TIMESTAMP_SIZE = 8;
export const INNER_HEADER_SIZE = 1 + 1 + TIMESTAMP_SIZE;

// Legacy web-only v1; kept readable so existing user blobs still decrypt.
export const LEGACY_PROTOCOL_VERSION = 0x01;
export const LEGACY_PREFIX_SIZE = 1 + NONCE_SIZE + SALT_SIZE;
export const LEGACY_INNER_HEADER_SIZE = 1;

export const HKDF_INFO = new TextEncoder().encode("riz/v1/message-key");
export const MULTI_FILE_MAGIC = 0xFF;

export const FLAG_COMPRESSED = 0x01;
export const FLAG_MULTI_FILE = 0x02;
