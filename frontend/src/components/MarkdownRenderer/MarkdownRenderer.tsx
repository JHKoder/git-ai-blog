import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Components } from 'react-markdown'
import { MermaidBlock } from '../MermaidBlock/MermaidBlock'

interface Props {
  content: string
}

const components: Components = {
  code({ className, children }) {
    const language = /language-(\w+)/.exec(className ?? '')?.[1] ?? ''
    const code = String(children).replace(/\n$/, '')

    if (language === 'mermaid') {
      return <MermaidBlock code={code} />
    }

    return (
      <code className={className}>
        {children}
      </code>
    )
  },
}

export function MarkdownRenderer({ content }: Props) {
  return (
    <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
      {content}
    </ReactMarkdown>
  )
}
