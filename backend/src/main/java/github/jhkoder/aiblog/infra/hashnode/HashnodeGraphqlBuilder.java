package github.jhkoder.aiblog.infra.hashnode;

import org.springframework.stereotype.Component;

@Component
public class HashnodeGraphqlBuilder {

    public String buildPublishQuery(String title, String content, String publicationId, java.util.List<String> tags) {
        String escapedTitle = escapeGraphql(title);
        String escapedContent = escapeGraphql(content);
        String tagsGql = buildTagsGql(tags);
        return """
                mutation {
                  publishPost(input: {
                    title: "%s",
                    contentMarkdown: "%s",
                    publicationId: "%s",
                    tags: [%s]
                  }) {
                    post {
                      id
                      url
                    }
                  }
                }
                """.formatted(escapedTitle, escapedContent, publicationId, tagsGql);
    }

    public String buildUpdateQuery(String postId, String title, String content, java.util.List<String> tags) {
        String escapedTitle = escapeGraphql(title);
        String escapedContent = escapeGraphql(content);
        String tagsGql = buildTagsGql(tags);
        return """
                mutation {
                  updatePost(input: {
                    id: "%s",
                    title: "%s",
                    contentMarkdown: "%s",
                    tags: [%s]
                  }) {
                    post {
                      id
                      url
                    }
                  }
                }
                """.formatted(postId, escapedTitle, escapedContent, tagsGql);
    }

    private String buildTagsGql(java.util.List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        return tags.stream()
                .map(t -> "{name: \"" + escapeGraphql(t) + "\"}")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public String buildDeleteQuery(String postId) {
        return """
                mutation {
                  removePost(input: {
                    id: "%s"
                  }) {
                    post {
                      id
                    }
                  }
                }
                """.formatted(postId);
    }

    public String buildFetchPostsQuery(String publicationId) {
        return """
                {
                  publication(id: "%s") {
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
                """.formatted(publicationId);
    }

    private String escapeGraphql(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
