package github.jhkoder.aiblog.post;

import github.jhkoder.aiblog.common.exception.InvalidStateException;
import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Post 도메인 상태 머신 단위 테스트.
 * DRAFT → AI_SUGGESTED → ACCEPTED → PUBLISHED 상태 전이 검증.
 */
class PostDomainTest {

    @Test
    @DisplayName("Post 생성 시 DRAFT 상태로 시작한다")
    void create_initialStatus_isDraft() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    @DisplayName("DRAFT → AI_SUGGESTED 상태 전이 성공")
    void markAiSuggested_fromDraft_succeeds() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.markAiSuggested();
        assertThat(post.getStatus()).isEqualTo(PostStatus.AI_SUGGESTED);
    }

    @Test
    @DisplayName("AI_SUGGESTED 상태에서 다시 markAiSuggested 호출하면 예외 발생")
    void markAiSuggested_alreadyAiSuggested_throwsException() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.markAiSuggested();

        assertThatThrownBy(post::markAiSuggested)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("이미 AI 제안 상태");
    }

    @Test
    @DisplayName("AI_SUGGESTED → ACCEPTED 상태 전이 성공 및 내용 업데이트")
    void accept_fromAiSuggested_updatesContentAndStatus() {
        Post post = Post.create(1L, "제목", "원본 내용", ContentType.CODING);
        post.markAiSuggested();
        String aiContent = "AI가 개선한 내용";

        post.accept(aiContent, null, null);

        assertThat(post.getStatus()).isEqualTo(PostStatus.ACCEPTED);
        assertThat(post.getContent()).isEqualTo(aiContent);
    }

    @Test
    @DisplayName("DRAFT 상태에서 accept 호출 성공 — 거절 후 히스토리 제안 재수락 지원")
    void accept_fromDraft_success() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);

        post.accept("AI 내용", null, null);

        assertThat(post.getStatus()).isEqualTo(PostStatus.ACCEPTED);
        assertThat(post.getContent()).isEqualTo("AI 내용");
    }

    @Test
    @DisplayName("ACCEPTED 상태에서 accept 재호출 성공 — 연속 수락 지원")
    void accept_fromAccepted_success() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.markAiSuggested();
        post.accept("첫 번째 AI 내용", null, null);

        post.accept("두 번째 AI 내용", "새 제목", null);

        assertThat(post.getStatus()).isEqualTo(PostStatus.ACCEPTED);
        assertThat(post.getContent()).isEqualTo("두 번째 AI 내용");
        assertThat(post.getTitle()).isEqualTo("새 제목");
    }

    @Test
    @DisplayName("PUBLISHED 상태에서 accept 호출 성공 — 발행 후 재개선 지원")
    void accept_fromPublished_success() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.markPublished("hashnode-1", "https://hashnode.com/1");

        post.accept("재개선 내용", null, null);

        assertThat(post.getStatus()).isEqualTo(PostStatus.ACCEPTED);
        assertThat(post.getContent()).isEqualTo("재개선 내용");
    }

    @Test
    @DisplayName("accept 시 suggestedTitle이 null이면 기존 제목을 유지한다")
    void accept_withNullTitle_keepsPreviousTitle() {
        Post post = Post.create(1L, "원래 제목", "내용", ContentType.CODING);
        post.markAiSuggested();

        post.accept("AI 내용", null, null);

        assertThat(post.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("accept 시 suggestedTitle이 blank이면 기존 제목을 유지한다")
    void accept_withBlankTitle_keepsPreviousTitle() {
        Post post = Post.create(1L, "원래 제목", "내용", ContentType.CODING);
        post.markAiSuggested();

        post.accept("AI 내용", "   ", null);

        assertThat(post.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("accept 시 suggestedTags가 있으면 태그가 교체된다")
    void accept_withSuggestedTags_replacesTags() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING, List.of("java"));
        post.markAiSuggested();

        post.accept("AI 내용", null, List.of("spring", "jpa"));

        assertThat(post.getTags()).containsExactlyInAnyOrder("spring", "jpa");
    }

    @Test
    @DisplayName("PostStatus.canTransitionTo — 허용/거절 전이 매트릭스 검증")
    void postStatus_canTransitionTo_매트릭스() {
        assertThat(PostStatus.DRAFT.canTransitionTo(PostStatus.AI_SUGGESTED)).isTrue();
        assertThat(PostStatus.DRAFT.canTransitionTo(PostStatus.ACCEPTED)).isTrue();
        assertThat(PostStatus.AI_SUGGESTED.canTransitionTo(PostStatus.DRAFT)).isTrue();
        assertThat(PostStatus.AI_SUGGESTED.canTransitionTo(PostStatus.ACCEPTED)).isTrue();
        assertThat(PostStatus.ACCEPTED.canTransitionTo(PostStatus.PUBLISHED)).isTrue();
        assertThat(PostStatus.PUBLISHED.canTransitionTo(PostStatus.AI_SUGGESTED)).isTrue();
        assertThat(PostStatus.ACCEPTED.canTransitionTo(PostStatus.DRAFT)).isFalse();
        assertThat(PostStatus.PUBLISHED.canTransitionTo(PostStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("updateTags — 동일한 태그 목록을 전달하면 결과가 유지된다")
    void updateTags_sameTags_noChange() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING, List.of("java", "spring"));

        post.updateTags(List.of("java", "spring"));

        assertThat(post.getTags()).containsExactly("java", "spring");
    }

    @Test
    @DisplayName("AI_SUGGESTED → DRAFT 복원 성공 (제안 거절)")
    void revertFromAiSuggested_fromAiSuggested_backToDraft() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.markAiSuggested();

        post.revertFromAiSuggested();

        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    @DisplayName("DRAFT 상태에서 revertFromAiSuggested 호출하면 예외 발생")
    void revertFromAiSuggested_fromDraft_throwsException() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);

        assertThatThrownBy(post::revertFromAiSuggested)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("AI 제안 상태에서만");
    }

    @Test
    @DisplayName("PUBLISHED 상태 전이 후 hashnodeId와 URL이 설정된다")
    void markPublished_setsHashnodeInfo() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);

        post.markPublished("hashnode-123", "https://hashnode.com/post/123");

        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getHashnodeId()).isEqualTo("hashnode-123");
        assertThat(post.getHashnodeUrl()).isEqualTo("https://hashnode.com/post/123");
    }

    @Test
    @DisplayName("조회수 증가가 정상 동작한다")
    void incrementViewCount_increases() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        int before = post.getViewCount();

        post.incrementViewCount();

        assertThat(post.getViewCount()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("태그는 소문자로 정규화된다")
    void updateTags_normalizesToLowercase() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.updateTags(List.of("Java", "SPRING", "Spring-Boot"));
        assertThat(post.getTags()).containsExactly("java", "spring", "spring-boot");
    }

    @Test
    @DisplayName("태그에서 허용되지 않는 특수문자가 제거된다")
    void updateTags_removesSpecialCharacters() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.updateTags(List.of("java!", "c++", "node.js", "백엔드@개발"));
        assertThat(post.getTags()).containsExactly("java", "c", "nodejs", "백엔드개발");
    }

    @Test
    @DisplayName("태그는 최대 30자로 잘린다")
    void updateTags_truncatesLongTag() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        String longTag = "a".repeat(40);
        post.updateTags(List.of(longTag));
        assertThat(post.getTags()).containsExactly("a".repeat(30));
    }

    @Test
    @DisplayName("빈 문자열 또는 null 태그는 제거된다")
    void updateTags_removesBlankAndNullTags() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.updateTags(List.of("java", "", "  ", "spring"));
        assertThat(post.getTags()).containsExactly("java", "spring");
    }

    @Test
    @DisplayName("중복 태그는 첫 번째만 유지된다")
    void updateTags_removesDuplicates() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.updateTags(List.of("java", "Java", "JAVA"));
        assertThat(post.getTags()).containsExactly("java");
    }

    @Test
    @DisplayName("null 태그 목록은 빈 리스트로 처리된다")
    void updateTags_withNull_setsEmptyList() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.updateTags(null);
        assertThat(post.getTags()).isEmpty();
    }

    @Test
    @DisplayName("create 팩토리 메서드에서도 태그가 정규화된다")
    void create_withTags_normalizesOnCreation() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING, List.of("Java", "SPRING!"));
        assertThat(post.getTags()).containsExactly("java", "spring");
    }

    @Test
    @DisplayName("한글 태그가 정상적으로 보존된다")
    void normalizeTags_한글태그_보존() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.updateTags(List.of("데이터베이스", "자바-스프링", "백엔드"));
        assertThat(post.getTags()).containsExactly("데이터베이스", "자바-스프링", "백엔드");
    }

    @Test
    @DisplayName("태그 목록에 null 원소가 포함되면 건너뛴다")
    void normalizeTags_null원소_무시() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        java.util.List<String> tagsWithNull = new java.util.ArrayList<>();
        tagsWithNull.add("java");
        tagsWithNull.add(null);
        tagsWithNull.add("spring");
        post.updateTags(tagsWithNull);
        assertThat(post.getTags()).containsExactly("java", "spring");
    }

    @Test
    @DisplayName("태그 정규화 후 빈 문자열이 되는 특수문자만 있는 태그는 제거된다")
    void normalizeTags_특수문자만_있는_태그_제거() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.updateTags(List.of("@#$%", "!!!", "java"));
        assertThat(post.getTags()).containsExactly("java");
    }

    @Test
    @DisplayName("하이픈이 포함된 태그가 정상적으로 보존된다")
    void normalizeTags_하이픈_보존() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.updateTags(List.of("spring-boot", "vue-js", "node-js"));
        assertThat(post.getTags()).containsExactly("spring-boot", "vue-js", "node-js");
    }

    @Test
    @DisplayName("정확히 30자 태그는 잘리지 않는다")
    void normalizeTags_정확히30자_유지() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        String exactTag = "a".repeat(30);
        post.updateTags(List.of(exactTag));
        assertThat(post.getTags()).containsExactly(exactTag);
    }

    @Test
    @DisplayName("대소문자 혼합 중복 태그 + 특수문자 조합이 올바르게 정규화된다")
    void normalizeTags_복합케이스_정규화() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);
        post.updateTags(List.of("Spring-Boot!", "spring-boot", "SPRING_BOOT"));
        assertThat(post.getTags()).containsExactly("spring-boot", "springboot");
    }
}
