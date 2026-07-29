import React, { useState } from 'react';
import {
  Button,
  Card,
  Checkbox,
  KeyValue,
  MetricTile,
  PageHeader,
  Section,
  Slider,
  Stack,
  StatusDot,
} from '../design-system';

const DEFAULTS = {
  secondsPerStage: 120,
  latencyMs: 0,
  killSwitch: false,
};

export default function BureauPanel() {
  const [dials, setDials] = useState(DEFAULTS);

  function update(patch) {
    setDials((prev) => ({ ...prev, ...patch }));
  }

  function reset() {
    setDials(DEFAULTS);
  }

  return (
    <>
      <PageHeader
        title="Bureau Control Panel"
        lede="control the mock card personalisation bureau's clock, latency and availability for demos"
      />

      <Stack>
        <Section title="Clock speed">
          <Card>
            <Slider
              label="Seconds per stage"
              value={dials.secondsPerStage}
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
              value={dials.latencyMs}
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
                checked={dials.killSwitch}
                onChange={(e) => update({ killSwitch: e.target.checked })}
              />
              <StatusDot
                tone={dials.killSwitch ? 'negative' : 'positive'}
              />
            </div>
            <p className="ds-muted" style={{ marginTop: 'var(--ds-space-2)' }}>
              When on, the bureau refuses all card instructions — the module fails with
              CRD_BUREAU_UNAVAILABLE and the case enters the Failed-Issue Queue.
            </p>
          </Card>
        </Section>

        <Section title="Current dials">
          <Card>
            <KeyValue
              items={[
                { label: 'Seconds per stage', value: `${dials.secondsPerStage}s` },
                { label: 'Latency', value: `${dials.latencyMs}ms` },
                {
                  label: 'Kill switch',
                  value: dials.killSwitch ? 'ON' : 'OFF',
                },
              ]}
            />
            <div style={{ marginTop: 'var(--ds-space-4)' }}>
              <Button variant="ghost" size="sm" onClick={reset}>
                Reset to defaults
              </Button>
            </div>
          </Card>
        </Section>
      </Stack>
    </>
  );
}
