// This module's vocabulary, mapped onto the design system's five tones — once, here, so no
// screen ever guesses what colour a status is.
//
// The design system deliberately knows no business words (design-system/DESIGN.md § "Tones"):
// ten modules speak ten vocabularies over one contract, and a Badge that knew "ACCEPTED" would
// have to learn "VERIFIED", "CLEAR" and "SIGNED" too.
import { TONES, toneMapper } from './design-system/tones.js';

export const statusTone = toneMapper({
  ACCEPTED: TONES.POSITIVE,
  REJECTED: TONES.NEGATIVE,
  REFERRED: TONES.WARNING,
  ISSUED: TONES.POSITIVE,
  FAILED: TONES.WARNING,
  IN_PROGRESS: TONES.INFO,
  'in-progress': TONES.INFO,
});

/** Fixed wire statuses plus the durable intake state visible before the worker finishes. */
export const STATUSES = ['in-progress', 'ACCEPTED', 'REFERRED', 'REJECTED'];

export function time(iso) {
  if (!iso) return '—';
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime()) ? '—' : parsed.toISOString();
}
