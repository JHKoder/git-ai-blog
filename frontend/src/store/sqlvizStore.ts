import { create } from 'zustand'
import { immer } from 'zustand/middleware/immer'
import type { SqlVizWidget, SqlVizCreateRequest } from '../types/sqlviz'
import { sqlvizApi } from '../api/sqlvizApi'

interface SqlVizState {
  widgets: SqlVizWidget[]
  loading: boolean
  fetchWidgets: () => Promise<void>
  createWidget: (req: SqlVizCreateRequest) => Promise<SqlVizWidget>
  deleteWidget: (id: number) => Promise<void>
}

export const useSqlVizStore = create<SqlVizState>()(
  immer((set) => ({
    widgets: [],
    loading: false,

    fetchWidgets: async () => {
      set((state) => { state.loading = true })
      try {
        const data = await sqlvizApi.getList()
        set((state) => {
          state.widgets = data
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
  }))
)
