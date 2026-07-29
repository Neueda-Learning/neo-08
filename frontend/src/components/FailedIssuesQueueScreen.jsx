import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  DataTable,
  EmptyState,
  Grid,
  PageHeader,
  TextInput,
  TONES,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';
import { time } from '../status.js';

const ADDRESS_FAILURE = 'CRD_DELIVERY_ADDRESS_INVALID';

function applicantName(application) {
  return (
    application?.applicant?.fullName ??
    application?.application?.applicant?.fullName ??
    '—'
  );
}

const EMPTY_ADDRESS = {
  line1: '',
  line2: '',
  city: '',
  postcode: '',
  country: '',
};

/** UC-04 operator queue for recoverable card-issue failures. */
export default function FailedIssuesQueueScreen({ info }) {
  const [rows, setRows] = useState([]);
  const [names, setNames] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [retrying, setRetrying] = useState(null);
  const [editing, setEditing] = useState(null);
  const [address, setAddress] = useState(EMPTY_ADDRESS);
  const nameCache = useRef(new Map());

  async function hydrateNames(cases) {
    const next = {};
    await Promise.all(
      cases.slice(0, 10).map(async (row) => {
        if (nameCache.current.has(row.applicationId)) {
          next[row.applicationId] = nameCache.current.get(row.applicationId);
          return;
        }
        try {
          const application = await api.getCaseApplicant(row.applicationId);
          const fullName = applicantName(application);
          nameCache.current.set(row.applicationId, fullName);
          next[row.applicationId] = fullName;
        } catch {
          next[row.applicationId] = '—';
        }
      })
    );
    setNames(next);
  }

  async function reload() {
    setLoading(true);
    setError(null);
    try {
      const failed = await api.listFailedQueue();
      setRows(failed);
      setLoading(false);
      void hydrateNames(failed);
    } catch (failure) {
      setRows([]);
      setLoading(false);
      setError(failure.message);
    }
  }

  useEffect(() => {
    void reload();
  }, []);

  async function retry(row, correctedAddress) {
    setRetrying(row.applicationId);
    setError(null);
    try {
      await api.retryCase(
        row.applicationId,
        correctedAddress ? { correctedAddress } : {}
      );
      setEditing(null);
      setAddress(EMPTY_ADDRESS);
      await reload();
    } catch (failure) {
      setError(
        failure.fieldErrors
          ? Object.entries(failure.fieldErrors)
              .map(([field, message]) => `${field} ${message}`)
              .join(' · ')
          : failure.message
      );
    } finally {
      setRetrying(null);
    }
  }

  function editAddress(row) {
    setEditing(row.applicationId);
    setAddress(EMPTY_ADDRESS);
    setError(null);
  }

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'applicant',
      header: 'Applicant',
      render: (row) => names[row.applicationId] ?? (loading ? 'Loading…' : '—'),
    },
    {
      key: 'reason',
      header: 'Failure reason',
      render: (row) => <Badge tone={TONES.NEGATIVE}>{row.reason}</Badge>,
    },
    {
      key: 'lastAttemptAt',
      header: 'Last attempt',
      render: (row) => time(row.lastAttemptAt),
    },
    {
      key: 'action',
      header: 'Action',
      render: (row) => (
        <Button
          size="sm"
          disabled={retrying === row.applicationId}
          onClick={() =>
            row.reason === ADDRESS_FAILURE ? editAddress(row) : retry(row)
          }
        >
          {retrying === row.applicationId ? 'Retrying…' : row.action}
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Failed-Issues Queue"
        lede="recoverable card issues · oldest attempt first · manual retry only"
        meta={info ? `${info.serviceId} · ${info.domain} · v${info.version}` : undefined}
      />

      {error && (
        <Alert tone="negative" title="Retry could not be completed">
          {error}
        </Alert>
      )}

      <Toolbar>
        <span>{loading ? 'Loading failed cases…' : `${rows.length} failed cases`}</span>
        <Toolbar.Spacer />
        <Button size="sm" variant="ghost" onClick={reload} disabled={loading}>
          Refresh
        </Button>
      </Toolbar>

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(row) => row.applicationId}
        maxRows={10}
        footnote="FAILED only · oldest attempt first"
        empty={
          <EmptyState title={loading ? 'Loading failed cases' : 'No failed card issues'}>
            {loading
              ? 'The queue will appear when the backend responds.'
              : 'There is currently nothing requiring an operator retry.'}
          </EmptyState>
        }
      />

      {editing && (
        <form
          style={{ marginTop: 'var(--ds-space-6)' }}
          onSubmit={(event) => {
            event.preventDefault();
            const row = rows.find((item) => item.applicationId === editing);
            if (row) void retry(row, address);
          }}
        >
          <Alert tone="warning" title={`Correct delivery address · ${editing}`}>
            The address is sent to the bureau for this attempt only and is never stored.
          </Alert>
          <Grid cols={2} min={220}>
            {[
              ['line1', 'Address line 1'],
              ['line2', 'Address line 2 (optional)'],
              ['city', 'City'],
              ['postcode', 'Postcode'],
              ['country', 'Country code'],
            ].map(([field, label]) => (
              <label key={field}>
                <span>{label}</span>
                <TextInput
                  value={address[field]}
                  maxLength={field === 'country' ? 2 : undefined}
                  onChange={(event) =>
                    setAddress((current) => ({
                      ...current,
                      [field]:
                        field === 'country'
                          ? event.target.value.toUpperCase()
                          : event.target.value,
                    }))
                  }
                />
              </label>
            ))}
          </Grid>
          <Toolbar>
            <Button type="submit" disabled={retrying === editing}>
              {retrying === editing ? 'Retrying…' : 'Fix address & retry'}
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => setEditing(null)}
              disabled={retrying === editing}
            >
              Cancel
            </Button>
          </Toolbar>
        </form>
      )}
    </>
  );
}
