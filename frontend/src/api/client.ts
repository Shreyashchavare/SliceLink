import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { BackendHealthResponse } from './types';
import { normalizeError } from '../utils/errorUtils';
import { getAccessToken, clearTokens } from '../auth/authStorage';
import { authEvents } from '../auth/authEvents';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

/**
 * Centralized Axios instance for SliceLink API.
 */
export const apiClient: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// Request Interceptor: Attach Authorization Bearer token from authStorage if available
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getAccessToken();
    if (token && config.headers && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Normalize errors and emit 401 session expiration event
apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error) => {
    const status = error.response?.status;
    const requestUrl = error.config?.url || '';

    // Handle 401 Unauthorized for authenticated endpoints (excluding login itself)
    if (status === 401 && !requestUrl.includes('/api/v1/auth/login')) {
      clearTokens();
      authEvents.emitUnauthorized();
    }

    const normalized = normalizeError(error);
    return Promise.reject(normalized);
  }
);

/**
 * Health check helper querying the backend Actuator /actuator/health endpoint.
 */
export async function checkBackendHealth(): Promise<BackendHealthResponse> {
  const response = await apiClient.get<BackendHealthResponse>('/actuator/health');
  return response.data;
}

export default apiClient;
