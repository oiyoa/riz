/**
 * Password policy: strength scoring + EFF Large Wordlist passphrase generator.
 */
import { zxcvbn, zxcvbnOptions } from '@zxcvbn-ts/core';
import * as zxcvbnCommonPackage from '@zxcvbn-ts/language-common';
import { EFF_LARGE_WORDLIST } from './wordlist.js';

zxcvbnOptions.setOptions({
  graphs: zxcvbnCommonPackage.adjacencyGraphs,
  dictionary: { ...zxcvbnCommonPackage.dictionary },
});

const DICEWARE = EFF_LARGE_WORDLIST;

export const MIN_SCORE = 3;
export const DEFAULT_PASSPHRASE_WORDS = 6;
const SEPARATOR = '-';

/**
 * Returns { score (0..4), warning, suggestions, crackTimesDisplay, guessesLog10 }.
 */
export function assessStrength(pwd) {
  const result = zxcvbn(pwd || '');
  return {
    score: result.score,
    warning: result.feedback?.warning || '',
    suggestions: result.feedback?.suggestions || [],
    crackTimesDisplay: result.crackTimesDisplay,
    guessesLog10: result.guessesLog10,
  };
}

/**
 * Generates a passphrase using uniform rejection sampling over the EFF list.
 * Each draw uses 32 bits from crypto.getRandomValues; values outside the
 * largest multiple of N are rejected to avoid modulo bias.
 */
export function generatePassphrase(numWords = DEFAULT_PASSPHRASE_WORDS) {
  if (!DICEWARE || DICEWARE.length < 7776) {
    throw new Error('Diceware wordlist unavailable');
  }
  const N = DICEWARE.length;
  const limit = Math.floor(0x100000000 / N) * N;
  const buf = new Uint32Array(1);
  const out = new Array(numWords);
  for (let i = 0; i < numWords; i++) {
    let r;
    do {
      crypto.getRandomValues(buf);
      r = buf[0];
    } while (r >= limit);
    out[i] = DICEWARE[r % N];
  }
  return out.join(SEPARATOR);
}
