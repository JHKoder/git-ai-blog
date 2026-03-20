import { create } from 'zustand'
import { immer } from 'zustand/middleware/immer'
import { Post, PostListItem } from '../types/post'
import { postApi } from '../api/postApi'

interface PostState {
  posts: PostListItem[]
  currentPost: Post | null
  totalPages: number
  currentPage: number
  activeTag: string | undefined
  loading: boolean
  fetchPosts: (page?: number, tag?: string) => Promise<void>
  fetchPost: (id: number) => Promise<void>
  clearCurrentPost: () => void
}

export const usePostStore = create<PostState>()(
  immer((set, get) => ({
    posts: [],
    currentPost: null,
    totalPages: 0,
    currentPage: 0,
    activeTag: undefined,
    loading: false,

    fetchPosts: async (page = 0, tag?: string) => {
      const resolvedTag = tag !== undefined ? tag : get().activeTag
      set((state) => { state.loading = true; state.activeTag = resolvedTag })
      try {
        const res = await postApi.getList(page, 10, resolvedTag)
        set((state) => {
          state.posts = res.data.data.content
          state.totalPages = res.data.data.totalPages
          state.currentPage = res.data.data.number
          state.loading = false
        })
      } catch {
        set((state) => { state.loading = false })
      }
    },

    fetchPost: async (id) => {
      set((state) => { state.loading = true })
      try {
        const res = await postApi.getDetail(id)
        set((state) => {
          state.currentPost = res.data.data
          state.loading = false
        })
      } catch {
        set((state) => { state.loading = false })
      }
    },

    clearCurrentPost: () => {
      set((state) => { state.currentPost = null })
    },
  }))
)
