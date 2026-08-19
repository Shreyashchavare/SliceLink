import React from 'react';
import { Link } from 'react-router-dom';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { EmptyState } from '../components/common/EmptyState';
import { Button } from '../components/common/Button';

export const AnalyticsPage: React.FC = () => {
  return (
    <PageContainer
      title="Click Analytics & Telemetry"
      subtitle="Real-time visitor engagement and Kafka streaming event insights"
    >
      <Card title="Analytics Dashboard">
        <EmptyState
          icon="📊"
          title="Analytics Dashboard Coming Soon"
          description="Backend Kafka click analytics event streaming (Phase 7) is fully active. Visual charts, geographic distributions, and historical timeseries visualizations will be connected in an upcoming analytics UI phase."
          action={
            <Link to="/urls">
              <Button variant="primary" size="sm">Manage Your Short URLs</Button>
            </Link>
          }
        />
      </Card>
    </PageContainer>
  );
};

export default AnalyticsPage;
