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

  // 현재 표시 중인 스텝이 BLOCKED인지 확인
  const isCurrentStepBlocked = steps[currentStep]?.result === 'blocked'

  // BLOCKED 상태에서 블로킹된 TX ID
  const blockedTxId = isCurrentStepBlocked ? steps[currentStep].txId : null

  // BLOCKED 상태에서 다른 TX의 다음 진행 가능한 스텝 인덱스 계산
  // (blockedTxId가 아닌 TX 중 currentStep 이후 첫 스텝)
  const nextOtherTxStepIdx: number | null = (() => {
    if (!isCurrentStepBlocked || blockedTxId === null) return null
    for (let i = currentStep + 1; i < steps.length; i++) {
      if (steps[i].txId !== blockedTxId) return i
    }
    return null
  })()

  useEffect(() => {
    // BLOCKED 스텝에 도달하면 자동 일시정지
    if (playing && isCurrentStepBlocked) {
      setPlaying(false)
      return
    }
    if (playing) {
      intervalRef.current = setInterval(() => {
        setCurrentStep(prev => {
          if (prev >= steps.length - 1) {
            setPlaying(false)
            return prev
          }
          // 다음 스텝이 BLOCKED면 그 스텝에서 멈춤
          const next = prev + 1
          if (steps[next]?.result === 'blocked') {
            setPlaying(false)
          }
          return next
        })
      }, 600)
    } else {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
    return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
  }, [playing, steps.length, isCurrentStepBlocked, steps])

  const handlePlay = () => {
    if (currentStep >= steps.length - 1) setCurrentStep(0)
    setPlaying(true)
  }

  // BLOCKED 구간에서 다른 TX의 다음 스텝으로 이동
  const handleAdvanceOtherTx = () => {
    if (nextOtherTxStepIdx === null) return
    setPlaying(false)
    setCurrentStep(nextOtherTxStepIdx)
  }

  const visibleSteps = steps.slice(0, currentStep + 1)

  const getStepForTx = (stepIdx: number, txId: string): SimulationStep | null => {
    const s = steps[stepIdx]
    return s && s.txId === txId ? s : null
  }

  // 현재 스텝에 BLOCKED가 있는지 (LOCK ZONE 배지 표시 조건)
  const hasBlockedAtCurrent = visibleSteps.some(
    (_, idx) => idx === currentStep && steps[idx]?.result === 'blocked'
  )

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

      {hasBlockedAtCurrent && (
        <div className={styles.lockZoneRow}>
          <div className={styles.lockZoneBadge}>
            🔒 LOCK ZONE — 잠금 대기 중 (재생이 일시정지됩니다)
          </div>
          {nextOtherTxStepIdx !== null && (
            <button
              className={styles.btnAdvance}
              onClick={handleAdvanceOtherTx}
              title={`${steps[nextOtherTxStepIdx].txId} 다음 단계 실행`}
            >
              ▶ {steps[nextOtherTxStepIdx].txId} 다음 단계
            </button>
          )}
          {nextOtherTxStepIdx === null && (
            <span className={styles.lockResolved}>락 해소 가능한 다음 단계 없음</span>
          )}
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
                    <div className={`${styles.stepBox} ${getResultColorClass(s.result, s.operation, styles)}`}>
                      <div className={styles.operation}>
                        {s.operation}
                        {s.warning && (
                          <span className={styles.warningIcon} title={s.warning}>⚠️</span>
                        )}
                      </div>
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

      {simulation.limitations && simulation.limitations.length > 0 && (
        <div className={styles.limitations}>
          <span className={styles.limitationsLabel}>⚠ 시뮬레이터 한계</span>
          <ul className={styles.limitationsList}>
            {simulation.limitations.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

/**
 * result 값 기준 5단계 색상 클래스:
 *  회색  — 진행 중 / 일반 (BEGIN, SELECT, INSERT, UPDATE 등 success)
 *  초록  — 커밋 완료 (COMMIT success)
 *  주황  — 롤백 / 경고 (ROLLBACK, dirty_value, non_repeatable, phantom, lost_update)
 *  빨강  — 데드락 (deadlock, rollback after deadlock)
 *  보라  — 잠금 대기 (blocked)
 */
function getResultColorClass(result: string, operation: string, s: Record<string, string>): string {
  const r = result.toLowerCase()
  const op = operation.toUpperCase()

  if (r === 'blocked') return s.opBlocked          // 보라
  if (r === 'deadlock') return s.opDeadlock         // 빨강
  if (op === 'COMMIT' && r === 'success') return s.opSuccess  // 초록
  if (
    r === 'rollback' ||
    op.includes('ROLLBACK') ||
    r === 'dirty_value' ||
    r === 'non_repeatable' ||
    r === 'phantom' ||
    r === 'lost_update'
  ) return s.opWarning                             // 주황
  return s.opNormal                                // 회색
}
