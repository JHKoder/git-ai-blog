import { PostStatus } from '../../types/post'

interface Props {
  status: PostStatus
}

const statusConfig: Record<PostStatus, { label: string; color: string }> = {
  DRAFT: { label: '초안', color: '#6b7280' },
  AI_SUGGESTED: { label: 'AI 제안', color: '#3b82f6' },
  ACCEPTED: { label: '수락됨', color: '#22c55e' },
  PUBLISHED: { label: '발행됨', color: '#8b5cf6' },
}

export function StatusBadge({ status }: Props) {
  const { label, color } = statusConfig[status]
  return (
    <span style={{
      background: color,
      color: '#fff',
      padding: '2px 10px',
      borderRadius: '12px',
      fontSize: '12px',
      fontWeight: 600,
    }}>
      {label}
    </span>
  )
}
