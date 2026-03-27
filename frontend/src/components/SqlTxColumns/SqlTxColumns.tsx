import styles from './SqlTxColumns.module.css'

interface Props {
  sqls: string[]
}

interface TxBlock {
  txId: string
  step: number
  sql: string
}

/**
 * widget.sqls 배열에서 `-- STEP:n TX:Tx` 주석을 파싱해
 * TX별 컬럼으로 나란히 표시한다.
 *
 * - 각 컬럼은 STEP 번호 오름차순 정렬
 * - STEP/TX 주석이 없는 SQL은 "기타" TX로 그루핑
 */
export function SqlTxColumns({ sqls }: Props) {
  const grouped = groupByTx(sqls)
  const txIds = Object.keys(grouped).sort()

  if (txIds.length === 0) return null

  // TX가 1개면 단일 컬럼으로 표시
  if (txIds.length === 1) {
    const txId = txIds[0]
    const blocks = grouped[txId]
    return (
      <div className={styles.singleCol}>
        <div className={styles.txColHeader}>{txId}</div>
        {blocks.map((b, i) => (
          <SqlBlock key={i} block={b} />
        ))}
      </div>
    )
  }

  return (
    <div
      className={styles.grid}
      style={{ gridTemplateColumns: `repeat(${txIds.length}, minmax(0, 1fr))` }}
    >
      {txIds.map(txId => (
        <div key={txId} className={styles.txCol}>
          <div className={styles.txColHeader}>{txId}</div>
          {grouped[txId].map((b, i) => (
            <SqlBlock key={i} block={b} />
          ))}
        </div>
      ))}
    </div>
  )
}

function SqlBlock({ block }: { block: TxBlock }) {
  // 주석 줄을 제거하고 실제 SQL만 표시
  const cleanSql = block.sql
    .split('\n')
    .filter(line => !line.trim().startsWith('--'))
    .join('\n')
    .trim()

  return (
    <div className={styles.sqlBlock}>
      <span className={styles.stepBadge}>STEP {block.step}</span>
      <pre className={styles.code}>{cleanSql}</pre>
    </div>
  )
}

// ── 파싱 유틸 ────────────────────────────────────────────────────────────────

const STEP_TX_RE = /--\s*STEP\s*:?\s*\[?(\d+)\]?\s+TX\s*:?\s*\[?(\w+)\]?/i
const STEP_ONLY_RE = /--\s*STEP\s*:?\s*\[?(\d+)\]?/i

function groupByTx(sqls: string[]): Record<string, TxBlock[]> {
  const result: Record<string, TxBlock[]> = {}

  sqls.forEach(sql => {
    const stepTxMatch = sql.match(STEP_TX_RE)
    if (stepTxMatch) {
      const step = parseInt(stepTxMatch[1], 10)
      const txId = stepTxMatch[2]
      if (!result[txId]) result[txId] = []
      result[txId].push({ txId, step, sql })
      return
    }

    const stepOnlyMatch = sql.match(STEP_ONLY_RE)
    if (stepOnlyMatch) {
      const step = parseInt(stepOnlyMatch[1], 10)
      const txId = `T${step}`
      if (!result[txId]) result[txId] = []
      result[txId].push({ txId, step, sql })
      return
    }

    // STEP 주석 없음 → 기타
    const txId = '기타'
    if (!result[txId]) result[txId] = []
    result[txId].push({ txId, step: 0, sql })
  })

  // 각 TX 내부를 STEP 번호 오름차순 정렬
  for (const txId of Object.keys(result)) {
    result[txId].sort((a, b) => a.step - b.step)
  }

  return result
}
