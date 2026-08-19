import React from 'react';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { EmptyState } from '../components/common/EmptyState';
import { Button } from '../components/common/Button';

export const DashboardPage: React.FC = () => {
  return (
    <PageContainer
      title="Dashboard"
      subtitle="Overview of your link performance and active redirects"
      actions={<Button variant="primary" size="md">+ New Link</Button>}
    >
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
        <Card title="Total Links" subtitle="Active short links">
          <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--color-primary)' }}>--</div>
        </Card>
        <Card title="Total Clicks" subtitle="All time click events">
          <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--color-accent)' }}>--</div>
        </Card>
        <Card title="Cache Hit Rate" subtitle="Redis performance">
          <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--color-success)' }}>--%</div>
        </Card>
      </div>

      <Card title="Recent Shortened Links">
        <EmptyState
          title="No links created yet"
          description="Your shortened links and their real-time performance metrics will show up here."
          action={<Button variant="outline" size="sm">Shorten your first URL</Button>}
        />
      </Card>
    </PageContainer>
  );
};

export default DashboardPage;
