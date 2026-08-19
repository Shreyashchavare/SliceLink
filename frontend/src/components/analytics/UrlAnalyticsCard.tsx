import React from 'react';
import { Card } from '../common/Card';
import { UrlResponse } from '../../api/types';
import { CopyUrlButton } from '../urls/CopyUrlButton';

interface UrlAnalyticsCardProps {
  url: UrlResponse;
}

export const UrlAnalyticsCard: React.FC<UrlAnalyticsCardProps> = ({ url }) => {
  const publicBaseUrl = import.meta.env.VITE_PUBLIC_URL || 'http://localhost:8080';
  const fullShortUrl = `${publicBaseUrl.replace(/\/$/, '')}/${url.shortCode}`;

  return (
    <Card title="Target Link Details" subtitle="Active redirection routing and destination">
      <div className="analytics-details-grid">
        <div className="analytics-detail-item">
          <span className="analytics-detail-label">Short Code</span>
          <div className="analytics-detail-content" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <a
              href={fullShortUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="short-code-link"
              style={{ fontSize: '1.1rem' }}
            >
              /{url.shortCode}
            </a>
            <CopyUrlButton shortCode={url.shortCode} size="sm" variant="secondary" />
          </div>
        </div>

        <div className="analytics-detail-item">
          <span className="analytics-detail-label">Original Destination URL</span>
          <div className="analytics-detail-content">
            <a
              href={url.originalUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="destination-link"
              style={{ fontSize: '0.95rem', wordBreak: 'break-all', whiteSpace: 'normal' }}
            >
              {url.originalUrl} ↗
            </a>
          </div>
        </div>
      </div>
    </Card>
  );
};

export default UrlAnalyticsCard;
