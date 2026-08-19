import React from 'react';
import { Card } from '../common/Card';
import { EmptyState } from '../common/EmptyState';
import { RecentClickItem } from '../../api/types';

interface RecentClicksTableProps {
  recentClicks: RecentClickItem[];
  shortCode: string;
}

export const RecentClicksTable: React.FC<RecentClicksTableProps> = ({
  recentClicks,
  shortCode,
}) => {
  const formatDateTime = (isoString: string) => {
    try {
      const date = new Date(isoString);
      return date.toLocaleString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  const getRelativeTime = (isoString: string) => {
    try {
      const date = new Date(isoString);
      const now = new Date();
      const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

      if (diffInSeconds < 5) return 'just now';
      if (diffInSeconds < 60) return `${diffInSeconds}s ago`;
      const diffInMinutes = Math.floor(diffInSeconds / 60);
      if (diffInMinutes < 60) return `${diffInMinutes}m ago`;
      const diffInHours = Math.floor(diffInMinutes / 60);
      if (diffInHours < 24) return `${diffInHours}h ago`;
      const diffInDays = Math.floor(diffInHours / 24);
      return `${diffInDays}d ago`;
    } catch {
      return '';
    }
  };

  return (
    <Card
      title="Recent Click Stream Activity"
      subtitle={`Latest recorded redirect events for /${shortCode}`}
    >
      {recentClicks.length === 0 ? (
        <EmptyState
          icon="⚡"
          title="No Click Events Yet"
          description={`No clicks have been recorded for /${shortCode}. Share this link to start capturing live redirect telemetry.`}
        />
      ) : (
        <div className="table-responsive">
          <table className="url-table">
            <thead>
              <tr>
                <th>Event ID</th>
                <th>Short Code</th>
                <th>Timestamp (Local)</th>
                <th style={{ textAlign: 'right' }}>Recency</th>
              </tr>
            </thead>
            <tbody>
              {recentClicks.map((click, index) => (
                <tr key={click.eventId || index}>
                  <td>
                    <code className="event-id-tag">
                      {click.eventId || `evt-${index + 1}`}
                    </code>
                  </td>
                  <td>
                    <span className="short-code-link">/{shortCode}</span>
                  </td>
                  <td>
                    <span className="date-text">{formatDateTime(click.occurredAt)}</span>
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <span className="recency-badge">{getRelativeTime(click.occurredAt)}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
};

export default RecentClicksTable;
