import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { MarkdownRenderer } from '../../components/MarkdownRenderer/MarkdownRenderer'
import toast from 'react-hot-toast'
import { usePostStore } from '../../store/postStore'
import { useSuggestionStore } from '../../store/suggestionStore'
import { postApi } from '../../api/postApi'
import { CONTENT_TYPE_LABEL } from '../../types/post'
import { StatusBadge } from '../../components/StatusBadge/StatusBadge'
import { AiSuggestionPanel } from '../../components/AiSuggestionPanel/AiSuggestionPanel'
import { AiEvaluationPanel } from '../../components/AiEvaluationPanel/AiEvaluationPanel'
import { ConfirmModal } from '../../components/Modal/ConfirmModal'
import styles from './PostDetailPage.module.css'

const MODEL_LABEL: Record<string, string> = {
  'claude-sonnet-4-6': 'Claude Sonnet 4.6',
  'claude-opus-4-5': 'Claude Opus 4.5',
  'grok-3': 'Grok 3',
  'gpt-4o-mini': 'GPT-4o mini',
  'gpt-4o': 'GPT-4o',
  'gemini-2.0-flash': 'Gemini 2.0 Flash',
}

export function PostDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { currentPost, fetchPost, clearCurrentPost } = usePostStore()
  const { latestSuggestion, history, fetchLatest, fetchHistory, clear } = useSuggestionStore()
  const [showDeleteModal, setShowDeleteModal] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [suggestionExtraPrompt, setSuggestionExtraPrompt] = useState('')

  useEffect(() => {
    if (id) {
      fetchPost(Number(id))
      fetchLatest(Number(id))
      fetchHistory(Number(id))
    }
    return () => { clearCurrentPost(); clear() }
  }, [id])

  const handleDelete = async () => {
    try {
      await postApi.delete(Number(id))
      toast.success('삭제됐습니다.')
      navigate('/')
    } catch {
      toast.error('삭제 실패')
    }
    setShowDeleteModal(false)
  }

  const handleExportPdf = () => {
    const content = currentPost?.content ?? ''
    const bytes = new TextEncoder().encode(content).length
    if (bytes > 99 * 1024 * 1024) {
      toast.error('게시글 내용이 99MB를 초과하여 PDF로 내보낼 수 없습니다.')
      return
    }
    window.print()
  }

  const handlePublish = async () => {
    if (currentPost && currentPost.title.length < 6) {
      toast.error('Hashnode 발행 조건: 제목은 최소 6자 이상이어야 합니다.')
      return
    }
    setPublishing(true)
    try {
      await postApi.publish(Number(id))
      toast.success('Hashnode에 발행됐습니다.')
      fetchPost(Number(id))
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      toast.error(err.response?.data?.message || '발행 실패')
    } finally {
      setPublishing(false)
    }
  }

  if (!currentPost) return <p>로딩 중...</p>

  const aiImprovedCount = history.length
  const lastSuggestion = history[0] ?? latestSuggestion

  return (
    <div>
      <div className={styles.header}>
        <div className={styles.meta}>
          <span className={styles.contentType}>{CONTENT_TYPE_LABEL[currentPost.contentType]}</span>
          <StatusBadge status={currentPost.status} />
          <span className={styles.views}>조회 {currentPost.viewCount}</span>
        </div>
        <div className={styles.actions}>
          <button className={styles.pdfBtn} onClick={handleExportPdf}>PDF 내보내기</button>
          <button className={styles.editBtn} onClick={() => navigate(`/posts/${id}/edit`)}>수정</button>
          <button className={styles.publishBtn} onClick={handlePublish} disabled={publishing}>
            {publishing ? '발행 중...' : currentPost.status === 'PUBLISHED' ? '재발행' : 'Hashnode 발행'}
          </button>
          <button className={styles.deleteBtn} onClick={() => setShowDeleteModal(true)}>삭제</button>
        </div>
      </div>

      <div className={styles.layout}>
        {/* 좌측: 본문 */}
        <div className={styles.main}>
          <h1 className={styles.title}>{currentPost.title}</h1>

          {currentPost.tags && currentPost.tags.length > 0 && (
            <div className={styles.tags}>
              {currentPost.tags.map(tag => (
                <span key={tag} className={styles.tag}>#{tag}</span>
              ))}
            </div>
          )}

          <div className={`${styles.content} markdown-body`}>
            <MarkdownRenderer content={currentPost.content} />
          </div>

          {aiImprovedCount > 0 && lastSuggestion && (
            <div className={styles.aiMeta}>
              <span className={styles.aiMetaLabel}>🤖 AI 작성 정보</span>
              <span className={styles.aiMetaItem}>
                모델: {MODEL_LABEL[lastSuggestion.model] ?? lastSuggestion.model}
              </span>
              <span className={styles.aiMetaItem}>
                최종 수정: {new Date(lastSuggestion.createdAt).toLocaleDateString('ko-KR')}
              </span>
              <span className={styles.aiMetaItem}>
                AI 개선 {aiImprovedCount}회
              </span>
            </div>
          )}

          <div className={styles.bottomActions}>
            {currentPost.status === 'PUBLISHED' && currentPost.hashnodeUrl && (
              <a href={currentPost.hashnodeUrl} target="_blank" rel="noopener noreferrer" className={styles.hashnodeLink}>
                Hashnode에서 보기 →
              </a>
            )}
          </div>
        </div>

        {/* 우측: sticky 사이드바 */}
        <aside className={styles.sidebar}>
          <AiEvaluationPanel
            postId={Number(id)}
            onApplyToImprovement={(prompt) => setSuggestionExtraPrompt(prompt)}
          />
          <AiSuggestionPanel
            postId={Number(id)}
            suggestion={latestSuggestion}
            initialExtraPrompt={suggestionExtraPrompt}
            onExtraPromptApplied={() => setSuggestionExtraPrompt('')}
            onSuggestionUpdate={() => { fetchPost(Number(id)); fetchLatest(Number(id)); fetchHistory(Number(id)) }}
          />
        </aside>
      </div>

      {showDeleteModal && (
        <ConfirmModal
          message="정말 삭제하시겠습니까? Hashnode에 발행된 글도 함께 삭제됩니다."
          onConfirm={handleDelete}
          onCancel={() => setShowDeleteModal(false)}
        />
      )}
    </div>
  )
}
