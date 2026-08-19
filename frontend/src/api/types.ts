/**
 * SliceLink API Data Types and Contracts
 */

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  requestId: string;
}

export interface NormalizedApiError {
  message: string;
  code: string;
  status?: number;
  requestId?: string;
  path?: string;
}

export interface UserResponse {
  id: number;
  email: string;
  name: string;
  status: 'ACTIVE' | 'DISABLED';
  createdAt: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  name: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface AuthenticationResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: UserResponse;
}

export type UrlStatus = 'ACTIVE' | 'DISABLED';

export interface CreateUrlRequest {
  originalUrl: string;
}

export interface UpdateUrlRequest {
  originalUrl: string;
}

export interface UpdateUrlStatusRequest {
  status: UrlStatus;
}

export interface UrlResponse {
  id: number | string;
  userId: number | string;
  originalUrl: string;
  shortCode: string;
  status: UrlStatus;
  createdAt: string;
  updatedAt?: string | null;
}

export interface RecentClickItem {
  eventId: string;
  occurredAt: string;
}

export interface UrlAnalyticsResponse {
  urlId: number | string;
  shortCode: string;
  totalClicks: number;
  recentClicks: RecentClickItem[];
}

export interface BackendHealthResponse {
  status: string;
  components?: Record<string, { status: string; details?: Record<string, unknown> }>;
}
