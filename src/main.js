import { setupTabs } from './ui/tabs.js';
import { setupPasswordUI, updatePasswordUI } from './ui/password.js';
import { setupFilePanel } from './ui/filePanel.js';
import { setupMessagePanel, performEncrypt, performDecrypt, btnPasteLarge, btnCopy } from './ui/messagePanel.js';

let pendingAction = null;

// ========== INIT ==========

document.addEventListener('DOMContentLoaded', () => {
  setupTabs();
  setupPasswordUI(() => pendingAction, a => pendingAction = a, performEncrypt, performDecrypt);
  setupMessagePanel(a => pendingAction = a);
  setupFilePanel();

  const protocol = window.location.protocol;
  if (protocol !== 'http:' && protocol !== 'https:') {
    if (btnPasteLarge) btnPasteLarge.classList.add('hidden');
    if (btnCopy) btnCopy.classList.add('hidden');
  }

  updatePasswordUI();
});
