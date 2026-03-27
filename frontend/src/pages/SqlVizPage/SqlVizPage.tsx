import { useState, useEffect } from 'react'
import toast from 'react-hot-toast'
import { useSqlVizStore } from '../../store/sqlvizStore'
import type {
  SqlVizScenario,
  IsolationLevel,
  SqlVizWidget,
  SqlVizCreateRequest,
} from '../../types/sqlviz'
import {
  SCENARIO_LABEL,
  ISOLATION_LEVEL_LABEL,
} from '../../types/sqlviz'
import { SqlEditor } from '../../components/Visualization/SqlEditor/SqlEditor'
import { ConcurrencyTimeline } from '../../components/Visualization/ConcurrencyTimeline/ConcurrencyTimeline'
import { ExecutionFlow } from '../../components/Visualization/ExecutionFlow/ExecutionFlow'
import { EmbedGenerator } from '../../components/Visualization/EmbedGenerator/EmbedGenerator'
import { SqlVizHelpPanel } from '../../components/SqlVizHelpPanel/SqlVizHelpPanel'
import styles from './SqlVizPage.module.css'

// ── 타입 ────────────────────────────────────────────────────────────────────

interface SqlRow {
  txId: string
  sql: string
}

// ── 상수 ────────────────────────────────────────────────────────────────────

const SCENARIOS: SqlVizScenario[] = [
  'DEADLOCK',
  'DIRTY_READ',
  'NON_REPEATABLE_READ',
  'PHANTOM_READ',
  'LOST_UPDATE',
  'MVCC',
]

const ISOLATION_LEVELS: IsolationLevel[] = [
  'READ_UNCOMMITTED',
  'READ_COMMITTED',
  'REPEATABLE_READ',
  'SERIALIZABLE',
]

const TX_OPTIONS = ['T1', 'T2', 'T3', 'T4']

const ISO_PRESETS: { label: string; value: IsolationLevel }[] = [
  { label: 'READ UNCOMMITTED', value: 'READ_UNCOMMITTED' },
  { label: 'READ COMMITTED',   value: 'READ_COMMITTED'   },
  { label: 'REPEATABLE READ',  value: 'REPEATABLE_READ'  },
  { label: 'SERIALIZABLE',     value: 'SERIALIZABLE'     },
]

// ── 시나리오 자동 감지 ────────────────────────────────────────────────────────

interface DetectResult {
  scenario: SqlVizScenario
  reason: string
}

function detectScenario(rows: SqlRow[]): DetectResult | null {
  const sqls = rows.map(r => r.sql.toUpperCase())
  const txIds = Array.from(new Set(rows.map(r => r.txId)))

  const hasForUpdate = sqls.some(s => s.includes('FOR UPDATE'))
  const hasInsert    = sqls.some(s => /^\s*INSERT/.test(s) || s.includes('\nINSERT'))
  const hasUpdate    = sqls.some(s => /^\s*UPDATE/.test(s) || s.includes('\nUPDATE'))
  const hasSelect    = sqls.some(s => /^\s*SELECT/.test(s) || s.includes('\nSELECT'))
  const hasRangeWhere = sqls.some(s => /WHERE\s+\w+\s*[><!]/.test(s))
  const multiTx      = txIds.length >= 2

  // DEADLOCK: 여러 TX가 각각 FOR UPDATE, 서로 다른 행
  if (multiTx && hasForUpdate) {
    const rowIds = sqls.flatMap(s => {
      const m = s.match(/WHERE\s+ID\s*=\s*(\d+)/g) ?? []
      return m.map(x => x.replace(/\D/g, ''))
    })
    const uniqueIds = new Set(rowIds)
    if (uniqueIds.size >= 2) {
      return { scenario: 'DEADLOCK', reason: '서로 다른 행에 FOR UPDATE 잠금 요청이 감지됐습니다.' }
    }
  }

  // PHANTOM_READ: 범위 조건 SELECT + INSERT
  if (multiTx && hasRangeWhere && hasInsert) {
    return { scenario: 'PHANTOM_READ', reason: '범위 조건 SELECT와 INSERT가 감지됐습니다.' }
  }

  // NON_REPEATABLE_READ: SELECT + UPDATE, 같은 테이블
  if (multiTx && hasSelect && hasUpdate && !hasForUpdate) {
    const selectTables = sqls.flatMap(s => {
      const m = s.match(/FROM\s+(\w+)/)
      return m ? [m[1]] : []
    })
    const updateTables = sqls.flatMap(s => {
      const m = s.match(/UPDATE\s+(\w+)/)
      return m ? [m[1]] : []
    })
    const overlap = selectTables.some(t => updateTables.includes(t))
    if (overlap) {
      return { scenario: 'NON_REPEATABLE_READ', reason: '같은 테이블에 SELECT와 UPDATE가 감지됐습니다.' }
    }
  }

  // DIRTY_READ: UPDATE(미커밋) + SELECT
  if (multiTx && hasSelect && hasUpdate) {
    return { scenario: 'DIRTY_READ', reason: 'UPDATE와 SELECT가 다른 TX에서 감지됐습니다.' }
  }

  // LOST_UPDATE: 같은 행에 두 UPDATE
  if (multiTx && hasUpdate) {
    const updateTables = sqls.flatMap(s => {
      const m = s.match(/UPDATE\s+(\w+)\s+.*WHERE\s+ID\s*=\s*(\d+)/i)
      return m ? [`${m[1]}:${m[2]}`] : []
    })
    const seen = new Set<string>()
    let dup = false
    for (const t of updateTables) { if (seen.has(t)) { dup = true; break } seen.add(t) }
    if (dup) {
      return { scenario: 'LOST_UPDATE', reason: '같은 행에 두 TX의 UPDATE가 감지됐습니다.' }
    }
  }

  // MVCC: SELECT만 있고 UPDATE 있음
  if (multiTx && hasSelect && hasUpdate) {
    return { scenario: 'MVCC', reason: 'SELECT와 UPDATE 패턴이 감지됐습니다. MVCC 시나리오로 추정합니다.' }
  }

  return null
}

