import React, { useMemo, useState } from 'react';
import {
  Alert,
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
import { statusTone, STATUSES, time } from '../status.js';

const FILTERS = ['All', ...STATUSES];

/**
 * Safe card-issuing records, newest first. The API never exposes a full PAN;
 * this screen renders only its already-masked representation.
 */
export default function RequestsScreen({ requests, error, info, actions }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');

  const counts = useMemo(
    () =>
      requests.reduce((acc, r) => {
        acc[r.status] = (acc[r.status] ?? 0) + 1;
        return acc;
      }, {}),
    [requests]
  );

  const matches = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return requests.filter((r) => {
      if (filter !== 'All' && r.status !== filter) return false;
      if (!needle) return true;
      return [
        r.applicationId,
        r.reference,
        r.productCode,
        r.panMasked,
        r.reasonCode,
        r.bureauCardId,
      ].some((value) => value?.toLowerCase().includes(needle));
    });
  }, [requests, query, filter]);

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'status',
      header: 'Status',
      tight: true,
      render: (r) => <Badge tone={statusTone(r.status)}>{r.status}</Badge>,
    },
    {
      key: 'outcome',
      header: 'Card outcome',
      tight: true,
      render: (r) => <Badge tone={statusTone(r.outcome)}>{r.outcome}</Badge>,
    },
    { key: 'productCode', header: 'Product', mono: true },
    {
      key: 'panMasked',
      header: 'Card',
      mono: true,
      render: (r) => r.panMasked ?? '—',
    },
    {
      key: 'bureauStatus',
      header: 'Bureau',
      tight: true,
      render: (r) => r.bureauStatus ?? '—',
    },
    {
      key: 'reasonCode',
      header: 'Result',
      render: (r) => (
        <div className="card-result">
          <span className="card-result__code">{r.reasonCode ?? 'Awaiting worker'}</span>
          {r.comment && <span className="card-result__comment">{r.comment}</span>}
        </div>
      ),
    },
    { key: 'decidedAt', header: 'Decided (UTC)', render: (r) => time(r.decidedAt) },
  ];

  return (
    <>
      <PageHeader
        title="Applications"
        lede="everything the orchestrator has sent this module, and what it answered · newest first"
        meta={
          info
            ? `${info.team} · ${info.serviceId} · ${info.domain} · v${info.version}` +
              (info.mockedDependencies?.length
                ? ` · mocking ${info.mockedDependencies.join(', ')}`
                : ' · nothing mocked')
            : undefined
        }
        actions={actions}
      />

      {error && (
        <Alert tone="negative" title="Could not load applications">
          {error} — the backend may still be starting. The list retries every two seconds.
        </Alert>
      )}

      <Grid cols="auto" min={160} style={{ marginBottom: 'var(--ds-space-6)' }}>
        <MetricTile label="Seen" value={requests.length} />
        <MetricTile label="In progress" value={counts['in-progress'] ?? 0} tone="info" />
        <MetricTile
          label="Issued"
          value={requests.filter((request) => request.outcome === 'ISSUED').length}
          tone="positive"
        />
        <MetricTile
          label="Needs attention"
          value={requests.filter((request) => request.outcome === 'FAILED').length}
          tone="warning"
        />
      </Grid>

      <Toolbar>
        <SearchInput
          grow
          placeholder="Application, reference, product, card or reason"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search applications"
        />
        <ChipGroup options={FILTERS} value={filter} onChange={setFilter} counts={counts} />
      </Toolbar>

      <DataTable
        columns={columns}
        rows={matches}
        total={matches.length}
        rowKey={(r) => r.applicationId}
        footnote="newest first · full card numbers and addresses are never exposed"
        empty={
          <EmptyState
            title={requests.length === 0 ? 'Nothing received yet' : 'No application matches that'}
          >
            {requests.length === 0 ? (
              <>
                Send one from the <strong>sidecar</strong> at <strong>localhost:9000</strong>, or turn
                the generator on in the orchestrator UI. Nothing in this screen sends applications —
                this module is called, it does not call itself.
              </>
            ) : (
              <>Clear the search, or pick a different status.</>
            )}
          </EmptyState>
        }
      />
    </>
  );
}
