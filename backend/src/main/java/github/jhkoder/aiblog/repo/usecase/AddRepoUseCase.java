package github.jhkoder.aiblog.repo.usecase;

import github.jhkoder.aiblog.infra.github.GitHubClient;
import github.jhkoder.aiblog.repo.domain.Repo;
import github.jhkoder.aiblog.repo.domain.RepoRepository;
import github.jhkoder.aiblog.repo.dto.RepoAddRequest;
import github.jhkoder.aiblog.repo.dto.RepoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddRepoUseCase {

    private final RepoRepository repoRepository;
    private final GitHubClient gitHubClient;

    @Value("${github.webhook.url:}")
    private String webhookUrl;

    @Value("${github.webhook.secret:local-webhook-secret}")
    private String webhookSecret;

    @Transactional
    public RepoResponse execute(Long memberId, RepoAddRequest request) {
        Repo repo = Repo.create(memberId, request.getOwner(), request.getRepoName(), request.getCollectType());
        repoRepository.save(repo);

        // Webhook 자동 등록 (URL 설정된 경우에만)
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            Long webhookId = gitHubClient.registerWebhook(
                    request.getOwner(), request.getRepoName(), webhookUrl, webhookSecret
            );
            if (webhookId != null) {
                repo.registerWebhook(webhookId);
                log.info("Webhook 등록 완료: {}/{} hookId={}", request.getOwner(), request.getRepoName(), webhookId);
            }
        }

        return RepoResponse.from(repo);
    }
}
