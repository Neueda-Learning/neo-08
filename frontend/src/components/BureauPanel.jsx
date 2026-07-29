import React, { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Checkbox,
  EmptyState,
  FormActions,
  KeyValue,
  PageHeader,
  Section,
  Slider,
  Spinner,
  Stack,
  StatusDot,
} from '../design-system';
import { api } from '../api.js';

const DEFAULTS = { secondsPerStage: 120, latencyMs: 0, killSwitch: false };

export default function BureauPanel() {
  const [current, setCurrent] = useState(null);
  const [draft, setDraft] = useState(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    api.getBureauDials()
      .then((d) => { setCurrent(d); setDraft(d); })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  function update(patch) {
    setDraft((prev) => ({ ...prev, ...patch }));
    setError(null);
  }

  async function save() {
    setSaving(true);
    setError(null);
    try {
      const updated = await api.updateBureauDials(draft);
      setCurrent(updated);
      setDraft(updated);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  function reset() {
    setDraft(DEFAULTS);
  }

  if (loading) {
    return (
      <>
        <PageHeader title="Bureau Control Panel" lede="loading…" />
        <div style={{ textAlign: 'center', padding: 'var(--ds-space-8)' }}>
          <Spinner />
        </div>
      </>
    );
  }

  if (error && !current) {
    return (
      <>
        <PageHeader title="Bureau Control Panel" />
        <EmptyState title="Failed to load dials">{error}</EmptyState>
      </>
    );
  }

  if (!draft) return null;

  const dirty = current &&
    (draft.secondsPerStage !== current.secondsPerStage ||
     draft.latencyMs !== current.latencyMs ||
     draft.killSwitch !== current.killSwitch);

  return (
    <>
      <PageHeader
        title="Bureau Control Panel"
        lede="control the mock card personalisation bureau's clock, latency and availability for demos"
      />

      {error && (
        <div style={{
          padding: 'var(--ds-space-2) var(--ds-space-4)',
          marginBottom: 'var(--ds-space-4)',
          borderRadius: 'var(--ds-radius-sm)',
          background: 'var(--ds-tone-negative-subtle)',
          color: 'var(--ds-tone-negative-accent)',
          fontSize: 'var(--ds-text-sm)',
        }}>
          {error}
        </div>
      )}

      <Stack>
        <Section title="Clock speed">
          <Card>
            <Slider
              label="Seconds per stage"
              value={draft.secondsPerStage}
              suffix="s"
              min={1}
              max={300}
              onChange={(e) => update({ secondsPerStage: Number(e.target.value) })}
            />
            <p className="ds-muted" style={{ marginTop: 'var(--ds-space-2)' }}>
              A card advances REQUESTED → PERSONALISED → DISPATCHED on the bureau's own clock,
              one hop per this many seconds. Set to 5s for a demo-speed timeline.
            </p>
          </Card>
        </Section>

        <Section title="Latency">
          <Card>
            <Slider
              label="Artificial latency"
              value={draft.latencyMs}
              suffix="ms"
              min={0}
              max={5000}
              step={100}
              onChange={(e) => update({ latencyMs: Number(e.target.value) })}
            />
            <p className="ds-muted" style={{ marginTop: 'var(--ds-space-2)' }}>
              Extra delay added to every bureau API call. 0 = instant.
            </p>
          </Card>
        </Section>

        <Section title="Kill switch">
          <Card>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--ds-space-4)' }}>
              <Checkbox
                label="Kill switch"
                checked={draft.killSwitch}
                onChange={(e) => update({ killSwitch: e.target.checked })}
              />
              <StatusDot tone={draft.killSwitch ? 'negative' : 'positive'} />
            </div>
            <p className="ds-muted" style={{ marginTop: 'var(--ds-space-2)' }}>
              When on, the bureau refuses all card instructions — the module fails with
              CRD_BUREAU_UNAVAILABLE and the case enters the Failed-Issue Queue.
            </p>
          </Card>
        </Section>

        <Section title="Actions">
          <FormActions>
            <Button variant="primary" disabled={!dirty || saving} onClick={save}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
            <Button variant="ghost" size="sm" onClick={reset}>
              Reset to defaults
            </Button>
          </FormActions>
        </Section>

        {current && (
          <Section title="Current dials (saved)">
            <Card>
              <KeyValue
                items={[
                  { label: 'Seconds per stage', value: `${current.secondsPerStage}s` },
                  { label: 'Latency', value: `${current.latencyMs}ms` },
                  { label: 'Kill switch', value: current.killSwitch ? 'ON' : 'OFF' },
                ]}
              />
            </Card>
          </Section>
        )}
      </Stack>
    </>
  );
}
