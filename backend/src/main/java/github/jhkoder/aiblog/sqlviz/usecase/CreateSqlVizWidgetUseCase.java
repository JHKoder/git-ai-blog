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

        SimulationResult simulation = simulationEngine.simulate(
                req.sqls(), req.scenario(), req.isolationLevel()
        );

        try {
            String sqlsJson = objectMapper.writeValueAsString(req.sqls());
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
