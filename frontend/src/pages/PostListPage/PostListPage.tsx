import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { usePostStore } from '../../store/postStore'
import { PostCard } from '../../components/PostCard/PostCard'
import styles from './PostListPage.module.css'

export function PostListPage() {
  const { posts, totalPages, currentPage, activeTag, loading, fetchPosts } = usePostStore()
  const navigate = useNavigate()

  useEffect(() => { fetchPosts(0, undefined) }, [])

  const handleTagClick = (tag: string) => { fetchPosts(0, tag) }
  const clearTag = () => { fetchPosts(0, '') }

  return (
    <div>
      <div className={styles.header}>
        <h2>내 게시글</h2>
        <button className={styles.newBtn} onClick={() => navigate('/posts/new')}>
          + 새 글 작성
        </button>
      </div>

      {activeTag && (
        <div className={styles.tagFilter}>
          <span className={styles.tagFilterLabel}>태그 필터:</span>
          <span className={styles.activeTag}>
            #{activeTag}
            <button className={styles.clearTagBtn} onClick={clearTag}>✕</button>
          </span>
        </div>
      )}

      {loading ? (
        <p className={styles.loading}>로딩 중...</p>
      ) : posts.length === 0 ? (
        <div className={styles.empty}>
          <p>{activeTag ? `"${activeTag}" 태그 게시글이 없습니다.` : '아직 게시글이 없습니다.'}</p>
          {!activeTag && <button className={styles.newBtn} onClick={() => navigate('/posts/new')}>첫 글 작성하기</button>}
        </div>
      ) : (
        <div className={styles.list}>
          {posts.map(post => <PostCard key={post.id} post={post} onTagClick={handleTagClick} />)}
        </div>
      )}

      {totalPages > 1 && (
        <div className={styles.pagination}>
          {Array.from({ length: totalPages }, (_, i) => (
            <button
              key={i}
              className={`${styles.pageBtn} ${i === currentPage ? styles.active : ''}`}
              onClick={() => fetchPosts(i)}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
