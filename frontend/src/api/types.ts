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

export interface AuthenticationResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: UserResponse;
}

export interface UrlResponse {
  id: string;
  userId: string;
  originalUrl: string;
  shortCode: string;
  shortUrl: string;
  status: 'ACTIVE' | 'DISABLED';
  createdAt: string;
  updatedAt: string;
}

export interface RecentClickItem {
  eventId: string;
  occurredAt: string;
}

export interface UrlAnalyticsResponse {
  urlId: number;
  shortCode: string;
  totalClicks: number;
  recentClicks: RecentClickItem[];
}

export interface BackendHealthResponse {
  status: string;
  components?: Record<string, { status: string; details?: Record<string, unknown> }>;
}
