import React from 'react';
import { NormalizedApiError } from '../../api/types';

interface ErrorMessageProps {
  error?: NormalizedApiError | string | null;
  title?: string;
  onRetry?: () => void;
  className?: string;
}

export const ErrorMessage: React.FC<ErrorMessageProps> = ({
  error,
  title = 'An error occurred',
  onRetry,
  className = '',
}) => {
  if (!error) return null;

  const message = typeof error === 'string' ? error : error.message;
  const code = typeof error === 'object' ? error.code : undefined;
  const requestId = typeof error === 'object' ? error.requestId : undefined;

  return (
    <div className={`error-card ${className}`} role="alert">
      <div className="error-icon" aria-hidden="true">⚠️</div>
      <div className="error-content">
        <h4 className="error-title">{title}</h4>
        <p className="error-message">{message}</p>
        {(code || requestId) && (
          <div className="error-meta">
            {code && <span className="error-code">Code: {code}</span>}
            {requestId && <span className="error-request-id">Request ID: {requestId}</span>}
          </div>
        )}
        {onRetry && (
          <button type="button" className="btn btn-sm btn-outline error-retry-btn" onClick={onRetry}>
            Retry
          </button>
        )}
      </div>
    </div>
  );
};

export default ErrorMessage;
