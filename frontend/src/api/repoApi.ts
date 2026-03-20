import axiosInstance from './axiosInstance'
import { Repo, RepoAddRequest, PrSummary } from '../types/repo'

export const repoApi = {
  getList: () =>
    axiosInstance.get<{ success: boolean; data: Repo[] }>('/repos'),

  add: (data: RepoAddRequest) =>
    axiosInstance.post<{ success: boolean; data: Repo }>('/repos', data),

  delete: (id: number) =>
    axiosInstance.delete<{ success: boolean }>(`/repos/${id}`),

  collect: (id: number, wikiPage?: string) =>
    axiosInstance.post<{ success: boolean; data: Repo }>(
      `/repos/${id}/collect${wikiPage ? `?wikiPage=${encodeURIComponent(wikiPage)}` : ''}`
    ),

  getWikiPages: (id: number) =>
    axiosInstance.get<{ success: boolean; data: string[] }>(`/repos/${id}/wiki-pages`),

  getPrList: (id: number) =>
    axiosInstance.get<{ success: boolean; data: PrSummary[] }>(`/repos/${id}/prs`),

  collectPrs: (id: number, prNumbers: number[]) =>
    axiosInstance.post<{ success: boolean; data: Repo }>(`/repos/${id}/collect-prs`, { prNumbers }),
}
