import axiosInstance from './axiosInstance'
import { Member, HashnodeConnectRequest, ApiKeyUpdateRequest } from '../types/member'
import { AiUsage } from '../types/post'

export const memberApi = {
  getMe: () =>
    axiosInstance.get<{ success: boolean; data: Member }>('/members/me'),

  connectHashnode: (data: HashnodeConnectRequest) =>
    axiosInstance.post<{ success: boolean; data: Member }>('/members/hashnode-connect', data),

  disconnectHashnode: () =>
    axiosInstance.delete<{ success: boolean }>('/members/hashnode-connect'),

  updateApiKeys: (data: ApiKeyUpdateRequest) =>
    axiosInstance.patch<{ success: boolean; data: Member }>('/members/api-keys', data),

  getAiUsage: () =>
    axiosInstance.get<{ success: boolean; data: AiUsage }>('/posts/ai-usage'),
}
