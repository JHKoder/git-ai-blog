import { useState, useEffect } from 'react'
import toast from 'react-hot-toast'
import { MarkdownRenderer } from '../MarkdownRenderer/MarkdownRenderer'
import { AiSuggestion, AiSuggestionRequest } from '../../types/suggestion'
import { suggestionApi } from '../../api/suggestionApi'
import { promptApi } from '../../api/promptApi'
import { Prompt } from '../../types/prompt'
import { useSuggestionStore } from '../../store/suggestionStore'
import styles from './AiSuggestionPanel.module.css'

interface Props {
  postId: number
  suggestion: AiSuggestion | null
  onSuggestionUpdate?: () => void
}

const MODELS = [
  { value: '', label: '자동 선택 (ContentType 기반)' },
  { value: 'claude-sonnet-4-6', label: 'Claude Sonnet 4.6' },
  { value: 'claude-opus-4-5', label: 'Claude Opus 4.5' },
  { value: 'grok-3', label: 'Grok 3' },
  { value: 'gpt-4o-mini', label: 'GPT-4o mini (가성비)' },
  { value: 'gpt-4o', label: 'GPT-4o (고성능)' },
  { value: 'gemini-2.0-flash', label: 'Gemini 2.0 Flash' },
]

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

export function AiSuggestionPanel({ postId, suggestion, onSuggestionUpdate }: Props) {
  const [requesting, setRequesting] = useState(false)
  const [model, setModel] = useState('')
  const [extraPrompt, setExtraPrompt] = useState('')
  const [myPrompts, setMyPrompts] = useState<Prompt[]>([])
  const [selectedPromptId, setSelectedPromptId] = useState<number | ''>('')
  const { accept, reject } = useSuggestionStore()

  useEffect(() => {
    promptApi.getMyPrompts().then(res => setMyPrompts(res.data.data)).catch(() => {})
  }, [])

  const handleRequest = async () => {
    setRequesting(true)
    try {
      const req: AiSuggestionRequest = {}
      if (model) req.model = model
      if (extraPrompt) req.extraPrompt = extraPrompt
      if (selectedPromptId !== '') req.promptId = selectedPromptId
      await suggestionApi.request(postId, req)
      toast.success('AI 제안이 생성됐습니다.')
      setExtraPrompt('')
      onSuggestionUpdate?.()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      toast.error(err.response?.data?.message || 'AI 요청 실패')
    } finally {
      setRequesting(false)
    }
  }

  const handleAccept = async () => {
    if (!suggestion) return
    try {
      await accept(postId, suggestion.id)
      toast.success('제안을 수락했습니다.')
      onSuggestionUpdate?.()
    } catch {
      toast.error('수락 실패')
    }
  }

  const handleReject = async () => {
    if (!suggestion) return
    try {
      await reject(postId, suggestion.id)
      toast.success('제안을 거절했습니다.')
      onSuggestionUpdate?.()
    } catch {
      toast.error('거절 실패')
    }
  }

  return (
    <div className={styles.panel}>
      {/* AI 요청 폼 */}
      <div className={styles.requestSection}>
        <h3 className={styles.sectionTitle}>AI 개선 요청</h3>
        <div className={styles.requestForm}>
          <div className={styles.formRow}>
            <label className={styles.formLabel}>모델 선택</label>
            <select value={model} onChange={e => setModel(e.target.value)} className={styles.select}>
              {MODELS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
            </select>
          </div>
          {myPrompts.length > 0 && (
            <div className={styles.formRow}>
              <label className={styles.formLabel}>커스텀 프롬프트 <span className={styles.optional}>(선택)</span></label>
              <select
                value={selectedPromptId}
                onChange={e => setSelectedPromptId(e.target.value === '' ? '' : Number(e.target.value))}
                className={styles.select}
              >
                <option value=''>선택 안 함</option>
                {myPrompts.map(p => (
                  <option key={p.id} value={p.id}>{p.title} ({p.usageCount}회)</option>
                ))}
              </select>
            </div>
          )}
          <div className={styles.formRow}>
            <label className={styles.formLabel}>추가 요청사항 <span className={styles.optional}>(선택)</span></label>
            <textarea
              placeholder="예: 더 기술적으로 작성해줘, 한국어로 다듬어줘..."
              value={extraPrompt}
              onChange={e => setExtraPrompt(e.target.value)}
              className={styles.textarea}
              rows={3}
            />
          </div>
          <button className={styles.requestBtn} onClick={handleRequest} disabled={requesting}>
            {requesting ? (
              <span className={styles.loadingRow}>
                <span className={styles.spinner} /> AI 개선 중...
              </span>
            ) : 'AI 개선 요청'}
          </button>
        </div>
      </div>

      {/* AI 제안 결과 */}
      {suggestion && (
        <div className={styles.suggestionSection}>
          <h3 className={styles.sectionTitle}>AI 제안 결과</h3>
          <div className={styles.suggestion}>
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
                  month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
                })}
              </span>
            </div>
            <div className={`${styles.content} markdown-body`}>
              <MarkdownRenderer content={suggestion.suggestedContent} />
            </div>
            <div className={styles.suggestionActions}>
              <button className={styles.acceptBtn} onClick={handleAccept}>✓ 수락 (내용 적용)</button>
              <button className={styles.rejectBtn} onClick={handleReject}>✕ 거절</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
