import { $ } from '../utils/dom.js';
import { textInput, btnEncrypt, btnDecrypt, btnPasteLarge, btnClear, outputContainer, hideError } from './messagePanel.js';
import { updateFileButtons } from './filePanel.js';
import { assessStrength, generatePassphrase, MIN_SCORE } from '../core/passwordPolicy.js';

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

const passwordSupporting = $('password-supporting');
const btnGeneratePassword = $('btn-generate-password');
const passwordDeleteRow = $('password-delete-row');
const btnDeletePassword = $('btn-delete-password');

const STRENGTH_LABELS = ['بسیار ضعیف', 'ضعیف', 'متوسط', 'قوی', 'بسیار قوی'];
const WEAK_ERROR = 'به‌سادگی قابل حدس است. عبارت پیشنهادی را امتحان کنید یا رمز طولانی‌تری بسازید.';

// The saved password at the moment we opened the sheet. If the user opens
// settings and saves without editing, no strength gate fires.
let baselinePassword = '';
let showWeakError = false;

export function updatePasswordUI() {
  const pwd = localStorage.getItem('cipher_pwd');
  if (pwd) {
    passwordStatusBanner.classList.add('hidden');
    passwordInlineBox.classList.add('hidden');
    btnSettingsText.classList.remove('hidden');
    btnSettingsFile.classList.remove('hidden');

    textInput.placeholder = 'متن خود را بنویسید یا متن فشرده‌شده را بچسبانید...';
    textInput.disabled = false;
    btnEncrypt.disabled = false;
    btnDecrypt.disabled = false;

    btnPasteLarge.disabled = false;
    if (!textInput.value.trim()) btnClear.disabled = true;

    $('file-pick-section').classList.remove('hidden');
    $('split-section').classList.remove('hidden');
  } else {
    passwordStatusBanner.classList.remove('hidden');
    passwordInlineBox.classList.add('hidden');
    btnSettingsText.classList.add('hidden');
    btnSettingsFile.classList.add('hidden');

    textInput.placeholder = 'ابتدا کلید را تنظیم کنید...';
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

/**
 * Repaints the single supporting-text slot under the password input.
 * Priority: weak-password error > strength meter > nothing.
 */
function renderSupporting() {
  if (!passwordSupporting) return;
  const pwd = passwordInputBox.value;

  if (showWeakError) {
    passwordSupporting.innerHTML = '';
    passwordSupporting.classList.add('is-error');
    passwordSupporting.textContent = WEAK_ERROR;
    return;
  }

  passwordSupporting.classList.remove('is-error');

  if (!pwd) {
    passwordSupporting.innerHTML = '';
    return;
  }

  const sample = pwd.length > 64 ? pwd.slice(0, 64) : pwd;
  const score = assessStrength(sample).score;
  const label = STRENGTH_LABELS[score] || '';

  // Build the compact meter inline. No innerHTML interpolation of user data —
  // the only text is our own localised label.
  passwordSupporting.innerHTML = '';
  const row = document.createElement('div');
  row.className = 'strength-row';
  row.dataset.score = String(score);
  row.setAttribute('role', 'meter');
  row.setAttribute('aria-valuemin', '0');
  row.setAttribute('aria-valuemax', '4');
  row.setAttribute('aria-valuenow', String(score));
  const meter = document.createElement('div');
  meter.className = 'strength-meter';
  for (let i = 0; i < 4; i++) {
    meter.appendChild(document.createElement('span')).className = 'strength-meter-seg';
  }
  row.appendChild(meter);
  const labelEl = document.createElement('span');
  labelEl.className = 'strength-label';
  labelEl.textContent = label;
  row.appendChild(labelEl);
  passwordSupporting.appendChild(row);
}

export function showPasswordBox(isSettings, pendingAction, setPendingAction) {
  if (isSettings) {
    passwordBoxDesc.textContent = 'کلید فعلی خود را مشاهده یا تغییر دهید.';
    passwordInputBox.value = localStorage.getItem('cipher_pwd') || '';
    baselinePassword = passwordInputBox.value;
    setPendingAction('settings');
    // Delete is only meaningful when a saved password exists.
    passwordDeleteRow?.classList.toggle('hidden', !baselinePassword);
  } else {
    passwordBoxDesc.textContent = 'برای ادامه، لطفاً کلید را وارد کنید.';
    passwordInputBox.value = '';
    baselinePassword = '';
    passwordStatusBanner.classList.add('hidden');
    passwordDeleteRow?.classList.add('hidden');
  }
  showWeakError = false;
  btnSavePassword.disabled = !passwordInputBox.value;
  renderSupporting();
  passwordInlineBox.classList.remove('hidden');
  passwordInputBox.focus();
}

export function hidePasswordBox(setPendingAction) {
  passwordInlineBox.classList.add('hidden');
  passwordInputBox.type = 'password';
  btnTogglePassword.querySelector('use').setAttribute('href', '#i-eye');
  showWeakError = false;
  if (passwordSupporting) {
    passwordSupporting.innerHTML = '';
    passwordSupporting.classList.remove('is-error');
  }
  passwordDeleteRow?.classList.add('hidden');
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

  const updateSaveEnabled = () => {
    // Empty field never commits — deletion is its own explicit button.
    btnSavePassword.disabled = !passwordInputBox.value;
  };

  btnSavePassword.addEventListener('click', async () => {
    const pwd = passwordInputBox.value;
    const action = getPendingAction();

    if (!pwd) {
      // Defensive: the button is disabled when empty, but if the click
      // somehow lands (assistive tech, race), do nothing rather than delete.
      passwordInputBox.focus();
      return;
    }

    // Strength gate only applies when the user actually changed the value.
    const isUnchangedExisting = action === 'settings' && pwd === baselinePassword && baselinePassword !== '';
    if (!isUnchangedExisting) {
      const score = assessStrength(pwd.length > 64 ? pwd.slice(0, 64) : pwd).score;
      if (score < MIN_SCORE) {
        showWeakError = true;
        renderSupporting();
        passwordInputBox.focus();
        return;
      }
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

  passwordInputBox.addEventListener('input', () => {
    showWeakError = false;
    renderSupporting();
    updateSaveEnabled();
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

  btnDeletePassword?.addEventListener('click', () => {
    // Only path to deletion. Save with empty field is disabled, so users
    // can't trigger this by accident from the primary commit flow.
    localStorage.removeItem('cipher_pwd');
    hidePasswordBox(setPendingAction);
  });

  btnGeneratePassword?.addEventListener('click', () => {
    const phrase = generatePassphrase();
    passwordInputBox.value = phrase;
    // Reveal so the user can read what they're committing to.
    passwordInputBox.type = 'text';
    btnTogglePassword.querySelector('use').setAttribute('href', '#i-eye-off');
    showWeakError = false;
    renderSupporting();
    updateSaveEnabled();
  });
}
