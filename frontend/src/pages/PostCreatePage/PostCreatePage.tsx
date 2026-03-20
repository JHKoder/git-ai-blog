import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { postApi } from '../../api/postApi'
import { ContentType, CONTENT_TYPE_LABEL } from '../../types/post'
import { TagInput } from '../../components/TagInput/TagInput'
import { ImageGenButton } from '../../components/ImageGenButton/ImageGenButton'
import { useDraft } from '../../hooks/useDraft'
import styles from './PostCreatePage.module.css'

const CONTENT_TYPES: ContentType[] = ['ALGORITHM', 'CODING', 'CS', 'TEST', 'AUTOMATION', 'DOCUMENT', 'CODE_REVIEW', 'ETC']
const DRAFT_KEY = 'draft:new-post'

const IMAGE_MODELS = [
  { value: '', label: '모델 미선택 (이미지 생성 불가)' },
  { value: 'gpt-4o-mini', label: 'GPT-4o mini (가성비)' },
  { value: 'gpt-4o', label: 'GPT-4o (고성능)' },
]

export function PostCreatePage() {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [contentType, setContentType] = useState<ContentType>('ETC')
  const [tags, setTags] = useState<string[]>([])
  const [selectedModel, setSelectedModel] = useState('')
  const [loading, setLoading] = useState(false)
  const [lastSaved, setLastSaved] = useState<Date | null>(null)

  const { draftExists, startAutoSave, loadDraft, clearDraft } = useDraft(DRAFT_KEY)

  // 임시저장 복구 제안
  useEffect(() => {
    if (draftExists) {
      const draft = loadDraft()
      if (!draft) return
      const savedTime = new Date(draft.savedAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
      toast((t) => (
        <span>
          {savedTime} 임시저장된 내용이 있습니다.{' '}
          <button
            style={{ marginLeft: 8, color: '#3b82f6', fontWeight: 600, background: 'none', border: 'none', cursor: 'pointer' }}
            onClick={() => {
              setTitle(draft.title)
              setContent(draft.content)
              setContentType(draft.contentType)
              setTags(draft.tags ?? [])
              toast.dismiss(t.id)
            }}
          >복구</button>
          <button
            style={{ marginLeft: 6, color: '#6b7280', background: 'none', border: 'none', cursor: 'pointer' }}
            onClick={() => { clearDraft(); toast.dismiss(t.id) }}
          >무시</button>
        </span>
      ), { duration: 10000 })
    }
  }, [])

  // ref로 최신 상태를 5초마다 저장
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const insertImageMarkdown = (markdown: string) => {
    const el = textareaRef.current
    if (!el) { setContent(prev => prev + '\n' + markdown); return }
    const start = el.selectionStart
    const end = el.selectionEnd
    const next = content.slice(0, start) + '\n' + markdown + '\n' + content.slice(end)
    setContent(next)
    setTimeout(() => { el.selectionStart = el.selectionEnd = start + markdown.length + 2; el.focus() }, 0)
  }

  const stateRef = useRef({ title, content, contentType, tags })
  useEffect(() => { stateRef.current = { title, content, contentType, tags } }, [title, content, contentType, tags])

  useEffect(() => {
    startAutoSave(() => stateRef.current)
    const interval = setInterval(() => {
      const s = stateRef.current
      if (s.title || s.content) setLastSaved(new Date())
    }, 5000)
    return () => clearInterval(interval)
  }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      const res = await postApi.create({ title, content, contentType, tags })
      clearDraft()
      toast.success('게시글이 생성됐습니다.')
      navigate(`/posts/${res.data.data.id}`)
    } catch {
      toast.error('생성 실패')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <div className={styles.pageHeader}>
        <h2>새 글 작성</h2>
        {lastSaved && (
          <span className={styles.autoSaveInfo}>
            임시저장 {lastSaved.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
          </span>
        )}
      </div>
      <form onSubmit={handleSubmit} className={styles.form}>
        <div className={styles.field}>
          <label>제목</label>
          <input
            type="text"
            value={title}
            onChange={e => setTitle(e.target.value)}
            required
            placeholder="제목을 입력하세요"
            className={styles.input}
          />
        </div>
        <div className={styles.field}>
          <label>콘텐츠 타입</label>
          <select value={contentType} onChange={e => setContentType(e.target.value as ContentType)} className={styles.select}>
            {CONTENT_TYPES.map(t => <option key={t} value={t}>{CONTENT_TYPE_LABEL[t]}</option>)}
          </select>
        </div>
        <div className={styles.field}>
          <label>태그</label>
          <TagInput tags={tags} onChange={setTags} />
        </div>
        <div className={styles.field}>
          <label>이미지 생성 모델</label>
          <select value={selectedModel} onChange={e => setSelectedModel(e.target.value)} className={styles.select}>
            {IMAGE_MODELS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </div>
        <div className={styles.field}>
          <div className={styles.labelRow}>
            <label>내용 (Markdown)</label>
            <ImageGenButton onInsert={insertImageMarkdown} selectedModel={selectedModel} />
          </div>
          <textarea
            ref={textareaRef}
            value={content}
            onChange={e => setContent(e.target.value)}
            required
            rows={20}
            placeholder="Markdown으로 작성하세요"
            className={styles.textarea}
          />
        </div>
        <div className={styles.actions}>
          <button type="button" className={styles.cancelBtn} onClick={() => navigate(-1)}>취소</button>
          <button type="submit" className={styles.submitBtn} disabled={loading}>
            {loading ? '저장 중...' : '저장'}
          </button>
        </div>
      </form>
    </div>
  )
}
