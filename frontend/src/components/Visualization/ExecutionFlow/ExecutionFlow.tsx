import { useMemo } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  type Node,
  type Edge,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import type { SimulationStep } from '../../../types/sqlviz'
import styles from './ExecutionFlow.module.css'

interface Props {
  steps: SimulationStep[]
}

export function ExecutionFlow({ steps }: Props) {
  const { nodes, edges } = useMemo(() => buildGraph(steps), [steps])

  return (
    <div className={styles.container}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        fitView
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
        proOptions={{ hideAttribution: true }}
      >
        <Background />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  )
}

function buildGraph(steps: SimulationStep[]): { nodes: Node[]; edges: Edge[] } {
  const txIds = Array.from(new Set(steps.map(s => s.txId)))
  const txColWidth = 220
  const rowHeight = 90

  const nodes: Node[] = steps.map((step, idx) => {
    const txIdx = txIds.indexOf(step.txId)
    const isConflict =
      step.operation.toUpperCase().includes('LOCK') ||
      step.operation.toUpperCase().includes('DEADLOCK') ||
      step.operation.toUpperCase().includes('WAIT')

    return {
      id: `step-${idx}`,
      position: { x: txIdx * txColWidth, y: idx * rowHeight },
      data: {
        label: (
          <div>
            <div style={{ fontWeight: 700, fontSize: 11 }}>{step.txId} — {step.operation}</div>
            <div style={{ fontFamily: 'monospace', fontSize: 10, color: '#6b7280' }}>{step.sql}</div>
            <div style={{ fontSize: 10, marginTop: 2, fontStyle: 'italic' }}>{step.result}</div>
          </div>
        ),
      },
      style: {
        background: isConflict ? '#fee2e2' : '#f9fafb',
        border: isConflict ? '1.5px solid #fca5a5' : '1px solid #e5e7eb',
        borderRadius: 8,
        padding: '6px 10px',
        width: 200,
        fontSize: 12,
      },
    }
  })

  const edges: Edge[] = []
  const txLastStep: Record<string, number> = {}
  steps.forEach((step, idx) => {
    const lastIdx = txLastStep[step.txId]
    if (lastIdx !== undefined) {
      edges.push({
        id: `edge-${lastIdx}-${idx}`,
        source: `step-${lastIdx}`,
        target: `step-${idx}`,
        type: 'smoothstep',
        animated: true,
        style: { stroke: '#9ca3af' },
      })
    }
    txLastStep[step.txId] = idx
  })

  return { nodes, edges }
}
