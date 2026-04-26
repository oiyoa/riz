import { 
  SALT_SIZE, NONCE_SIZE, TAG_LENGTH_BITS, 
  HEADER_SIZE
} from './constants';
import { compressData } from './compression';
import { getMasterKey, deriveMessageKey } from './kdf';
import { packMultipleFiles } from './packing';
import { encryptData, decryptData } from './crypto_ops';
import { Base64Url } from './base64url';

self.onmessage = async (e) => {
  try {
    const { id, type, payload } = e.data;
    let result, transfer = [];

    const reportProgress = (status) => {
      self.postMessage({ id, progress: true, status });
    };

    switch (type) {
      case 'encrypt_text': {
        const { text, pwd } = payload;
        const data = new TextEncoder().encode(text);
        const encBytes = await encryptData(data, pwd, "");
        result = Base64Url.encode(encBytes);
        break;
      }

      case 'decrypt_text': {
        const { text, pwd } = payload;
        const packed = Base64Url.decode(text);
        const res = await decryptData(packed, pwd);
        result = new TextDecoder().decode(res.data);
        break;
      }

      case 'encrypt_files': {
        const { filesData, pwd, splitEnabled, splitSizeBytes } = payload;
        reportProgress('در حال آماده‌سازی...');
        let encrypted;

        if (filesData.length === 1) {
          const f = filesData[0];
          encrypted = await encryptData(new Uint8Array(f.buffer), pwd, f.name);
        } else {
          const packed = packMultipleFiles(filesData.map(f => ({ name: f.name, data: new Uint8Array(f.buffer) })));
          reportProgress('در حال فشرده‌سازی...');
          // encryptData handles compression internally
          encrypted = await encryptData(packed, pwd, "", 0x03); // 0x03 = Compressed | Multi-file
        }

        reportProgress('در حال ذخیره‌سازی...');

        let parts = [];
        if (splitEnabled && encrypted.length > splitSizeBytes) {
          for (let i = 0; i < encrypted.length; i += splitSizeBytes) {
            parts.push(encrypted.subarray(i, i + splitSizeBytes));
          }
        } else {
          parts = [encrypted];
        }
        
        result = parts;
        transfer = parts.map(p => p.buffer);
        break;
      }

      case 'decrypt_files': {
        const { buffers, pwd } = payload;
        reportProgress('در حال آماده‌سازی...');
        let total = buffers.reduce((acc, b) => acc + b.byteLength, 0);
        const combined = new Uint8Array(total);
        let offset = 0;
        for (const b of buffers) {
          combined.set(new Uint8Array(b), offset);
          offset += b.byteLength;
        }

        reportProgress('در حال استخراج...');
        const res = await decryptData(combined, pwd);
        result = res;
        if (res.multiFile) {
          transfer = res.files.map(f => f.data.buffer);
        } else {
          transfer = [res.data.buffer];
        }
        break;
      }


      default:
        throw new Error(`Unknown message type: ${type}`);
    }
  
    self.postMessage({ id, result }, transfer);
  } catch (err) {
    self.postMessage({ id: e.data.id, error: err.message });
  }
};
