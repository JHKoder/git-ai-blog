import { useState } from 'react'
import toast from 'react-hot-toast'
import { MarkdownRenderer } from '../MarkdownRenderer/MarkdownRenderer'
import { AiSuggestion } from '../../types/suggestion'
import { useSuggestionStore } from '../../store/suggestionStore'
import { memberApi } from '../../api/memberApi'
import styles from './AiSuggestionResult.module.css'

interface Props {
  postId: number
  suggestion: AiSuggestion
  onSuggestionUpdate?: () => void
}

interface HashnodeTagEntry {
  name: string
  id: string
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
  const [savingTags, setSavingTags] = useState(false)

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

  const handleAddTagsToProfile = async () => {
    if (!suggestion.suggestedTags) return
    setSavingTags(true)
    try {
      const meRes = await memberApi.getMe()
      const existing: HashnodeTagEntry[] = (() => {
        try {
          return meRes.data.data.hashnodeTags
            ? (JSON.parse(meRes.data.data.hashnodeTags) as HashnodeTagEntry[])
            : []
        } catch {
          return []
        }
      })()

      const newTags = suggestion.suggestedTags
        .split(',')
        .map(t => t.trim())
        .filter(t => t.length > 0)

      const existingNames = new Set(existing.map(e => e.name.toLowerCase()))
      const toAdd: HashnodeTagEntry[] = newTags
        .filter(t => !existingNames.has(t.toLowerCase()))
        .map(t => ({ name: t, id: '' }))

      if (toAdd.length === 0) {
        toast('모든 태그가 이미 등록되어 있습니다.')
        return
      }

      const merged = [...existing, ...toAdd]
      await memberApi.updateApiKeys({ hashnodeTags: JSON.stringify(merged) })
      toast.success(`태그 ${toAdd.length}개가 프로필에 추가됐습니다. (프로필에서 Hashnode ID 입력 필요)`)
    } catch {
      toast.error('태그 등록 실패')
    } finally {
      setSavingTags(false)
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
        <div className={styles.tagRow}>
          <span className={styles.metaRow}>
            제안 태그: <strong>{suggestion.suggestedTags.split(',').map(t => `#${t.trim()}`).join(' ')}</strong>
          </span>
          <button
            className={styles.addTagsBtn}
            onClick={handleAddTagsToProfile}
            disabled={savingTags}
          >
            {savingTags ? '등록 중...' : '+ 프로필에 추가'}
          </button>
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
