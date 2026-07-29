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

export default function CardBoard({ onSelectCard }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');
  const [cards, setCards] = useState(null);
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
      setCards(body);
    } catch (err) {
      setError(err.message);
      setCards([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // Debounced search — empty query reloads all cards (handles initial mount and clearing search)
  useEffect(() => {
    const needl = query.trim();
    if (timer.current) clearTimeout(timer.current);
    if (!needl) {
      timer.current = setTimeout(() => {
        setLoading(true);
        setError(null);
        api.listAllCards()
          .then(({ body, headers }) => {
            setHasMore(headers.get('X-Has-More') === 'true');
            setCards(body);
          })
          .catch((err) => { setError(err.message); setCards([]); })
          .finally(() => setLoading(false));
      }, 150);
      return;
    }
    timer.current = setTimeout(() => doSearch(needl), 300);
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
    { key: 'applicationId', header: 'Application', mono: true, width: '20%' },
    {
      key: 'panLast4',
      header: 'PAN',
      mono: true,
      width: '20%',
      render: (r) => (r.panLast4 ? `****${r.panLast4}` : '—'),
    },
    {
      key: 'outcome',
      header: 'Outcome',
      width: '20%',
      render: (r) => <Badge tone={outcomeTone(r.outcome)}>{r.outcome}</Badge>,
    },
    {
      key: 'bureauStatus',
      header: 'Bureau',
      width: '20%',
      render: (r) =>
        r.bureauStatus ? (
          <Badge tone={bureauStatusTone(r.bureauStatus)}>{r.bureauStatus}</Badge>
        ) : (
          '—'
        ),
    },
    { key: 'issuedAt', header: 'Issued', width: '20%', render: (r) => time(r.issuedAt) },
  ];

  const footnote = [hasMore && 'more results available — refine your search', 'newest first']
    .filter(Boolean)
    .join(' · ');

  return (
    <>
      <style>{'.board-table .ds-table th,.board-table .ds-table td{text-align:center}'}</style>
      <PageHeader
        title="Card Board"
        lede="search by application id · newest first · max 10 rows"
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
          placeholder="Application id"
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
          className="board-table"
          columns={columns}
          rows={matches}
          total={matches.length}
          rowKey={(r) => r.applicationId}
          footnote={cards ? footnote : undefined}
          onRowClick={onSelectCard}
          empty={
            <EmptyState
              title={cards && cards.length === 0 ? 'No cards match that search' : 'No card records yet'}
            >
              {cards && cards.length === 0
                ? 'Clear the search, or pick a different outcome.'
                : 'When card issuing records are created they will appear here.'}
            </EmptyState>
          }
        />
      )}
    </>
  );
}
