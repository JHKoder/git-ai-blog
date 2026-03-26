import { useState, useEffect, useRef } from 'react'
import toast from 'react-hot-toast'
import { MarkdownRenderer } from '../MarkdownRenderer/MarkdownRenderer'
import { AiSuggestion, AiSuggestionRequest } from '../../types/suggestion'
import { suggestionApi } from '../../api/suggestionApi'
import { promptApi } from '../../api/promptApi'
import { Prompt } from '../../types/prompt'
import { useSuggestionStore } from '../../store/suggestionStore'
import { useAuthStore } from '../../store/authStore'
import styles from './AiSuggestionPanel.module.css'

interface Props {
  postId: number
  suggestion: AiSuggestion | null
  initialExtraPrompt?: string
  onExtraPromptApplied?: () => void
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

const POLL_INTERVAL_MS = 3000
const POLL_MAX_ATTEMPTS = 40

function getModelLabel(model: string) {
  return MODEL_LABEL[model] ?? model
}

export function AiSuggestionPanel({ postId, suggestion, initialExtraPrompt, onExtraPromptApplied, onSuggestionUpdate }: Props) {
  const [polling, setPolling] = useState(false)
  const [streaming, setStreaming] = useState(false)
  const [streamingText, setStreamingText] = useState('')
  const [countdown, setCountdown] = useState<number | null>(null)
  const [model, setModel] = useState('')
  const [extraPrompt, setExtraPrompt] = useState('')
  const [myPrompts, setMyPrompts] = useState<Prompt[]>([])
  const [selectedPromptId, setSelectedPromptId] = useState<number | ''>('')
  const { accept, reject } = useSuggestionStore()

  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const pollAttemptsRef = useRef(0)
  const latestSuggestionIdRef = useRef<number | null>(suggestion?.id ?? null)
  const streamAbortRef = useRef<AbortController | null>(null)
  const countdownTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    latestSuggestionIdRef.current = suggestion?.id ?? null
  }, [suggestion?.id])

  useEffect(() => {
    promptApi.getMyPrompts().then(res => setMyPrompts(res.data.data)).catch(() => {})
  }, [])

  useEffect(() => {
    return () => {
      stopPolling()
      stopStreaming()
    }
  }, [])

  useEffect(() => {
    if (initialExtraPrompt) {
      setExtraPrompt(initialExtraPrompt)
      onExtraPromptApplied?.()
      // 화면에서 AiSuggestionPanel 위치로 스크롤
      window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' })
    }
  }, [initialExtraPrompt])

  function stopPolling() {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current)
      pollTimerRef.current = null
    }
    setPolling(false)
    pollAttemptsRef.current = 0
  }

  function stopStreaming() {
    if (streamAbortRef.current) {
      streamAbortRef.current.abort()
      streamAbortRef.current = null
    }
    if (countdownTimerRef.current) {
      clearInterval(countdownTimerRef.current)
      countdownTimerRef.current = null
    }
    setStreaming(false)
    setCountdown(null)
  }

  function startCountdown(seconds: number) {
    setCountdown(seconds)
    countdownTimerRef.current = setInterval(() => {
      setCountdown(prev => {
        if (prev === null || prev <= 1) {
          if (countdownTimerRef.current) clearInterval(countdownTimerRef.current)
          return null
        }
        return prev - 1
      })
    }, 1000)
  }

  function startPolling() {
    setPolling(true)
    pollAttemptsRef.current = 0

    pollTimerRef.current = setInterval(async () => {
      pollAttemptsRef.current += 1
      if (pollAttemptsRef.current > POLL_MAX_ATTEMPTS) {
        stopPolling()
        toast.error('AI 처리 시간이 초과됐습니다. 잠시 후 다시 시도해주세요.')
        return
      }

      try {
        const res = await suggestionApi.getLatest(postId)
        const latest = res.data.data
        if (latest && latest.id !== latestSuggestionIdRef.current) {
          stopPolling()
          toast.success('AI 제안이 생성됐습니다.')
          onSuggestionUpdate?.()
        }
      } catch {
        // 폴링 중 에러는 무시하고 계속
      }
    }, POLL_INTERVAL_MS)
  }

  async function startStreaming(req: AiSuggestionRequest) {
    setStreaming(true)
    setStreamingText('')
    setCountdown(null)

    const token = useAuthStore.getState().token
    const controller = new AbortController()
    streamAbortRef.current = controller

    try {
      const response = await fetch(`/api/ai-suggestions/${postId}/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(req),
        signal: controller.signal,
      })

      if (!response.ok) {
        const errorData = (await response.json()) as { message?: string }
        throw new Error(errorData.message || `HTTP ${response.status}`)
      }

      if (!response.body) throw new Error('스트리밍 응답 없음')

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''
      let finished = false

      while (!finished) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const rawLine of lines) {
          const line = rawLine.replace(/\r$/, '') // \r\n 대응
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
            continue
          }
          if (line.startsWith('data:')) {
            // SSE 스펙: 콜론 뒤 공백 하나는 제거
            const data = line.slice(5).replace(/^ /, '')
            if (currentEvent === 'estimated') {
              const secs = parseInt(data, 10)
              if (!isNaN(secs) && secs > 0) startCountdown(secs)
            } else if (currentEvent === 'error') {
              throw new Error(data || 'AI 스트리밍 오류')
            } else if (currentEvent === 'done' || data === '[DONE]') {
              finished = true
              break
            } else {
              // token 이벤트
              setStreamingText(prev => prev + data)
            }
            continue
          }
          // 빈 줄 = SSE 이벤트 구분자 → currentEvent 초기화
          if (line === '') {
            currentEvent = ''
          }
        }
      }

      stopStreaming()
      toast.success('AI 제안이 생성됐습니다.')
      onSuggestionUpdate?.()
    } catch (e: unknown) {
      if ((e as { name?: string }).name === 'AbortError') return
      const err = e as Error
      toast.error(err.message || 'AI 스트리밍 실패')
      stopStreaming()
    }
  }

  const handleRequest = async () => {
    try {
      const req: AiSuggestionRequest = {}
      if (model) req.model = model
      if (extraPrompt) req.extraPrompt = extraPrompt
      if (selectedPromptId !== '') req.promptId = selectedPromptId
      setExtraPrompt('')
      await startStreaming(req)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      toast.error(err.response?.data?.message || 'AI 요청 실패')
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

  const requesting = polling || streaming

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
              maxLength={500}
            />
            <span className={styles.charCount}>{extraPrompt.length} / 500</span>
          </div>
          <button className={styles.requestBtn} onClick={handleRequest} disabled={requesting}>
            {requesting ? (
              <span className={styles.loadingRow}>
                <span className={styles.spinner} /> AI 개선 중...
              </span>
            ) : 'AI 개선 요청'}
          </button>
          {polling && (
            <p className={styles.pollingNote}>
              AI가 글을 개선하고 있습니다. 연결이 끊겨도 완료 후 자동으로 표시됩니다.
            </p>
          )}
        </div>
      </div>

      {/* 스트리밍 중 실시간 출력 */}
      {streaming && (
        <div className={styles.suggestionSection}>
          <h3 className={styles.sectionTitle}>
            <span className={styles.loadingRow}>
              <span className={styles.spinnerDark} />
              AI 생성 중...
              {countdown !== null && (
                <span className={styles.countdown}>약 {countdown}초 남음</span>
              )}
            </span>
          </h3>
          {streamingText && (
            <div className={`${styles.content} ${styles.streamingContent}`}>
              <pre className={styles.streamingPre}>{streamingText}</pre>
            </div>
          )}
        </div>
      )}

      {/* AI 제안 결과 */}
      {!streaming && suggestion && (
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
            {suggestion.suggestedTitle && (
              <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 4 }}>
                제안 제목: <strong>{suggestion.suggestedTitle}</strong>
              </div>
            )}
            <div className={`${styles.content} markdown-body`}>
              <MarkdownRenderer content={suggestion.suggestedContent} />
            </div>
            <div className={styles.suggestionActions}>
              <button className={styles.acceptBtn} onClick={handleAccept}>
                ✓ 수락 {suggestion.suggestedTitle ? '(본문 + 제목 적용)' : '(내용 적용)'}
              </button>
              <button className={styles.rejectBtn} onClick={handleReject}>✕ 거절</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
