import { useState, KeyboardEvent } from 'react'
import styles from './TagInput.module.css'

interface Props {
  tags: string[]
  onChange: (tags: string[]) => void
}

export function TagInput({ tags, onChange }: Props) {
  const [input, setInput] = useState('')

  const addTag = (value: string) => {
    const tag = value.trim().replace(/^#+/, '')
    if (!tag || tags.includes(tag)) return
    onChange([...tags, tag])
    setInput('')
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      addTag(input)
    } else if (e.key === 'Backspace' && input === '' && tags.length > 0) {
      onChange(tags.slice(0, -1))
    }
  }

  const removeTag = (tag: string) => {
    onChange(tags.filter(t => t !== tag))
  }

  return (
    <div className={styles.container}>
      {tags.map(tag => (
        <span key={tag} className={styles.tag}>
          #{tag}
          <button type="button" className={styles.remove} onClick={() => removeTag(tag)}>✕</button>
        </span>
      ))}
      <input
        className={styles.input}
        value={input}
        onChange={e => setInput(e.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={() => { if (input) addTag(input) }}
        placeholder={tags.length === 0 ? '태그 입력 후 Enter' : ''}
      />
    </div>
  )
}
