import CryptoWorker from './crypto.worker.js?worker&inline';

let worker = null;
let currentResolve = null;
let currentReject = null;
let currentTaskId = 0;

function getWorker() {
  if (!worker) {
    worker = new CryptoWorker();
    worker.onmessage = (e) => {
      const { id, result, error, progress, status } = e.data;
      if (id !== currentTaskId) return;
      
      if (progress) {
        if (currentOnProgress) currentOnProgress(status);
        return;
      }

      if (currentResolve) {
        if (error) currentReject(new Error(error));
        else currentResolve(result);
      }
      
      currentResolve = null;
      currentReject = null;
      currentOnProgress = null;
    };
    worker.onerror = (err) => {
      if (currentReject) currentReject(new Error(err.message || 'خطای داخلی در پردازش'));
      currentResolve = null;
      currentReject = null;
    };
  }
  return worker;
}

let currentOnProgress = null;

function runTask(type, payload, transfer = [], onProgress = null) {
  return new Promise((resolve, reject) => {
    // Cancel any previous task
    if (currentResolve) {
      cancelTask();
    }

    const id = ++currentTaskId;
    currentResolve = resolve;
    currentReject = reject;
    currentOnProgress = onProgress;
    
    getWorker().postMessage({ id, type, payload }, transfer);
  });
}

export function cancelTask() {
  if (worker) {
    worker.terminate();
    worker = null;
  }
  if (currentReject) {
    currentReject(new Error('عملیات لغو شد'));
  }
  currentResolve = null;
  currentReject = null;
  currentOnProgress = null;
}

export async function encrypt(text, pwd, onProgress = null) {
  return await runTask('encrypt_text', { text, pwd }, [], onProgress);
}

export async function decrypt(text, pwd, onProgress = null) {
  return await runTask('decrypt_text', { text, pwd }, [], onProgress);
}

export async function encryptFiles(files, pwd, splitEnabled, splitSizeBytes, onProgress = null) {
  const filesData = await Promise.all(Array.from(files).map(async f => ({
    name: f.name,
    buffer: await f.arrayBuffer()
  })));
  
  return await runTask('encrypt_files', { 
    filesData, 
    pwd, 
    splitEnabled, 
    splitSizeBytes 
  }, filesData.map(f => f.buffer), onProgress);
}

export async function decryptFiles(files, pwd, onProgress = null) {
  const buffers = await Promise.all(Array.from(files).map(f => f.arrayBuffer()));
  return await runTask('decrypt_files', { buffers, pwd }, buffers, onProgress);
}
