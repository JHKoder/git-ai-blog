package github.jhkoder.aiblog.repo.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.github.GitHubClient;
import github.jhkoder.aiblog.repo.domain.Repo;
import github.jhkoder.aiblog.repo.domain.RepoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteRepoUseCase {

    private final RepoRepository repoRepository;
    private final GitHubClient gitHubClient;

    @Transactional
    public void execute(Long repoId, Long memberId) {
        Repo repo = repoRepository.findByIdAndMemberId(repoId, memberId)
                .orElseThrow(() -> new NotFoundException("레포를 찾을 수 없습니다."));

        // Webhook 삭제 (등록된 경우)
        if (repo.getWebhookId() != null) {
            gitHubClient.deleteWebhook(repo.getOwner(), repo.getRepoName(), repo.getWebhookId());
        }

        repoRepository.deleteRepo(repoId);
    }
}
