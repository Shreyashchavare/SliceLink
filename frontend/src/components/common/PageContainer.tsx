import React, { ReactNode } from 'react';

export interface PageContainerProps {
  title?: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}

export const PageContainer: React.FC<PageContainerProps> = ({
  title,
  subtitle,
  actions,
  children,
  className = '',
}) => {
  return (
    <div className={`page-container ${className}`}>
      {(title || actions) && (
        <div className="page-header">
          <div className="page-header-text">
            {title && <h1 className="page-title">{title}</h1>}
            {subtitle && <p className="page-subtitle">{subtitle}</p>}
          </div>
          {actions && <div className="page-header-actions">{actions}</div>}
        </div>
      )}
      <div className="page-content">{children}</div>
    </div>
  );
};

export default PageContainer;
