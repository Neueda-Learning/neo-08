import React, { useCallback, useEffect, useState } from 'react';
import { AppShell, Button, StatusPill } from './design-system';
import RequestsScreen from './components/RequestsScreen.jsx';
import { api } from './api.js';

const POLL_MS = 2000;
const HEALTH_MS = 10000;

/**
 * One read-only operator screen. Applications arrive only from the orchestrator;
 * this UI observes the module and never creates work or bypasses the callback flow.
 */
export default function App() {
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState(null);
  const [health, setHealth] = useState(null);
  const [info, setInfo] = useState(null);

  const reload = useCallback(async () => {
    try {
      setRequests(await api.listApplications());
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    reload();
    const id = setInterval(reload, POLL_MS);
    return () => clearInterval(id);
  }, [reload]);

  const refreshHealth = useCallback(async () => {
    const [healthResult, infoResult] = await Promise.allSettled([api.health(), api.info()]);
    setHealth(healthResult.status === 'fulfilled' ? healthResult.value : null);
    if (infoResult.status === 'fulfilled') {
      setInfo(infoResult.value);
    }
  }, []);

  useEffect(() => {
    refreshHealth();
    const id = setInterval(refreshHealth, HEALTH_MS);
    return () => clearInterval(id);
  }, [refreshHealth]);

  useEffect(() => {
    document.title = info ? `${info.serviceId} · ${info.service}` : 'Card Issuing';
  }, [info]);

  const healthKnown = health != null;
  const up = health?.status === 'UP';
  const healthLabel = healthKnown ? (up ? 'Up' : 'Down') : 'Unavailable';

  return (
    <AppShell
      footer="One of ten modules · applications arrive from the orchestrator, never from this UI"
    >
      <RequestsScreen
        requests={requests}
        error={error}
        info={info}
        actions={
          <div className="app-actions">
            <StatusPill tone={healthKnown ? (up ? 'positive' : 'negative') : 'neutral'}>
              Service {healthLabel}
            </StatusPill>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                reload();
                refreshHealth();
              }}
            >
              Refresh
            </Button>
          </div>
        }
      />
    </AppShell>
  );
}
