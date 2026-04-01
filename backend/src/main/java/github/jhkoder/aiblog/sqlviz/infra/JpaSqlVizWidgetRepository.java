package github.jhkoder.aiblog.sqlviz.infra;

import github.jhkoder.aiblog.sqlviz.domain.SqlVizScenario;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizWidget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaSqlVizWidgetRepository extends JpaRepository<SqlVizWidget, Long> {
    List<SqlVizWidget> findByMemberId(Long memberId);
    Page<SqlVizWidget> findByMemberId(Long memberId, Pageable pageable);
    Optional<SqlVizWidget> findByMemberIdAndSqlsHashAndScenario(Long memberId, String sqlsHash, SqlVizScenario scenario);
    boolean existsByIdAndMemberId(Long id, Long memberId);
}
