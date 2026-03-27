export type SqlVizScenario =
  | 'DEADLOCK'
  | 'DIRTY_READ'
  | 'NON_REPEATABLE_READ'
  | 'PHANTOM_READ'
  | 'LOST_UPDATE'
  | 'MVCC'

export type IsolationLevel =
  | 'READ_UNCOMMITTED'
  | 'READ_COMMITTED'
  | 'REPEATABLE_READ'
  | 'SERIALIZABLE'

export const SCENARIO_LABEL: Record<SqlVizScenario, string> = {
  DEADLOCK: '데드락',
  DIRTY_READ: 'Dirty Read',
  NON_REPEATABLE_READ: 'Non-Repeatable Read',
  PHANTOM_READ: 'Phantom Read',
  LOST_UPDATE: 'Lost Update',
  MVCC: 'MVCC',
}

export const ISOLATION_LEVEL_LABEL: Record<IsolationLevel, string> = {
  READ_UNCOMMITTED: 'READ UNCOMMITTED',
  READ_COMMITTED: 'READ COMMITTED',
  REPEATABLE_READ: 'REPEATABLE READ',
  SERIALIZABLE: 'SERIALIZABLE',
}

export interface SimulationStep {
  step: number
  txId: string
  operation: string
  sql: string
  result: string
  detail: string
  durationMs: number
  /** race condition / 시뮬레이터 한계 구간. null이면 정상. 프론트에서 ⚠️ 아이콘 + 툴팁 표시. */
  warning?: string | null
}

export interface SimulationResult {
  steps: SimulationStep[]
  summary: string
  hasConflict: boolean
  conflictType: string | null
  /** 이 시뮬레이션의 알려진 한계 목록. 빈 배열이면 표시 안 함. */
  limitations?: string[]
}

export interface SqlVizWidget {
  id: number
  title: string
  sqls: string[]
  scenario: SqlVizScenario
  isolationLevel: IsolationLevel
  simulation: SimulationResult
  embedUrl: string
  hashnodeWidgetCode: string
  createdAt: string
}

export interface SqlVizCreateRequest {
  title: string
  sqls: string[]
  scenario: SqlVizScenario
  isolationLevel: IsolationLevel
}

export interface SqlVizPageResponse {
  content: SqlVizWidget[]
  totalPages: number
  totalElements: number
  number: number
  size: number
}
