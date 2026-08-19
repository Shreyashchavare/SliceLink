import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { Input } from '../components/common/Input';
import { Button } from '../components/common/Button';

export const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // Foundational placeholder for future Phase 10 authentication integration
    alert('Authentication integration will be completed in the next phase.');
  };

  return (
    <PageContainer className="auth-page-container">
      <div style={{ maxWidth: '440px', margin: '0 auto' }}>
        <Card title="Sign in to SliceLink" subtitle="Enter your email and password to access your dashboard">
          <form onSubmit={handleSubmit}>
            <Input
              label="Email Address"
              type="email"
              placeholder="user@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
            <Input
              label="Password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
            <Button type="submit" variant="primary" style={{ width: '100%', marginTop: '0.5rem' }}>
              Sign In
            </Button>
          </form>

          <div style={{ marginTop: '1.25rem', textAlign: 'center', fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>
            Don't have an account? <Link to="/register">Create one</Link>
          </div>
        </Card>
      </div>
    </PageContainer>
  );
};

export default LoginPage;
