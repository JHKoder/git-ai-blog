import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { postApi } from '../../api/postApi'
import { usePostStore } from '../../store/postStore'
import { TagInput } from '../../components/TagInput/TagInput'
import { ImageGenButton } from '../../components/ImageGenButton/ImageGenButton'
import { useDraft } from '../../hooks/useDraft'
import styles from './PostEditPage.module.css'

const IMAGE_MODELS = [
  { value: '', label: '모델 미선택 (이미지 생성 불가)' },
  { value: 'gpt-4o-mini', label: 'GPT-4o mini (가성비)' },
  { value: 'gpt-4o', label: 'GPT-4o (고성능)' },
]

export function PostEditPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { currentPost, fetchPost } = usePostStore()
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [tags, setTags] = useState<string[]>([])
  const [selectedModel, setSelectedModel] = useState('')
  const [loading, setLoading] = useState(false)
  const [lastSaved, setLastSaved] = useState<Date | null>(null)
  const [initialized, setInitialized] = useState(false)

  const draftKey = `draft:edit-${id}`
  const { draftExists, startAutoSave, loadDraft, clearDraft } = useDraft(draftKey)

  useEffect(() => {
    if (id) fetchPost(Number(id))
  }, [id])

  // 서버 데이터 로드 후 임시저장 복구 제안
  useEffect(() => {
    if (!currentPost || initialized) return
    setInitialized(true)

    if (draftExists) {
      const draft = loadDraft()
      // 서버 내용과 다를 때만 복구 제안
      if (draft && (draft.title !== currentPost.title || draft.content !== currentPost.content)) {
        const savedTime = new Date(draft.savedAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
        setTitle(currentPost.title)
        setContent(currentPost.content)
        setTags(currentPost.tags ?? [])
        toast((t) => (
          <span>
            {savedTime} 임시저장된 수정 내용이 있습니다.{' '}
            <button
              style={{ marginLeft: 8, color: '#3b82f6', fontWeight: 600, background: 'none', border: 'none', cursor: 'pointer' }}
              onClick={() => {
                setTitle(draft.title)
                setContent(draft.content)
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
        return
      }
    }

    setTitle(currentPost.title)
    setContent(currentPost.content)
    setTags(currentPost.tags ?? [])
  }, [currentPost])

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

  const stateRef = useRef({ title, content, contentType: 'ETC' as const, tags })
  useEffect(() => { stateRef.current = { title, content, contentType: 'ETC' as const, tags } }, [title, content, tags])

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
      await postApi.update(Number(id), { title, content, tags })
      clearDraft()
      toast.success('수정됐습니다.')
      navigate(`/posts/${id}`)
    } catch {
      toast.error('수정 실패')
    } finally {
      setLoading(false)
    }
  }

  if (!currentPost) return <p>로딩 중...</p>

  return (
    <div>
      <div className={styles.pageHeader}>
        <h2>글 수정</h2>
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
            className={styles.input}
          />
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
