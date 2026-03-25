import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Components } from 'react-markdown'
import { MermaidBlock } from '../MermaidBlock/MermaidBlock'
import { SqlVizMarker } from '../SqlVizMarker/SqlVizMarker'

interface Props {
  content: string
}

// sql visualize 블록을 remark 파싱 전에 플레이스홀더로 치환
// 형식: ```sql visualize [dialect] [옵션...]
const SQL_VIZ_RE = /```sql visualize ([^\n`]+)\n([\s\S]*?)```/g

interface SqlVizBlock {
  placeholder: string
  dialect: string
  scenario: string
  sql: string
}

function preprocessContent(content: string): { processed: string; blocks: Map<string, SqlVizBlock> } {
  const blocks = new Map<string, SqlVizBlock>()
  let idx = 0

  const processed = content.replace(SQL_VIZ_RE, (_match, args: string, sql: string) => {
    const parts = args.trim().split(/\s+/)
    const dialect = parts[0] ?? 'generic'
    const scenario = parts[1] ?? 'deadlock'
    const placeholder = `SQLVIZ_PLACEHOLDER_${idx++}`
    blocks.set(placeholder, { placeholder, dialect, scenario, sql: sql.trim() })
    // [IMAGE: ...] と同様にテキストとして埋め込む — パースされてもpタグに包まれるだけ
    return `\n\n${placeholder}\n\n`
  })

  return { processed, blocks }
}

// [IMAGE: ...] 플레이스홀더 제거 (이미지 없는 경우 숨김)
function removeImagePlaceholders(content: string): string {
  return content.replace(/\[IMAGE:[^\]]*\]/g, '')
}

// AI 저자 인용 줄 제거 — PostDetailPage AI 메타 카드로 통합 표시
function removeAiAuthorLine(content: string): string {
  return content.replace(/^>\s*이 글은 .+이 작성을 도왔습니다\.?\s*$/gm, '')
}

const components = (blocks: Map<string, SqlVizBlock>): Components => ({
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
  p({ children }) {
    // SQLVIZ_PLACEHOLDER_N 텍스트 노드를 SqlVizMarker로 치환
    if (typeof children === 'string') {
      const block = blocks.get(children.trim())
      if (block) {
        return <SqlVizMarker dialect={block.dialect} scenario={block.scenario} sql={block.sql} />
      }
    }
    return <p>{children}</p>
  },
})

export function MarkdownRenderer({ content }: Props) {
  const cleaned = removeAiAuthorLine(removeImagePlaceholders(content))
  const { processed, blocks } = preprocessContent(cleaned)

  return (
    <ReactMarkdown remarkPlugins={[remarkGfm]} components={components(blocks)}>
      {processed}
    </ReactMarkdown>
  )
}
