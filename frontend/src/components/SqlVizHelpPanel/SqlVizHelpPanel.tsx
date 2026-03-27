import { useState } from 'react'
import styles from './SqlVizHelpPanel.module.css'

export function SqlVizHelpPanel() {
  const [open, setOpen] = useState(false)

  return (
    <div className={styles.wrap}>
      <button className={styles.toggle} onClick={() => setOpen(v => !v)}>
        {open ? '▲ 사용법 닫기' : '▼ 사용법 보기'}
      </button>
      {open && (
        <div className={styles.panel}>
          <ol className={styles.steps}>
            <li>
              <strong>1. 시나리오 선택</strong>
              <p>데드락, Dirty Read, Non-Repeatable Read, Phantom Read, Lost Update, MVCC 중 시각화할 동시성 문제를 선택합니다.</p>
            </li>
            <li>
              <strong>2. SQL 입력</strong>
              <p>
                각 트랜잭션에서 실행할 SQL을 입력합니다. <b>TX / STEP 삽입</b> 버튼으로 실행 순서를 지정할 수 있습니다.
              </p>
              <div className={styles.tip}>
                💡 <code>-- STEP:1 TX:T1</code> 주석을 앞에 붙이면 여러 TX를 원하는 순서로 인터리빙 실행할 수 있습니다.
              </div>
            </li>
            <li>
              <strong>3. 시뮬레이션 생성</strong>
              <p>제목을 입력하고 <b>시뮬레이션 생성</b> 버튼을 누르면 가상 실행 결과(타임라인/흐름)가 생성됩니다.</p>
            </li>
            <li>
              <strong>4. 임베드 복사</strong>
              <p>생성된 위젯의 <b>임베드 코드</b> 탭에서 URL 또는 iframe 코드를 복사해 블로그 게시글에 붙여넣습니다.</p>
            </li>
          </ol>
          <div className={styles.examplesTitle}>예시</div>
          <div className={styles.examples}>
            <div className={styles.example}>
              <div className={styles.exampleLabel}>예시 1 — 데드락 (T1↔T2 순환 잠금)</div>
              <div className={styles.exampleCols}>
                <div>
                  <div className={styles.txLabel}>T1 에디터</div>
                  <pre className={styles.code}>{`-- STEP:1
BEGIN;
-- STEP:3
UPDATE orders SET status='ok'
WHERE id = 1;
-- STEP:5
UPDATE orders SET status='ok'
WHERE id = 2;
-- STEP:7
COMMIT;`}</pre>
                </div>
                <div>
                  <div className={styles.txLabel}>T2 에디터</div>
                  <pre className={styles.code}>{`-- STEP:2
BEGIN;
-- STEP:4
UPDATE orders SET status='ok'
WHERE id = 2;
-- STEP:6
UPDATE orders SET status='ok'
WHERE id = 1;
-- STEP:8
COMMIT;`}</pre>
                </div>
              </div>
            </div>
            <div className={styles.example}>
              <div className={styles.exampleLabel}>예시 2 — 락 대기 (FOR KEY SHARE → DELETE 블로킹)</div>
              <div className={styles.exampleCols}>
                <div>
                  <div className={styles.txLabel}>T1 에디터</div>
                  <pre className={styles.code}>{`-- STEP:1
BEGIN ISOLATION LEVEL READ COMMITTED;
-- STEP:3
SELECT * FROM parent
WHERE id = 1 FOR KEY SHARE;
-- STEP:5
INSERT INTO child VALUES (202, 1);
-- STEP:6
COMMIT;`}</pre>
                </div>
                <div>
                  <div className={styles.txLabel}>T2 에디터</div>
                  <pre className={styles.code}>{`-- STEP:2
BEGIN ISOLATION LEVEL READ COMMITTED;
-- STEP:4
DELETE FROM parent
WHERE id = 1;
-- STEP:7
COMMIT;`}</pre>
                </div>
              </div>
            </div>
          </div>
          <div className={styles.note}>
            ⚠ SQL은 직접 실행되지 않습니다 — 순수 Java 가상 시뮬레이션으로 안전하게 동작합니다.
          </div>
        </div>
      )}
    </div>
  )
}
