import { $ } from '../utils/dom.js';

export function setupTabs() {
  const tabText = $('tab-text');
  const tabFile = $('tab-file');
  const panelText = $('panel-text');
  const panelFile = $('panel-file');

  tabText.addEventListener('click', () => {
    tabText.classList.add('active');
    tabFile.classList.remove('active');
    panelText.classList.remove('hidden');
    panelFile.classList.add('hidden');
  });

  tabFile.addEventListener('click', () => {
    tabFile.classList.add('active');
    tabText.classList.remove('active');
    panelFile.classList.remove('hidden');
    panelText.classList.add('hidden');
  });
}
