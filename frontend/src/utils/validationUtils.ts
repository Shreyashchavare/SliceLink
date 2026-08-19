/**
 * Client-Side Validation Utilities for SliceLink.
 * Matches backend validation rules and RFC standards.
 */

const EMAIL_REGEX = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/;

export function validateEmail(email: string): string | null {
  const trimmed = email.trim();
  if (!trimmed) {
    return 'Email address is required.';
  }
  if (!EMAIL_REGEX.test(trimmed)) {
    return 'Please enter a valid email address.';
  }
  if (trimmed.length > 255) {
    return 'Email address must not exceed 255 characters.';
  }
  return null;
}

export function validatePassword(password: string): string | null {
  if (!password) {
    return 'Password is required.';
  }
  if (password.length < 8) {
    return 'Password must be at least 8 characters long.';
  }
  if (password.length > 100) {
    return 'Password must not exceed 100 characters.';
  }
  return null;
}

export function validateName(name: string): string | null {
  const trimmed = name.trim();
  if (!trimmed) {
    return 'Full name is required.';
  }
  if (trimmed.length < 2) {
    return 'Name must be at least 2 characters long.';
  }
  if (trimmed.length > 100) {
    return 'Name must not exceed 100 characters.';
  }
  return null;
}

export function validateConfirmPassword(password: string, confirmPassword: string): string | null {
  if (!confirmPassword) {
    return 'Please confirm your password.';
  }
  if (password !== confirmPassword) {
    return 'Passwords do not match.';
  }
  return null;
}

export function validateUrl(url: string): string | null {
  const trimmed = url.trim();
  if (!trimmed) {
    return 'Destination URL is required.';
  }
  if (!/^https?:\/\//i.test(trimmed)) {
    return 'Destination URL must start with http:// or https://';
  }
  try {
    const parsed = new URL(trimmed);
    if (!parsed.hostname || !parsed.hostname.includes('.')) {
      return 'Please enter a valid URL with a valid domain name (e.g. example.com).';
    }
  } catch {
    return 'Please enter a valid URL structure.';
  }
  if (trimmed.length > 2048) {
    return 'URL must not exceed 2048 characters.';
  }
  return null;
}
