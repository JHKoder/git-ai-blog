import { useState, useEffect } from 'react'
import { sqlvizApi } from '../../api/sqlvizApi'
import { ConcurrencyTimeline } from '../Visualization/ConcurrencyTimeline/ConcurrencyTimeline'
import type { SimulationResult, SqlVizScenario, IsolationLevel } from '../../types/sqlviz'

interface Props {
  dialect: string
  scenario: string
  sql: string
}

const SCENARIO_MAP: Record<string, SqlVizScenario> = {
  deadlock: 'DEADLOCK',
  'lost-update': 'LOST_UPDATE',
  'dirty-read': 'DIRTY_READ',
  'non-repeatable': 'NON_REPEATABLE_READ',
  'phantom-read': 'PHANTOM_READ',
  mvcc: 'MVCC',
}

const DEFAULT_ISOLATION: Record<string, IsolationLevel> = {
  deadlock: 'READ_COMMITTED',
  'lost-update': 'READ_COMMITTED',
  'dirty-read': 'READ_UNCOMMITTED',
  'non-repeatable': 'READ_COMMITTED',
  'phantom-read': 'REPEATABLE_READ',
  mvcc: 'READ_COMMITTED',
}

export function SqlVizMarker({ dialect, scenario, sql }: Props) {
  const [simulation, setSimulation] = useState<SimulationResult | null>(null)
  const [error, setError] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const resolvedScenario: SqlVizScenario = SCENARIO_MAP[scenario] ?? 'DEADLOCK'
    const isolationLevel: IsolationLevel = DEFAULT_ISOLATION[scenario] ?? 'READ_COMMITTED'

    sqlvizApi.create({
      title: `${dialect} ${scenario}`,
      sqls: sql.split('\n').filter(line => line.trim() && !line.trim().startsWith('--')).length > 0
        ? [sql]
        : ['SELECT 1'],
      scenario: resolvedScenario,
      isolationLevel,
    }).then(widget => {
      setSimulation(widget.simulation)
    }).catch(() => {
      setError(true)
    }).finally(() => {
      setLoading(false)
    })
  }, [dialect, scenario, sql])

  if (loading) return <div style={{ padding: '12px', color: 'var(--text-secondary)', fontSize: '13px' }}>SQL 시뮬레이션 로딩 중...</div>
  if (error || !simulation) return null

  return <ConcurrencyTimeline simulation={simulation} />
}
