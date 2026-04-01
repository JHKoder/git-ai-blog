package github.jhkoder.aiblog.sqlviz.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SqlVizWidgetRepository {
    SqlVizWidget save(SqlVizWidget widget);
    Optional<SqlVizWidget> findById(Long id);
    List<SqlVizWidget> findByMemberId(Long memberId);
    Page<SqlVizWidget> findByMemberId(Long memberId, Pageable pageable);
    Optional<SqlVizWidget> findByMemberIdAndSqlsHashAndScenario(Long memberId, String sqlsHash, SqlVizScenario scenario);
    void deleteById(Long id);
    boolean existsByIdAndMemberId(Long id, Long memberId);
}
