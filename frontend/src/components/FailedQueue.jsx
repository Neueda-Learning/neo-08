import React from 'react';
import {
  Badge,
  Button,
  Card,
  DataTable,
  EmptyState,
  KeyValue,
  Modal,
  PageHeader,
  Section,
  Stack,
  TextInput,
} from '../design-system';
import { outcomeTone } from '../status.js';

// --- Mock failed cases (UC acceptance criteria) ---
const MOCK_FAILED = [
  {
    applicationId: 'app-1237',
    reference: 'crd-000067',
    applicantName: 'Sofia Andersson',
    outcome: 'FAILED',
    reason: 'CRD_DELIVERY_ADDRESS_INVALID',
    reasonLabel: 'Invalid delivery address',
    productCode: 'CREDIT_CARD_REWARDS',
    accountId: 'acc-000126',
    failedAt: '2026-07-22T08:15:00Z',
  },
  {
    applicationId: 'app-1240',
    reference: 'crd-000070',
    applicantName: 'Tom Baker',
    outcome: 'FAILED',
    reason: 'CRD_BUREAU_UNAVAILABLE',
    reasonLabel: 'Bureau unavailable',
    productCode: 'DEBIT_CARD_PREMIUM',
    accountId: 'acc-000129',
    failedAt: '2026-07-22T08:30:00Z',
  },
];

function actionFor(reason) {
  if (reason === 'CRD_DELIVERY_ADDRESS_INVALID') return 'Fix address & retry';
  if (reason === 'CRD_BUREAU_UNAVAILABLE') return 'Retry';
  return '—';
}

function actionVariant(reason) {
  if (reason === 'CRD_BUREAU_UNAVAILABLE') return 'primary';
  return 'secondary';
}

export default function FailedQueue() {
  const columns = [
    { key: 'reference', header: 'Reference', mono: true },
    { key: 'applicantName', header: 'Applicant' },
    {
      key: 'reasonLabel',
      header: 'Reason',
      render: (r) => <Badge tone="negative">{r.reasonLabel}</Badge>,
    },
    { key: 'productCode', header: 'Product' },
    { key: 'accountId', header: 'Account', mono: true },
    {
      key: 'action',
      header: 'Action',
      tight: true,
      render: (r) => (
        <Button variant={actionVariant(r.reason)} size="sm">
          {actionFor(r.reason)}
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Failed-Issue Queue"
        lede="FAILED cases only · oldest first · action depends on the failure reason"
      />

      <DataTable
        columns={columns}
        rows={MOCK_FAILED}
        total={MOCK_FAILED.length}
        rowKey={(r) => r.applicationId}
        footnote="oldest first"
        empty={
          <EmptyState title="No failed cases">
            When a card issue fails — bad address or bureau down — it appears here with the
            right fix action.
          </EmptyState>
        }
      />

      {/* Address-fix modal (shown on click for address failures) */}
    </>
  );
}
