import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { BackendHealthResponse } from './types';
import { normalizeError } from '../utils/errorUtils';

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

// Request Interceptor: Attach Authorization Bearer token if stored in memory / storage
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Placeholder hook for token attachment in future auth phases
    const token = getStoredToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Format errors cleanly
apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error) => {
    const normalized = normalizeError(error);
    return Promise.reject(normalized);
  }
);

// Lightweight token storage helper (future auth phases will wire full session management)
let inMemoryToken: string | null = null;

export function setApiAuthToken(token: string | null): void {
  inMemoryToken = token;
}

function getStoredToken(): string | null {
  return inMemoryToken;
}

/**
 * Health check helper querying the backend Actuator /actuator/health endpoint.
 */
export async function checkBackendHealth(): Promise<BackendHealthResponse> {
  const response = await apiClient.get<BackendHealthResponse>('/actuator/health');
  return response.data;
}

export default apiClient;
