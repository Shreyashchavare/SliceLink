import React, { useEffect, useState } from 'react';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { checkBackendHealth } from '../api/client';
import { BackendHealthResponse, NormalizedApiError } from '../api/types';

export const ConnectivityPage: React.FC = () => {
  const [health, setHealth] = useState<BackendHealthResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<NormalizedApiError | null>(null);
  const [latency, setLatency] = useState<number | null>(null);

  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  const testConnection = async () => {
    setLoading(true);
    setError(null);
    const start = performance.now();
    try {
      const data = await checkBackendHealth();
      const duration = Math.round(performance.now() - start);
      setHealth(data);
      setLatency(duration);
    } catch (err) {
      setError(err as NormalizedApiError);
      setHealth(null);
      setLatency(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    testConnection();
  }, []);

  return (
    <PageContainer
      title="Backend Connectivity & Health"
      subtitle="Verify connectivity and CORS configuration between frontend and backend API"
      actions={
        <Button variant="primary" size="md" onClick={testConnection} isLoading={loading}>
          Ping Health Endpoint
        </Button>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', maxWidth: '700px' }}>
        <Card title="API Configuration">
          <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: '0.75rem', fontSize: '0.925rem' }}>
            <span style={{ color: 'var(--color-text-secondary)' }}>Configured Base URL:</span>
            <code style={{ color: 'var(--color-accent)', fontFamily: 'var(--font-mono)' }}>{apiBaseUrl}</code>

            <span style={{ color: 'var(--color-text-secondary)' }}>Target Endpoint:</span>
            <code style={{ color: 'var(--color-text-primary)', fontFamily: 'var(--font-mono)' }}>/actuator/health</code>

            <span style={{ color: 'var(--color-text-secondary)' }}>Status:</span>
            <span>
              {loading && <LoadingSpinner size="sm" label="Testing connection..." />}
              {!loading && health && (
                <span style={{ color: 'var(--color-success)', fontWeight: 600 }}>
                  ● Connected ({health.status})
                </span>
              )}
              {!loading && error && (
                <span style={{ color: 'var(--color-danger)', fontWeight: 600 }}>
                  ● Offline / Unreachable
                </span>
              )}
            </span>

            {latency !== null && (
              <>
                <span style={{ color: 'var(--color-text-secondary)' }}>Roundtrip Latency:</span>
                <span style={{ color: 'var(--color-text-primary)' }}>{latency} ms</span>
              </>
            )}
          </div>
        </Card>

        {error && (
          <ErrorMessage
            error={error}
            title="Backend Connection Failed"
            onRetry={testConnection}
          />
        )}

        {health && (
          <Card title="Raw Health Payload">
            <pre style={{
              backgroundColor: 'rgba(0, 0, 0, 0.3)',
              padding: '1rem',
              borderRadius: 'var(--radius-sm)',
              fontSize: '0.85rem',
              fontFamily: 'var(--font-mono)',
              overflowX: 'auto',
            }}>
              {JSON.stringify(health, null, 2)}
            </pre>
          </Card>
        )}
      </div>
    </PageContainer>
  );
};

export default ConnectivityPage;
