import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { sqlvizApi } from '../../api/sqlvizApi'
import type { SqlVizWidget } from '../../types/sqlviz'
import { SCENARIO_LABEL, ISOLATION_LEVEL_LABEL } from '../../types/sqlviz'
import { ConcurrencyTimeline } from '../../components/Visualization/ConcurrencyTimeline/ConcurrencyTimeline'
import { ExecutionFlow } from '../../components/Visualization/ExecutionFlow/ExecutionFlow'
import { SqlTxColumns } from '../../components/SqlTxColumns/SqlTxColumns'
import styles from './SqlVizEmbedPage.module.css'

export function SqlVizEmbedPage() {
  const { id } = useParams<{ id: string }>()
  const [widget, setWidget] = useState<SqlVizWidget | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [activeTab, setActiveTab] = useState<'timeline' | 'flow'>('timeline')

  // 다크모드: URL ?theme=dark 우선, 없으면 prefers-color-scheme 자동 감지
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const themeParam = params.get('theme')

    const applyTheme = (dark: boolean) => {
      document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light')
    }

    if (themeParam === 'dark' || themeParam === 'light') {
      applyTheme(themeParam === 'dark')
      return
    }

    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    applyTheme(mq.matches)
    const handler = (e: MediaQueryListEvent) => applyTheme(e.matches)
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  useEffect(() => {
    if (!id) return
    sqlvizApi.getEmbed(Number(id))
      .then(data => { setWidget(data); setLoading(false) })
      .catch(() => { setError(true); setLoading(false) })
  }, [id])

  if (loading) return <div className={styles.center}>로딩 중...</div>
  if (error || !widget) return <div className={styles.center}>위젯을 불러올 수 없습니다.</div>

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>{widget.title}</h1>
        <div className={styles.meta}>
          <span className={styles.badge}>{SCENARIO_LABEL[widget.scenario]}</span>
          <span className={styles.badge}>{ISOLATION_LEVEL_LABEL[widget.isolationLevel]}</span>
        </div>
      </div>

      <div className={styles.tabs}>
        <button
          className={`${styles.tab} ${activeTab === 'timeline' ? styles.activeTab : ''}`}
          onClick={() => setActiveTab('timeline')}
        >타임라인</button>
        <button
          className={`${styles.tab} ${activeTab === 'flow' ? styles.activeTab : ''}`}
          onClick={() => setActiveTab('flow')}
        >실행 흐름</button>
      </div>

      <div className={styles.content}>
        {activeTab === 'timeline' && (
          <ConcurrencyTimeline simulation={widget.simulation} />
        )}
        {activeTab === 'flow' && (
          <ExecutionFlow simulation={widget.simulation} />
        )}
      </div>

      <div className={styles.sqlList}>
        <h3 className={styles.sqlTitle}>SQL 목록</h3>
        <SqlTxColumns sqls={widget.sqls} />
      </div>
    </div>
  )
}
