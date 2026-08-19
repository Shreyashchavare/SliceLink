import React from 'react';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { EmptyState } from '../components/common/EmptyState';

export const AnalyticsPage: React.FC = () => {
  return (
    <PageContainer
      title="Click Analytics"
      subtitle="Insights into visitor activity and Kafka click stream telemetry"
    >
      <Card title="Traffic & Engagement">
        <EmptyState
          title="No analytics recorded yet"
          description="Analytics will automatically stream in when visitors click your active SliceLink redirects."
          icon="📈"
        />
      </Card>
    </PageContainer>
  );
};

export default AnalyticsPage;
