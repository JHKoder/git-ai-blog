import axiosInstance from './axiosInstance'
import { Post, PostPage, AiUsage } from '../types/post'

export const postApi = {
  create: (data: { title: string; content: string; contentType: string; tags?: string[] }) =>
    axiosInstance.post<{ success: boolean; data: Post }>('/posts', data),

  getList: (page = 0, size = 10, tag?: string) =>
    axiosInstance.get<{ success: boolean; data: PostPage }>(
      `/posts?page=${page}&size=${size}${tag ? `&tag=${encodeURIComponent(tag)}` : ''}`
    ),

  getDetail: (id: number) =>
    axiosInstance.get<{ success: boolean; data: Post }>(`/posts/${id}`),

  update: (id: number, data: { title: string; content: string; tags?: string[] }) =>
    axiosInstance.put<{ success: boolean; data: Post }>(`/posts/${id}`, data),

  delete: (id: number) =>
    axiosInstance.delete<{ success: boolean }>(`/posts/${id}`),

  publish: (id: number) =>
    axiosInstance.post<{ success: boolean; data: Post }>(`/posts/${id}/publish`),

  importFromHashnode: () =>
    axiosInstance.post<{ success: boolean; data: Post[] }>('/posts/import-hashnode'),

  syncHashnode: () =>
    axiosInstance.post<{ success: boolean; data: { added: number; updated: number; deleted: number } }>('/posts/sync-hashnode'),

  getAiUsage: () =>
    axiosInstance.get<{ success: boolean; data: AiUsage }>('/posts/ai-usage'),

  generateImage: (prompt: string, model: string) =>
    axiosInstance.post<{ success: boolean; data: string }>('/posts/generate-image', { prompt, model }),
}
