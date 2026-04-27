import { $ } from '../utils/dom.js';
import { formatSize, generateFilename, downloadBlob, detectSequence, revokeAllUrls } from '../utils/fileUtils.js';
import { encryptFiles, decryptFiles, cancelTask } from '../core/crypto.js';
import { isEncryptedBuffer } from '../core/crypto_ops.js';
export const fileInput = $('file-input');
export const fileInfo = $('file-info');

export const splitToggle = $('split-toggle');
export const splitControls = $('split-controls');
export const splitSlider = $('split-slider');
export const splitInput = $('split-input');
export const btnFileCompress = $('btn-file-compress');
export const btnFileExtract = $('btn-file-extract');
export const fileError = $('file-error');
export const fileResults = $('file-results');
export const btnFileClear = $('btn-file-clear');

function renderFileRow(name, size, icon, onClick) {
  const el = document.createElement(onClick ? 'button' : 'div');
  if (onClick) { el.type = 'button'; el.addEventListener('click', onClick); }
  el.className = 'download-btn';
  el.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18"><use href="#i-${icon}"/></svg><span class="dl-name">${name}</span><span class="dl-size">${formatSize(size)}</span>`;
  return el;
}

let isProcessingCompress = false;
let isProcessingExtract = false;
export function showFileError(msg) {
  fileError.textContent = msg;
  fileError.classList.remove('hidden');
  fileResults.classList.add('hidden');
  revokeAllUrls();
}

function hideLoading() {
  const loadingBtn = document.querySelector('.btn.loading');
  if (loadingBtn) {
    const originalText = loadingBtn.dataset.originalText;
    const span = loadingBtn.querySelector('span');
    if (span && originalText) span.textContent = originalText;
    loadingBtn.classList.remove('loading');
    const progress = loadingBtn.querySelector('.btn-progress');
    if (progress) progress.remove();
  }
}

function showLoading(btn, status) {
  hideLoading();
  const span = btn.querySelector('span');
  btn.dataset.originalText = span.textContent;
  span.textContent = status;
  btn.classList.add('loading');
  
  const progress = document.createElement('div');
  progress.className = 'btn-progress';
  btn.appendChild(progress);
}

function updateLoading(status) {
  const loadingBtn = document.querySelector('.btn.loading');
  if (loadingBtn) {
    const span = loadingBtn.querySelector('span');
    if (span) span.textContent = status;
  }
}

async function updateActionPriorities() {
  const files = fileInput.files;
  if (!files || files.length === 0) {
    btnFileCompress.classList.remove('primary-action', 'secondary-action');
    btnFileExtract.classList.remove('primary-action', 'secondary-action');
    return;
  }

  try {
    const firstFile = files[0];
    const buffer = await firstFile.slice(0, 100).arrayBuffer();
    const isEnc = isEncryptedBuffer(new Uint8Array(buffer));

    if (isEnc) {
      btnFileExtract.classList.add('primary-action');
      btnFileExtract.classList.remove('secondary-action');
      btnFileCompress.classList.add('secondary-action');
      btnFileCompress.classList.remove('primary-action');
    } else {
      btnFileCompress.classList.add('primary-action');
      btnFileCompress.classList.remove('secondary-action');
      btnFileExtract.classList.add('secondary-action');
      btnFileExtract.classList.remove('primary-action');
    }
  } catch (e) {
    console.error('Action priority check failed', e);
  }
}

export function hideFileError() {
  fileError.classList.add('hidden');
}

export function updateFileButtons() {
  const hasFiles = fileInput.files && fileInput.files.length > 0;
  const pwd = localStorage.getItem('cipher_pwd');
  btnFileCompress.disabled = !hasFiles || !pwd;
  btnFileExtract.disabled = !hasFiles || !pwd;
}

