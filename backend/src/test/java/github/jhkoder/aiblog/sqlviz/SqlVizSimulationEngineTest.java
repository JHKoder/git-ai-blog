package github.jhkoder.aiblog.sqlviz;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizScenario;
import github.jhkoder.aiblog.sqlviz.simulation.SimulationResult;
import github.jhkoder.aiblog.sqlviz.simulation.SimulationStep;
import github.jhkoder.aiblog.sqlviz.simulation.SqlParser;
import github.jhkoder.aiblog.sqlviz.simulation.SqlVizSimulationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SqlVizSimulationEngine 단위 테스트.
 * 사용자 시나리오: T1의 FOR KEY SHARE가 T2의 DELETE를 블로킹하고, T1 커밋 후 T2가 잠금을 획득하는 흐름.
 */
class SqlVizSimulationEngineTest {

    private SqlVizSimulationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SqlVizSimulationEngine();
    }

    @Test
    @DisplayName("FOR KEY SHARE + DELETE 인터리빙: T2 DELETE가 T1 FOR KEY SHARE에 BLOCKED, T1 커밋 후 T2 락 획득")
    void interleavedLockWait_forKeyShareBlocksDelete() {
        // 사용자 제시 SQL 시나리오
        List<String> sqls = List.of(
                "-- STEP:1 TX:T1\nBEGIN ISOLATION LEVEL READ COMMITTED;",
                "-- STEP:2 TX:T2\nBEGIN ISOLATION LEVEL READ COMMITTED;",
                "-- STEP:3 TX:T1\nSELECT * FROM parent WHERE id = 1 FOR KEY SHARE;",
                "-- STEP:4 TX:T2\nDELETE FROM parent WHERE id = 1;",
                "-- STEP:5 TX:T1\nINSERT INTO child VALUES (202, 1);",
                "-- STEP:6 TX:T1\nCOMMIT;",
                "-- STEP:7 TX:T2\nCOMMIT;"
        );

        SimulationResult result = engine.simulate(sqls, SqlVizScenario.LOCK_WAIT, IsolationLevel.READ_COMMITTED);

        assertThat(result).isNotNull();
        assertThat(result.steps()).isNotEmpty();

        // T1 BEGIN 확인
        assertThat(result.steps()).anyMatch(s -> "T1".equals(s.txId()) && "BEGIN".equals(s.operation()));

        // T2 BEGIN 확인
        assertThat(result.steps()).anyMatch(s -> "T2".equals(s.txId()) && "BEGIN".equals(s.operation()));

        // T1 FOR KEY SHARE SELECT 성공 확인
        assertThat(result.steps()).anyMatch(s ->
                "T1".equals(s.txId()) && "SELECT".equals(s.operation()) && "success".equals(s.result()));

        // T2 DELETE BLOCKED 확인 (FOR KEY SHARE가 FOR UPDATE와 충돌)
        SimulationStep blockedStep = result.steps().stream()
                .filter(s -> "T2".equals(s.txId()) && "blocked".equals(s.result()))
                .findFirst()
                .orElse(null);
        assertThat(blockedStep).as("T2 DELETE는 T1 FOR KEY SHARE에 의해 BLOCKED 되어야 함").isNotNull();

        // T1 COMMIT 확인
        assertThat(result.steps()).anyMatch(s -> "T1".equals(s.txId()) && "COMMIT".equals(s.operation()));

        // T1 커밋 후 T2 잠금 획득(재시도 성공) 확인
        assertThat(result.steps()).anyMatch(s ->
                "T2".equals(s.txId()) && "success".equals(s.result()) && "DELETE".equals(s.operation()));

        // hasConflict: BLOCKED가 있으므로 true
        assertThat(result.hasConflict()).isTrue();
    }

    @Test
    @DisplayName("SqlParser: SELECT FOR KEY SHARE 파싱 시 lockType = FOR_KEY_SHARE")
    void sqlParser_forKeyShare_lockType() {
        var parsed = SqlParser.parse("SELECT * FROM parent WHERE id = 1 FOR KEY SHARE");
        assertThat(parsed.lockType()).isNotNull();
        assertThat(parsed.lockType().name()).isEqualTo("FOR_KEY_SHARE");
    }

    @Test
    @DisplayName("SqlParser: SELECT FOR UPDATE 파싱 시 lockType = FOR_UPDATE")
    void sqlParser_forUpdate_lockType() {
        var parsed = SqlParser.parse("SELECT * FROM orders WHERE id = 1 FOR UPDATE");
        assertThat(parsed.lockType()).isNotNull();
        assertThat(parsed.lockType().name()).isEqualTo("FOR_UPDATE");
    }

    @Test
    @DisplayName("SqlParser: 일반 SELECT는 lockType = null")
    void sqlParser_plainSelect_lockTypeNull() {
        var parsed = SqlParser.parse("SELECT * FROM orders WHERE id = 1");
        assertThat(parsed.lockType()).isNull();
    }

    @Test
    @DisplayName("SqlParser: -- STEP:n (TX 없음) → StepMeta(step, null)")
    void sqlParser_stepOnly_noTx() {
        var parsed = SqlParser.parse("-- STEP:3\nSELECT * FROM parent WHERE id = 1");
        assertThat(parsed.stepMeta()).isNotNull();
        assertThat(parsed.stepMeta().step()).isEqualTo(3);
        assertThat(parsed.stepMeta().txId()).isNull();
    }

    @Test
    @DisplayName("LOCK_WAIT 시나리오 빌더: T1 미커밋 → T2 BLOCKED → T1 커밋 → T2 획득")
    void lockWait_builderScenario() {
        List<String> sqls = List.of(
                "UPDATE orders SET status='processing' WHERE id = 1",
                "UPDATE orders SET status='cancelled' WHERE id = 1"
        );

        SimulationResult result = engine.simulate(sqls, SqlVizScenario.LOCK_WAIT, IsolationLevel.READ_COMMITTED);

        assertThat(result.steps()).isNotEmpty();
        // T1 UPDATE 성공
        assertThat(result.steps()).anyMatch(s -> "T1".equals(s.txId()) && "UPDATE".equals(s.operation()) && "success".equals(s.result()));
        // T2 BLOCKED
        assertThat(result.steps()).anyMatch(s -> "T2".equals(s.txId()) && "blocked".equals(s.result()));
        // T1 COMMIT
        assertThat(result.steps()).anyMatch(s -> "T1".equals(s.txId()) && "COMMIT".equals(s.operation()));
        // T2 최종 성공
        assertThat(result.steps()).anyMatch(s -> "T2".equals(s.txId()) && "success".equals(s.result()));
        assertThat(result.hasConflict()).isTrue();
        assertThat(result.conflictType()).isEqualTo("LOCK_WAIT");
    }
}
