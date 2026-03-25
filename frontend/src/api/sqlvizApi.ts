import api from './axiosInstance'
import type { SqlVizWidget, SqlVizCreateRequest } from '../types/sqlviz'

interface ApiResponse<T> {
  data: T
  message: string
  success: boolean
}

export const sqlvizApi = {
  create: (req: SqlVizCreateRequest) =>
    api.post<ApiResponse<SqlVizWidget>>('/sqlviz', req).then(r => r.data.data),

  getList: () =>
    api.get<ApiResponse<SqlVizWidget[]>>('/sqlviz').then(r => r.data.data),

  delete: (id: number) =>
    api.delete<ApiResponse<null>>(`/sqlviz/${id}`),

  getEmbed: (id: number) =>
    api.get<ApiResponse<SqlVizWidget>>(`/embed/sqlviz/${id}`).then(r => r.data.data),
}
