import React from 'react';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  KeyValue,
  PageHeader,
  Section,
  Stack,
  TextInput,
} from '../design-system';
import { outcomeTone, bureauStatusTone, time } from '../status.js';

function maskPan(last4) {
  if (!last4) return '—';
  return `**** **** **** ${last4}`;
}

export default function CardDetail({ selectedCard, onBack }) {
  if (!selectedCard) {
    return (
      <>
        <PageHeader
          title="Card Detail"
          lede="enter an application id or reference to view a card record"
        />
        <EmptyState title="No card selected">
          Click a row on the <strong>Card Board</strong> to see its details, or paste an
          application id below.
        </EmptyState>
      </>
    );
  }

  const r = selectedCard;

  return (
    <>
      <PageHeader
        title={`Card ${r.reference}`}
        lede={`application ${r.applicationId}`}
      >
        <Button variant="ghost" size="sm" onClick={onBack}>
          Back to board
        </Button>
      </PageHeader>

      <Stack>
        <Section title="Outcome">
          <Card>
            <KeyValue
              items={[
                { label: 'Outcome', value: <Badge tone={outcomeTone(r.outcome)}>{r.outcome}</Badge> },
                { label: 'Reference', value: r.reference, mono: true },
                { label: 'Issued at', value: time(r.issuedAt) },
              ]}
            />
          </Card>
        </Section>

        <Section title="Card details">
          <Card>
            <KeyValue
              items={[
                { label: 'PAN', value: maskPan(r.panLast4), mono: true },
                { label: 'PAN Hash', value: r.panHash ?? '—', mono: true },
                { label: 'Bureau card ID', value: r.bureauCardId ?? '—', mono: true },
                {
                  label: 'Bureau status',
                  value: r.bureauStatus ? (
                    <Badge tone={bureauStatusTone(r.bureauStatus)}>{r.bureauStatus}</Badge>
                  ) : '—',
                },
                { label: 'Dispatch ref', value: r.dispatchRef ?? '—', mono: true },
              ]}
            />
          </Card>
        </Section>

        <Section title="Linked resources">
          <Card>
            <KeyValue
              items={[
                { label: 'Account', value: r.accountId, mono: true },
                { label: 'Product code', value: r.productCode },
                { label: 'Issuing config version', value: r.issuingConfigVersion ?? 1 },
              ]}
            />
          </Card>
        </Section>
      </Stack>
    </>
  );
}
