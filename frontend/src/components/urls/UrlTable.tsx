import React from 'react';
import { useNavigate } from 'react-router-dom';
import { UrlResponse } from '../../api/types';
import { Button } from '../common/Button';
import { CopyUrlButton } from './CopyUrlButton';

interface UrlTableProps {
  urls: UrlResponse[];
  onEdit: (url: UrlResponse) => void;
  onToggleStatus: (url: UrlResponse) => void;
  onDelete: (url: UrlResponse) => void;
  actionLoadingId?: string | number | null;
}

export const UrlTable: React.FC<UrlTableProps> = ({
  urls,
  onEdit,
  onToggleStatus,
  onDelete,
  actionLoadingId,
}) => {
  const navigate = useNavigate();
  const publicBaseUrl = import.meta.env.VITE_PUBLIC_URL || 'http://localhost:8080';

  const formatDate = (isoString: string) => {
    try {
      const date = new Date(isoString);
      return date.toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="table-responsive">
      <table className="url-table">
        <thead>
          <tr>
            <th>Short Code</th>
            <th>Original Destination</th>
            <th>Status</th>
            <th>Created</th>
            <th style={{ textAlign: 'right' }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {urls.map((url) => {
            const isLoading = actionLoadingId === url.id;
            const fullShortUrl = `${publicBaseUrl.replace(/\/$/, '')}/${url.shortCode}`;

            return (
              <tr key={url.id} className={url.status === 'DISABLED' ? 'row-disabled' : ''}>
                <td className="cell-code">
                  <div className="code-container">
                    <a
                      href={fullShortUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="short-code-link"
                      title={`Open ${fullShortUrl}`}
                    >
                      /{url.shortCode}
                    </a>
                    <CopyUrlButton shortCode={url.shortCode} size="sm" variant="ghost" />
                  </div>
                </td>

                <td className="cell-destination">
                  <a
                    href={url.originalUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="destination-link"
                    title={url.originalUrl}
                  >
                    {url.originalUrl}
                  </a>
                </td>

                <td className="cell-status">
                  <span className={`status-badge status-${url.status.toLowerCase()}`}>
                    <span className="status-dot" aria-hidden="true" />
                    {url.status}
                  </span>
                </td>

                <td className="cell-date">
                  <span className="date-text">{formatDate(url.createdAt)}</span>
                </td>

                <td className="cell-actions">
                  <div className="actions-group">
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => navigate(`/analytics?urlId=${url.id}`)}
                      disabled={isLoading}
                      title="View Click Analytics"
                    >
                      📊 Analytics
                    </Button>

                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => onEdit(url)}
                      disabled={isLoading}
                      title="Edit Destination URL"
                    >
                      ✏️ Edit
                    </Button>

                    <Button
                      size="sm"
                      variant={url.status === 'ACTIVE' ? 'outline' : 'secondary'}
                      onClick={() => onToggleStatus(url)}
                      disabled={isLoading}
                      title={url.status === 'ACTIVE' ? 'Disable URL' : 'Enable URL'}
                    >
                      {url.status === 'ACTIVE' ? '⏸️ Disable' : '▶️ Enable'}
                    </Button>

                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() => onDelete(url)}
                      disabled={isLoading}
                      title="Delete shortened URL"
                    >
                      🗑️
                    </Button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

export default UrlTable;
