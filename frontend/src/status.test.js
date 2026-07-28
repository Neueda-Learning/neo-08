import assert from 'node:assert/strict';
import test from 'node:test';
import { STATUSES, statusTone, time } from './status.js';

test('maps durable and wire states to deliberate tones', () => {
  assert.equal(statusTone('in-progress'), 'info');
  assert.equal(statusTone('IN_PROGRESS'), 'info');
  assert.equal(statusTone('ISSUED'), 'positive');
  assert.equal(statusTone('FAILED'), 'warning');
  assert.equal(statusTone('ACCEPTED'), 'positive');
  assert.equal(statusTone('REFERRED'), 'warning');
  assert.equal(statusTone('REJECTED'), 'negative');
  assert.equal(statusTone('unexpected'), 'neutral');
});

test('includes the durable intake state in the board filters', () => {
  assert.deepEqual(STATUSES, ['in-progress', 'ACCEPTED', 'REFERRED', 'REJECTED']);
});

test('renders timestamps deterministically in UTC', () => {
  assert.equal(time('2026-07-28T08:15:30Z'), '2026-07-28T08:15:30.000Z');
  assert.equal(time(null), '—');
  assert.equal(time('not-a-date'), '—');
});
