import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { PageContainer } from '../components/common/PageContainer';
import { Card } from '../components/common/Card';
import { Input } from '../components/common/Input';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../context/ToastContext';
import { NormalizedApiError } from '../api/types';
import { validateEmail, validatePassword } from '../utils/validationUtils';

export const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<NormalizedApiError | null>(null);

  const { login } = useAuth();
  const { showSuccess } = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const from = (location.state as { from?: { pathname?: string } })?.from?.pathname || '/dashboard';

  const handleEmailBlur = () => {
    if (email) {
      setEmailError(validateEmail(email));
    }
  };

  const handlePasswordBlur = () => {
    if (password) {
      setPasswordError(validatePassword(password));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;

    setError(null);

    const emailValidation = validateEmail(email);
    const passwordValidation = validatePassword(password);

    setEmailError(emailValidation);
    setPasswordError(passwordValidation);

    if (emailValidation || passwordValidation) {
      return;
    }

    setLoading(true);
    try {
      await login({ email: email.trim(), password });
      showSuccess('Welcome back to SliceLink!');
      navigate(from, { replace: true });
    } catch (err) {
      const apiError = err as NormalizedApiError;
      if (apiError.status === 401) {
        setError({
          ...apiError,
          message: 'Invalid email or password. Please verify your credentials and try again.',
        });
      } else if (apiError.status === 429) {
        setError({
          ...apiError,
          message: 'Too many login attempts. Please wait a moment before trying again.',
        });
      } else {
        setError(apiError);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageContainer className="auth-page-container">
      <div style={{ maxWidth: '440px', margin: '0 auto' }}>
        <Card title="Sign in to SliceLink" subtitle="Enter your credentials to manage your shortened links">
          <ErrorMessage error={error} title="Sign In Failed" />

          <form onSubmit={handleSubmit} noValidate>
            <Input
              label="Email Address"
              type="email"
              placeholder="user@example.com"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (emailError) setEmailError(null);
              }}
              onBlur={handleEmailBlur}
              error={emailError}
              disabled={loading}
              required
              autoComplete="email"
              autoFocus
            />

            <Input
              label="Password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (passwordError) setPasswordError(null);
              }}
              onBlur={handlePasswordBlur}
              error={passwordError}
              disabled={loading}
              required
              autoComplete="current-password"
            />

            <Button
              type="submit"
              variant="primary"
              isLoading={loading}
              disabled={loading}
              style={{ width: '100%', marginTop: '0.75rem' }}
            >
              Sign In
            </Button>
          </form>

          <div style={{ marginTop: '1.5rem', textAlign: 'center', fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>
            Don't have an account? <Link to="/register">Create one here</Link>
          </div>
        </Card>
      </div>
    </PageContainer>
  );
};

export default LoginPage;
