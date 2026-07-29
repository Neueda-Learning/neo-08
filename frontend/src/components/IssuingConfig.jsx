import React, { useState } from 'react';
import {
  Badge,
  Button,
  Card,
  Checkbox,
  DataTable,
  Field,
  FormActions,
  FormGrid,
  KeyValue,
  MetricTile,
  PageHeader,
  Section,
  Stack,
  TextInput,
} from '../design-system';

const ALL_ADDRESS_FIELDS = ['line1', 'line2', 'city', 'postcode', 'country'];

// --- Mock version history (UC acceptance criteria checkpoint values) ---

const MOCK_VERSIONS = [
  {
    version: 1,
    panPrefix: '999900',
    panLength: 16,
    deliveryCountries: ['GB', 'IE'],
    requiredAddressFields: ['line1', 'city', 'postcode', 'country'],
    bureauBaseUrl: 'http://mock-bureau:8091',
    effectiveFrom: '2026-07-22T07:00:00Z',
  },
  {
    version: 2,
    panPrefix: '999901',
    panLength: 16,
    deliveryCountries: ['GB', 'IE', 'FR'],
    requiredAddressFields: ['line1', 'city', 'postcode', 'country'],
    bureauBaseUrl: 'http://mock-bureau:8091',
    effectiveFrom: '2026-07-22T11:00:00Z',
  },
];

const CURRENT = MOCK_VERSIONS[MOCK_VERSIONS.length - 1];

function parseCsv(s) {
  return s
    .split(',')
    .map((v) => v.trim().toUpperCase())
    .filter(Boolean);
}

export default function IssuingConfig() {
  const [prefix, setPrefix] = useState('');
  const [length, setLength] = useState('16');
  const [countries, setCountries] = useState('');
  const [addressFields, setAddressFields] = useState(
    Object.fromEntries(ALL_ADDRESS_FIELDS.map((f) => [f, true]))
  );
  const [bureauUrl, setBureauUrl] = useState('');

  function toggleField(f) {
    setAddressFields((prev) => ({ ...prev, [f]: !prev[f] }));
  }

  function selectedFields() {
    return ALL_ADDRESS_FIELDS.filter((f) => addressFields[f]);
  }

  function handleSubmit() {
    /* API call placeholder — POST /config */
    alert(`POST /config\npanPrefix=${prefix}\npanLength=${length}\ndeliveryCountries=${countries}\nrequiredAddressFields=${selectedFields().join(',')}\nbureauBaseUrl=${bureauUrl}`);
  }

  const versionColumns = [
    { key: 'version', header: 'Version', tight: true },
    { key: 'panPrefix', header: 'PAN Prefix', mono: true },
    { key: 'panLength', header: 'Length', tight: true },
    {
      key: 'deliveryCountries',
      header: 'Countries',
      render: (r) => r.deliveryCountries.join(', '),
    },
    {
      key: 'requiredAddressFields',
      header: 'Address fields',
      render: (r) => r.requiredAddressFields.join(', '),
    },
    { key: 'bureauBaseUrl', header: 'Bureau URL', mono: true },
    {
      key: 'effectiveFrom',
      header: 'Effective',
      render: (r) => new Date(r.effectiveFrom).toLocaleString(),
    },
  ];

  return (
    <>
      <PageHeader
        title="Issuing Configuration"
        lede="versioned PAN range and delivery rules · insert-only · current = MAX(version)"
      />

      <Stack>
        {/* Current config summary */}
        <Section title="Current config">
          <Card>
            <KeyValue
              items={[
                { label: 'Version', value: CURRENT.version },
                { label: 'PAN prefix', value: CURRENT.panPrefix, mono: true },
                { label: 'PAN length', value: CURRENT.panLength },
                { label: 'Delivery countries', value: CURRENT.deliveryCountries.join(', ') },
                {
                  label: 'Required address fields',
                  value: CURRENT.requiredAddressFields.join(', '),
                },
                { label: 'Bureau URL', value: CURRENT.bureauBaseUrl, mono: true },
                {
                  label: 'Effective from',
                  value: new Date(CURRENT.effectiveFrom).toLocaleString(),
                },
              ]}
            />
          </Card>
        </Section>

        {/* New version form */}
        <Section title="Issue a new version">
          <Card>
            <FormGrid cols={2}>
              <Field label="PAN prefix" hint="6 digits inside the reserved 9999xx test block" required>
                {({ id, invalid, describedBy }) => (
                  <TextInput
                    id={id}
                    mono
                    value={prefix}
                    placeholder="e.g. 999901"
                    maxLength={6}
                    invalid={invalid}
                    aria-describedby={describedBy}
                    onChange={(e) => setPrefix(e.target.value)}
                  />
                )}
              </Field>

              <Field label="PAN length" hint="total length including the Luhn check digit" required>
                {({ id, invalid, describedBy }) => (
                  <TextInput
                    id={id}
                    mono
                    value={length}
                    placeholder="16"
                    invalid={invalid}
                    aria-describedby={describedBy}
                    onChange={(e) => setLength(e.target.value)}
                  />
                )}
              </Field>

              <FormGrid.Full>
                <Field label="Delivery countries" hint="comma-separated ISO country codes, e.g. GB, IE, FR" required>
                  {({ id, invalid, describedBy }) => (
                    <TextInput
                      id={id}
                      value={countries}
                      placeholder="GB, IE, FR"
                      invalid={invalid}
                      aria-describedby={describedBy}
                      onChange={(e) => setCountries(e.target.value)}
                    />
                  )}
                </Field>
              </FormGrid.Full>

              <FormGrid.Full>
                <Field label="Required address fields" hint="which fields an alternative delivery address must have">
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
                <Field label="Bureau base URL" hint="where the mock bureau lives" required>
                  {({ id, invalid, describedBy }) => (
                    <TextInput
                      id={id}
                      mono
                      value={bureauUrl}
                      placeholder="http://mock-bureau:8091"
                      invalid={invalid}
                      aria-describedby={describedBy}
                      onChange={(e) => setBureauUrl(e.target.value)}
                    />
                  )}
                </Field>
              </FormGrid.Full>

              <FormGrid.Full>
                <FormActions>
                  <Button variant="primary" onClick={handleSubmit}>Save new version</Button>
                </FormActions>
              </FormGrid.Full>
            </FormGrid>
          </Card>
        </Section>

        {/* Version history */}
        <Section title="Version history">
          <DataTable
            columns={versionColumns}
            rows={MOCK_VERSIONS}
            total={MOCK_VERSIONS.length}
            rowKey={(r) => r.version}
            footnote="insert-only — rows are never updated or deleted"
          />
        </Section>
      </Stack>
    </>
  );
}
