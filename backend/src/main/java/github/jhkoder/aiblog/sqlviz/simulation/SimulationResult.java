package github.jhkoder.aiblog.sqlviz.simulation;

import java.util.List;

/**
 * 시뮬레이션 전체 결과.
 *
 * limitations: 이 시뮬레이션의 알려진 한계 목록.
 *              null/빈 리스트면 표시 안 함.
 *              프론트에서 결과 하단 회색 작은 텍스트로 표시 (핵심 시각화 방해 안 함).
 *              예: "SSI(Serializable Snapshot Isolation) 충돌은 시뮬레이터 범위 밖입니다."
 */
public record SimulationResult(
        List<SimulationStep> steps,
        String summary,
        boolean hasConflict,
        String conflictType,
        List<String> limitations
) {
    /** limitations 없는 기본 생성자 (하위 호환) */
    public SimulationResult(List<SimulationStep> steps, String summary,
                            boolean hasConflict, String conflictType) {
        this(steps, summary, hasConflict, conflictType, List.of());
    }
}
