import { useEffect, useRef, useState } from 'react'
import { ContentType } from '../types/post'

interface Draft {
  title: string
  content: string
  contentType: ContentType
  tags: string[]
  savedAt: number
}

const INTERVAL_MS = 5000

export function useDraft(key: string) {
  const [draftExists, setDraftExists] = useState(false)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // 마운트 시 임시저장 존재 여부 확인
  useEffect(() => {
    const raw = localStorage.getItem(key)
    if (raw) {
      try {
        const d: Draft = JSON.parse(raw)
        if (d.title || d.content) setDraftExists(true)
      } catch {
        localStorage.removeItem(key)
      }
    }
    return () => { if (timerRef.current) clearInterval(timerRef.current) }
  }, [key])

  const startAutoSave = (getter: () => Omit<Draft, 'savedAt'>) => {
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = setInterval(() => {
      const draft = getter()
      if (draft.title || draft.content) {
        localStorage.setItem(key, JSON.stringify({ ...draft, savedAt: Date.now() }))
      }
    }, INTERVAL_MS)
  }

  const loadDraft = (): Draft | null => {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    try { return JSON.parse(raw) } catch { return null }
  }

  const clearDraft = () => {
    localStorage.removeItem(key)
    setDraftExists(false)
    if (timerRef.current) clearInterval(timerRef.current)
  }

  return { draftExists, startAutoSave, loadDraft, clearDraft }
}
