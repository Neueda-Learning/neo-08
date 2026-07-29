// This module's vocabulary, mapped onto the design system's five tones — once, here, so no
// screen ever guesses what colour a status is.

import { TONES, toneMapper } from './design-system';

export const outcomeTone = toneMapper({
  ISSUED: TONES.POSITIVE,
  FAILED: TONES.NEGATIVE,
  IN_PROGRESS: TONES.INFO,
});

export const bureauStatusTone = toneMapper({
  DISPATCHED: TONES.POSITIVE,
  PERSONALISED: TONES.WARNING,
  REQUESTED: TONES.INFO,
});

export const OUTCOMES = ['IN_PROGRESS', 'ISSUED', 'FAILED'];

export function time(iso) {
  return iso ? new Date(iso).toLocaleTimeString() : '—';
}
