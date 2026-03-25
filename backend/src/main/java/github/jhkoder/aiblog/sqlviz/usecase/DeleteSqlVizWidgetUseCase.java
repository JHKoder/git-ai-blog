package github.jhkoder.aiblog.sqlviz.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizWidgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteSqlVizWidgetUseCase {

    private final SqlVizWidgetRepository widgetRepository;

    @Transactional
    public void execute(Long widgetId, Long memberId) {
        if (!widgetRepository.existsByIdAndMemberId(widgetId, memberId)) {
            throw new NotFoundException("위젯을 찾을 수 없습니다.");
        }
        widgetRepository.deleteById(widgetId);
    }
}