export function setupFilePanel() {
  fileInput.addEventListener('change', () => {
    hideFileError();
    fileResults.classList.add('hidden');
    const files = fileInput.files;
    if (!files || files.length === 0) {
      fileInfo.classList.add('hidden');
      if (btnFileClear) btnFileClear.classList.add('hidden');
      updateFileButtons();
      updateActionPriorities();
      return;
    }

    fileInfo.classList.remove('hidden');
    if (btnFileClear) {
      if (files.length > 1) {
        btnFileClear.classList.remove('hidden');
      } else {
        btnFileClear.classList.add('hidden');
      }
    }
    fileInfo.innerHTML = '';
    for (const f of files) {
      fileInfo.appendChild(renderFileRow(f.name, f.size, 'file', null));
    }
    const seq = detectSequence(Array.from(files));
    if (seq) {
      const note = document.createElement('div');
      note.className = 'file-split-detected';
      note.textContent = `✓ فایل‌های تقسیم‌شده شناسایی شدند (${seq.length} بخش)`;
      fileInfo.appendChild(note);
    }
    updateFileButtons();
    updateActionPriorities();
  });

  if (btnFileClear) {
    btnFileClear.addEventListener('click', () => {
      fileInput.value = '';
      const ev = new Event('change');
      fileInput.dispatchEvent(ev);
      fileResults.classList.add('hidden');
      fileResults.innerHTML = '';
      revokeAllUrls();
    });
  }

  // Restore split settings
  const savedSplit = localStorage.getItem('split_enabled') === 'true';
  const savedSize = parseInt(localStorage.getItem('split_size_mb') || '10', 10);
  splitToggle.checked = savedSplit;
  splitSlider.value = savedSize;
  splitInput.value = savedSize;
  if (savedSplit) splitControls.classList.remove('hidden');

  splitToggle.addEventListener('change', () => {
    if (splitToggle.checked) splitControls.classList.remove('hidden');
    else splitControls.classList.add('hidden');
    localStorage.setItem('split_enabled', splitToggle.checked);
  });

  splitSlider.addEventListener('input', () => {
    splitInput.value = splitSlider.value;
    localStorage.setItem('split_size_mb', splitSlider.value);
  });

  splitInput.addEventListener('input', () => {
    let v = Math.min(100, Math.max(1, parseInt(splitInput.value) || 1));
    splitSlider.value = v;
    localStorage.setItem('split_size_mb', v);
  });

  // ========== FILE COMPRESSION/EXTRACTION ==========

  btnFileCompress.addEventListener('click', async () => {
    if (isProcessingCompress) {
      cancelTask();
      return;
    }
    hideFileError();
    fileResults.classList.add('hidden');
    revokeAllUrls();
    const files = Array.from(fileInput.files || []);
    if (files.length === 0) { showFileError('لطفاً فایلی انتخاب کنید.'); return; }
    const pwd = localStorage.getItem('cipher_pwd');
    if (!pwd) { showFileError('لطفاً ابتدا کلید اختصاصی را تنظیم کنید.'); return; }

    isProcessingCompress = true;
    showLoading(btnFileCompress, 'در حال آماده‌سازی...');
    btnFileExtract.disabled = true;

    try {
      const splitEnabled = splitToggle.checked;
      const splitSizeMB = parseInt(splitInput.value) || 10;
      const splitSizeBytes = splitSizeMB * 1024 * 1024;
      const parts = await encryptFiles(files, pwd, splitEnabled, splitSizeBytes, (status) => updateLoading(status));

      fileResults.innerHTML = '';
      const label = document.createElement('label');
      label.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18"><use href="#i-extract"/></svg> نتیجه — ${parts.length === 1 ? '۱ فایل' : parts.length + ' بخش'}`;
      fileResults.appendChild(label);

      for (let i = 0; i < parts.length; i++) {
        const filename = generateFilename(i + 1, parts.length);
        fileResults.appendChild(renderFileRow(filename, parts[i].length, 'extract', async () => await downloadBlob(parts[i], filename)));
      }

      fileResults.classList.remove('hidden');
      fileResults.scrollIntoView({ behavior: 'smooth' });

      // Automatic download removed as per user request to follow best practices and avoid browser blocking
    } catch (err) {
      showFileError('خطا در پردازش فایل: ' + (err.message || err));
    } finally {
      isProcessingCompress = false;
      hideLoading();
      btnFileExtract.disabled = false;
      updateFileButtons();
    }
  });

  btnFileExtract.addEventListener('click', async () => {
    if (isProcessingExtract) {
      cancelTask();
      return;
    }
    hideFileError();
    fileResults.classList.add('hidden');
    revokeAllUrls();
    let files = Array.from(fileInput.files || []);
    if (files.length === 0) { showFileError('لطفاً فایلی انتخاب کنید.'); return; }
    const pwd = localStorage.getItem('cipher_pwd');
    if (!pwd) { showFileError('لطفاً ابتدا کلید اختصاصی را تنظیم کنید.'); return; }

    isProcessingExtract = true;
    showLoading(btnFileExtract, 'در حال آماده‌سازی...');
    btnFileCompress.disabled = true;

    try {
      if (files.length > 1) {
        const seq = detectSequence(files);
        if (!seq) {
          showFileError('فایل‌های انتخابی دارای شماره‌گذاری صحیح نیستند. لطفاً بخش‌های پشت سر هم را انتخاب کنید.');
          return;
        }
        files = seq.map(s => s.file);
      }

      const res = await decryptFiles(files, pwd, (status) => updateLoading(status));

      fileResults.innerHTML = '';
      const label = document.createElement('label');

      if (res.multiFile) {
        const fileList = res.files;
        label.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18"><use href="#i-extract"/></svg> نتیجه — ${fileList.length} فایل`;
        fileResults.appendChild(label);

        for (const f of fileList) {
          fileResults.appendChild(renderFileRow(f.filename, f.data.length, 'extract', async () => await downloadBlob(f.data, f.filename)));
        }
      } else {
        const filename = res.filename || 'extracted_file';
        label.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18"><use href="#i-extract"/></svg> نتیجه`;
        fileResults.appendChild(label);
        fileResults.appendChild(renderFileRow(filename, res.data.length, 'extract', async () => await downloadBlob(res.data, filename)));
      }

      fileResults.classList.remove('hidden');
      fileResults.scrollIntoView({ behavior: 'smooth' });

    } catch (err) {
      showFileError(err.message === 'عملیات لغو شد' ? err.message : 'خطا در استخراج فایل. لطفاً کلید و فایل ورودی را بررسی کنید.');
    } finally {
      isProcessingExtract = false;
      hideLoading();
      btnFileCompress.disabled = false;
      updateFileButtons();
    }
  });
}
