import React, { useMemo, useState } from 'react';
import {
  Badge,
  ChipGroup,
  DataTable,
  EmptyState,
  Grid,
  MetricTile,
  PageHeader,
  SearchInput,
  Toolbar,
} from '../design-system';
import { outcomeTone, bureauStatusTone, OUTCOMES, time } from '../status.js';

const FILTERS = ['All', ...OUTCOMES];

// --- Mock data (UC acceptance criteria checkpoint values) ---
const MOCK = [
  {
    applicationId: 'app-1234',
    reference: 'crd-000064',
    applicantName: 'Maria Nowak',
    outcome: 'ISSUED',
    panLast4: '4242',
    bureauStatus: 'DISPATCHED',
    dispatchRef: 'RM-2214-9915',
    accountId: 'acc-000123',
    productCode: 'CREDIT_CARD_REWARDS',
    issuedAt: '2026-07-22T09:21:00Z',
  },
  {
    applicationId: 'app-1235',
    reference: 'crd-000065',
    applicantName: 'James Chen',
    outcome: 'ISSUED',
    panLast4: '7812',
    bureauStatus: 'PERSONALISED',
    dispatchRef: null,
    accountId: 'acc-000124',
    productCode: 'DEBIT_CARD_STANDARD',
    issuedAt: '2026-07-22T10:05:00Z',
  },
  {
    applicationId: 'app-1236',
    reference: 'crd-000066',
    applicantName: 'Elena Rossi',
    outcome: 'IN_PROGRESS',
    panLast4: null,
    bureauStatus: null,
    dispatchRef: null,
    accountId: 'acc-000125',
    productCode: 'CREDIT_CARD_REWARDS',
    issuedAt: null,
  },
  {
    applicationId: 'app-1237',
    reference: 'crd-000067',
    applicantName: 'Sofia Andersson',
    outcome: 'FAILED',
    panLast4: null,
    bureauStatus: null,
    dispatchRef: null,
    accountId: 'acc-000126',
    productCode: 'CREDIT_CARD_REWARDS',
    issuedAt: null,
    reason: 'CRD_DELIVERY_ADDRESS_INVALID',
  },
  {
    applicationId: 'app-1240',
    reference: 'crd-000070',
    applicantName: 'Tom Baker',
    outcome: 'FAILED',
    panLast4: null,
    bureauStatus: null,
    dispatchRef: null,
    accountId: 'acc-000129',
    productCode: 'DEBIT_CARD_PREMIUM',
    issuedAt: null,
    reason: 'CRD_BUREAU_UNAVAILABLE',
  },
];

function maskPan(last4) {
  if (!last4) return '—';
  return `**** **** **** ${last4}`;
}

export default function CardBoard({ onSelectCard }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');

  const counts = useMemo(
    () =>
      MOCK.reduce((acc, r) => {
        acc[r.outcome] = (acc[r.outcome] ?? 0) + 1;
        return acc;
      }, {}),
    []
  );

  const matches = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return MOCK.filter((r) => {
      if (filter !== 'All' && r.outcome !== filter) return false;
      if (!needle) return true;
      return (
        r.applicationId.toLowerCase().includes(needle) ||
        r.reference.toLowerCase().includes(needle) ||
        (r.applicantName && r.applicantName.toLowerCase().includes(needle))
      );
    });
  }, [query, filter]);

  const columns = [
    { key: 'reference', header: 'Reference', mono: true },
    { key: 'applicantName', header: 'Applicant' },
    {
      key: 'panLast4',
      header: 'Card',
      mono: true,
      render: (r) => maskPan(r.panLast4),
    },
    {
      key: 'outcome',
      header: 'Outcome',
      tight: true,
      render: (r) => <Badge tone={outcomeTone(r.outcome)}>{r.outcome}</Badge>,
    },
    {
      key: 'bureauStatus',
      header: 'Bureau',
      tight: true,
      render: (r) =>
        r.bureauStatus ? (
          <Badge tone={bureauStatusTone(r.bureauStatus)}>{r.bureauStatus}</Badge>
        ) : (
          '—'
        ),
    },
    { key: 'productCode', header: 'Product' },
    { key: 'issuedAt', header: 'Issued', render: (r) => time(r.issuedAt) },
  ];

  return (
    <>
      <PageHeader
        title="Card Board"
        lede="search by application id, reference or applicant name · newest first · max 10 rows"
      />

      <Grid cols={4} min={120} style={{ marginBottom: 'var(--ds-space-6)' }}>
        <MetricTile label="Total" value={MOCK.length} />
        <MetricTile label="In Progress" value={counts.IN_PROGRESS ?? 0} tone="info" />
        <MetricTile label="Issued" value={counts.ISSUED ?? 0} tone="positive" />
        <MetricTile label="Failed" value={counts.FAILED ?? 0} tone="negative" />
      </Grid>

      <Toolbar>
        <SearchInput
          grow
          placeholder="Application id, reference or name"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search cards"
        />
        <ChipGroup options={FILTERS} value={filter} onChange={setFilter} counts={counts} />
      </Toolbar>

      <DataTable
        columns={columns}
        rows={matches}
        total={matches.length}
        rowKey={(r) => r.applicationId}
        footnote="newest first"
        onRowClick={onSelectCard}
        empty={
          <EmptyState
            title={query ? 'No cards match that search' : 'Search to see cards'}
          >
            {query
              ? 'Clear the search, or pick a different outcome.'
              : 'Enter an application id, reference or applicant name to find card records.'}
          </EmptyState>
        }
      />
    </>
  );
}
