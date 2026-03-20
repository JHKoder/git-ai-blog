package github.jhkoder.aiblog.repo.usecase;

import github.jhkoder.aiblog.repo.domain.*;
import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandleWebhookEventUseCase {

    private final RepoRepository repoRepository;
    private final PostRepository postRepository;
    private final RepoCollectHistoryRepository historyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Async
    @Transactional
    public void execute(String event, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String owner = root.path("repository").path("owner").path("login").asText();
            String repoName = root.path("repository").path("name").asText();

            if (owner.isBlank() || repoName.isBlank()) return;

            switch (event) {
                case "push" -> handlePush(owner, repoName, root);
                case "pull_request" -> handlePr(owner, repoName, root);
                default -> log.debug("처리하지 않는 이벤트: {}", event);
            }
        } catch (Exception e) {
            log.error("Webhook 이벤트 처리 실패: {}", e.getMessage(), e);
        }
    }

    private void handlePush(String owner, String repoName, JsonNode root) {
        List<Repo> targets = repoRepository.findByOwnerAndRepoName(owner, repoName)
                .stream().filter(r -> r.getCollectType() == CollectType.COMMIT).toList();

        for (JsonNode commit : root.path("commits")) {
            String sha = commit.path("id").asText();
            String message = commit.path("message").asText();

            if (!message.toLowerCase().contains("[blog]")) continue;

            for (Repo repo : targets) {
                if (historyRepository.existsByRepoIdAndRefTypeAndRefId(repo.getId(), CollectType.COMMIT, sha)) {
                    log.debug("Webhook 커밋 중복 스킵: {}", sha);
                    continue;
                }
                String content = "# 커밋\n\n" + message;
                String title = owner + "/" + repoName + " 커밋 - " + sha.substring(0, 7);
                Post post = Post.create(repo.getMemberId(), title, content, ContentType.CODING);
                postRepository.save(post);
                historyRepository.save(RepoCollectHistory.of(repo.getId(), CollectType.COMMIT, sha, post.getId()));
                log.info("Webhook 커밋 수집 완료: {} -> postId={}", sha, post.getId());
            }
        }
    }

    private void handlePr(String owner, String repoName, JsonNode root) {
        String action = root.path("action").asText();
        if (!"closed".equals(action) || !root.path("pull_request").path("merged").asBoolean()) return;

        boolean hasBlogLabel = false;
        for (JsonNode label : root.path("pull_request").path("labels")) {
            if ("blog".equalsIgnoreCase(label.path("name").asText())) {
                hasBlogLabel = true;
                break;
            }
        }
        if (!hasBlogLabel) return;

        int prNumber = root.path("pull_request").path("number").asInt();
        String title = root.path("pull_request").path("title").asText();
        String body = root.path("pull_request").path("body").asText();
        String prKey = String.valueOf(prNumber);

        List<Repo> targets = repoRepository.findByOwnerAndRepoName(owner, repoName)
                .stream().filter(r -> r.getCollectType() == CollectType.PR).toList();

        for (Repo repo : targets) {
            if (historyRepository.existsByRepoIdAndRefTypeAndRefId(repo.getId(), CollectType.PR, prKey)) {
                log.debug("Webhook PR 중복 스킵: #{}", prNumber);
                continue;
            }
            String content = "# PR #" + prNumber + ": " + title + (body.isBlank() ? "" : "\n\n" + body);
            Post post = Post.create(repo.getMemberId(), owner + "/" + repoName + " PR #" + prNumber, content, ContentType.CODING);
            postRepository.save(post);
            historyRepository.save(RepoCollectHistory.of(repo.getId(), CollectType.PR, prKey, post.getId()));
            log.info("Webhook PR 수집 완료: #{} -> postId={}", prNumber, post.getId());
        }
    }
}
