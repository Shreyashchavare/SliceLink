import React from 'react';

interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  label?: string;
  className?: string;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  size = 'md',
  label = 'Loading...',
  className = '',
}) => {
  return (
    <div className={`spinner-container ${className}`} role="status" aria-live="polite">
      <div className={`spinner spinner-${size}`} />
      {label && <span className="spinner-label">{label}</span>}
    </div>
  );
};

export default LoadingSpinner;
