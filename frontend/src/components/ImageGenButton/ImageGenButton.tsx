import { useState } from 'react'
import toast from 'react-hot-toast'
import { postApi } from '../../api/postApi'
import styles from './ImageGenButton.module.css'

interface Props {
  onInsert: (markdown: string) => void
  selectedModel?: string
  hasGptApiKey?: boolean
}

const isGptModel = (model: string | undefined): boolean =>
  model != null && model.startsWith('gpt-')

export function ImageGenButton({ onInsert, selectedModel, hasGptApiKey }: Props) {
  const [open, setOpen] = useState(false)
  const [prompt, setPrompt] = useState('')
  const [loading, setLoading] = useState(false)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)

  const handleOpen = () => {
    if (!isGptModel(selectedModel)) {
      toast.error('이미지 생성은 GPT 모델(gpt-4o, gpt-4o-mini) 선택 시에만 가능합니다.')
      return
    }
    if (hasGptApiKey === false) {
      toast.error('GPT API 키가 설정되지 않았습니다. 프로필 페이지에서 GPT API 키를 먼저 등록해 주세요.')
      return
    }
    setOpen(true)
  }

  const handleGenerate = async () => {
    if (!prompt.trim()) return
    if (!isGptModel(selectedModel)) {
      toast.error('이미지 생성은 GPT 모델 선택 시에만 가능합니다.')
      return
    }
    setLoading(true)
    setPreviewUrl(null)
    try {
      const res = await postApi.generateImage(prompt.trim(), selectedModel as string)
      setPreviewUrl(res.data.data)
      toast.success('이미지가 생성됐습니다.')
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      toast.error(err.response?.data?.message ?? '이미지 생성 실패')
    } finally {
      setLoading(false)
    }
  }

  const handleInsert = () => {
    if (!previewUrl) return
    onInsert(`![${prompt}](${previewUrl})`)
    setOpen(false)
    setPrompt('')
    setPreviewUrl(null)
    toast.success('이미지가 삽입됐습니다.')
  }

  const gptEnabled = isGptModel(selectedModel)
  const keyReady = hasGptApiKey !== false
  const available = gptEnabled && keyReady

  const buttonTitle = !gptEnabled
    ? 'GPT 모델 선택 시에만 이미지 생성 가능'
    : !keyReady
      ? 'GPT API 키를 먼저 설정해 주세요 (프로필 페이지)'
      : 'GPT DALL-E 3으로 이미지 생성'

  return (
    <>
      <button
        type="button"
        className={styles.trigger}
        onClick={handleOpen}
        title={buttonTitle}
        style={{ opacity: available ? 1 : 0.5 }}
      >
        AI 이미지 생성
      </button>

      {open && (
        <div className={styles.overlay} onClick={() => setOpen(false)}>
          <div className={styles.modal} onClick={e => e.stopPropagation()}>
            <h3 className={styles.title}>AI 이미지 생성</h3>
            <p className={styles.desc}>DALL-E 3으로 이미지를 생성하고 Cloudinary에 업로드합니다.</p>

            <textarea
              className={styles.textarea}
              rows={3}
              placeholder="이미지 설명을 영어 또는 한국어로 입력하세요&#10;예: A dark theme coding blog cover with circuit board patterns"
              value={prompt}
              onChange={e => setPrompt(e.target.value)}
            />

            <button
              type="button"
              className={styles.generateBtn}
              onClick={handleGenerate}
              disabled={loading || !prompt.trim()}
            >
              {loading ? '생성 중...' : '이미지 생성'}
            </button>

            {previewUrl && (
              <div className={styles.preview}>
                <img src={previewUrl} alt="생성된 이미지" className={styles.previewImg} />
                <button type="button" className={styles.insertBtn} onClick={handleInsert}>
                  본문에 삽입
                </button>
              </div>
            )}

            <button type="button" className={styles.closeBtn} onClick={() => setOpen(false)}>닫기</button>
          </div>
        </div>
      )}
    </>
  )
}
