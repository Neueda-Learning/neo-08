import React, { useEffect, useState } from 'react';
import {
  Badge,
  Button,
  Card,
  Checkbox,
  DataTable,
  EmptyState,
  Field,
  FormActions,
  FormGrid,
  KeyValue,
  PageHeader,
  Section,
  Spinner,
  Stack,
  TextInput,
} from '../design-system';
import { api } from '../api.js';

const ALL_ADDRESS_FIELDS = ['line1', 'line2', 'city', 'postcode', 'country'];

const PAN_PREFIX_RE = /^9999\d{2}$/;
const ISO_CODE_RE = /^[A-Z]{2}$/;

function validate({ prefix, countries, addressFields }) {
  const errs = {};
  if (!PAN_PREFIX_RE.test(prefix)) {
    errs.prefix = 'Must be 6 digits starting with 9999 (e.g. 999901)';
  }
  const countryList = countries.split(',').map((v) => v.trim()).filter(Boolean);
  if (countryList.length === 0) {
    errs.countries = 'At least one ISO country code required';
  } else {
    const bad = countryList.find((c) => !ISO_CODE_RE.test(c));
    if (bad) errs.countries = `"${bad}" is not a valid ISO alpha-2 code (e.g. GB)`;
  }
  const selected = ALL_ADDRESS_FIELDS.filter((f) => addressFields[f]);
  if (selected.length === 0) {
    errs.addressFields = 'At least one address field required';
  }
  return errs;
}

