export function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' بایت';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' کیلوبایت';
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' مگابایت';
  return (bytes / 1073741824).toFixed(2) + ' گیگابایت';
}

export function generateFilename(partNum, totalParts) {
  const now = new Date();
  const pad = (n, l = 2) => String(n).padStart(l, '0');
  const dt = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  if (totalParts <= 1) return `readme_${dt}.txt`;
  return `readme_${dt}_${pad(partNum, 3)}.txt`;
}

// Keeps track of active object URLs to prevent memory leaks
let activeUrls = new Set();

export function revokeAllUrls() {
  activeUrls.forEach(url => URL.revokeObjectURL(url));
  activeUrls.clear();
}

export async function downloadBlob(bytes, filename) {
  const canShare = !!navigator.share;
  const isMobile = /Android|iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

  if (canShare && isMobile) {
    try {
      const file = new File([bytes], filename, { type: 'application/octet-stream' });
      const shareData = { files: [file], title: filename, text: `فایل: ${filename}` };
      if (navigator.canShare && navigator.canShare(shareData)) {
        await navigator.share(shareData);
        return;
      }
    } catch (e) {
      if (e.name !== 'AbortError') console.error('Share failed:', e);
      else return;
    }
  }

  // Fallback: Use persistent URL management instead of arbitrary setTimeout
  const blob = new Blob([bytes], { type: 'application/octet-stream' });
  const url = URL.createObjectURL(blob);
  activeUrls.add(url);

  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();

  // We remove the element, but keep the URL alive until revokeAllUrls() is called
  // This ensures the download has time to complete regardless of file size/speed.
  document.body.removeChild(a);
}

export function detectSequence(files) {
  if (files.length <= 1) return null;
  const regex = /_(\d{3})\.[^.]+$/;
  const items = [];
  for (const f of files) {
    const m = f.name.match(regex);
    if (!m) return null;
    items.push({ index: parseInt(m[1], 10), file: f });
  }
  items.sort((a, b) => a.index - b.index);
  for (let i = 0; i < items.length; i++) {
    if (items[i].index !== i + 1) return null;
  }
  return items;
}
