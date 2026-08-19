import apiClient from './client';
import { CreateUrlRequest, UpdateUrlRequest, UpdateUrlStatusRequest, UrlResponse, UrlStatus } from './types';

export const urlApi = {
  /**
   * Retrieves all shortened URLs owned by the authenticated user.
   */
  async getUrls(): Promise<UrlResponse[]> {
    const response = await apiClient.get<UrlResponse[]>('/api/v1/urls');
    return response.data;
  },

  /**
   * Retrieves details for a specific shortened URL by ID.
   */
  async getUrl(id: number | string): Promise<UrlResponse> {
    const response = await apiClient.get<UrlResponse>(`/api/v1/urls/${id}`);
    return response.data;
  },

  /**
   * Creates a new shortened URL.
   */
  async createUrl(request: CreateUrlRequest): Promise<UrlResponse> {
    const response = await apiClient.post<UrlResponse>('/api/v1/urls', request);
    return response.data;
  },

  /**
   * Updates the destination URL of an existing shortened URL.
   */
  async updateUrl(id: number | string, request: UpdateUrlRequest): Promise<UrlResponse> {
    const response = await apiClient.put<UrlResponse>(`/api/v1/urls/${id}`, request);
    return response.data;
  },

  /**
   * Updates the status (ACTIVE / DISABLED) of a shortened URL.
   */
  async updateStatus(id: number | string, status: UrlStatus): Promise<UrlResponse> {
    const payload: UpdateUrlStatusRequest = { status };
    const response = await apiClient.patch<UrlResponse>(`/api/v1/urls/${id}/status`, payload);
    return response.data;
  },

  /**
   * Deletes a shortened URL by ID.
   */
  async deleteUrl(id: number | string): Promise<void> {
    await apiClient.delete(`/api/v1/urls/${id}`);
  },
};

export default urlApi;
