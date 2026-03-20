import axiosInstance from './axiosInstance'
import { AiSuggestion, AiSuggestionRequest } from '../types/suggestion'

export const suggestionApi = {
  request: (postId: number, data?: AiSuggestionRequest) =>
    axiosInstance.post<{ success: boolean; data: AiSuggestion }>(`/ai-suggestions/${postId}`, data || {}),

  getLatest: (postId: number) =>
    axiosInstance.get<{ success: boolean; data: AiSuggestion }>(`/ai-suggestions/${postId}/latest`),

  getHistory: (postId: number) =>
    axiosInstance.get<{ success: boolean; data: AiSuggestion[] }>(`/ai-suggestions/${postId}/history`),

  accept: (postId: number, id: number) =>
    axiosInstance.post<{ success: boolean; data: AiSuggestion }>(`/ai-suggestions/${postId}/${id}/accept`),

  reject: (postId: number, id: number) =>
    axiosInstance.post<{ success: boolean }>(`/ai-suggestions/${postId}/${id}/reject`),
}
