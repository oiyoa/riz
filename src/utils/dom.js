export const $ = id => document.getElementById(id);

export function copyOutputText(outputText, btnCopy) {
  if (!outputText.value) return;
  outputText.select();
  outputText.setSelectionRange(0, 99999);
  const showFeedback = (ok) => {
    if (ok) {
      btnCopy.classList.add('copied');
      btnCopy.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20"><use href="#i-check"/></svg>';
      setTimeout(() => {
        btnCopy.classList.remove('copied');
        btnCopy.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20"><use href="#i-copy"/></svg>';
      }, 2000);
    } else {
      btnCopy.classList.add('hidden');
    }
  };
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(outputText.value).then(() => showFeedback(true)).catch(() => showFeedback(false));
  } else {
    try { showFeedback(document.execCommand('copy')); } catch { showFeedback(false); }
  }
}

export function isBase64Url(str) {
  const s = str.trim();
  return s.length >= 20 && !/\s/.test(s) && /^[a-zA-Z0-9-_]+$/.test(s);
}
