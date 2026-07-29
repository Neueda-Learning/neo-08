import React, { useEffect, useState } from 'react';
import {
  Badge,
  Button,
  DataTable,
  EmptyState,
  Field,
  FormActions,
  FormGrid,
  Modal,
  PageHeader,
  Spinner,
  Stack,
  TextInput,
} from '../design-system';
import { api } from '../api.js';

const ADDRESS_FIELDS = ['line1', 'city', 'postcode', 'country'];

function emptyAddress() {
  return Object.fromEntries(ADDRESS_FIELDS.map((f) => [f, '']));
}

export default function FailedQueue() {
  const [rows, setRows] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [names, setNames] = useState({});

  // Modal state — type: 'address' (UC04 address fix) or 'confirm' (bureau retry)
  const [modal, setModal] = useState(null); // { type, applicationId, reason }
  const [addr, setAddr] = useState(emptyAddress());
  const [addrError, setAddrError] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setLoading(true);
    api.getFailedQueue()
      .then(async (list) => {
        setRows(list);
        const nameMap = {};
        await Promise.all(
          list.map((r) =>
            api.getApplicant(r.applicationId)
              .then((a) => { nameMap[r.applicationId] = a.fullName; })
              .catch(() => { nameMap[r.applicationId] = '—'; })
          )
        );
        setNames(nameMap);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  function openAddressModal(row) {
    setModal({ type: 'address', applicationId: row.applicationId, reason: row.reason });
    setAddr(emptyAddress());
    setAddrError(null);
  }

  async function handleAddressRetry() {
    setAddrError(null);
    const missing = ADDRESS_FIELDS.filter((f) => !addr[f].trim());
    if (missing.length > 0) {
      setAddrError(`Missing: ${missing.join(', ')}`);
      return;
    }
    setSaving(true);
    try {
      const correctedAddress = Object.fromEntries(
        ADDRESS_FIELDS.map((f) => [f, addr[f]?.trim() || null])
      );
      await api.retryFailedIssue(modal.applicationId, correctedAddress);
      setModal(null);
      const list = await api.getFailedQueue();
      setRows(list);
    } catch (err) {
      setAddrError(err.message);
    } finally {
      setSaving(false);
    }
  }

  async function handleBureauRetry() {
    setSaving(true);
    try {
      await api.retryFailedIssue(modal.applicationId);
      setModal(null);
      const list = await api.getFailedQueue();
      setRows(list);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <>
        <PageHeader title="Failed-Issue Queue" lede="loading…" />
        <div style={{ textAlign: 'center', padding: 'var(--ds-space-8)' }}>
          <Spinner />
        </div>
      </>
    );
  }

  if (error && !rows) {
    return (
      <>
        <PageHeader title="Failed-Issue Queue" />
        <EmptyState title="Failed to load queue">{error}</EmptyState>
      </>
    );
  }

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'applicant',
      header: 'Applicant',
      render: (r) => names[r.applicationId] ?? '…',
    },
    {
      key: 'reason',
      header: 'Reason',
      render: (r) => <Badge tone="negative">{r.reason}</Badge>,
    },
    {
      key: 'action',
      header: 'Action',
      tight: true,
      render: (r) => {
        if (r.reason === 'CRD_DELIVERY_ADDRESS_INVALID') {
          return (
            <Button variant="secondary" size="sm" disabled={saving} onClick={() => openAddressModal(r)}>
              Fix address & retry
            </Button>
          );
        }
        return (
          <Button variant="primary" size="sm" disabled={saving} onClick={() => setModal({
            type: 'confirm',
            applicationId: r.applicationId,
            reason: r.reason,
          })}>
            Retry
          </Button>
        );
      },
    },
  ];

  return (
    <>
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

      <PageHeader
        title="Failed-Issue Queue"
        lede="FAILED cases only · oldest first · max 10 rows"
      />

      <DataTable
        columns={columns}
        rows={rows ?? []}
        total={rows?.length ?? 0}
        rowKey={(r) => r.applicationId}
        footnote="oldest first"
        empty={
          <EmptyState title="No failed cases">
            When a card issue fails — bad address or bureau down — it appears here with the
            right fix action.
          </EmptyState>
        }
      />

      {modal && modal.type === 'address' && (
        <Modal
          open
          title="Fix delivery address"
          onClose={() => setModal(null)}
        >
          <Stack>
            <FormGrid cols={2}>
              {ADDRESS_FIELDS.map((f) => (
                <Field key={f} label={f.charAt(0).toUpperCase() + f.slice(1)} required>
                  {({ id }) => (
                    <TextInput
                      id={id}
                      value={addr[f]}
                      onChange={(e) => setAddr((prev) => ({ ...prev, [f]: e.target.value }))}
                    />
                  )}
                </Field>
              ))}
            </FormGrid>
            {addrError && (
              <p style={{ color: 'var(--ds-tone-negative-accent)', fontSize: 'var(--ds-text-sm)' }}>
                {addrError}
              </p>
            )}
            <FormActions>
              <Button variant="primary" disabled={saving} onClick={handleAddressRetry}>
                {saving ? 'Retrying…' : 'Retry with corrected address'}
              </Button>
              <Button variant="ghost" onClick={() => setModal(null)}>
                Cancel
              </Button>
            </FormActions>
          </Stack>
        </Modal>
      )}

      {modal && modal.type === 'confirm' && (
        <Modal
          open
          title="Confirm retry"
          onClose={() => setModal(null)}
          footer={
            <div style={{ display: 'flex', gap: 'var(--ds-space-2)', justifyContent: 'flex-end' }}>
              <Button variant="ghost" size="sm" onClick={() => setModal(null)}>
                Cancel
              </Button>
              <Button variant="primary" size="sm" disabled={saving} onClick={handleBureauRetry}>
                {saving ? 'Retrying…' : 'Confirm retry'}
              </Button>
            </div>
          }
        >
          <p style={{ fontSize: 'var(--ds-text-sm)' }}>
            Retry application <strong>{modal.applicationId}</strong>?
          </p>
          <p style={{ fontSize: 'var(--ds-text-sm)', color: 'var(--ds-tone-negative-accent)' }}>
            Reason: <Badge tone="negative">{modal.reason}</Badge>
          </p>
        </Modal>
      )}
    </>
  );
}
