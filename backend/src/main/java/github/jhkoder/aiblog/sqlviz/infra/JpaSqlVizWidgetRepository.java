package github.jhkoder.aiblog.sqlviz.infra;

import github.jhkoder.aiblog.sqlviz.domain.SqlVizWidget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaSqlVizWidgetRepository extends JpaRepository<SqlVizWidget, Long> {
    List<SqlVizWidget> findByMemberId(Long memberId);
    boolean existsByIdAndMemberId(Long id, Long memberId);
}
