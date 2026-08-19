import apiClient from './client';
import { AuthenticationResponse, LoginRequest, RegisterRequest } from './types';

export const authApi = {
  /**
   * Authenticates a user with email and password.
   */
  async login(request: LoginRequest): Promise<AuthenticationResponse> {
    const response = await apiClient.post<AuthenticationResponse>('/api/v1/auth/login', request);
    return response.data;
  },

  /**
   * Registers a new user account.
   */
  async register(request: RegisterRequest): Promise<AuthenticationResponse> {
    const response = await apiClient.post<AuthenticationResponse>('/api/v1/auth/register', request);
    return response.data;
  },

  /**
   * Revokes the active refresh token session on the backend.
   */
  async logout(refreshToken?: string): Promise<void> {
    if (!refreshToken) return;
    try {
      await apiClient.post('/api/v1/auth/logout', { refreshToken });
    } catch {
      // Best-effort logout: ignore network/auth errors during logout
    }
  },
};

export default authApi;
