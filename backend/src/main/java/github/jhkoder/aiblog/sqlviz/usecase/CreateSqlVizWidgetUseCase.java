package github.jhkoder.aiblog.sqlviz.usecase;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import github.jhkoder.aiblog.common.exception.BusinessRuleException;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizWidget;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizWidgetRepository;
import github.jhkoder.aiblog.sqlviz.dto.SqlVizCreateRequest;
import github.jhkoder.aiblog.sqlviz.dto.SqlVizResponse;
import github.jhkoder.aiblog.sqlviz.simulation.SimulationResult;
import github.jhkoder.aiblog.sqlviz.simulation.SqlVizSimulationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CreateSqlVizWidgetUseCase {

    private final SqlVizWidgetRepository widgetRepository;
    private final SqlVizSimulationEngine simulationEngine;
    private final ObjectMapper objectMapper;

    @Value("${app.base-url:https://git-ai-blog.kr}")
    private String baseUrl;

    @Transactional
    public SqlVizResponse execute(Long memberId, SqlVizCreateRequest req) {
        if (req.sqls().size() > 10) {
            throw new BusinessRuleException("SQL은 최대 10개까지 입력 가능합니다.");
        }

        try {
            String sqlsJson = objectMapper.writeValueAsString(req.sqls());
            String sqlsHash = SqlVizWidget.sha256Hex(sqlsJson);

            // 동일 SQL + 시나리오 조합이 이미 존재하면 재사용 (중복 생성 방지 — 해시 기반)
            Optional<SqlVizWidget> existing = widgetRepository
                    .findByMemberIdAndSqlsHashAndScenario(memberId, sqlsHash, req.scenario());
            if (existing.isPresent()) {
                SqlVizWidget widget = existing.get();
                SimulationResult cached = objectMapper.readValue(
                        widget.getSimulationJson(), SimulationResult.class);
                return SqlVizResponse.of(widget, req.sqls(), cached, baseUrl);
            }

            SimulationResult simulation = simulationEngine.simulate(
                    req.sqls(), req.scenario(), req.isolationLevel()
            );
            String simJson = objectMapper.writeValueAsString(simulation);

            SqlVizWidget widget = SqlVizWidget.create(
                    memberId, req.title(), sqlsJson, req.scenario(), req.isolationLevel()
            );
            widget.updateSimulation(simJson);
            SqlVizWidget saved = widgetRepository.save(widget);

            return SqlVizResponse.of(saved, req.sqls(), simulation, baseUrl);
        } catch (JacksonException e) {
            throw new BusinessRuleException("시뮬레이션 데이터 직렬화 실패: " + e.getMessage());
        }
    }
}
