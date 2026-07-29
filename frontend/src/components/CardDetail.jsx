import React, { useEffect, useState } from 'react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  KeyValue,
  PageHeader,
  Section,
  Spinner,
  Stack,
} from '../design-system';
import { api } from '../api.js';
import { outcomeTone, bureauStatusTone } from '../status.js';

export default function CardDetail({ selectedCard, onBack }) {
  const [detail, setDetail] = useState(null);
  const [applicant, setApplicant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!selectedCard) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    setDetail(null);
    setApplicant(null);

    Promise.all([
      api.getCardDetail(selectedCard.applicationId),
      api.getApplicant(selectedCard.applicationId),
    ])
      .then(([d, a]) => {
        if (cancelled) return;
        setDetail(d);
        setApplicant(a);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [selectedCard]);

  if (!selectedCard) {
    return (
      <>
        <PageHeader
          title="Card Detail"
          lede="select a card from the board to see its details"
        />
        <EmptyState title="No card selected">
          Click a row on the <strong>Card Board</strong> to see its details.
        </EmptyState>
      </>
    );
  }

  if (loading || !detail) {
    return (
      <>
        <PageHeader
          title="Card Detail"
          lede={`loading ${selectedCard.applicationId}…`}
        />
        <div style={{ textAlign: 'center', padding: 'var(--ds-space-8)' }}>
          <Spinner />
        </div>
      </>
    );
  }

  if (error) {
    return (
      <>
        <PageHeader
          title="Card Detail"
          lede={selectedCard.applicationId}
        >
          <Button variant="ghost" size="sm" onClick={onBack}>
            Back to board
          </Button>
        </PageHeader>
        <EmptyState title="Failed to load card">{error}</EmptyState>
      </>
    );
  }

  const title = applicant?.fullName
    ? `${applicant.fullName}  ·  ${detail.reference}`
    : `Card ${detail.reference}`;

  const address = applicant?.deliveryAddress;
  const addressLine = address
    ? [address.line1, address.line2, address.city, address.postcode, address.country]
        .filter(Boolean)
        .join(', ')
    : '—';

  return (
    <>
      <PageHeader title={title} lede={selectedCard.applicationId}>
        <Button variant="ghost" size="sm" onClick={onBack}>
          Back to board
        </Button>
      </PageHeader>

      <Stack>
        <Section title="Outcome">
          <Card>
            <KeyValue
              items={[
                { label: 'Outcome', value: <Badge tone={outcomeTone(detail.outcome)}>{detail.outcome}</Badge> },
                { label: 'Reference', value: detail.reference, mono: true },
                {
                  label: 'Reason',
                  value: detail.reasons?.length > 0
                    ? detail.reasons.map((r, i) => <Badge key={i} tone="negative">{r.code}</Badge>)
                    : '—',
                },
              ]}
            />
          </Card>
        </Section>

        <Section title="Applicant">
          <Card>
            <KeyValue
              items={[
                { label: 'Name', value: applicant?.fullName ?? '—' },
                { label: 'Product', value: detail.productCode ?? '—' },
                {
                  label: 'Delivery',
                  value: applicant?.useCurrentAddress ? 'Current address' : 'Alternative address',
                },
                { label: 'Address', value: addressLine, mono: true },
              ]}
            />
          </Card>
        </Section>

        <Section title="Card details">
          <Card>
            <KeyValue
              items={[
                { label: 'PAN', value: detail.panMasked ?? '—', mono: true },
                { label: 'PAN Hash', value: detail.panHash ?? '—', mono: true },
                { label: 'Bureau card ID', value: detail.bureauCardId ?? '—', mono: true },
                {
                  label: 'Bureau status',
                  value: detail.bureauStatus ? (
                    <Badge tone={bureauStatusTone(detail.bureauStatus)}>{detail.bureauStatus}</Badge>
                  ) : '—',
                },
                { label: 'Dispatch ref', value: detail.dispatchRef ?? '—', mono: true },
              ]}
            />
          </Card>
        </Section>

        <Section title="Linked resources">
          <Card>
            <KeyValue
              items={[
                { label: 'Account', value: detail.accountId ?? '—', mono: true },
                { label: 'Issuing config version', value: detail.issuingConfigVersion ?? '—' },
              ]}
            />
          </Card>
        </Section>
      </Stack>
    </>
  );
}
