package github.jhkoder.aiblog.sqlviz.usecase;

import github.jhkoder.aiblog.common.exception.BusinessRuleException;
import github.jhkoder.aiblog.sqlviz.dto.SqlVizCreateRequest;
import github.jhkoder.aiblog.sqlviz.simulation.SimulationResult;
import github.jhkoder.aiblog.sqlviz.simulation.SqlVizSimulationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 저장 없는 미리보기 시뮬레이션.
 * POST /api/sqlviz/preview — 인증 필요, DB 저장 없음.
 */
@Service
@RequiredArgsConstructor
public class PreviewSqlVizUseCase {

    private final SqlVizSimulationEngine simulationEngine;

    public SimulationResult execute(SqlVizCreateRequest req) {
        if (req.sqls().size() > 10) {
            throw new BusinessRuleException("SQL은 최대 10개까지 입력 가능합니다.");
        }
        return simulationEngine.simulate(req.sqls(), req.scenario(), req.isolationLevel());
    }
}
