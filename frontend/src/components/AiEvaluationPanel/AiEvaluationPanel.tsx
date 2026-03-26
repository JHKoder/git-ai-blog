import { useState, useEffect, useRef } from 'react'
import toast from 'react-hot-toast'
import { MarkdownRenderer } from '../MarkdownRenderer/MarkdownRenderer'
import { useAuthStore } from '../../store/authStore'
import styles from './AiEvaluationPanel.module.css'

interface Props {
  postId: number
  onApplyToImprovement?: (extraPrompt: string) => void
  onEvalComplete?: (text: string) => void
}

interface StoredEval {
  result: string
  suggestedPrompt: string | null
  model: string
  savedAt: string
}

const MODELS = [
  { value: '', label: '자동 선택' },
  { value: 'claude-sonnet-4-6', label: 'Claude Sonnet 4.6' },
  { value: 'claude-opus-4-5', label: 'Claude Opus 4.5' },
  { value: 'grok-3', label: 'Grok 3' },
  { value: 'gpt-4o-mini', label: 'GPT-4o mini' },
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'gemini-2.0-flash', label: 'Gemini 2.0 Flash' },
]

const RECOMMENDED_RE = /```\n([\s\S]*?)\n```/

function storageKey(postId: number) {
  return `ai_eval_${postId}`
}

function loadStored(postId: number): StoredEval | null {
  try {
    const raw = localStorage.getItem(storageKey(postId))
    if (!raw) return null
    return JSON.parse(raw) as StoredEval
  } catch {
    return null
  }
}

function saveStored(postId: number, data: StoredEval) {
  try {
    localStorage.setItem(storageKey(postId), JSON.stringify(data))
  } catch {
    // localStorage 용량 초과 등 무시
  }
}

function clearStored(postId: number) {
  localStorage.removeItem(storageKey(postId))
}

export function AiEvaluationPanel({ postId, onApplyToImprovement, onEvalComplete }: Props) {
  const [model, setModel] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [streamingText, setStreamingText] = useState('')
  const [evalResult, setEvalResult] = useState<string | null>(null)
  const [countdown, setCountdown] = useState<number | null>(null)
  const [suggestedPrompt, setSuggestedPrompt] = useState<string | null>(null)
  const [savedAt, setSavedAt] = useState<string | null>(null)

  const streamAbortRef = useRef<AbortController | null>(null)
  const countdownTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // 컴포넌트 마운트 시 localStorage에서 이전 결과 복원
  useEffect(() => {
    const stored = loadStored(postId)
    if (stored) {
      setEvalResult(stored.result)
      setSuggestedPrompt(stored.suggestedPrompt)
      setSavedAt(stored.savedAt)
      if (stored.model) setModel(stored.model)
      onEvalComplete?.(stored.result)
    }
  }, [postId])

  useEffect(() => {
    return () => {
      stopStreaming()
    }
  }, [])

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
    setStreamingText('')
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

  function extractSuggestedPrompt(text: string): string | null {
    const match = RECOMMENDED_RE.exec(text)
    if (match) return match[1].trim()
    return null
  }

  const handleEvaluate = async () => {
    setStreaming(true)
    setStreamingText('')
    setEvalResult(null)
    setSuggestedPrompt(null)
    setCountdown(null)

    const token = useAuthStore.getState().token
    const controller = new AbortController()
    streamAbortRef.current = controller

    try {
      const response = await fetch(`/api/ai-suggestions/${postId}/evaluate`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ model: model || undefined }),
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
      let accumulated = ''
      let finished = false

      while (!finished) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const rawLine of lines) {
          const line = rawLine.replace(/\r$/, '')
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
            continue
          }
          if (line.startsWith('data:')) {
            const data = line.slice(5).replace(/^ /, '')
            if (currentEvent === 'estimated') {
              const secs = parseInt(data, 10)
              if (!isNaN(secs) && secs > 0) startCountdown(secs)
            } else if (currentEvent === 'error') {
              throw new Error(data || 'AI 평가 오류')
            } else if (currentEvent === 'done' || data === '[DONE]') {
              finished = true
              break
            } else {
              accumulated += data
              setStreamingText(accumulated)
            }
            continue
          }
          if (line === '') {
            currentEvent = ''
          }
        }
      }

      const extracted = extractSuggestedPrompt(accumulated)
      const now = new Date().toISOString()
      setSuggestedPrompt(extracted)
      setEvalResult(accumulated)
      setSavedAt(now)
      saveStored(postId, { result: accumulated, suggestedPrompt: extracted, model, savedAt: now })
      onEvalComplete?.(accumulated)
      stopStreaming()
      toast.success('AI 평가가 완료됐습니다.')
    } catch (e: unknown) {
      if ((e as { name?: string }).name === 'AbortError') return
      const err = e as Error
      toast.error(err.message || 'AI 평가 실패')
      stopStreaming()
    }
  }

  return (
    <div className={styles.panel}>
      <div className={styles.requestSection}>
        <h3 className={styles.sectionTitle}>AI 글 평가</h3>
        <p className={styles.description}>발행 전 6가지 기준으로 글 품질을 평가합니다.</p>
        <div className={styles.formRow}>
          <label className={styles.formLabel}>모델 선택</label>
          <select value={model} onChange={e => setModel(e.target.value)} className={styles.select}>
            {MODELS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </div>
        <button className={styles.evalBtn} onClick={handleEvaluate} disabled={streaming}>
          {streaming ? (
            <span className={styles.loadingRow}>
              <span className={styles.spinner} /> 평가 중...
              {countdown !== null && <span className={styles.countdown}>약 {countdown}초 남음</span>}
            </span>
          ) : 'AI 평가 요청'}
        </button>
      </div>

      {streaming && streamingText && (
        <div className={styles.resultSection}>
          <h3 className={styles.sectionTitle}>
            <span className={styles.loadingRow}>
              <span className={styles.spinnerDark} /> 평가 중...
            </span>
          </h3>
          <div className={`${styles.content} ${styles.streamingContent}`}>
            <pre className={styles.streamingPre}>{streamingText}</pre>
          </div>
        </div>
      )}

      {!streaming && evalResult && (
        <div className={styles.resultSection}>
          <div className={styles.resultHeader}>
            <h3 className={styles.sectionTitle}>평가 결과</h3>
            <div className={styles.resultMeta}>
              {savedAt && (
                <span className={styles.savedAt}>
                  {new Date(savedAt).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })} 저장됨
                </span>
              )}
              <button
                className={styles.clearBtn}
                onClick={() => { clearStored(postId); setEvalResult(null); setSuggestedPrompt(null); setSavedAt(null) }}
              >
                지우기
              </button>
            </div>
          </div>
          <div className={`${styles.content} markdown-body`}>
            <MarkdownRenderer content={evalResult} />
          </div>
          {suggestedPrompt && onApplyToImprovement && (
            <div className={styles.applyRow}>
              <p className={styles.applyNote}>
                💡 추천 개선 요청사항이 추출됐습니다. AI 개선에 바로 적용할 수 있습니다.
              </p>
              <button
                className={styles.applyBtn}
                onClick={() => onApplyToImprovement(suggestedPrompt)}
              >
                AI 개선에 적용하기 →
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
