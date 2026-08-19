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
import {
  validateName,
  validateEmail,
  validatePassword,
  validateConfirmPassword,
} from '../utils/validationUtils';

export const RegisterPage: React.FC = () => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [nameError, setNameError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<NormalizedApiError | null>(null);

  const { register } = useAuth();
  const { showSuccess } = useToast();
  const navigate = useNavigate();

  const handleNameBlur = () => {
    if (name) setNameError(validateName(name));
  };

  const handleEmailBlur = () => {
    if (email) setEmailError(validateEmail(email));
  };

  const handlePasswordBlur = () => {
    if (password) setPasswordError(validatePassword(password));
  };

  const handleConfirmBlur = () => {
    if (confirmPassword) setConfirmError(validateConfirmPassword(password, confirmPassword));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (loading) return;

    setError(null);

    const nErr = validateName(name);
    const eErr = validateEmail(email);
    const pErr = validatePassword(password);
    const cErr = validateConfirmPassword(password, confirmPassword);

    setNameError(nErr);
    setEmailError(eErr);
    setPasswordError(pErr);
    setConfirmError(cErr);

    if (nErr || eErr || pErr || cErr) {
      return;
    }

    setLoading(true);
    try {
      await register({ name: name.trim(), email: email.trim(), password });
      showSuccess('Account created successfully! Welcome to SliceLink.');
      navigate('/dashboard', { replace: true });
    } catch (err) {
      const apiError = err as NormalizedApiError;
      if (apiError.status === 409) {
        setError({
          ...apiError,
          message: 'An account with this email address already exists. Please sign in instead.',
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
        <Card title="Create your SliceLink account" subtitle="Join thousands of developers shortening URLs at scale">
          <ErrorMessage error={error} title="Registration Failed" />

          <form onSubmit={handleSubmit} noValidate>
            <Input
              label="Full Name"
              type="text"
              placeholder="Jane Doe"
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                if (nameError) setNameError(null);
              }}
              onBlur={handleNameBlur}
              error={nameError}
              disabled={loading}
              required
              autoFocus
            />

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
              autoComplete="new-password"
              helperText="Minimum 8 characters"
            />

            <Input
              label="Confirm Password"
              type="password"
              placeholder="••••••••"
              value={confirmPassword}
              onChange={(e) => {
                setConfirmPassword(e.target.value);
                if (confirmError) setConfirmError(null);
              }}
              onBlur={handleConfirmBlur}
              error={confirmError}
              disabled={loading}
              required
              autoComplete="new-password"
            />

            <Button
              type="submit"
              variant="primary"
              isLoading={loading}
              disabled={loading}
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
