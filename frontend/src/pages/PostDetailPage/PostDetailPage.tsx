import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import toast from 'react-hot-toast'
import { usePostStore } from '../../store/postStore'
import { useSuggestionStore } from '../../store/suggestionStore'
import { postApi } from '../../api/postApi'
import { CONTENT_TYPE_LABEL } from '../../types/post'
import { StatusBadge } from '../../components/StatusBadge/StatusBadge'
import { AiSuggestionPanel } from '../../components/AiSuggestionPanel/AiSuggestionPanel'
import { ConfirmModal } from '../../components/Modal/ConfirmModal'
import styles from './PostDetailPage.module.css'

export function PostDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { currentPost, fetchPost, clearCurrentPost } = usePostStore()
  const { latestSuggestion, fetchLatest, clear } = useSuggestionStore()
  const [showDeleteModal, setShowDeleteModal] = useState(false)
  const [publishing, setPublishing] = useState(false)

  useEffect(() => {
    if (id) {
      fetchPost(Number(id))
      fetchLatest(Number(id))
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
    // 99MB 제한 체크 (UTF-8 기준 바이트)
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
          {(currentPost.status === 'ACCEPTED' || currentPost.status === 'PUBLISHED') && (
            <button className={styles.publishBtn} onClick={handlePublish} disabled={publishing}>
              {publishing ? '발행 중...' : currentPost.status === 'PUBLISHED' ? '재발행' : 'Hashnode 발행'}
            </button>
          )}
          <button className={styles.deleteBtn} onClick={() => setShowDeleteModal(true)}>삭제</button>
        </div>
      </div>

      <h1 className={styles.title}>{currentPost.title}</h1>

      {currentPost.tags && currentPost.tags.length > 0 && (
        <div className={styles.tags}>
          {currentPost.tags.map(tag => (
            <span key={tag} className={styles.tag}>#{tag}</span>
          ))}
        </div>
      )}

      <div className={`${styles.content} markdown-body`}>
        <ReactMarkdown>{currentPost.content}</ReactMarkdown>
      </div>

      <div className={styles.bottomActions}>
        {currentPost.status === 'ACCEPTED' && (
          <button className={styles.publishBtn} onClick={handlePublish} disabled={publishing}>
            {publishing ? '발행 중...' : 'Hashnode 발행'}
          </button>
        )}
        {currentPost.status === 'PUBLISHED' && currentPost.hashnodeUrl && (
          <a href={currentPost.hashnodeUrl} target="_blank" rel="noopener noreferrer" className={styles.hashnodeLink}>
            Hashnode에서 보기 →
          </a>
        )}
      </div>

      <AiSuggestionPanel
        postId={Number(id)}
        suggestion={latestSuggestion}
        onSuggestionUpdate={() => { fetchPost(Number(id)); fetchLatest(Number(id)) }}
      />

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
