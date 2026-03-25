package github.jhkoder.aiblog.sqlviz.dto;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizScenario;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizWidget;
import github.jhkoder.aiblog.sqlviz.simulation.SimulationResult;

import java.time.LocalDateTime;
import java.util.List;

public record SqlVizResponse(
        Long id,
        String title,
        List<String> sqls,
        SqlVizScenario scenario,
        IsolationLevel isolationLevel,
        SimulationResult simulationData,
        String embedUrl,
        String hashnodeWidgetCode,
        LocalDateTime createdAt
) {
    public static SqlVizResponse of(SqlVizWidget widget, List<String> sqls,
                                    SimulationResult simulation, String baseUrl) {
        String embedUrl = baseUrl + "/embed/sqlviz/" + widget.getId();
        String widgetCode = "%%[sqlviz-" + widget.getId() + "]";
        return new SqlVizResponse(
                widget.getId(),
                widget.getTitle(),
                sqls,
                widget.getScenario(),
                widget.getIsolationLevel(),
                simulation,
                embedUrl,
                widgetCode,
                widget.getCreatedAt()
        );
    }
}
