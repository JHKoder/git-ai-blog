package github.jhkoder.aiblog.infra.hashnode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hashnode GraphQL 요청 페이로드 빌더.
 * query + variables 분리 방식으로 이중 이스케이프 문제를 완전히 회피한다.
 * HashnodeClient.execute()가 {"query":..., "variables":...} 형태로 직렬화한다.
 */
@Component
public class HashnodeGraphqlBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record GqlRequest(String query, Object variables) {}

    public GqlRequest buildPublishRequest(String title, String content, String publicationId, List<String> tags) {
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
        input.put("contentMarkdown", content);
        input.put("publicationId", publicationId);
        input.set("tags", buildTagsNode(tags));

        ObjectNode variables = objectMapper.createObjectNode();
        variables.set("input", input);
        return new GqlRequest(query, variables);
    }

    public GqlRequest buildUpdateRequest(String postId, String title, String content, List<String> tags) {
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
        input.put("contentMarkdown", content);
        input.set("tags", buildTagsNode(tags));

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

    private ArrayNode buildTagsNode(List<String> tags) {
        ArrayNode arr = objectMapper.createArrayNode();
        if (tags == null) return arr;
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) continue;
            String slug = tag.toLowerCase()
                    .replaceAll("[^a-z0-9가-힣]+", "-")
                    .replaceAll("^-|-$", "");
            if (slug.isEmpty()) continue;
            ObjectNode tagNode = objectMapper.createObjectNode();
            tagNode.put("slug", slug);
            tagNode.put("name", tag);
            arr.add(tagNode);
        }
        return arr;
    }
}
