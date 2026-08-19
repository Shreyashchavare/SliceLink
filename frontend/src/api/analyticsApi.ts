import apiClient from './client';
import { UrlAnalyticsResponse } from './types';

export const analyticsApi = {
  /**
   * Retrieves click analytics and recent click events for a specific shortened URL.
   * @param id The URL identifier
   */
  async getUrlAnalytics(id: number | string): Promise<UrlAnalyticsResponse> {
    const response = await apiClient.get<UrlAnalyticsResponse>(`/api/v1/urls/${id}/analytics`);
    return response.data;
  },
};

export default analyticsApi;
