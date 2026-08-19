import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { Input } from '../components/common/Input';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../context/ToastContext';
import { NormalizedApiError } from '../api/types';

export const RegisterPage: React.FC = () => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<NormalizedApiError | null>(null);

  const { register } = useAuth();
  const { showSuccess } = useToast();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const trimmedName = name.trim();
    const trimmedEmail = email.trim();

    if (!trimmedName || trimmedName.length < 2) {
      setError({
        code: 'VALIDATION_ERROR',
        message: 'Name must be at least 2 characters.',
      });
      return;
    }

    if (!trimmedEmail) {
      setError({
        code: 'VALIDATION_ERROR',
        message: 'Email address is required.',
      });
      return;
    }

    if (password.length < 8) {
      setError({
        code: 'VALIDATION_ERROR',
        message: 'Password must be at least 8 characters long.',
      });
      return;
    }

    if (password !== confirmPassword) {
      setError({
        code: 'VALIDATION_ERROR',
        message: 'Passwords do not match.',
      });
      return;
    }

    setLoading(true);
    try {
      await register({ name: trimmedName, email: trimmedEmail, password });
      showSuccess('Account created successfully! Welcome to SliceLink.');
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(err as NormalizedApiError);
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageContainer className="auth-page-container">
      <div style={{ maxWidth: '440px', margin: '0 auto' }}>
        <Card title="Create your SliceLink account" subtitle="Join thousands of developers shortening URLs at scale">
          <ErrorMessage error={error} title="Registration Failed" />

          <form onSubmit={handleSubmit}>
            <Input
              label="Full Name"
              type="text"
              placeholder="Jane Doe"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoFocus
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
              helperText="Minimum 8 characters"
            />

            <Input
              label="Confirm Password"
              type="password"
              placeholder="••••••••"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              autoComplete="new-password"
            />

            <Button
              type="submit"
              variant="primary"
              isLoading={loading}
              style={{ width: '100%', marginTop: '0.75rem' }}
            >
              Create Account
            </Button>
          </form>

          <div style={{ marginTop: '1.5rem', textAlign: 'center', fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>
            Already have an account? <Link to="/login">Sign In</Link>
          </div>
        </Card>
      </div>
    </PageContainer>
  );
};

export default RegisterPage;
