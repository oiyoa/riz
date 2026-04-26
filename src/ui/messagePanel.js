import { $, copyOutputText, isBase64Url } from '../utils/dom.js';
import { encrypt, decrypt, cancelTask } from '../core/crypto.js';
import { isEncryptedBuffer } from '../core/crypto_ops.js';
import { Base64Url } from '../core/base64url.js';

export const textInput = $('input-text');
export const btnEncrypt = $('btn-encrypt');
export const btnDecrypt = $('btn-decrypt');
export const outputContainer = $('output-container');
export const outputText = $('output-text');
export const btnCopy = $('btn-copy');
export const errorMsg = $('error-msg');
export const btnPasteLarge = $('btn-paste-large');
export const btnClear = $('btn-clear');

let isProcessingEncrypt = false;
let isProcessingDecrypt = false;

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

export function updateActionPriorities() {
  const text = textInput.value.trim();
  if (!text) {
    btnEncrypt.classList.remove('primary-action', 'secondary-action');
    btnDecrypt.classList.remove('primary-action', 'secondary-action');
    return;
  }

  try {
    // Basic check for our format
    if (text.length > 20 && !text.includes(' ') && /^[a-zA-Z0-9-_]+$/.test(text)) {
      const decoded = Base64Url.decode(text);
      if (isEncryptedBuffer(decoded)) {
        btnDecrypt.classList.add('primary-action');
        btnDecrypt.classList.remove('secondary-action');
        btnEncrypt.classList.add('secondary-action');
        btnEncrypt.classList.remove('primary-action');
        return;
      }
    }
  } catch (e) {}

  btnEncrypt.classList.add('primary-action');
  btnEncrypt.classList.remove('secondary-action');
  btnDecrypt.classList.add('secondary-action');
  btnDecrypt.classList.remove('primary-action');
}

export function showError(msg) {
  errorMsg.textContent = msg;
  errorMsg.classList.remove('hidden');
  outputContainer.classList.add('hidden');
}

export function hideError() { 
  errorMsg.classList.add('hidden'); 
}

export async function performEncrypt(text, pwd) {
  if (isProcessingEncrypt) {
    cancelTask();
    return;
  }
  
  try {
    isProcessingEncrypt = true;
    showLoading(btnEncrypt, 'در حال فشرده‌سازی...');
    btnDecrypt.disabled = true;

    const result = await encrypt(text, pwd, (status) => updateLoading(status));
    outputText.value = result;
    outputText.classList.remove('success-flash');
    void outputText.offsetWidth;
    outputText.classList.add('success-flash');
    outputContainer.classList.remove('hidden');
    copyOutputText(outputText, btnCopy);
    outputContainer.scrollIntoView({ behavior: 'smooth' });
  } catch (err) { showError(err.message); }
  finally {
    isProcessingEncrypt = false;
    hideLoading();
    btnDecrypt.disabled = false;
  }
}

export async function performDecrypt(text, pwd) {
  if (isProcessingDecrypt) {
    cancelTask();
    return;
  }
  
  try {
    isProcessingDecrypt = true;
    showLoading(btnDecrypt, 'در حال استخراج...');
    btnEncrypt.disabled = true;

    const result = await decrypt(text, pwd, (status) => updateLoading(status));
    outputText.value = result;
    outputText.classList.remove('success-flash');
    void outputText.offsetWidth;
    outputText.classList.add('success-flash');
    outputContainer.classList.remove('hidden');
    outputContainer.scrollIntoView({ behavior: 'smooth' });
  } catch (err) { 
    showError(err.message === 'عملیات لغو شد' ? err.message : "استخراج متن ناموفق بود. لطفاً کلید و متن ورودی را بررسی کنید."); 
  }
  finally {
    isProcessingDecrypt = false;
    hideLoading();
    btnEncrypt.disabled = false;
  }
}

export function setupMessagePanel(setPendingAction) {
  btnEncrypt.addEventListener('click', async () => {
    if (isProcessingEncrypt) {
      cancelTask();
      return;
    }
    hideError();
    const text = textInput.value;
    if (!text) { showError("لطفاً متن را وارد کنید."); return; }
    const pwd = localStorage.getItem('cipher_pwd');
    if (!pwd) { 
      setPendingAction('encrypt');
      import('./password.js').then(m => m.showPasswordBox(false, 'encrypt', setPendingAction));
      return; 
    }
    await performEncrypt(text, pwd);
  });

  btnDecrypt.addEventListener('click', async () => {
    if (isProcessingDecrypt) {
      cancelTask();
      return;
    }
    hideError();
    const text = textInput.value.trim();
    if (!text) { showError("لطفاً متن فشرده‌شده را وارد کنید."); return; }
    const pwd = localStorage.getItem('cipher_pwd');
    if (!pwd) { 
      setPendingAction('decrypt');
      import('./password.js').then(m => m.showPasswordBox(false, 'decrypt', setPendingAction));
      return; 
    }
    await performDecrypt(text, pwd);
  });

  textInput.addEventListener('input', () => { 
    btnClear.disabled = !textInput.value.trim(); 
    updateMessageButtons();
    updateActionPriorities();
  });
  btnCopy.addEventListener('click', () => copyOutputText(outputText, btnCopy));

  btnPasteLarge.addEventListener('click', async () => {
    try {
      const text = await navigator.clipboard.readText();
      textInput.value = text;
      textInput.dispatchEvent(new Event('input'));
      if (isBase64Url(text)) btnDecrypt.click();
    } catch { btnPasteLarge.classList.add('hidden'); }
  });

  btnClear.addEventListener('click', () => {
    textInput.value = '';
    outputText.value = '';
    outputContainer.classList.add('hidden');
    hideError();
    textInput.dispatchEvent(new Event('input'));
    textInput.focus();
  });

  outputText.addEventListener('focus', () => {
    outputText.select();
    outputText.setSelectionRange(0, 99999);
  });
}
