// Dependency-free operator status page served by Spring Boot itself.
const POLL_MS = 2000;
const COLUMN_COUNT = 7;

const el = (id) => document.getElementById(id);

async function json(path) {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

function time(iso) {
  if (!iso) return '—';
  const parsed = new Date(iso);
  return Number.isNaN(parsed.valueOf()) ? '—' : parsed.toISOString();
}

function cell(text, className) {
  const td = document.createElement('td');
  td.textContent = text ?? '—';
  if (className) td.className = className;
  return td;
}

function statusCell(row) {
  const td = document.createElement('td');
  const badge = document.createElement('span');
  const value = row.outcome ?? row.status ?? '—';
  badge.className = `st st-${value}`;
  badge.textContent = value;
  td.appendChild(badge);
  return td;
}

function emptyRow(message) {
  const row = document.createElement('tr');
  const td = cell(message, 'empty');
  td.colSpan = COLUMN_COUNT;
  row.appendChild(td);
  return row;
}

async function refreshIdentity() {
  try {
    const [health, info] = await Promise.all([json('/health'), json('/info')]);
    const pill = el('pill');
    pill.textContent = health.status;
    pill.className = `pill ${health.status === 'UP' ? 'up' : 'down'}`;
    el('who').textContent = info.service;
    document.title = `${info.serviceId} · ${info.service}`;
    const mocked = info.mockedDependencies?.length
      ? info.mockedDependencies.join(', ')
      : 'nothing';
    el('sub').textContent =
      `${info.serviceId} · ${info.domain} · v${info.version} · mocking ${mocked}`;
  } catch {
    const pill = el('pill');
    pill.textContent = 'DOWN';
    pill.className = 'pill down';
  }
}

async function refreshRows() {
  const tbody = el('rows');
  let rows;
  try {
    rows = await json('/api/v1/applications');
  } catch {
    tbody.replaceChildren(emptyRow('Could not load card cases; retrying…'));
    return;
  }

  el('count').textContent = rows.length ? `${rows.length} seen` : '';
  if (rows.length === 0) {
    tbody.replaceChildren(
      emptyRow('Nothing received yet — send an application from the sidecar at localhost:9000.')
    );
    return;
  }

  // Build text nodes rather than interpolating request values into HTML. The
  // application id originates outside this service and must be treated as untrusted.
  const rendered = rows.map((card) => {
    const row = document.createElement('tr');
    row.appendChild(cell(card.applicationId, 'mono'));
    row.appendChild(statusCell(card));
    row.appendChild(cell(card.panMasked));
    row.appendChild(cell(card.bureauStatus));
    row.appendChild(cell(time(card.createdAt)));
    row.appendChild(cell(time(card.decidedAt)));
    row.appendChild(
      cell(
        [card.reasonCode, card.comment].filter(Boolean).join(' · ') || '—',
        'comment'
      )
    );
    return row;
  });
  tbody.replaceChildren(...rendered);
}

function tick() {
  refreshIdentity();
  refreshRows();
}

tick();
setInterval(tick, POLL_MS);
