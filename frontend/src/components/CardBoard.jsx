import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Badge,
  ChipGroup,
  DataTable,
  EmptyState,
  Grid,
  MetricTile,
  PageHeader,
  SearchInput,
  Spinner,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';
import { outcomeTone, bureauStatusTone, OUTCOMES, time } from '../status.js';

const FILTERS = ['All', ...OUTCOMES];

function maskPan(last4) {
  if (!last4) return '—';
  return `**** **** **** ${last4}`;
}

/** Hydrate applicant names + product codes for a batch of card summaries. */
async function hydrate(cases) {
  const hydrated = await Promise.all(
    cases.map(async (c) => {
      try {
        const applicant = await api.getApplicant(c.applicationId);
        return {
          ...c,
          applicantName: applicant.fullName,
          productCode: applicant.productCode,
        };
      } catch {
        // applicant lookup failed — still show the row without a name
        return { ...c, applicantName: null, productCode: null };
      }
    })
  );
  return hydrated;
}

export default function CardBoard({ onSelectCard }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');
  const [cards, setCards] = useState(null); // null = haven't searched yet
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState(null);
  const timer = useRef(null);

  const doSearch = useCallback(async (q) => {
    setLoading(true);
    setError(null);
    try {
      const { body, headers } = await api.searchCards(q);
      setHasMore(headers.get('X-Has-More') === 'true');
      const hydrated = await hydrate(body);
      setCards(hydrated);
    } catch (err) {
      setError(err.message);
      setCards([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // Debounced search
  useEffect(() => {
    const needle = query.trim();
    if (!needle) {
      setCards(null);
      setHasMore(false);
      return;
    }
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => doSearch(needle), 300);
    return () => clearTimeout(timer.current);
  }, [query, doSearch]);

  const counts = useMemo(() => {
    if (!cards) return {};
    return cards.reduce((acc, r) => {
      acc[r.outcome] = (acc[r.outcome] ?? 0) + 1;
      return acc;
    }, {});
  }, [cards]);

  const matches = useMemo(() => {
    if (!cards) return [];
    if (filter === 'All') return cards;
    return cards.filter((r) => r.outcome === filter);
  }, [cards, filter]);

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'applicantName',
      header: 'Applicant',
      render: (r) => r.applicantName ?? '—',
    },
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

  const footnote = [hasMore && 'more results available — refine your search', 'newest first']
    .filter(Boolean)
    .join(' · ');

  return (
    <>
      <PageHeader
        title="Card Board"
        lede="search by application id or applicant name · newest first · max 10 rows"
      />

      <Grid cols={4} min={120} style={{ marginBottom: 'var(--ds-space-6)' }}>
        <MetricTile label="Total" value={cards?.length ?? 0} />
        <MetricTile label="In Progress" value={counts.IN_PROGRESS ?? 0} tone="info" />
        <MetricTile label="Issued" value={counts.ISSUED ?? 0} tone="positive" />
        <MetricTile label="Failed" value={counts.FAILED ?? 0} tone="negative" />
      </Grid>

      <Toolbar>
        <SearchInput
          grow
          placeholder="Application id or applicant name"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search cards"
        />
        <ChipGroup options={FILTERS} value={filter} onChange={setFilter} counts={counts} />
      </Toolbar>

      {loading && (
        <div style={{ textAlign: 'center', padding: 'var(--ds-space-8)' }}>
          <Spinner />
        </div>
      )}

      {error && !loading && (
        <EmptyState title="Search failed">{error}</EmptyState>
      )}

      {!loading && !error && (
        <DataTable
          columns={columns}
          rows={matches}
          total={matches.length}
          rowKey={(r) => r.applicationId}
          footnote={cards ? footnote : undefined}
          onRowClick={onSelectCard}
          empty={
            <EmptyState
              title={cards === null ? 'Search to see cards' : 'No cards match that search'}
            >
              {cards === null
                ? 'Enter an application id or applicant name to find card records.'
                : 'Clear the search, or pick a different outcome.'}
            </EmptyState>
          }
        />
      )}
    </>
  );
}
