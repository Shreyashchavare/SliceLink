import React from 'react';
import { Card } from '../common/Card';
import { LoadingSpinner } from '../common/LoadingSpinner';
import { UrlAnalyticsResponse, UrlResponse } from '../../api/types';

interface AnalyticsSummaryCardsProps {
  analytics: UrlAnalyticsResponse | null;
  url: UrlResponse | null;
  loading: boolean;
}

export const AnalyticsSummaryCards: React.FC<AnalyticsSummaryCardsProps> = ({
  analytics,
  url,
  loading,
}) => {
  const formatDate = (isoString?: string) => {
    if (!isoString) return '--';
    try {
      return new Date(isoString).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="analytics-summary-grid">
      <Card title="Total Clicks" subtitle="All-time direct redirect hits">
        <div className="analytics-metric-value" style={{ color: 'var(--color-accent)' }}>
          {loading ? <LoadingSpinner size="sm" /> : analytics?.totalClicks ?? '--'}
        </div>
      </Card>

      <Card title="Link Status" subtitle="Redirection availability">
        <div style={{ marginTop: '0.5rem' }}>
          {url ? (
            <span className={`status-badge status-${url.status.toLowerCase()}`}>
              <span className="status-dot" aria-hidden="true" />
              {url.status}
            </span>
          ) : (
            <span style={{ color: 'var(--color-text-muted)' }}>--</span>
          )}
        </div>
      </Card>

      <Card title="Created On" subtitle="Original creation date">
        <div style={{ fontSize: '1.25rem', fontWeight: 600, color: 'var(--color-text-primary)', marginTop: '0.4rem' }}>
          {formatDate(url?.createdAt)}
        </div>
      </Card>

      <Card title="Recent Events" subtitle="Recorded in telemetry buffer">
        <div className="analytics-metric-value" style={{ color: 'var(--color-primary)' }}>
          {loading ? <LoadingSpinner size="sm" /> : analytics?.recentClicks?.length ?? 0}
        </div>
      </Card>
    </div>
  );
};

export default AnalyticsSummaryCards;
