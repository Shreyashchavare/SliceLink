import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { Input } from '../components/common/Input';
import { Button } from '../components/common/Button';

export const RegisterPage: React.FC = () => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    alert('Registration integration will be completed in the next phase.');
  };

  return (
    <PageContainer className="auth-page-container">
      <div style={{ maxWidth: '440px', margin: '0 auto' }}>
        <Card title="Create your SliceLink account" subtitle="Get started with fast, reliable URL shortening">
          <form onSubmit={handleSubmit}>
            <Input
              label="Full Name"
              type="text"
              placeholder="Alex Smith"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
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
              autoComplete="new-password"
            />
            <Button type="submit" variant="primary" style={{ width: '100%', marginTop: '0.5rem' }}>
              Create Account
            </Button>
          </form>

          <div style={{ marginTop: '1.25rem', textAlign: 'center', fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>
            Already have an account? <Link to="/login">Sign In</Link>
          </div>
        </Card>
      </div>
    </PageContainer>
  );
};

export default RegisterPage;
