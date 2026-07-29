// Thin fetch wrapper. Base is empty so paths are same-origin (nginx proxies in the
// container, Vite proxies in dev). Override with VITE_API_BASE if you must.
//
// Everything the UI calls goes through here on purpose: in the deployed stack the whole
// app is served under a path prefix (/neo-08) and VITE_API_BASE is how every URL
// picks it up. A raw fetch('/api/...') inside a component works on your laptop and 404s
// on the load balancer.
const BASE = import.meta.env.VITE_API_BASE || '';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body.message) message = body.message;
    } catch {
      /* non-JSON error body */
    }
    const error = new Error(message);
    error.status = res.status;
    throw error;
  }
  if (res.status === 204) return null;
  return res.json();
}

/** Returns { body, headers } so callers can read response headers. */
async function requestWithHeaders(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body.message) message = body.message;
    } catch {
      /* non-JSON error body */
    }
    const error = new Error(message);
    error.status = res.status;
    throw error;
  }
  const body = res.status === 204 ? null : await res.json();
  return { body, headers: res.headers };
}

// This UI only ever READS. Applications arrive from the orchestrator — the real one, or the
// sidecar playing it at http://localhost:9000 — never from a button in here. That is the
// contract: your module is called, it does not call itself.
export const api = {
  health: () => request('/health'),
  info: () => request('/info'),

  // UC 01 — Card Board
  searchCards: (q) =>
    requestWithHeaders(`/cases?q=${encodeURIComponent(q)}&limit=10`),
  listAllCards: () =>
    requestWithHeaders('/cases/all?limit=10'),

  // UC 02 — Card Detail
  getCardDetail: (id) => request(`/cases/${id}`),

  // UC 03 — applicant proxy
  getApplicant: (id) => request(`/cases/${id}/applicant`),

  // UC 08 — Issuing Config
  getCurrentConfig: () => request('/config/current'),
  getConfigHistory: () => request('/config/history'),
  createConfigVersion: (data) => request('/config', {
    method: 'POST',
    body: JSON.stringify(data),
  }),
  getApplication: (id) => request(`/api/v1/applications/${id}`),
};
