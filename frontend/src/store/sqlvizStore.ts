import { create } from 'zustand'
import { immer } from 'zustand/middleware/immer'
import type { SqlVizWidget, SqlVizCreateRequest } from '../types/sqlviz'
import { sqlvizApi } from '../api/sqlvizApi'

interface SqlVizState {
  widgets: SqlVizWidget[]
  loading: boolean
  page: number
  pageSize: number
  totalPages: number
  totalElements: number
  fetchWidgets: (page: number, size: number) => Promise<void>
  createWidget: (req: SqlVizCreateRequest) => Promise<SqlVizWidget>
  deleteWidget: (id: number) => Promise<void>
  setPage: (page: number) => void
  setPageSize: (size: number) => void
}

export const useSqlVizStore = create<SqlVizState>()(
  immer((set) => ({
    widgets: [],
    loading: false,
    page: 0,
    pageSize: 10,
    totalPages: 0,
    totalElements: 0,

    fetchWidgets: async (page, size) => {
      set((state) => { state.loading = true })
      try {
        const data = await sqlvizApi.getList(page, size)
        set((state) => {
          state.widgets = data.content
          state.totalPages = data.totalPages
          state.totalElements = data.totalElements
          state.page = data.number
          state.pageSize = data.size
          state.loading = false
        })
      } catch {
        set((state) => { state.loading = false })
      }
    },

    createWidget: async (req) => {
      const created = await sqlvizApi.create(req)
      set((state) => {
        state.widgets.unshift(created)
      })
      return created
    },

    deleteWidget: async (id) => {
      await sqlvizApi.delete(id)
      set((state) => {
        state.widgets = state.widgets.filter(w => w.id !== id)
      })
    },

    setPage: (page) => set((state) => { state.page = page }),
    setPageSize: (size) => set((state) => { state.pageSize = size }),
  }))
)
