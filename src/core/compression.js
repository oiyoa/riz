import { inflateSync, deflateSync } from 'fflate';

/**
 * Compresses data using native CompressionStream or fflate fallback.
 */
export async function compressData(bytes) {
  try {
    if (typeof CompressionStream !== 'undefined') {
      const s = new Blob([bytes]).stream().pipeThrough(new CompressionStream('deflate-raw'));
      return new Uint8Array(await new Response(s).arrayBuffer());
    }
  } catch (err) {
    console.warn('Native CompressionStream failed, falling back to fflate:', err);
  }
  return deflateSync(bytes, { level: 6 });
}

/**
 * Decompresses data using native DecompressionStream or fflate fallback.
 */
export async function decompressData(bytes) {
  try {
    if (typeof DecompressionStream !== 'undefined') {
      const s = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
      return new Uint8Array(await new Response(s).arrayBuffer());
    }
  } catch (err) {
    console.warn('Native DecompressionStream failed, falling back to fflate:', err);
  }

  try {
    return inflateSync(bytes);
  } catch (err) {
    console.error('fflate decompression failed:', err);
    return bytes;
  }
}
