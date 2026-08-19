/**
 * Centralized Token Storage Utility for SliceLink Authentication.
 * Never stores passwords, sensitive credentials, or unencrypted secrets.
 */

const ACCESS_TOKEN_KEY = 'slicelink_access_token';
const REFRESH_TOKEN_KEY = 'slicelink_refresh_token';

export function saveAccessToken(token: string): void {
  try {
    localStorage.setItem(ACCESS_TOKEN_KEY, token);
  } catch (err) {
    console.warn('Unable to persist access token in localStorage:', err);
  }
}

export function getAccessToken(): string | null {
  try {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  } catch (err) {
    console.warn('Unable to retrieve access token from localStorage:', err);
    return null;
  }
}

export function removeAccessToken(): void {
  try {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
  } catch (err) {
    console.warn('Unable to remove access token from localStorage:', err);
  }
}

export function saveRefreshToken(token: string): void {
  try {
    localStorage.setItem(REFRESH_TOKEN_KEY, token);
  } catch (err) {
    console.warn('Unable to persist refresh token in localStorage:', err);
  }
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  } catch (err) {
    console.warn('Unable to retrieve refresh token from localStorage:', err);
    return null;
  }
}

export function removeRefreshToken(): void {
  try {
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  } catch (err) {
    console.warn('Unable to remove refresh token from localStorage:', err);
  }
}

export function clearTokens(): void {
  removeAccessToken();
  removeRefreshToken();
}
