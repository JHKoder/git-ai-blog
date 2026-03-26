import toast from 'react-hot-toast'
import { MarkdownRenderer } from '../MarkdownRenderer/MarkdownRenderer'
import { AiSuggestion } from '../../types/suggestion'
import { useSuggestionStore } from '../../store/suggestionStore'
import styles from './AiSuggestionResult.module.css'

interface Props {
  postId: number
  suggestion: AiSuggestion
  onSuggestionUpdate?: () => void
}

const MODEL_LABEL: Record<string, string> = {
  'claude-sonnet-4-6': 'Claude Sonnet 4.6',
  'claude-opus-4-5': 'Claude Opus 4.5',
  'grok-3': 'Grok 3',
  'gpt-4o-mini': 'GPT-4o mini',
  'gpt-4o': 'GPT-4o',
  'gemini-2.0-flash': 'Gemini 2.0 Flash',
}

function getModelLabel(model: string) {
  return MODEL_LABEL[model] ?? model
}

export function AiSuggestionResult({ postId, suggestion, onSuggestionUpdate }: Props) {
  const { accept, reject } = useSuggestionStore()

  const handleAccept = async () => {
    try {
      await accept(postId, suggestion.id)
      toast.success('제안을 수락했습니다.')
      onSuggestionUpdate?.()
    } catch {
      toast.error('수락 실패')
    }
  }

  const handleReject = async () => {
    try {
      await reject(postId, suggestion.id)
      toast.success('제안을 거절했습니다.')
      onSuggestionUpdate?.()
    } catch {
      toast.error('거절 실패')
    }
  }

  const acceptLabel = (() => {
    const parts = ['본문']
    if (suggestion.suggestedTitle) parts.push('제목')
    if (suggestion.suggestedTags) parts.push(`태그 ${suggestion.suggestedTags.split(',').length}개`)
    return `✓ 수락 (${parts.join(' + ')} 적용)`
  })()

  return (
    <div className={styles.wrapper}>
      <div className={styles.header}>
        <h3 className={styles.title}>AI 제안 결과</h3>
        <div className={styles.metaTags}>
          <span className={styles.modelTag}>{getModelLabel(suggestion.model)}</span>
          {suggestion.extraPrompt && (
            <span className={styles.promptTag} title={suggestion.extraPrompt}>
              {suggestion.extraPrompt.length > 20
                ? suggestion.extraPrompt.slice(0, 20) + '…'
                : suggestion.extraPrompt}
            </span>
          )}
          <span className={styles.dateTag}>
            {new Date(suggestion.createdAt).toLocaleString('ko-KR', {
              month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
            })}
          </span>
        </div>
      </div>

      {suggestion.suggestedTitle && (
        <div className={styles.metaRow}>
          제안 제목: <strong>{suggestion.suggestedTitle}</strong>
        </div>
      )}
      {suggestion.suggestedTags && (
        <div className={styles.metaRow}>
          제안 태그: <strong>{suggestion.suggestedTags.split(',').map(t => `#${t}`).join(' ')}</strong>
        </div>
      )}

      <div className={`${styles.content} markdown-body`}>
        <MarkdownRenderer content={suggestion.suggestedContent} />
      </div>

      <div className={styles.actions}>
        <button className={styles.acceptBtn} onClick={handleAccept}>{acceptLabel}</button>
        <button className={styles.rejectBtn} onClick={handleReject}>✕ 거절</button>
      </div>
    </div>
  )
}
