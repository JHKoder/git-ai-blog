import { useEffect, useRef, useState } from 'react'
import styles from './MermaidBlock.module.css'

interface Props {
  code: string
}

export function MermaidBlock({ code }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function render() {
      try {
        const mermaid = (await import('mermaid')).default
        const isDark = document.documentElement.getAttribute('data-theme') === 'dark'
        mermaid.initialize({ startOnLoad: false, theme: isDark ? 'dark' : 'default' })
        const id = `mermaid-${Math.random().toString(36).slice(2)}`
        const { svg } = await mermaid.render(id, code)
        if (!cancelled && ref.current) {
          ref.current.innerHTML = svg
        }
      } catch {
        if (!cancelled) setError(true)
      }
    }

    render()
    return () => { cancelled = true }
  }, [code])

  if (error) {
    return <pre className={styles.fallback}>{code}</pre>
  }

  return <div ref={ref} className={styles.diagram} />
}
