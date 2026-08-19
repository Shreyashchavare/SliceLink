import React from 'react';
import { Link } from 'react-router-dom';
import { PageContainer } from '../components/common/PageContainer';
import { Button } from '../components/common/Button';

export const NotFoundPage: React.FC = () => {
  return (
    <PageContainer>
      <div style={{ textAlign: 'center', padding: '4rem 1rem' }}>
        <h1 style={{ fontSize: '4.5rem', fontWeight: 800, color: 'var(--color-primary)', lineHeight: 1 }}>404</h1>
        <h2 style={{ fontSize: '1.5rem', fontWeight: 600, marginTop: '1rem', marginBottom: '0.5rem' }}>Page Not Found</h2>
        <p style={{ color: 'var(--color-text-secondary)', maxWidth: '400px', margin: '0 auto 1.5rem auto' }}>
          The page you are looking for does not exist or has been moved.
        </p>
        <Link to="/">
          <Button variant="primary" size="md">Return to Home</Button>
        </Link>
      </div>
    </PageContainer>
  );
};

export default NotFoundPage;
