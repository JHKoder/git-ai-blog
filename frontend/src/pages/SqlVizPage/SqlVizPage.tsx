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

interface TxEditor {
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
  'LOCK_WAIT',
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

function detectScenario(editors: TxEditor[]): DetectResult | null {
  const sqls = editors.map(e => e.sql.toUpperCase())
  const txCount = editors.filter(e => e.sql.trim()).length

  const hasForUpdate  = sqls.some(s => s.includes('FOR UPDATE'))
  const hasInsert     = sqls.some(s => /\bINSERT\b/.test(s))
  const hasUpdate     = sqls.some(s => /\bUPDATE\b/.test(s))
  const hasSelect     = sqls.some(s => /\bSELECT\b/.test(s))
  const hasRangeWhere = sqls.some(s => /WHERE\s+\w+\s*[><!]/.test(s))
  const multiTx       = txCount >= 2

  // LOCK_WAIT: FOR UPDATE가 같은 행에 집중 (데드락 순환 없음) — 단순 락 대기
  if (multiTx && hasForUpdate) {
    const rowIds = sqls.flatMap(s => {
      const m = s.match(/WHERE\s+ID\s*=\s*(\d+)/g) ?? []
      return m.map(x => x.replace(/\D/g, ''))
    })
    const uniqueRows = new Set(rowIds)
    if (uniqueRows.size === 1 && rowIds.length >= 2) {
      return { scenario: 'LOCK_WAIT', reason: '같은 행에 여러 TX의 FOR UPDATE가 감지됐습니다. 단순 락 대기 시나리오로 추정합니다.' }
    }

    // DEADLOCK: 서로 다른 행에 교차 FOR UPDATE
    if (uniqueRows.size >= 2) {
      return { scenario: 'DEADLOCK', reason: '서로 다른 행에 FOR UPDATE 잠금 요청이 감지됐습니다.' }
    }
  }

  // PHANTOM_READ: 범위 조건 SELECT + INSERT
  if (multiTx && hasRangeWhere && hasInsert) {
    return { scenario: 'PHANTOM_READ', reason: '범위 조건 SELECT와 INSERT가 감지됐습니다.' }
  }

  // NON_REPEATABLE_READ: SELECT + UPDATE, 같은 테이블
  if (multiTx && hasSelect && hasUpdate && !hasForUpdate) {
    const selectTables = sqls.flatMap(s => { const m = s.match(/FROM\s+(\w+)/); return m ? [m[1]] : [] })
    const updateTables = sqls.flatMap(s => { const m = s.match(/UPDATE\s+(\w+)/); return m ? [m[1]] : [] })
    if (selectTables.some(t => updateTables.includes(t))) {
      return { scenario: 'NON_REPEATABLE_READ', reason: '같은 테이블에 SELECT와 UPDATE가 감지됐습니다.' }
    }
  }

  // DIRTY_READ: UPDATE + SELECT
  if (multiTx && hasSelect && hasUpdate) {
    return { scenario: 'DIRTY_READ', reason: 'UPDATE와 SELECT가 다른 TX에서 감지됐습니다.' }
  }

  // LOST_UPDATE: 같은 행에 두 UPDATE
  if (multiTx && hasUpdate) {
    const updateKeys = sqls.flatMap(s => {
      const m = s.match(/UPDATE\s+(\w+)\s+.*WHERE\s+ID\s*=\s*(\d+)/i)
      return m ? [`${m[1]}:${m[2]}`] : []
    })
    const seen = new Set<string>()
    let dup = false
    for (const k of updateKeys) { if (seen.has(k)) { dup = true; break } seen.add(k) }
    if (dup) return { scenario: 'LOST_UPDATE', reason: '같은 행에 두 TX의 UPDATE가 감지됐습니다.' }
  }

  // MVCC
  if (multiTx && hasSelect && hasUpdate) {
    return { scenario: 'MVCC', reason: 'SELECT와 UPDATE 패턴이 감지됐습니다. MVCC 시나리오로 추정합니다.' }
  }

  return null
}

// ── 유틸 ─────────────────────────────────────────────────────────────────────

/** 에디터 내용이 이미 BEGIN으로 시작하는지 확인 (-- 주석 줄 건너뜀) */
function startsWithBegin(sql: string): boolean {
  const lines = sql.split('\n')
  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('--')) continue
    return trimmed.toUpperCase().startsWith('BEGIN')
  }
  return false
}

