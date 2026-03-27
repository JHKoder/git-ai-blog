package github.jhkoder.aiblog.infra.hashnode;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Hashnode GraphQL 요청 페이로드 빌더.
 * query + variables 분리 방식으로 이중 이스케이프 문제를 완전히 회피한다.
 * HashnodeClient.execute()가 {"query":..., "variables":...} 형태로 직렬화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HashnodeGraphqlBuilder {

    private final ObjectMapper objectMapper;

    public record GqlRequest(String query, Object variables) {}

    public GqlRequest buildPublishRequest(String title, String content, String publicationId, List<String> localTags, String memberHashnodeTags) {
        String query = """
                mutation PublishPost($input: PublishPostInput!) {
                  publishPost(input: $input) {
                    post {
                      id
                      url
                    }
                  }
                }
                """;
        ObjectNode input = objectMapper.createObjectNode();
        input.put("title", title);
        input.put("contentMarkdown", sanitizeForHashnode(content));
        input.put("publicationId", publicationId);
        input.set("tags", buildTagsNode(localTags, memberHashnodeTags));

        ObjectNode variables = objectMapper.createObjectNode();
        variables.set("input", input);
        return new GqlRequest(query, variables);
    }

    public GqlRequest buildUpdateRequest(String postId, String title, String content, List<String> localTags, String memberHashnodeTags) {
        String query = """
                mutation UpdatePost($input: UpdatePostInput!) {
                  updatePost(input: $input) {
                    post {
                      id
                      url
                    }
                  }
                }
                """;
        ObjectNode input = objectMapper.createObjectNode();
        input.put("id", postId);
        input.put("title", title);
        input.put("contentMarkdown", sanitizeForHashnode(content));
        input.set("tags", buildTagsNode(localTags, memberHashnodeTags));

        ObjectNode variables = objectMapper.createObjectNode();
        variables.set("input", input);
        return new GqlRequest(query, variables);
    }

    public GqlRequest buildDeleteRequest(String postId) {
        String query = """
                mutation RemovePost($input: RemovePostInput!) {
                  removePost(input: $input) {
                    post {
                      id
                    }
                  }
                }
                """;
        ObjectNode input = objectMapper.createObjectNode();
        input.put("id", postId);

        ObjectNode variables = objectMapper.createObjectNode();
        variables.set("input", input);
        return new GqlRequest(query, variables);
    }

    public GqlRequest buildFetchPostsRequest(String publicationId) {
        String query = """
                query FetchPosts($id: ObjectId!) {
                  publication(id: $id) {
                    posts(first: 20) {
                      edges {
                        node {
                          id
                          title
                          content {
                            markdown
                          }
                          url
                        }
                      }
                    }
                  }
                }
                """;
        ObjectNode variables = objectMapper.createObjectNode();
        variables.put("id", publicationId);
        return new GqlRequest(query, variables);
    }

    /**
     * Hashnode에 발행하기 전 우리 블로그 전용 마커를 제거한다.
     * - ```sql visualize ... ``` 블록: Hashnode 파서가 인식 못해 깨져 보임 → 제거
     * - [IMAGE: ...] 플레이스홀더: 이미지 미생성 시 노출됨 → 제거
     */
    private String sanitizeForHashnode(String content) {
        if (content == null) return null;
        // sql visualize 블록 제거 (플래그: DOTALL)
        String result = content.replaceAll("(?s)```sql visualize[^\\n]*\\n.*?```", "");
        // [IMAGE: ...] 마커 제거
        result = result.replaceAll("\\[IMAGE:[^\\]]*]", "");
        return result;
    }

    /**
     * 로컬 태그명을 Hashnode tag ID로 매핑해 tags 배열을 생성한다.
     * memberHashnodeTags — JSON 배열: [{"name":"java","slug":"java","id":"..."},...]
     * 매핑되지 않는 로컬 태그는 무시한다. (Hashnode API는 자체 DB에 등록된 ID만 허용)
     */
    private ArrayNode buildTagsNode(List<String> localTags, String memberHashnodeTags) {
        ArrayNode tagsNode = objectMapper.createArrayNode();
        if (localTags == null || localTags.isEmpty() || memberHashnodeTags == null || memberHashnodeTags.isBlank()) {
            return tagsNode;
        }
        try {
            List<Map<String, String>> tagMappings = objectMapper.readValue(
                    memberHashnodeTags, new TypeReference<>() {});
            Map<String, Map<String, String>> byName = new java.util.HashMap<>();
            for (Map<String, String> mapping : tagMappings) {
                String name = mapping.get("name");
                if (name != null) byName.put(name.toLowerCase(), mapping);
            }
            for (String localTag : localTags) {
                Map<String, String> mapping = byName.get(localTag.toLowerCase());
                if (mapping != null && mapping.get("id") != null) {
                    ObjectNode tagNode = objectMapper.createObjectNode();
                    tagNode.put("id", mapping.get("id"));
                    tagsNode.add(tagNode);
                }
            }
        } catch (Exception e) {
            log.warn("Hashnode 태그 매핑 파싱 실패 — 빈 배열로 발행: {}", e.getMessage());
        }
        return tagsNode;
    }
}
