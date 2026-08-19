import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { CreateUrlModal } from '../components/urls/CreateUrlModal';
import { UrlTable } from '../components/urls/UrlTable';
import { EditUrlModal } from '../components/urls/EditUrlModal';
import { urlApi } from '../api/urlApi';
import { NormalizedApiError, UrlResponse } from '../api/types';
import { useToast } from '../context/ToastContext';

export const DashboardPage: React.FC = () => {
  const [urls, setUrls] = useState<UrlResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<NormalizedApiError | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState<boolean>(false);
  const [editingUrl, setEditingUrl] = useState<UrlResponse | null>(null);
  const [actionLoadingId, setActionLoadingId] = useState<string | number | null>(null);

  const { showSuccess, showError } = useToast();

  const fetchUrls = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await urlApi.getUrls();
      setUrls(data);
    } catch (err) {
      setError(err as NormalizedApiError);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUrls();
  }, []);

  const totalUrls = urls.length;
  const activeUrls = urls.filter((u) => u.status === 'ACTIVE').length;
  const disabledUrls = urls.filter((u) => u.status === 'DISABLED').length;

  const handleToggleStatus = async (url: UrlResponse) => {
    setActionLoadingId(url.id);
    const newStatus = url.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      const updated = await urlApi.updateStatus(url.id, newStatus);
      setUrls((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
      showSuccess(`URL /${url.shortCode} is now ${newStatus.toLowerCase()}.`);
    } catch (err) {
      showError((err as NormalizedApiError).message || 'Failed to update URL status.');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleDelete = async (url: UrlResponse) => {
    if (!window.confirm(`Are you sure you want to delete /${url.shortCode}?`)) {
      return;
    }
    setActionLoadingId(url.id);
    try {
      await urlApi.deleteUrl(url.id);
      setUrls((prev) => prev.filter((u) => u.id !== url.id));
      showSuccess(`Shortened link /${url.shortCode} was deleted.`);
    } catch (err) {
      showError((err as NormalizedApiError).message || 'Failed to delete URL.');
    } finally {
      setActionLoadingId(null);
    }
  };

  return (
    <PageContainer
      title="Dashboard"
      subtitle="Overview of your active link portfolio, redirection status, and telemetry"
      actions={
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <Link to="/analytics">
            <Button variant="secondary" size="md">
              📊 View Analytics →
            </Button>
          </Link>
          <Button variant="primary" size="md" onClick={() => setIsCreateOpen(true)}>
            + Create Short URL
          </Button>
        </div>
      }
    >
      <ErrorMessage error={error} title="Failed to load dashboard" onRetry={fetchUrls} />

      {/* Metrics Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
        <Card title="Total Links" subtitle="Shortened URLs generated">
          <div style={{ fontSize: '2.25rem', fontWeight: 700, color: 'var(--color-primary)' }}>
            {loading ? <LoadingSpinner size="sm" /> : totalUrls}
          </div>
        </Card>

        <Card title="Active Links" subtitle="Receiving live redirect traffic">
          <div style={{ fontSize: '2.25rem', fontWeight: 700, color: 'var(--color-success)' }}>
            {loading ? <LoadingSpinner size="sm" /> : activeUrls}
          </div>
        </Card>

        <Card title="Disabled Links" subtitle="Temporarily paused (410 Gone)">
          <div style={{ fontSize: '2.25rem', fontWeight: 700, color: 'var(--color-danger)' }}>
            {loading ? <LoadingSpinner size="sm" /> : disabledUrls}
          </div>
        </Card>
      </div>

      {/* Recent Links Table */}
      <Card
        title="Recent Short Links"
        subtitle="Recently created and updated redirection links"
        headerAction={
          urls.length > 0 && (
            <Link to="/urls">
              <Button variant="ghost" size="sm">View All ({urls.length}) →</Button>
            </Link>
          )
        }
      >
        {loading && (
          <div style={{ padding: '3rem 0', textAlign: 'center' }}>
            <LoadingSpinner size="md" label="Loading links..." />
          </div>
        )}

        {!loading && urls.length === 0 && (
          <EmptyState
            title="No links created yet"
            description="Create your first shortened link to begin accelerating redirects with Redis."
            action={
              <Button variant="primary" size="sm" onClick={() => setIsCreateOpen(true)}>
                Create Short URL
              </Button>
            }
          />
        )}

        {!loading && urls.length > 0 && (
          <UrlTable
            urls={urls.slice(0, 5)}
            onEdit={(u) => setEditingUrl(u)}
            onToggleStatus={handleToggleStatus}
            onDelete={handleDelete}
            actionLoadingId={actionLoadingId}
          />
        )}
      </Card>

      {/* Modals */}
      <CreateUrlModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onSuccess={(created) => setUrls((prev) => [created, ...prev])}
      />

      <EditUrlModal
        isOpen={Boolean(editingUrl)}
        url={editingUrl}
        onClose={() => setEditingUrl(null)}
        onSuccess={(updated) => setUrls((prev) => prev.map((u) => (u.id === updated.id ? updated : u)))}
      />
    </PageContainer>
  );
};

export default DashboardPage;
