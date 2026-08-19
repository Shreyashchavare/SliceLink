import React from 'react';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { EmptyState } from '../components/common/EmptyState';
import { Button } from '../components/common/Button';

export const UrlsPage: React.FC = () => {
  return (
    <PageContainer
      title="URL Management"
      subtitle="Manage your destination targets, short codes, and link status"
      actions={<Button variant="primary" size="md">+ Shorten URL</Button>}
    >
      <Card>
        <EmptyState
          title="No links in your inventory"
          description="Create your first shortened link to begin redirecting traffic with Redis acceleration."
          action={<Button variant="primary" size="sm">Create Short Link</Button>}
        />
      </Card>
    </PageContainer>
  );
};

export default UrlsPage;