// ── 컴포넌트 ─────────────────────────────────────────────────────────────────

export function SqlVizPage() {
  const { widgets, loading, fetchWidgets, createWidget, deleteWidget } = useSqlVizStore()

  const [title, setTitle]                 = useState('')
  const [rows, setRows]                   = useState<SqlRow[]>([{ txId: 'T1', sql: 'SELECT * FROM orders WHERE id = 1;' }])
  const [scenario, setScenario]           = useState<SqlVizScenario>('DEADLOCK')
  const [isolationLevel, setIsolationLevel] = useState<IsolationLevel>('READ_COMMITTED')
  const [creating, setCreating]           = useState(false)
  const [selectedWidget, setSelectedWidget] = useState<SqlVizWidget | null>(null)
  const [activeTab, setActiveTab]         = useState<'timeline' | 'flow' | 'embed'>('timeline')
  const [previewIsolation, setPreviewIsolation] = useState<IsolationLevel | null>(null)
  const [detectHint, setDetectHint]       = useState<DetectResult | null>(null)

  useEffect(() => {
    fetchWidgets()
  }, [fetchWidgets])

  // ── 행 관리 ──────────────────────────────────────────────────────────────

  const addRow = () => {
    if (rows.length >= 10) { toast.error('SQL은 최대 10개까지 입력 가능합니다.'); return }
    setRows(prev => [...prev, { txId: 'T1', sql: '' }])
    setDetectHint(null)
  }

  const removeRow = (idx: number) => {
    setRows(prev => prev.filter((_, i) => i !== idx))
    setDetectHint(null)
  }

  const updateRow = (idx: number, field: keyof SqlRow, value: string) => {
    setRows(prev => prev.map((r, i) => i === idx ? { ...r, [field]: value } : r))
    setDetectHint(null)
  }

  const moveRow = (idx: number, dir: -1 | 1) => {
    const next = idx + dir
    if (next < 0 || next >= rows.length) return
    setRows(prev => {
      const arr = [...prev]
      const tmp = arr[idx]
      arr[idx] = arr[next]
      arr[next] = tmp
      return arr
    })
  }

  // ── 격리 수준 BEGIN 삽입 ──────────────────────────────────────────────────

  const insertIsoBegin = (iso: IsolationLevel, rowIdx: number) => {
    const isoLabel = ISO_PRESETS.find(p => p.value === iso)?.label ?? iso
    const beginLine = `BEGIN ISOLATION LEVEL ${isoLabel};`
    const current = rows[rowIdx].sql
    const newSql = current.trim() ? `${beginLine}\n${current}` : beginLine
    updateRow(rowIdx, 'sql', newSql)
  }

  // ── sqls 조립 (STEP 주석 포함) ──────────────────────────────────────────

  const buildSqls = (): string[] =>
    rows
      .filter(r => r.sql.trim())
      .map((r, i) => `-- STEP:${i + 1} TX:${r.txId}\n${r.sql}`)

  // ── 시나리오 자동 감지 ────────────────────────────────────────────────────

  const handleDetect = () => {
    const result = detectScenario(rows)
    if (result) {
      setDetectHint(result)
      setScenario(result.scenario)
      toast.success(`시나리오 추정: ${SCENARIO_LABEL[result.scenario]}`)
    } else {
      setDetectHint(null)
      toast('패턴을 감지하지 못했습니다. 시나리오를 직접 선택해주세요.')
    }
  }

  // ── 위젯 생성 ─────────────────────────────────────────────────────────────

  const handleCreate = async () => {
    if (!title.trim()) { toast.error('제목을 입력해주세요.'); return }
    const sqls = buildSqls()
    if (sqls.length === 0) { toast.error('SQL을 최소 1개 입력해주세요.'); return }
    const req: SqlVizCreateRequest = { title: title.trim(), sqls, scenario, isolationLevel }
    setCreating(true)
    try {
      const created = await createWidget(req)
      toast.success('위젯이 생성됐습니다.')
      setSelectedWidget(created)
      setPreviewIsolation(null)
      setActiveTab('timeline')
    } catch {
      toast.error('위젯 생성에 실패했습니다.')
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!confirm('위젯을 삭제할까요?')) return
    try {
      await deleteWidget(id)
      toast.success('삭제됐습니다.')
      if (selectedWidget?.id === id) { setSelectedWidget(null); setPreviewIsolation(null) }
    } catch {
      toast.error('삭제에 실패했습니다.')
    }
  }

  // ── 렌더 ─────────────────────────────────────────────────────────────────

  return (
    <div className={styles.page}>
      <div className={styles.formPanel}>
        <h2 className={styles.heading}>SQL Visualization 위젯 생성</h2>

        {/* 사용법 안내 */}
        <SqlVizHelpPanel />

        {/* 제목 */}
        <div className={styles.field}>
          <label className={styles.label}>제목</label>
          <input
            className={styles.input}
            value={title}
            onChange={e => setTitle(e.target.value)}
            placeholder="예: 데드락 시나리오 분석"
          />
        </div>

        {/* 시나리오 + 격리 수준 */}
        <div className={styles.fieldRow}>
          <div className={styles.field}>
            <div className={styles.scenarioHeader}>
              <label className={styles.label}>시나리오</label>
              <button className={styles.detectBtn} onClick={handleDetect} title="SQL 패턴으로 시나리오 추정">
                🔍 자동 감지
              </button>
            </div>
            <select
              className={styles.select}
              value={scenario}
              onChange={e => { setScenario(e.target.value as SqlVizScenario); setDetectHint(null) }}
            >
              {SCENARIOS.map(s => (
                <option key={s} value={s}>{SCENARIO_LABEL[s]}</option>
              ))}
            </select>
            {detectHint && (
              <div className={styles.detectHint}>
                💡 추정 근거: {detectHint.reason}
              </div>
            )}
          </div>
          <div className={styles.field}>
            <label className={styles.label}>격리 수준</label>
            <select
              className={styles.select}
              value={isolationLevel}
              onChange={e => setIsolationLevel(e.target.value as IsolationLevel)}
            >
              {ISOLATION_LEVELS.map(l => (
                <option key={l} value={l}>{ISOLATION_LEVEL_LABEL[l]}</option>
              ))}
            </select>
          </div>
        </div>

        {/* SQL 행 리스트 */}
        <div className={styles.field}>
          <div className={styles.sqlHeader}>
            <label className={styles.label}>SQL ({rows.length}/10)</label>
            <button className={styles.addBtn} onClick={addRow}>+ 행 추가</button>
          </div>
          <div className={styles.sqlList}>
            {rows.map((row, idx) => (
              <div key={idx} className={styles.sqlItem}>
                {/* 순서 이동 버튼 */}
                <div className={styles.orderBtns}>
                  <button
                    className={styles.orderBtn}
                    onClick={() => moveRow(idx, -1)}
                    disabled={idx === 0}
                    title="위로"
                  >▲</button>
                  <span className={styles.sqlIdx}>#{idx + 1}</span>
                  <button
                    className={styles.orderBtn}
                    onClick={() => moveRow(idx, 1)}
                    disabled={idx === rows.length - 1}
                    title="아래로"
                  >▼</button>
                </div>

                {/* TX 선택 드롭다운 */}
                <select
                  className={styles.txSelect}
                  value={row.txId}
                  onChange={e => updateRow(idx, 'txId', e.target.value)}
                >
                  {TX_OPTIONS.map(t => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>

                {/* SQL 에디터 + ISO 삽입 */}
                <div className={styles.sqlEditorWrap}>
                  <div className={styles.isoInsertRow}>
                    {ISO_PRESETS.map(p => (
                      <button
                        key={p.value}
                        className={styles.isoInsertBtn}
                        onClick={() => insertIsoBegin(p.value, idx)}
                        title={`BEGIN ISOLATION LEVEL ${p.label} 삽입`}
                      >
                        {p.label.split(' ').slice(-1)[0]}
                      </button>
                    ))}
                  </div>
                  <SqlEditor value={row.sql} onChange={v => updateRow(idx, 'sql', v)} height="90px" />
                </div>

                {rows.length > 1 && (
                  <button className={styles.removeBtn} onClick={() => removeRow(idx)}>✕</button>
                )}
              </div>
            ))}
          </div>
        </div>

        <button
          className={styles.createBtn}
          onClick={handleCreate}
          disabled={creating}
        >
          {creating ? '생성 중...' : '시뮬레이션 생성'}
        </button>
      </div>

      {/* 우측 패널 */}
      <div className={styles.rightPanel}>
        {selectedWidget ? (
          <div className={styles.preview}>
            <div className={styles.previewHeader}>
              <h3 className={styles.previewTitle}>{selectedWidget.title}</h3>
              <div className={styles.tabs}>
                <button
                  className={`${styles.tab} ${activeTab === 'timeline' ? styles.activeTab : ''}`}
                  onClick={() => setActiveTab('timeline')}
                >타임라인</button>
                <button
                  className={`${styles.tab} ${activeTab === 'flow' ? styles.activeTab : ''}`}
                  onClick={() => setActiveTab('flow')}
                >실행 흐름</button>
                <button
                  className={`${styles.tab} ${activeTab === 'embed' ? styles.activeTab : ''}`}
                  onClick={() => setActiveTab('embed')}
                >임베드 코드</button>
              </div>
            </div>

            {activeTab !== 'embed' && (
              <div className={styles.isolationToggle}>
                <span className={styles.isolationLabel}>격리 수준:</span>
                {ISOLATION_LEVELS.map(l => (
                  <button
                    key={l}
                    className={`${styles.isolationBtn} ${(previewIsolation ?? selectedWidget.isolationLevel) === l ? styles.isolationBtnActive : ''}`}
                    onClick={() => setPreviewIsolation(l === selectedWidget.isolationLevel ? null : l)}
                    title={ISOLATION_LEVEL_LABEL[l]}
                  >
                    {ISOLATION_LEVEL_LABEL[l]}
                  </button>
                ))}
              </div>
            )}

            <div className={styles.tabContent}>
              {activeTab === 'timeline' && (
                <ConcurrencyTimeline simulation={selectedWidget.simulation} />
              )}
              {activeTab === 'flow' && (
                <ExecutionFlow simulation={selectedWidget.simulation} />
              )}
              {activeTab === 'embed' && (
                <EmbedGenerator widget={selectedWidget} />
              )}
            </div>
          </div>
        ) : (
          <div className={styles.emptyPreview}>
            위젯을 생성하거나 목록에서 선택하면 시각화가 표시됩니다
          </div>
        )}

        <div className={styles.widgetList}>
          <h3 className={styles.listTitle}>내 위젯 목록</h3>
          {loading ? (
            <p className={styles.loading}>불러오는 중...</p>
          ) : widgets.length === 0 ? (
            <p className={styles.empty}>생성된 위젯이 없습니다.</p>
          ) : (
            <ul className={styles.list}>
              {widgets.map(w => (
                <li
                  key={w.id}
                  className={`${styles.listItem} ${selectedWidget?.id === w.id ? styles.selected : ''}`}
                  onClick={() => { setSelectedWidget(w); setPreviewIsolation(null); setActiveTab('timeline') }}
                >
                  <div className={styles.listItemInfo}>
                    <span className={styles.listItemTitle}>{w.title}</span>
                    <span className={styles.listItemMeta}>{SCENARIO_LABEL[w.scenario]} · {ISOLATION_LEVEL_LABEL[w.isolationLevel]}</span>
                  </div>
                  <button
                    className={styles.deleteBtn}
                    onClick={e => { e.stopPropagation(); handleDelete(w.id) }}
                  >삭제</button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}
