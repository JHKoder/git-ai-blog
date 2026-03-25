package github.jhkoder.aiblog.sqlviz.infra;

import github.jhkoder.aiblog.sqlviz.domain.SqlVizWidget;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizWidgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SqlVizWidgetRepositoryImpl implements SqlVizWidgetRepository {

    private final JpaSqlVizWidgetRepository jpa;

    @Override
    public SqlVizWidget save(SqlVizWidget widget) {
        return jpa.save(widget);
    }

    @Override
    public Optional<SqlVizWidget> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<SqlVizWidget> findByMemberId(Long memberId) {
        return jpa.findByMemberId(memberId);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean existsByIdAndMemberId(Long id, Long memberId) {
        return jpa.existsByIdAndMemberId(id, memberId);
    }
}
