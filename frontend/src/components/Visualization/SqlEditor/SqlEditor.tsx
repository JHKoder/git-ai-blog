import Editor from '@monaco-editor/react'
import { useTheme } from '../../../hooks/useTheme'
import styles from './SqlEditor.module.css'

interface Props {
  value: string
  onChange: (value: string) => void
  height?: string
}

export function SqlEditor({ value, onChange, height = '120px' }: Props) {
  const { theme } = useTheme()

  return (
    <div className={styles.wrapper} style={{ height }}>
      <Editor
        height={height}
        language="sql"
        theme={theme === 'dark' ? 'vs-dark' : 'vs-light'}
        value={value}
        onChange={(v) => onChange(v ?? '')}
        options={{
          minimap: { enabled: false },
          fontSize: 13,
          lineNumbers: 'off',
          scrollBeyondLastLine: false,
          wordWrap: 'on',
          padding: { top: 8, bottom: 8 },
        }}
      />
    </div>
  )
}
