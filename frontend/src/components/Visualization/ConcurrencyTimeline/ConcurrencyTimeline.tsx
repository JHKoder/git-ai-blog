import { useState, useEffect, useRef } from 'react'
import type { SimulationResult, SimulationStep } from '../../../types/sqlviz'
import styles from './ConcurrencyTimeline.module.css'

interface Props {
  simulation: SimulationResult
}

export function ConcurrencyTimeline({ simulation }: Props) {
  const [currentStep, setCurrentStep] = useState(0)
  const [playing, setPlaying] = useState(false)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const steps = simulation.steps
  const txIds = Array.from(new Set(steps.map(s => s.txId)))

  useEffect(() => {
    if (playing) {
      intervalRef.current = setInterval(() => {
        setCurrentStep(prev => {
          if (prev >= steps.length - 1) {
            setPlaying(false)
            return prev
          }
          return prev + 1
        })
      }, 600)
    } else {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
    return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
  }, [playing, steps.length])

  const handlePlay = () => {
    if (currentStep >= steps.length - 1) setCurrentStep(0)
    setPlaying(true)
  }

  const visibleSteps = steps.slice(0, currentStep + 1)

  const getStepForTx = (stepIdx: number, txId: string): SimulationStep | null => {
    const s = steps[stepIdx]
    return s && s.txId === txId ? s : null
  }

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <div className={styles.controls}>
          <button
            className={styles.btn}
            onClick={playing ? () => setPlaying(false) : handlePlay}
          >
            {playing ? '⏸ 일시정지' : '▶ 재생'}
          </button>
          <button className={styles.btn} onClick={() => { setPlaying(false); setCurrentStep(0) }}>
            ⏮ 처음
          </button>
        </div>
        <input
          type="range"
          min={0}
          max={steps.length - 1}
          value={currentStep}
          onChange={e => { setPlaying(false); setCurrentStep(Number(e.target.value)) }}
          className={styles.slider}
        />
        <span className={styles.stepCount}>{currentStep + 1} / {steps.length}</span>
      </div>

      {simulation.hasConflict && (
        <div className={styles.conflictBadge}>
          ⚠ 충돌 감지: {simulation.conflictType}
        </div>
      )}

      <div className={styles.timeline}>
        <div className={styles.txHeaders}>
          <div className={styles.stepCol} />
          {txIds.map(txId => (
            <div key={txId} className={styles.txHeader}>{txId}</div>
          ))}
        </div>

        {visibleSteps.map((step, idx) => (
          <div key={idx} className={`${styles.row} ${idx === currentStep ? styles.active : ''}`}>
            <div className={styles.stepCol}>
              <span className={styles.stepNum}>#{step.step}</span>
            </div>
            {txIds.map(txId => {
              const s = getStepForTx(idx, txId)
              return (
                <div key={txId} className={styles.cell}>
                  {s && (
                    <div className={`${styles.stepBox} ${getOperationClass(s.operation, styles)}`}>
                      <div className={styles.operation}>{s.operation}</div>
                      <div className={styles.sql}>{s.sql}</div>
                      <div className={styles.result}>{s.result}</div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        ))}
      </div>

      <div className={styles.summary}>
        <strong>요약:</strong> {simulation.summary}
      </div>
    </div>
  )
}

function getOperationClass(operation: string, styles: Record<string, string>): string {
  const op = operation.toUpperCase()
  if (op.includes('LOCK') || op.includes('WAIT') || op.includes('DEADLOCK')) return styles.opDanger
  if (op.includes('COMMIT')) return styles.opSuccess
  if (op.includes('ROLLBACK') || op.includes('ABORT')) return styles.opWarning
  return styles.opNormal
}
