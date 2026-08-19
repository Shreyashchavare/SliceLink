import apiClient from './client';
import { UserResponse } from './types';

export const userApi = {
  /**
   * Retrieves profile details for the currently authenticated user.
   */
  async getCurrentUser(): Promise<UserResponse> {
    const response = await apiClient.get<UserResponse>('/api/v1/users/me');
    return response.data;
  },
};

export default userApi;
