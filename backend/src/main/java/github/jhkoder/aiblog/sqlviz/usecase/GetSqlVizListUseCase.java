package github.jhkoder.aiblog.sqlviz.usecase;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import github.jhkoder.aiblog.common.exception.BusinessRuleException;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizWidgetRepository;
import github.jhkoder.aiblog.sqlviz.dto.SqlVizPageResponse;
import github.jhkoder.aiblog.sqlviz.dto.SqlVizResponse;
import github.jhkoder.aiblog.sqlviz.simulation.SimulationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetSqlVizListUseCase {

    private final SqlVizWidgetRepository widgetRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.base-url:https://git-ai-blog.kr}")
    private String baseUrl;

    @Transactional(readOnly = true)
    public SqlVizPageResponse execute(Long memberId, int page, int size) {
        int clampedSize = Math.min(Math.max(size, 5), 30);
        PageRequest pageable = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SqlVizResponse> resultPage = widgetRepository.findByMemberId(memberId, pageable)
                .map(widget -> {
                    try {
                        List<String> sqls = objectMapper.readValue(widget.getSqlsJson(), new TypeReference<>() {});
                        SimulationResult simulation = objectMapper.readValue(widget.getSimulationJson(), SimulationResult.class);
                        return SqlVizResponse.of(widget, sqls, simulation, baseUrl);
                    } catch (JacksonException e) {
                        throw new BusinessRuleException("위젯 데이터 파싱 실패: " + e.getMessage());
                    }
                });
        return SqlVizPageResponse.of(
                resultPage.getContent(),
                resultPage.getTotalPages(),
                resultPage.getTotalElements(),
                resultPage.getNumber(),
                resultPage.getSize()
        );
    }
}
