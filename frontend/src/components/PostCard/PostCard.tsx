import { Link } from 'react-router-dom'
import { PostListItem, CONTENT_TYPE_LABEL } from '../../types/post'
import { StatusBadge } from '../StatusBadge/StatusBadge'
import styles from './PostCard.module.css'

interface Props {
  post: PostListItem
  onTagClick?: (tag: string) => void
}

export function PostCard({ post, onTagClick }: Props) {
  return (
    <Link to={`/posts/${post.id}`} className={styles.card}>
      <div className={styles.header}>
        <span className={styles.contentType}>{CONTENT_TYPE_LABEL[post.contentType]}</span>
        <StatusBadge status={post.status} />
      </div>
      <h3 className={styles.title}>{post.title}</h3>
      {post.tags && post.tags.length > 0 && (
        <div className={styles.tags}>
          {post.tags.map(tag => (
            <span
              key={tag}
              className={styles.tag}
              onClick={e => { e.preventDefault(); onTagClick?.(tag) }}
            >
              #{tag}
            </span>
          ))}
        </div>
      )}
      <div className={styles.footer}>
        <span>조회 {post.viewCount}</span>
        <span>{new Date(post.createdAt).toLocaleDateString('ko-KR')}</span>
      </div>
    </Link>
  )
}