export default function IssuingConfig() {
  const [config, setConfig] = useState(null);
  const [history, setHistory] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Form state
  const [prefix, setPrefix] = useState('');
  const [length] = useState('16');
  const [countries, setCountries] = useState('');
  const [addressFields, setAddressFields] = useState(
    Object.fromEntries(ALL_ADDRESS_FIELDS.map((f) => [f, true]))
  );
  const [bureauUrl] = useState('http://mock-bureau:8091');
  const [saving, setSaving] = useState(false);
  const [saveResult, setSaveResult] = useState(null); // { ok, message }
  const [formErrors, setFormErrors] = useState({});

  // Load current config + history on mount
  useEffect(() => {
    setLoading(true);
    Promise.all([api.getCurrentConfig(), api.getConfigHistory()])
      .then(([c, h]) => { setConfig(c); setHistory(h); })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  function toggleField(f) {
    setAddressFields((prev) => ({ ...prev, [f]: !prev[f] }));
    setFormErrors((prev) => ({ ...prev, addressFields: undefined }));
  }

  function selectedFields() {
    return ALL_ADDRESS_FIELDS.filter((f) => addressFields[f]);
  }

  function setVal(setter, field) {
    return (e) => {
      setter(e.target.value);
      setFormErrors((prev) => ({ ...prev, [field]: undefined }));
    };
  }

  async function handleSubmit(e) {
    e?.preventDefault();
    const errs = validate({ prefix, countries, addressFields });
    setFormErrors(errs);
    if (Object.keys(errs).length > 0) return;

    setSaving(true);
    setSaveResult(null);
    setFormErrors({});
    try {
      const response = await api.createConfigVersion({
        panPrefix: prefix,
        panLength: parseInt(length, 10),
        deliveryCountries: countries
          .split(',')
          .map((v) => v.trim())
          .filter(Boolean),
        requiredAddressFields: selectedFields(),
        bureauBaseUrl: bureauUrl,
      });
      setSaveResult({ ok: true, message: `Version ${response.version} created` });
      // Reload current config + history
      Promise.all([api.getCurrentConfig(), api.getConfigHistory()])
        .then(([c, h]) => { setConfig(c); setHistory(h); });
      // Clear form (prefix & countries only; length & bureauUrl are fixed)
      setPrefix('');
      setCountries('');
    } catch (err) {
      setSaveResult({ ok: false, message: err.message });
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <>
        <PageHeader title="Issuing Configuration" lede="loading…" />
        <div style={{ textAlign: 'center', padding: 'var(--ds-space-8)' }}>
          <Spinner />
        </div>
      </>
    );
  }

  if (error) {
    return (
      <>
        <PageHeader title="Issuing Configuration" />
        <EmptyState title="Failed to load config">{error}</EmptyState>
      </>
    );
  }

  if (!config) {
    return (
      <>
        <PageHeader title="Issuing Configuration" />
        <EmptyState title="No config found">No issuing configuration exists yet.</EmptyState>
      </>
    );
  }

  return (
    <>
      <style>{'.history-table .ds-table th,.history-table .ds-table td{text-align:center}'}</style>
      <PageHeader
        title="Issuing Configuration"
        lede="versioned PAN range and delivery rules · insert-only · current = MAX(version)"
      />

      <Stack>
        <Section title="Current config">
          <Card>
            <KeyValue
              items={[
                { label: 'Version', value: config.version },
                { label: 'PAN prefix', value: config.panPrefix, mono: true },
                { label: 'PAN length', value: config.panLength },
                { label: 'Delivery countries', value: (config.deliveryCountries ?? []).join(', ') },
                {
                  label: 'Required address fields',
                  value: (config.requiredAddressFields ?? []).join(', '),
                },
                { label: 'Bureau URL', value: config.bureauBaseUrl, mono: true },
                {
                  label: 'Effective from',
                  value: config.effectiveFrom
                    ? new Date(config.effectiveFrom).toLocaleString()
                    : '—',
                },
              ]}
            />
          </Card>
        </Section>

        <Section title="Issue a new version">
          <Card>
            <FormGrid cols={2}>
              <Field label="PAN prefix" hint="6 digits inside the reserved 9999xx test block" required error={formErrors.prefix}>
                {({ id, invalid, describedBy }) => (
                  <TextInput
                    id={id}
                    mono
                    value={prefix}
                    placeholder="e.g. 999901"
                    maxLength={6}
                    invalid={invalid}
                    aria-describedby={describedBy}
                    onChange={setVal(setPrefix, 'prefix')}
                  />
                )}
              </Field>

              <Field label="PAN length" hint="fixed at 16" required>
                {({ id, describedBy }) => (
                  <TextInput
                    id={id}
                    mono
                    value={length}
                    readOnly
                    aria-describedby={describedBy}
                  />
                )}
              </Field>

              <FormGrid.Full>
                <Field label="Delivery countries" hint="comma-separated ISO codes, e.g. GB, IE, FR" required error={formErrors.countries}>
                  {({ id, invalid, describedBy }) => (
                    <TextInput
                      id={id}
                      value={countries}
                      placeholder="GB, IE, FR"
                      invalid={invalid}
                      aria-describedby={describedBy}
                      onChange={setVal(setCountries, 'countries')}
                    />
                  )}
                </Field>
              </FormGrid.Full>

              <FormGrid.Full>
                <Field label="Required address fields" error={formErrors.addressFields}>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--ds-space-4)' }}>
                    {ALL_ADDRESS_FIELDS.map((f) => (
                      <Checkbox
                        key={f}
                        label={f}
                        checked={addressFields[f]}
                        onChange={() => toggleField(f)}
                      />
                    ))}
                  </div>
                </Field>
              </FormGrid.Full>

              <FormGrid.Full>
                <Field label="Bureau base URL" hint="fixed" required>
                  {({ id, describedBy }) => (
                    <TextInput
                      id={id}
                      mono
                      value={bureauUrl}
                      readOnly
                      aria-describedby={describedBy}
                    />
                  )}
                </Field>
              </FormGrid.Full>

              <FormGrid.Full>
                <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--ds-space-4)' }}>
                  <FormActions>
                    <Button variant="primary" disabled={saving} onClick={handleSubmit}>
                      {saving ? 'Saving…' : 'Save new version'}
                    </Button>
                  </FormActions>
                  {saveResult && (
                    <span style={{
                      fontSize: 'var(--ds-text-sm)',
                      color: saveResult.ok ? 'var(--ds-tone-positive-accent)' : 'var(--ds-tone-negative-accent)',
                    }}>
                      {saveResult.message}
                    </span>
                  )}
                </div>
              </FormGrid.Full>
            </FormGrid>
          </Card>
        </Section>

        <Section title="Version history">
          {!history ? (
            <div style={{ textAlign: 'center', padding: 'var(--ds-space-4)' }}>
              <Spinner />
            </div>
          ) : history.length === 0 ? (
            <EmptyState title="No versions yet" />
          ) : (
            <Card>
              <DataTable
                className="history-table"
                columns={[
                  { key: 'version', header: 'Version', width: '15%' },
                  { key: 'panPrefix', header: 'PAN prefix', width: '20%' },
                  { key: 'panLength', header: 'Length', width: '15%' },
                  { key: 'deliveryCountries', header: 'Countries', width: '25%' },
                  { key: 'effectiveFrom', header: 'Effective from', width: '25%' },
                ]}
                rows={history.map((v) => ({
                  version: v.version,
                  panPrefix: v.panPrefix,
                  panLength: v.panLength,
                  deliveryCountries: (v.deliveryCountries ?? []).join(', '),
                  effectiveFrom: v.effectiveFrom
                    ? new Date(v.effectiveFrom).toLocaleString()
                    : '—',
                }))}
                rowKey={(row) => row.version}
                total={history.length}
              />
            </Card>
          )}
        </Section>
      </Stack>
    </>
  );
}
