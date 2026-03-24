import axiosInstance from './axiosInstance'
import { Prompt, PromptRequest } from '../types/prompt'

export const promptApi = {
  getMyPrompts: () =>
    axiosInstance.get<{ success: boolean; data: Prompt[] }>('/prompts'),

  create: (data: PromptRequest) =>
    axiosInstance.post<{ success: boolean; data: Prompt }>('/prompts', data),

  update: (id: number, data: PromptRequest) =>
    axiosInstance.put<{ success: boolean; data: Prompt }>(`/prompts/${id}`, data),

  delete: (id: number) =>
    axiosInstance.delete<{ success: boolean; data: null }>(`/prompts/${id}`),

  getPopular: () =>
    axiosInstance.get<{ success: boolean; data: Prompt[] }>('/prompts/popular'),

  getPopularByMember: (memberId: number) =>
    axiosInstance.get<{ success: boolean; data: Prompt[] }>(`/prompts/members/${memberId}/popular`),
}
