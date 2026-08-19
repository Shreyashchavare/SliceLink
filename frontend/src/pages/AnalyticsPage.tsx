import React, { useEffect, useState, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { EmptyState } from '../components/common/EmptyState';
import { AnalyticsSummaryCards } from '../components/analytics/AnalyticsSummaryCards';
import { UrlAnalyticsCard } from '../components/analytics/UrlAnalyticsCard';
import { RecentClicksTable } from '../components/analytics/RecentClicksTable';
import { urlApi } from '../api/urlApi';
import { analyticsApi } from '../api/analyticsApi';
import { NormalizedApiError, UrlAnalyticsResponse, UrlResponse } from '../api/types';
import { useToast } from '../context/ToastContext';

export const AnalyticsPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryUrlId = searchParams.get('urlId');

  const [urls, setUrls] = useState<UrlResponse[]>([]);
  const [selectedUrlId, setSelectedUrlId] = useState<string | number | null>(queryUrlId);
  const [analytics, setAnalytics] = useState<UrlAnalyticsResponse | null>(null);

  const [loadingUrls, setLoadingUrls] = useState<boolean>(true);
  const [loadingAnalytics, setLoadingAnalytics] = useState<boolean>(false);
  const [error, setError] = useState<NormalizedApiError | null>(null);

  const { showSuccess } = useToast();

  // Load user's URLs on mount
  useEffect(() => {
    let isMounted = true;

    async function loadUrls() {
      setLoadingUrls(true);
      setError(null);
      try {
        const userUrls = await urlApi.getUrls();
        if (isMounted) {
          setUrls(userUrls);
          // If query param matches an existing URL, select it, otherwise select the first URL
          if (userUrls.length > 0) {
            const matched = queryUrlId ? userUrls.find((u) => String(u.id) === String(queryUrlId)) : null;
            const targetId = matched ? matched.id : userUrls[0].id;
            setSelectedUrlId(targetId);
            setSearchParams({ urlId: String(targetId) }, { replace: true });
          }
        }
      } catch (err) {
        if (isMounted) {
          setError(err as NormalizedApiError);
        }
      } finally {
        if (isMounted) {
          setLoadingUrls(false);
        }
      }
    }

    loadUrls();

    return () => {
      isMounted = false;
    };
  }, []);

  // Fetch analytics whenever selectedUrlId changes
  const fetchAnalytics = useCallback(async (id: string | number, showNotification = false) => {
    setLoadingAnalytics(true);
    setError(null);
    try {
      const data = await analyticsApi.getUrlAnalytics(id);
      setAnalytics(data);
      if (showNotification) {
        showSuccess('Analytics refreshed.');
      }
    } catch (err) {
      setError(err as NormalizedApiError);
    } finally {
      setLoadingAnalytics(false);
    }
  }, [showSuccess]);

  useEffect(() => {
    if (selectedUrlId) {
      fetchAnalytics(selectedUrlId);
    }
  }, [selectedUrlId, fetchAnalytics]);

  const handleSelectUrl = (id: string | number) => {
    setSelectedUrlId(id);
    setSearchParams({ urlId: String(id) });
  };

  const selectedUrl = urls.find((u) => String(u.id) === String(selectedUrlId)) || null;

  return (
    <PageContainer
      title="Click Analytics & Telemetry"
      subtitle="Real-time visitor redirection metrics and Kafka click event telemetry"
      actions={
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          {selectedUrlId && (
            <Button
              variant="secondary"
              size="md"
              onClick={() => fetchAnalytics(selectedUrlId, true)}
              disabled={loadingAnalytics}
              isLoading={loadingAnalytics}
            >
              🔄 Refresh
            </Button>
          )}
          <Link to="/urls">
            <Button variant="primary" size="md">
              Manage Links →
            </Button>
          </Link>
        </div>
      }
    >
      <ErrorMessage
        error={error}
        title="Failed to Load Analytics"
        onRetry={() => {
          if (selectedUrlId) {
            fetchAnalytics(selectedUrlId);
          }
        }}
      />

      {loadingUrls ? (
        <div style={{ padding: '4rem 0', textAlign: 'center' }}>
          <LoadingSpinner size="lg" label="Loading URL inventory..." />
        </div>
      ) : urls.length === 0 ? (
        <Card>
          <EmptyState
            icon="📊"
            title="No Shortened URLs Found"
            description="Create your first shortened link to begin capturing click analytics and Kafka event telemetry."
            action={
              <Link to="/urls">
                <Button variant="primary" size="sm">Create Short URL</Button>
              </Link>
            }
          />
        </Card>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          {/* URL Selector Toolbar */}
          <Card>
            <div className="analytics-selector-container">
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', flex: 1 }}>
                <label htmlFor="url-select" className="input-label" style={{ fontWeight: 600 }}>
                  Select Short Link to Inspect:
                </label>
                <select
                  id="url-select"
                  className="input-field analytics-select"
                  value={selectedUrlId ? String(selectedUrlId) : ''}
                  onChange={(e) => handleSelectUrl(e.target.value)}
                  disabled={loadingAnalytics}
                >
                  {urls.map((url) => (
                    <option key={url.id} value={String(url.id)}>
                      /{url.shortCode} — {url.originalUrl.length > 60 ? `${url.originalUrl.substring(0, 60)}...` : url.originalUrl} ({url.status})
                    </option>
                  ))}
                </select>
              </div>

              {selectedUrl && (
                <div className="analytics-url-quickbadge">
                  <span className={`status-badge status-${selectedUrl.status.toLowerCase()}`}>
                    <span className="status-dot" aria-hidden="true" />
                    {selectedUrl.status}
                  </span>
                </div>
              )}
            </div>
          </Card>

          {/* Analytics Summary Cards */}
          <AnalyticsSummaryCards
            analytics={analytics}
            url={selectedUrl}
            loading={loadingAnalytics}
          />

          {/* Selected URL Details Card */}
          {selectedUrl && <UrlAnalyticsCard url={selectedUrl} />}

          {/* Recent Clicks Stream Table */}
          {analytics && (
            <RecentClicksTable
              recentClicks={analytics.recentClicks || []}
              shortCode={analytics.shortCode || selectedUrl?.shortCode || ''}
            />
          )}
        </div>
      )}
    </PageContainer>
  );
};

export default AnalyticsPage;
