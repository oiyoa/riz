import { $ } from '../utils/dom.js';
import { textInput, btnEncrypt, btnDecrypt, btnPasteLarge, btnClear, outputContainer, hideError } from './messagePanel.js';
import { updateFileButtons } from './filePanel.js';


export const passwordStatusBanner = $('password-status-banner');
export const btnSetupPassword = $('btn-setup-password');
export const passwordInlineBox = $('password-inline-box');
export const passwordInputBox = $('password-input-box');
export const btnSavePassword = $('btn-save-password');
export const btnCancelPassword = $('btn-cancel-password');
export const passwordBoxDesc = $('password-box-desc');
export const btnSettingsText = $('btn-settings-text');
export const btnSettingsFile = $('btn-settings-file');
export const btnTogglePassword = $('btn-toggle-password');


export function updatePasswordUI() {
  const pwd = localStorage.getItem('cipher_pwd');
  if (pwd) {
    passwordStatusBanner.classList.add('hidden');
    passwordInlineBox.classList.add('hidden');
    btnSettingsText.classList.remove('hidden');
    btnSettingsFile.classList.remove('hidden');
    
    textInput.placeholder = "متن خود را بنویسید یا متن فشرده‌شده را بچسبانید...";
    textInput.disabled = false;
    btnEncrypt.disabled = false;
    btnDecrypt.disabled = false;
    
    // Background key derivation is no longer used with per-operation salts
    btnPasteLarge.disabled = false;
    if (!textInput.value.trim()) btnClear.disabled = true;
    
    $('file-pick-section').classList.remove('hidden');
    $('split-section').classList.remove('hidden');
  } else {
    passwordStatusBanner.classList.remove('hidden');
    passwordInlineBox.classList.add('hidden');
    btnSettingsText.classList.add('hidden');
    btnSettingsFile.classList.add('hidden');
    
    textInput.placeholder = "ابتدا کلید را تنظیم کنید...";
    textInput.disabled = true;
    textInput.value = '';
    btnEncrypt.disabled = true;
    btnDecrypt.disabled = true;
    btnPasteLarge.disabled = true;
    btnClear.disabled = true;
    outputContainer.classList.add('hidden');
    hideError();
    
    $('file-pick-section').classList.add('hidden');
    $('split-section').classList.add('hidden');
  }
  updateFileButtons();
}

export function showPasswordBox(isSettings, pendingAction, setPendingAction) {
  if (isSettings) {
    passwordBoxDesc.textContent = "کلید فعلی خود را تغییر دهید.";
    passwordInputBox.value = localStorage.getItem('cipher_pwd') || '';
    setPendingAction('settings');
  } else {
    passwordBoxDesc.textContent = "برای ادامه، لطفاً کلید را وارد کنید.";
    passwordInputBox.value = '';
    passwordStatusBanner.classList.add('hidden');
  }
  passwordInlineBox.classList.remove('hidden');
  passwordInputBox.focus();
}

export function hidePasswordBox(setPendingAction) {
  passwordInlineBox.classList.add('hidden');
  passwordInputBox.type = 'password';
  btnTogglePassword.querySelector('use').setAttribute('href', '#i-eye');
  setPendingAction(null);
  updatePasswordUI();
}

export function setupPasswordUI(getPendingAction, setPendingAction, performEncrypt, performDecrypt) {
  const showSettings = () => showPasswordBox(true, getPendingAction(), setPendingAction);
  btnSettingsText.addEventListener('click', showSettings);
  btnSettingsFile.addEventListener('click', showSettings);
  btnSetupPassword.addEventListener('click', () => showPasswordBox(false, getPendingAction(), setPendingAction));
  
  btnCancelPassword.addEventListener('click', () => {
    hidePasswordBox(setPendingAction);
  });
 
  btnSavePassword.addEventListener('click', async () => {
    const pwd = passwordInputBox.value;
    const action = getPendingAction();
    if (!pwd) {
      if (action === 'settings') { 
        localStorage.removeItem('cipher_pwd'); 
        hidePasswordBox(setPendingAction); 
      }
      else passwordInputBox.focus();
      return;
    }
    const oldPwd = localStorage.getItem('cipher_pwd');
    if (pwd !== oldPwd) {
      localStorage.setItem('cipher_pwd', pwd);
    }
    hidePasswordBox(setPendingAction);
    
    const text = textInput.value.trim();
    if (action === 'encrypt' && text) await performEncrypt(text, pwd);
    else if (action === 'decrypt' && text) await performDecrypt(text, pwd);
  });

  passwordInputBox.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') btnSavePassword.click();
    else if (e.key === 'Escape') btnCancelPassword.click();
  });

  btnTogglePassword.addEventListener('click', () => {
    const isPwd = passwordInputBox.type === 'password';
    passwordInputBox.type = isPwd ? 'text' : 'password';
    btnTogglePassword.querySelector('use').setAttribute('href', isPwd ? '#i-eye-off' : '#i-eye');
  });
}
