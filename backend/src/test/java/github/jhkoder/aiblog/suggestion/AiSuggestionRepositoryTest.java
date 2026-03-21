package github.jhkoder.aiblog.suggestion;

import github.jhkoder.aiblog.config.TestRedisConfig;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestion;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiSuggestionRepository 통합 테스트.
 * H2 in-memory DB 사용, local 프로파일 기준.
 * Redis는 Mock으로 대체.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestRedisConfig.class)
@ActiveProfiles("local")
@Transactional
class AiSuggestionRepositoryTest {

    @Autowired
    private AiSuggestionRepository suggestionRepository;

    @Test
    @DisplayName("AI 제안을 저장하고 ID로 조회할 수 있다")
    void saveAndFindById() {
        AiSuggestion suggestion = AiSuggestion.create(1L, 10L, "제안 내용", "claude-sonnet", "추가 프롬프트");

        AiSuggestion saved = suggestionRepository.save(suggestion);

        Optional<AiSuggestion> found = suggestionRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSuggestedContent()).isEqualTo("제안 내용");
        assertThat(found.get().getModel()).isEqualTo("claude-sonnet");
    }

    @Test
    @DisplayName("postId로 가장 최근 AI 제안을 조회할 수 있다")
    void findTopByPostIdOrderByCreatedAtDesc() {
        Long postId = 2L;
        suggestionRepository.save(AiSuggestion.create(postId, 10L, "첫 번째 제안", "claude-sonnet", ""));
        suggestionRepository.save(AiSuggestion.create(postId, 10L, "두 번째 제안", "grok-3", ""));
        suggestionRepository.save(AiSuggestion.create(postId, 10L, "세 번째 제안", "claude-sonnet", "추가"));

        Optional<AiSuggestion> top = suggestionRepository.findTopByPostIdOrderByCreatedAtDesc(postId);

        assertThat(top).isPresent();
    }

    @Test
    @DisplayName("postId로 모든 AI 제안 목록을 최신순으로 조회할 수 있다")
    void findByPostIdOrderByCreatedAtDesc() {
        Long postId = 3L;
        suggestionRepository.save(AiSuggestion.create(postId, 20L, "제안1", "claude-sonnet", ""));
        suggestionRepository.save(AiSuggestion.create(postId, 20L, "제안2", "claude-sonnet", ""));
        suggestionRepository.save(AiSuggestion.create(99L, 20L, "다른 포스트", "claude-sonnet", ""));

        List<AiSuggestion> results = suggestionRepository.findByPostIdOrderByCreatedAtDesc(postId);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("postId로 AI 제안을 전체 삭제할 수 있다")
    void deleteByPostId() {
        Long postId = 4L;
        suggestionRepository.save(AiSuggestion.create(postId, 30L, "제안A", "grok-3", ""));
        suggestionRepository.save(AiSuggestion.create(postId, 30L, "제안B", "grok-3", ""));

        suggestionRepository.deleteByPostId(postId);

        assertThat(suggestionRepository.findByPostIdOrderByCreatedAtDesc(postId)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 postId 조회 시 empty를 반환한다")
    void findTopByPostId_notFound() {
        Optional<AiSuggestion> result = suggestionRepository.findTopByPostIdOrderByCreatedAtDesc(999L);
        assertThat(result).isEmpty();
    }
}
