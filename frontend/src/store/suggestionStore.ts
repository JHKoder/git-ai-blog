import { create } from 'zustand'
import { immer } from 'zustand/middleware/immer'
import { AiSuggestion } from '../types/suggestion'
import { suggestionApi } from '../api/suggestionApi'
import { usePostStore } from './postStore'

interface SuggestionState {
  latestSuggestion: AiSuggestion | null
  history: AiSuggestion[]
  loading: boolean
  fetchLatest: (postId: number) => Promise<void>
  fetchHistory: (postId: number) => Promise<void>
  accept: (postId: number, suggestionId: number) => Promise<void>
  reject: (postId: number, suggestionId: number) => Promise<void>
  clear: () => void
}

export const useSuggestionStore = create<SuggestionState>()(
  immer((set, get) => ({
    latestSuggestion: null,
    history: [],
    loading: false,

    fetchLatest: async (postId) => {
      try {
        const res = await suggestionApi.getLatest(postId)
        set((state) => { state.latestSuggestion = res.data.data })
      } catch {
        set((state) => { state.latestSuggestion = null })
      }
    },

    fetchHistory: async (postId) => {
      try {
        const res = await suggestionApi.getHistory(postId)
        set((state) => { state.history = res.data.data })
      } catch {
        set((state) => { state.history = [] })
      }
    },

    accept: async (postId, suggestionId) => {
      const postStore = usePostStore.getState()
      const prevPost = postStore.currentPost
      const suggestion = get().latestSuggestion

      // Optimistic update
      if (prevPost && suggestion) {
        usePostStore.setState((state: { currentPost: Post | null }) => {
          if (state.currentPost) {
            state.currentPost.status = 'ACCEPTED'
            state.currentPost.content = suggestion.suggestedContent
          }
        })
      }

      try {
        await suggestionApi.accept(postId, suggestionId)
      } catch (e) {
        // 롤백
        if (prevPost) {
          usePostStore.setState((state: { currentPost: Post | null }) => {
            state.currentPost = prevPost
          })
        }
        throw e
      }
    },

    reject: async (postId, suggestionId) => {
      await suggestionApi.reject(postId, suggestionId)
      await usePostStore.getState().fetchPost(postId)
      set((state) => { state.latestSuggestion = null })
    },

    clear: () => {
      set((state) => {
        state.latestSuggestion = null
        state.history = []
      })
    },
  }))
)

// Post 타입 import를 위한 참조
import type { Post } from '../types/post'
