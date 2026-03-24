package github.jhkoder.aiblog.post.usecase;

import github.jhkoder.aiblog.common.exception.BusinessRuleException;
import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.hashnode.HashnodeClient;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.dto.PostResponse;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PublishPostUseCase {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final HashnodeClient hashnodeClient;
    private final AiSuggestionRepository aiSuggestionRepository;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Transactional
    public PostResponse execute(Long postId, Long memberId) {
        if (!"prod".equals(activeProfile)) {
            throw new BusinessRuleException("Hashnode 발행은 prod 환경에서만 허용됩니다. (현재: " + activeProfile + ")");
        }

        Post post = postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        // Hashnode 발행 전 사전 유효성 검증
        if (post.getTitle() == null || post.getTitle().trim().length() < 6) {
            throw new BusinessRuleException("Hashnode 발행 조건: 제목은 최소 6자 이상이어야 합니다. (현재: " + (post.getTitle() == null ? 0 : post.getTitle().trim().length()) + "자)");
        }
        if (post.getContent() == null || post.getContent().isBlank()) {
            throw new BusinessRuleException("Hashnode 발행 조건: 본문이 비어있습니다.");
        }
        if (!member.hasHashnodeConnection()) {
            throw new BusinessRuleException("Hashnode 연동이 필요합니다. 마이페이지에서 Hashnode Token과 Publication ID를 설정해 주세요.");
        }

        long aiImproveCount = aiSuggestionRepository.countByPostId(postId);
        String contentWithMeta = appendAiMeta(post.getContent(), post, aiImproveCount);

        if (post.getHashnodeId() == null) {
            HashnodeClient.PublishResult result = hashnodeClient.publishPost(
                    post.getTitle(), contentWithMeta, member.getHashnodeToken(), member.getHashnodePublicationId(),
                    post.getTags());
            post.markPublished(result.getId(), result.getUrl());
        } else {
            hashnodeClient.updatePost(
                    post.getHashnodeId(), post.getTitle(), contentWithMeta, member.getHashnodeToken(),
                    post.getTags());
            post.markPublished(post.getHashnodeId(), post.getHashnodeUrl());
        }

        return PostResponse.from(post);
    }

    private String appendAiMeta(String content, Post post, long aiImproveCount) {
        String latestModel = aiSuggestionRepository
                .findTopByPostIdOrderByCreatedAtDesc(post.getId())
                .map(s -> s.getModel())
                .orElse(null);

        StringBuilder meta = new StringBuilder(content);
        meta.append("\n\n---\n\n");
        meta.append("> **AI 작성 정보**  \n");
        meta.append("> 생성일: ").append(post.getCreatedAt().toLocalDate()).append("  \n");
        meta.append("> 최종 수정: ").append(LocalDate.now()).append("  \n");
        if (latestModel != null) {
            meta.append("> AI 모델: ").append(latestModel).append("  \n");
        }
        meta.append("> AI 개선 횟수: ").append(aiImproveCount).append("회");
        return meta.toString();
    }
}
