package github.jhkoder.aiblog.sqlviz.simulation;

/**
 * 시뮬레이션 단일 스텝.
 *
 * warning: null이면 정상, 값이 있으면 race condition / 시뮬레이터 한계 구간 — 프론트에서 ⚠️ 아이콘 + 툴팁으로 표시.
 */
public record SimulationStep(
        int step,
        String txId,
        String operation,
        String sql,
        String result,
        String detail,
        int durationMs,
        String warning
) {
    /** warning 없는 기본 생성자 (하위 호환) */
    public SimulationStep(int step, String txId, String operation, String sql,
                          String result, String detail, int durationMs) {
        this(step, txId, operation, sql, result, detail, durationMs, null);
    }
}
