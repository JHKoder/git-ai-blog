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
import styles from './SqlVizPage.module.css'

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

export function SqlVizPage() {
  const { widgets, loading, fetchWidgets, createWidget, deleteWidget } = useSqlVizStore()

  const [title, setTitle] = useState('')
  const [sqls, setSqls] = useState<string[]>(['SELECT * FROM orders WHERE id = 1;'])
  const [scenario, setScenario] = useState<SqlVizScenario>('DEADLOCK')
  const [isolationLevel, setIsolationLevel] = useState<IsolationLevel>('READ_COMMITTED')
  const [creating, setCreating] = useState(false)
  const [selectedWidget, setSelectedWidget] = useState<SqlVizWidget | null>(null)
  const [activeTab, setActiveTab] = useState<'timeline' | 'flow' | 'embed'>('timeline')
  const [previewIsolation, setPreviewIsolation] = useState<IsolationLevel | null>(null)

  useEffect(() => {
    fetchWidgets()
  }, [fetchWidgets])

  const addSql = () => {
    if (sqls.length >= 10) {
      toast.error('SQL은 최대 10개까지 입력 가능합니다.')
      return
    }
    setSqls(prev => [...prev, ''])
  }

  const removeSql = (idx: number) => {
    setSqls(prev => prev.filter((_, i) => i !== idx))
  }

  const updateSql = (idx: number, value: string) => {
    setSqls(prev => prev.map((s, i) => i === idx ? value : s))
  }

  const handleCreate = async () => {
    if (!title.trim()) {
      toast.error('제목을 입력해주세요.')
      return
    }
    const filtered = sqls.filter(s => s.trim())
    if (filtered.length === 0) {
      toast.error('SQL을 최소 1개 입력해주세요.')
      return
    }
    const req: SqlVizCreateRequest = { title: title.trim(), sqls: filtered, scenario, isolationLevel }
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

  return (
    <div className={styles.page}>
      <div className={styles.formPanel}>
        <h2 className={styles.heading}>SQL Visualization 위젯 생성</h2>

        <div className={styles.field}>
          <label className={styles.label}>제목</label>
          <input
            className={styles.input}
            value={title}
            onChange={e => setTitle(e.target.value)}
            placeholder="예: 데드락 시나리오 분석"
          />
        </div>

        <div className={styles.fieldRow}>
          <div className={styles.field}>
            <label className={styles.label}>시나리오</label>
            <select
              className={styles.select}
              value={scenario}
              onChange={e => setScenario(e.target.value as SqlVizScenario)}
            >
              {SCENARIOS.map(s => (
                <option key={s} value={s}>{SCENARIO_LABEL[s]}</option>
              ))}
            </select>
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

        <div className={styles.field}>
          <div className={styles.sqlHeader}>
            <label className={styles.label}>SQL ({sqls.length}/10)</label>
            <button className={styles.addBtn} onClick={addSql}>+ SQL 추가</button>
          </div>
          <div className={styles.sqlList}>
            {sqls.map((sql, idx) => (
              <div key={idx} className={styles.sqlItem}>
                <span className={styles.sqlIdx}>#{idx + 1}</span>
                <div className={styles.sqlEditor}>
                  <SqlEditor value={sql} onChange={v => updateSql(idx, v)} height="90px" />
                </div>
                {sqls.length > 1 && (
                  <button className={styles.removeBtn} onClick={() => removeSql(idx)}>✕</button>
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