/** 다음 STEP 번호를 전체 에디터에서 계산 */
function nextStepNumber(editors: TxEditor[]): number {
  let max = 0
  for (const e of editors) {
    const matches = e.sql.match(/--\s*STEP\s*:\s*(\d+)/gi) ?? []
    for (const m of matches) {
      const n = parseInt(m.replace(/\D/g, ''), 10)
      if (n > max) max = n
    }
  }
  return max + 1
}

// ── 컴포넌트 ─────────────────────────────────────────────────────────────────

export function SqlVizPage() {
  const { widgets, loading, totalPages, page, pageSize, fetchWidgets, createWidget, deleteWidget, setPage, setPageSize } = useSqlVizStore()

  const [title, setTitle]                     = useState('')
  const [editors, setEditors]                 = useState<TxEditor[]>([
    { txId: 'T1', sql: '' },
    { txId: 'T2', sql: '' },
  ])
  const [scenario, setScenario]               = useState<SqlVizScenario>('DEADLOCK')
  const [isolationLevel, setIsolationLevel]   = useState<IsolationLevel>('READ_COMMITTED')
  const [creating, setCreating]               = useState(false)
  const [selectedWidget, setSelectedWidget]   = useState<SqlVizWidget | null>(null)
  const [activeTab, setActiveTab]             = useState<'timeline' | 'flow' | 'embed'>('timeline')
  const [previewIsolation, setPreviewIsolation] = useState<IsolationLevel | null>(null)
  const [detectHint, setDetectHint]           = useState<DetectResult | null>(null)

  useEffect(() => {
    fetchWidgets(page, pageSize)
  }, [fetchWidgets, page, pageSize])

  // ── 에디터 관리 ──────────────────────────────────────────────────────────

  const addEditor = () => {
    const used = new Set(editors.map(e => e.txId))
    const next = TX_OPTIONS.find(t => !used.has(t))
    if (!next) { toast.error('최대 4개 TX 에디터까지 추가 가능합니다.'); return }
    setEditors(prev => [...prev, { txId: next, sql: '' }])
    setDetectHint(null)
  }

  const removeEditor = (idx: number) => {
    if (editors.length <= 1) { toast.error('에디터는 최소 1개 필요합니다.'); return }
    setEditors(prev => prev.filter((_, i) => i !== idx))
    setDetectHint(null)
  }

  const updateEditor = (idx: number, field: keyof TxEditor, value: string) => {
    setEditors(prev => prev.map((e, i) => i === idx ? { ...e, [field]: value } : e))
    setDetectHint(null)
  }

  // ── ISO BEGIN 삽입 ────────────────────────────────────────────────────────

  const insertIsoBegin = (iso: IsolationLevel, editorIdx: number) => {
    const current = editors[editorIdx].sql
    // 이미 BEGIN으로 시작하면 스킵
    if (startsWithBegin(current)) {
      toast('이미 BEGIN 구문이 있습니다.')
      return
    }
    const isoLabel = ISO_PRESETS.find(p => p.value === iso)?.label ?? iso
    const stepN = nextStepNumber(editors)
    const insertLine = `-- STEP:${stepN}\nBEGIN ISOLATION LEVEL ${isoLabel};`
    const newSql = current.trim() ? `${insertLine}\n${current}` : insertLine
    updateEditor(editorIdx, 'sql', newSql)
  }

  // ── sqls 조립 ─────────────────────────────────────────────────────────────
  // 에디터 내부의 -- STEP:n 주석을 기준으로 분할 → 각 STEP 블록을 독립 원소로 전달
  // 백엔드 SqlParser가 -- STEP:n TX:id 형식 하나씩 파싱하므로 원소 단위가 일치해야 함

  const buildSqls = (): string[] => {
    const result: string[] = []
    for (const e of editors) {
      if (!e.sql.trim()) continue

      const STEP_RE = /(?=--\s*STEP\s*:\s*\d+)/i
      const blocks = e.sql.split(STEP_RE).map(b => b.trim()).filter(b => b)

      if (blocks.length === 0) continue

      if (blocks.length === 1 && !/--\s*STEP\s*:/i.test(blocks[0])) {
        // STEP 주석 없는 에디터 → 에디터 전체를 단일 STEP으로 래핑
        const stepN = result.length + 1
        result.push(`-- STEP:${stepN} TX:${e.txId}\n${e.sql}`)
        continue
      }

      for (const block of blocks) {
        // TX 정보가 없는 STEP 주석(-- STEP:n) → TX 자동 추가
        const withTx = block.replace(
          /(--\s*STEP\s*:\s*\d+)(?!\s+TX\s*:)/i,
          `$1 TX:${e.txId}`
        )
        result.push(withTx)
      }
    }
    return result
  }

  // ── 시나리오 자동 감지 ────────────────────────────────────────────────────

  const handleDetect = () => {
    const result = detectScenario(editors)
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

  // ── 페이지네이션 ──────────────────────────────────────────────────────────

  const handlePageSizeChange = (size: number) => {
    setPageSize(size)
    setPage(0)
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

        {/* TX 에디터 그룹 */}
        <div className={styles.field}>
          <div className={styles.sqlHeader}>
            <label className={styles.label}>TX 에디터 ({editors.length}/4)</label>
            <button className={styles.addBtn} onClick={addEditor}>+ TX 추가</button>
          </div>
          <p className={styles.editorHint}>
            각 에디터에 SQL을 작성하고, 실행 순서는 <code>-- STEP:1</code> 형식으로 지정하세요.
          </p>
          <div className={styles.txEditorList}>
            {editors.map((editor, idx) => (
              <div key={editor.txId} className={styles.txEditorCard}>
                <div className={styles.txEditorHeader}>
                  <span className={styles.txBadge}>{editor.txId}</span>
                  <div className={styles.isoInsertRow}>
                    {ISO_PRESETS.map(p => (
                      <button
                        key={p.value}
                        className={styles.isoInsertBtn}
                        onClick={() => insertIsoBegin(p.value, idx)}
                        title={`BEGIN ISOLATION LEVEL ${p.label} 삽입 (-- STEP:n 자동 포함)`}
                      >
                        {p.label.split(' ').slice(-1)[0]}
                      </button>
                    ))}
                  </div>
                  {editors.length > 1 && (
                    <button className={styles.removeEditorBtn} onClick={() => removeEditor(idx)}>✕</button>
                  )}
                </div>
                <SqlEditor
                  value={editor.sql}
                  onChange={v => updateEditor(idx, 'sql', v)}
                  height="160px"
                />
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

        {/* 위젯 목록 */}
        <div className={styles.widgetList}>
          <div className={styles.listHeader}>
            <h3 className={styles.listTitle}>내 위젯 목록</h3>
            <div className={styles.pageSizeRow}>
              <span className={styles.pageSizeLabel}>페이지당</span>
              {[10, 20, 30].map(n => (
                <button
                  key={n}
                  className={`${styles.pageSizeBtn} ${pageSize === n ? styles.pageSizeBtnActive : ''}`}
                  onClick={() => handlePageSizeChange(n)}
                >
                  {n}
                </button>
              ))}
            </div>
          </div>

          {loading ? (
            <p className={styles.loading}>불러오는 중...</p>
          ) : widgets.length === 0 ? (
            <p className={styles.empty}>생성된 위젯이 없습니다.</p>
          ) : (
            <>
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

              {/* 페이지네이션 */}
              {totalPages > 1 && (
                <div className={styles.pagination}>
                  <button
                    className={styles.pageBtn}
                    onClick={() => setPage(page - 1)}
                    disabled={page === 0}
                  >‹</button>
                  <span className={styles.pageInfo}>{page + 1} / {totalPages}</span>
                  <button
                    className={styles.pageBtn}
                    onClick={() => setPage(page + 1)}
                    disabled={page >= totalPages - 1}
                  >›</button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
