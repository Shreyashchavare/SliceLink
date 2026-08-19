import React, { useEffect, useState, useMemo } from 'react';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Input } from '../components/common/Input';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { CreateUrlModal } from '../components/urls/CreateUrlModal';
import { EditUrlModal } from '../components/urls/EditUrlModal';
import { UrlTable } from '../components/urls/UrlTable';
import { urlApi } from '../api/urlApi';
import { NormalizedApiError, UrlResponse } from '../api/types';
import { useToast } from '../context/ToastContext';

export const UrlsPage: React.FC = () => {
  const [urls, setUrls] = useState<UrlResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<NormalizedApiError | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'DISABLED'>('ALL');

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

  const filteredUrls = useMemo(() => {
    return urls.filter((url) => {
      const matchesSearch =
        url.shortCode.toLowerCase().includes(searchQuery.toLowerCase()) ||
        url.originalUrl.toLowerCase().includes(searchQuery.toLowerCase());

      const matchesStatus =
        statusFilter === 'ALL' || url.status === statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [urls, searchQuery, statusFilter]);

  const handleToggleStatus = async (url: UrlResponse) => {
    if (actionLoadingId) return;
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
    if (actionLoadingId) return;
    if (!window.confirm(`Are you sure you want to delete /${url.shortCode}?\n\nDestination: ${url.originalUrl}\n\nThis will permanently remove the short link and clear its cache entry.`)) {
      return;
    }
    setActionLoadingId(url.id);
    try {
      await urlApi.deleteUrl(url.id);
      setUrls((prev) => prev.filter((u) => u.id !== url.id));
      showSuccess(`Shortened link /${url.shortCode} was permanently deleted.`);
    } catch (err) {
      showError((err as NormalizedApiError).message || 'Failed to delete URL.');
    } finally {
      setActionLoadingId(null);
    }
  };

  return (
    <PageContainer
      title="My Shortened URLs"
      subtitle="Manage your link inventory, destination redirects, and active statuses"
      actions={
        <Button variant="primary" size="md" onClick={() => setIsCreateOpen(true)}>
          + Create Short URL
        </Button>
      }
    >
      <ErrorMessage error={error} title="Failed to load URLs" onRetry={fetchUrls} />

      <Card>
        {/* Search & Filter Bar */}
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginBottom: '1.25rem', alignItems: 'center' }}>
          <div style={{ flex: '1', minWidth: '220px', position: 'relative' }}>
            <Input
              type="search"
              placeholder="Search by short code or destination..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              containerClassName=""
              style={{ marginBottom: 0 }}
              aria-label="Filter shortened URLs"
            />
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }} role="group" aria-label="Status filters">
            <Button
              variant={statusFilter === 'ALL' ? 'primary' : 'secondary'}
              size="sm"
              onClick={() => setStatusFilter('ALL')}
            >
              All ({urls.length})
            </Button>
            <Button
              variant={statusFilter === 'ACTIVE' ? 'primary' : 'secondary'}
              size="sm"
              onClick={() => setStatusFilter('ACTIVE')}
            >
              Active ({urls.filter((u) => u.status === 'ACTIVE').length})
            </Button>
            <Button
              variant={statusFilter === 'DISABLED' ? 'primary' : 'secondary'}
              size="sm"
              onClick={() => setStatusFilter('DISABLED')}
            >
              Disabled ({urls.filter((u) => u.status === 'DISABLED').length})
            </Button>
          </div>
        </div>

        {/* Content */}
        {loading && (
          <div style={{ padding: '3rem 0', textAlign: 'center' }}>
            <LoadingSpinner size="md" label="Loading URL inventory..." />
          </div>
        )}

        {!loading && urls.length === 0 && (
          <EmptyState
            title="No shortened URLs yet"
            description="Create your first shortened link to begin redirecting traffic with Redis acceleration."
            action={
              <Button variant="primary" size="sm" onClick={() => setIsCreateOpen(true)}>
                Create Short URL
              </Button>
            }
          />
        )}

        {!loading && urls.length > 0 && filteredUrls.length === 0 && (
          <EmptyState
            title="No matching links found"
            description="Try adjusting your search query or status filter to locate links."
            action={
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  setSearchQuery('');
                  setStatusFilter('ALL');
                }}
              >
                Clear Filters
              </Button>
            }
          />
        )}

        {!loading && filteredUrls.length > 0 && (
          <UrlTable
            urls={filteredUrls}
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

export default UrlsPage;
