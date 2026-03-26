package github.jhkoder.aiblog.sqlviz.domain;

import java.util.List;
import java.util.Optional;

public interface SqlVizWidgetRepository {
    SqlVizWidget save(SqlVizWidget widget);
    Optional<SqlVizWidget> findById(Long id);
    List<SqlVizWidget> findByMemberId(Long memberId);
    Optional<SqlVizWidget> findByMemberIdAndSqlsJsonAndScenario(Long memberId, String sqlsJson, SqlVizScenario scenario);
    void deleteById(Long id);
    boolean existsByIdAndMemberId(Long id, Long memberId);
}
