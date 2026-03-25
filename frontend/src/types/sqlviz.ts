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
}

export interface SimulationResult {
  steps: SimulationStep[]
  summary: string
  hasConflict: boolean
  conflictType: string
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
