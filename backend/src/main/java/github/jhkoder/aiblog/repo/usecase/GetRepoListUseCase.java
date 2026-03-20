package github.jhkoder.aiblog.repo.usecase;

import github.jhkoder.aiblog.repo.domain.RepoRepository;
import github.jhkoder.aiblog.repo.dto.RepoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetRepoListUseCase {

    private final RepoRepository repoRepository;

    @Transactional(readOnly = true)
    public List<RepoResponse> execute(Long memberId) {
        return repoRepository.findByMemberId(memberId).stream()
                .map(RepoResponse::from).toList();
    }
}
