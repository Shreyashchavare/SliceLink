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
    const status = error.response.status;

    // Use backend-provided message if clean, otherwise use tailored status message
    const message = data.message || getDefaultMessageForStatus(status);

    return {
      message,
      code: data.code || `HTTP_${status}`,
      status,
      requestId: data.requestId,
      path: data.path,
    };
  }

  // Axios Error without response data (network error, timeout, CORS)
  if (isAxiosError(error)) {
    if (error.code === 'ERR_NETWORK') {
      return {
        code: 'NETWORK_ERROR',
        message: 'Unable to connect to the SliceLink server. Please check your network connection or try again later.',
      };
    }
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return {
        code: 'TIMEOUT_ERROR',
        message: 'The request took too long to complete. Please try again.',
      };
    }
    return {
      code: error.code || 'HTTP_ERROR',
      message: error.message || 'Network request failed. Please check your connection.',
      status: error.response?.status,
    };
  }

  // Standard JS Error
  if (error instanceof Error) {
    return {
      code: 'CLIENT_ERROR',
      message: error.message || 'An unexpected client error occurred.',
    };
  }

  // Fallback
  return {
    code: 'UNKNOWN_ERROR',
    message: typeof error === 'string' ? error : 'An unexpected error occurred.',
  };
}

function isAxiosError(error: unknown): error is AxiosError {
  return typeof error === 'object' && error !== null && 'isAxiosError' in error;
}

function getDefaultMessageForStatus(status: number): string {
  switch (status) {
    case 400:
      return 'The request could not be processed due to invalid parameters.';
    case 401:
      return 'Authentication required or invalid credentials.';
    case 403:
      return 'You do not have permission to access this resource.';
    case 404:
      return 'The requested resource could not be found.';
    case 409:
      return 'A conflict occurred. A record with this value may already exist.';
    case 410:
      return 'This short link is disabled and no longer redirects traffic.';
    case 422:
      return 'Validation failed on the submitted data.';
    case 429:
      return 'Too many requests. Please slow down and try again later.';
    case 500:
    case 502:
    case 503:
    case 504:
      return 'The server encountered an error while processing your request. Please try again later.';
    default:
      return 'An error occurred while processing your request.';
  }
}
