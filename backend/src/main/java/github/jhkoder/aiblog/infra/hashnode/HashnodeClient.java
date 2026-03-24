package github.jhkoder.aiblog.infra.hashnode;

import github.jhkoder.aiblog.common.exception.ExternalApiException;
import github.jhkoder.aiblog.common.exception.RateLimitException;
import github.jhkoder.aiblog.infra.hashnode.HashnodeGraphqlBuilder.GqlRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HashnodeClient {

    private static final String HASHNODE_API_URL = "https://gql.hashnode.com";
    private final WebClient.Builder webClientBuilder;
    private final HashnodeGraphqlBuilder graphqlBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Getter
    public static class PublishResult {
        private final String id;
        private final String url;
        public PublishResult(String id, String url) {
            this.id = id;
            this.url = url;
        }
    }

    @Getter
    public static class HashnodePostInfo {
        private final String id;
        private final String title;
        private final String content;
        private final String url;
        public HashnodePostInfo(String id, String title, String content, String url) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.url = url;
        }
    }

    public PublishResult publishPost(String title, String content, String token, String publicationId, List<String> tags) {
        GqlRequest req = graphqlBuilder.buildPublishRequest(title, content, publicationId, tags);
        JsonNode response = executeWithRetry(req, token);
        JsonNode post = response.path("data").path("publishPost").path("post");
        return new PublishResult(post.path("id").asText(), post.path("url").asText());
    }

    public void updatePost(String postId, String title, String content, String token, List<String> tags) {
        GqlRequest req = graphqlBuilder.buildUpdateRequest(postId, title, content, tags);
        executeWithRetry(req, token);
    }

    public void deletePost(String postId, String token) {
        GqlRequest req = graphqlBuilder.buildDeleteRequest(postId);
        executeWithRetry(req, token);
    }

    public List<HashnodePostInfo> fetchMyPosts(String token, String publicationId) {
        GqlRequest req = graphqlBuilder.buildFetchPostsRequest(publicationId);
        JsonNode response = executeWithRetry(req, token);
        List<HashnodePostInfo> posts = new ArrayList<>();
        JsonNode edges = response.path("data").path("publication").path("posts").path("edges");
        for (JsonNode edge : edges) {
            JsonNode node = edge.path("node");
            posts.add(new HashnodePostInfo(
                    node.path("id").asText(),
                    node.path("title").asText(),
                    node.path("content").path("markdown").asText(),
                    node.path("url").asText()
            ));
        }
        return posts;
    }

    @Retry(name = "hashnode")
    @CircuitBreaker(name = "hashnode")
    private JsonNode executeWithRetry(GqlRequest req, String token) {
        int maxRetries = 2;
        long delayMs = 1000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return execute(req, token);
            } catch (RateLimitException e) {
                throw e;
            } catch (WebClientResponseException e) {
                int statusCode = e.getStatusCode().value();
                if (statusCode == 429) {
                    throw new RateLimitException("Hashnode rate limit 초과");
                }
                if ((statusCode == 500 || statusCode == 503) && attempt < maxRetries) {
                    log.warn("Hashnode API {}. {}ms 후 재시도 ({}/{})", statusCode, delayMs, attempt + 1, maxRetries);
                    sleepSafely(delayMs);
                    delayMs *= 2;
                } else {
                    throw new ExternalApiException("Hashnode API 오류: " + e.getMessage(), e);
                }
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    log.warn("Hashnode 요청 실패. {}ms 후 재시도", delayMs);
                    sleepSafely(delayMs);
                    delayMs *= 2;
                } else {
                    throw new ExternalApiException("Hashnode API 호출 실패: " + e.getMessage(), e);
                }
            }
        }
        throw new ExternalApiException("Hashnode API 최대 재시도 횟수 초과");
    }

    private JsonNode execute(GqlRequest req, String token) throws Exception {
        // objectMapper가 query + variables 전체를 올바르게 직렬화 — 이중 이스케이프 없음
        String jsonBody = objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("query", req.query())
                        .set("variables", objectMapper.valueToTree(req.variables()))
        );

        WebClient.RequestBodySpec spec = webClientBuilder.build()
                .post()
                .uri(HASHNODE_API_URL)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (token != null && !token.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        String responseBody = spec.bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));

        JsonNode root = objectMapper.readTree(responseBody);

        // GraphQL errors 필드 확인
        JsonNode errors = root.path("errors");
        if (!errors.isMissingNode() && errors.isArray() && errors.size() > 0) {
            String errorMsg = errors.get(0).path("message").asText("Unknown GraphQL error");
            log.error("Hashnode GraphQL 오류: {}", errors);
            throw new ExternalApiException("Hashnode GraphQL 오류: " + errorMsg);
        }

        return root;
    }

    private void sleepSafely(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
