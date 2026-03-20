package github.jhkoder.aiblog.infra.hashnode;

import github.jhkoder.aiblog.common.exception.ExternalApiException;
import github.jhkoder.aiblog.common.exception.RateLimitException;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    public PublishResult publishPost(String title, String content, String token, String publicationId, java.util.List<String> tags) {
        String query = graphqlBuilder.buildPublishQuery(title, content, publicationId, tags);
        JsonNode response = executeWithRetry(query, token);
        JsonNode post = response.path("data").path("publishPost").path("post");
        return new PublishResult(post.path("id").asText(), post.path("url").asText());
    }

    public void updatePost(String postId, String title, String content, String token, java.util.List<String> tags) {
        String query = graphqlBuilder.buildUpdateQuery(postId, title, content, tags);
        executeWithRetry(query, token);
    }

    public void deletePost(String postId) {
        String query = graphqlBuilder.buildDeleteQuery(postId);
        executeWithRetry(query, null);
    }

    public List<HashnodePostInfo> fetchMyPosts(String token, String publicationId) {
        String query = graphqlBuilder.buildFetchPostsQuery(publicationId);
        JsonNode response = executeWithRetry(query, token);
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
    private JsonNode executeWithRetry(String query, String token) {
        int maxRetries = 2;
        long delayMs = 1000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return execute(query, token);
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

    private JsonNode execute(String query, String token) throws Exception {
        // JSON body를 문자열로 직접 구성 (Map 직렬화 codec 의존 제거)
        String jsonBody = "{\"query\":" + objectMapper.writeValueAsString(query) + "}";

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

        return objectMapper.readTree(responseBody);
    }

    private void sleepSafely(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
