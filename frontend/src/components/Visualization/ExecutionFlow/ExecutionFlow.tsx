import { useMemo, useState, useCallback } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  type Node,
  type Edge,
  type NodeMouseHandler,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import type { SimulationStep, SimulationResult } from '../../../types/sqlviz'
import styles from './ExecutionFlow.module.css'

interface Props {
  simulation: SimulationResult
}

interface StepDetail {
  txId: string
  operation: string
  sql: string
  result: string
  detail: string
}

export function ExecutionFlow({ simulation }: Props) {
  const [tooltip, setTooltip] = useState<StepDetail | null>(null)
  const { nodes, edges } = useMemo(() => buildGraph(simulation.steps), [simulation.steps])

  const onNodeClick: NodeMouseHandler = useCallback((_event, node) => {
    const data = node.data as { stepDetail: StepDetail }
    setTooltip(prev => prev?.txId === data.stepDetail.txId && prev?.operation === data.stepDetail.operation ? null : data.stepDetail)
  }, [])

  return (
    <div className={styles.wrapper}>
      <div className={styles.container}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          fitView
          nodesDraggable={false}
          nodesConnectable={false}
          elementsSelectable={true}
          onNodeClick={onNodeClick}
          proOptions={{ hideAttribution: true }}
        >
          <Background />
          <Controls showInteractive={false} />
        </ReactFlow>

        {simulation.hasConflict && (
          <div className={styles.deadlockBanner}>
            💀 {simulation.conflictType} 발생
          </div>
        )}
      </div>

      {tooltip && (
        <div className={styles.tooltip}>
          <div className={styles.tooltipHeader}>
            <span className={styles.tooltipTx}>{tooltip.txId}</span>
            <span className={styles.tooltipOp}>{tooltip.operation}</span>
            <button className={styles.tooltipClose} onClick={() => setTooltip(null)}>✕</button>
          </div>
          {tooltip.sql && <div className={styles.tooltipSql}>{tooltip.sql}</div>}
          <div className={styles.tooltipResult}>{tooltip.result}</div>
          {tooltip.detail && <div className={styles.tooltipDetail}>{tooltip.detail}</div>}
        </div>
      )}
    </div>
  )
}

function getEdgeStyle(step: SimulationStep): { stroke: string; strokeDasharray?: string } {
  const op = step.operation.toUpperCase()
  if (op.includes('DEADLOCK')) return { stroke: '#ef4444' }
  if (op.includes('LOCK') || op.includes('WAIT')) return { stroke: '#f97316', strokeDasharray: '5,3' }
  if (op.includes('COMMIT')) return { stroke: '#22c55e' }
  if (op.includes('ROLLBACK') || op.includes('ABORT')) return { stroke: '#eab308' }
  return { stroke: '#9ca3af' }
}

function getNodeStyle(step: SimulationStep): React.CSSProperties {
  const op = step.operation.toUpperCase()
  if (op.includes('DEADLOCK')) return { background: '#fee2e2', border: '2px solid #ef4444' }
  if (op.includes('LOCK') || op.includes('WAIT')) return { background: '#fff7ed', border: '1.5px solid #fb923c' }
  if (op.includes('COMMIT')) return { background: '#f0fdf4', border: '1.5px solid #86efac' }
  if (op.includes('ROLLBACK') || op.includes('ABORT')) return { background: '#fefce8', border: '1.5px solid #fde047' }
  return { background: '#f9fafb', border: '1px solid #e5e7eb' }
}

function getOperationIcon(step: SimulationStep): string {
  const op = step.operation.toUpperCase()
  if (op.includes('DEADLOCK')) return '💀'
  if (op.includes('WAIT')) return '⌛'
  if (op.includes('LOCK')) return '🔒'
  if (op.includes('COMMIT')) return '✅'
  if (op.includes('ROLLBACK') || op.includes('ABORT')) return '↩️'
  return ''
}

function buildGraph(steps: SimulationStep[]): { nodes: Node[]; edges: Edge[] } {
  const txIds = Array.from(new Set(steps.map(s => s.txId)))
  const txColWidth = 240
  const rowHeight = 90

  const nodes: Node[] = steps.map((step, idx) => {
    const txIdx = txIds.indexOf(step.txId)
    const icon = getOperationIcon(step)

    return {
      id: `step-${idx}`,
      position: { x: txIdx * txColWidth, y: idx * rowHeight },
      data: {
        label: (
          <div>
            <div style={{ fontWeight: 700, fontSize: 11 }}>
              {icon && <span style={{ marginRight: 4 }}>{icon}</span>}
              {step.txId} — {step.operation}
            </div>
            {step.sql && (
              <div style={{ fontFamily: 'monospace', fontSize: 10, color: '#6b7280', marginTop: 2 }}>
                {step.sql.length > 40 ? step.sql.slice(0, 40) + '…' : step.sql}
              </div>
            )}
            <div style={{ fontSize: 10, marginTop: 2, fontStyle: 'italic' }}>{step.result}</div>
          </div>
        ),
        stepDetail: {
          txId: step.txId,
          operation: step.operation,
          sql: step.sql,
          result: step.result,
          detail: step.detail,
        },
      },
      style: {
        ...getNodeStyle(step),
        borderRadius: 8,
        padding: '6px 10px',
        width: 210,
        fontSize: 12,
        cursor: 'pointer',
      },
    }
  })

  const edges: Edge[] = []
  const txLastStep: Record<string, number> = {}
  steps.forEach((step, idx) => {
    const lastIdx = txLastStep[step.txId]
    if (lastIdx !== undefined) {
      const edgeStyle = getEdgeStyle(step)
      edges.push({
        id: `edge-${lastIdx}-${idx}`,
        source: `step-${lastIdx}`,
        target: `step-${idx}`,
        type: 'smoothstep',
        animated: edgeStyle.stroke !== '#9ca3af',
        style: edgeStyle,
      })
    }
    txLastStep[step.txId] = idx
  })

  return { nodes, edges }
}
