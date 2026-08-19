import { AxiosError } from 'axios';
import { ApiErrorResponse, NormalizedApiError } from '../api/types';

/**
 * Normalizes any error (AxiosError, ApiErrorResponse, Error, or unknown)
 * into a structured NormalizedApiError format.
 */
export function normalizeError(error: unknown): NormalizedApiError {
  if (!error) {
    return {
      code: 'UNKNOWN_ERROR',
      message: 'An unexpected error occurred.',
    };
  }

  // Axios Error with backend ApiErrorResponse payload
  if (isAxiosError(error) && error.response?.data) {
    const data = error.response.data as Partial<ApiErrorResponse>;
    return {
      message: data.message || getDefaultMessageForStatus(error.response.status),
      code: data.code || `HTTP_${error.response.status}`,
      status: error.response.status,
      requestId: data.requestId,
      path: data.path,
    };
  }

  // Axios Error without response data (network error, timeout, CORS)
  if (isAxiosError(error)) {
    if (error.code === 'ERR_NETWORK') {
      return {
        code: 'NETWORK_ERROR',
        message: 'Unable to connect to the backend server. Please verify the API is running.',
      };
    }
    if (error.code === 'ECONNABORTED') {
      return {
        code: 'TIMEOUT_ERROR',
        message: 'The request timed out. Please try again.',
      };
    }
    return {
      code: error.code || 'HTTP_ERROR',
      message: error.message || 'Network request failed.',
      status: error.response?.status,
    };
  }

  // Standard JS Error
  if (error instanceof Error) {
    return {
      code: 'CLIENT_ERROR',
      message: error.message,
    };
  }

  // Fallback
  return {
    code: 'UNKNOWN_ERROR',
    message: String(error),
  };
}

function isAxiosError(error: unknown): error is AxiosError {
  return typeof error === 'object' && error !== null && 'isAxiosError' in error;
}

function getDefaultMessageForStatus(status: number): string {
  switch (status) {
    case 400:
      return 'Invalid request parameters.';
    case 401:
      return 'Authentication required. Please sign in.';
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'The requested resource was not found.';
    case 409:
      return 'A conflict occurred with an existing resource.';
    case 410:
      return 'This resource is disabled or no longer available.';
    case 429:
      return 'Too many requests. Please try again later.';
    case 500:
    case 502:
    case 503:
      return 'A server error occurred. Please try again later.';
    default:
      return 'An error occurred processing your request.';
  }
}
