import { useState } from 'react'
import toast from 'react-hot-toast'
import type { SqlVizWidget } from '../../../types/sqlviz'
import styles from './EmbedGenerator.module.css'

interface Props {
  widget: SqlVizWidget
}

export function EmbedGenerator({ widget }: Props) {
  const [copied, setCopied] = useState<'iframe' | 'widget' | null>(null)

  const iframeCode = `<iframe
  src="${widget.embedUrl}"
  width="100%"
  height="600"
  frameborder="0"
  loading="lazy"
  title="${widget.title}"
></iframe>`

  const copy = async (text: string, type: 'iframe' | 'widget') => {
    await navigator.clipboard.writeText(text)
    setCopied(type)
    toast.success('클립보드에 복사됨')
    setTimeout(() => setCopied(null), 2000)
  }

  return (
    <div className={styles.container}>
      <h3 className={styles.title}>임베드 코드</h3>

      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <span className={styles.label}>Hashnode Widget 코드</span>
          <button
            className={`${styles.copyBtn} ${copied === 'widget' ? styles.copied : ''}`}
            onClick={() => copy(widget.hashnodeWidgetCode, 'widget')}
          >
            {copied === 'widget' ? '✓ 복사됨' : '복사'}
          </button>
        </div>
        <code className={styles.code}>{widget.hashnodeWidgetCode}</code>
        <p className={styles.hint}>Hashnode 글 편집기에서 위젯으로 삽입할 때 사용</p>
      </div>

      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <span className={styles.label}>iframe 코드</span>
          <button
            className={`${styles.copyBtn} ${copied === 'iframe' ? styles.copied : ''}`}
            onClick={() => copy(iframeCode, 'iframe')}
          >
            {copied === 'iframe' ? '✓ 복사됨' : '복사'}
          </button>
        </div>
        <pre className={styles.code}>{iframeCode}</pre>
        <p className={styles.hint}>일반 HTML 페이지나 다른 블랫폼에 삽입할 때 사용</p>
      </div>

      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <span className={styles.label}>공개 URL</span>
        </div>
        <a href={widget.embedUrl} target="_blank" rel="noopener noreferrer" className={styles.link}>
          {widget.embedUrl}
        </a>
      </div>
    </div>
  )
}
