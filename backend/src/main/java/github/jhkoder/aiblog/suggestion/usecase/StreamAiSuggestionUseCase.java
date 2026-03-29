package github.jhkoder.aiblog.suggestion.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.ai.AiClientRouter;
import github.jhkoder.aiblog.infra.ai.AiUsageLimiter;
import github.jhkoder.aiblog.infra.ai.TokenUsageTracker;
import github.jhkoder.aiblog.infra.ai.prompt.PromptBuilder;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.domain.PostStatus;
import github.jhkoder.aiblog.prompt.domain.Prompt;
import github.jhkoder.aiblog.prompt.domain.PromptRepository;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestion;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 개선 요청을 SSE 스트리밍으로 처리한다.
 * - 토큰 단위로 클라이언트에 실시간 emit.
 * - 스트리밍 완료 후 DB에 전체 텍스트 저장 + durationMs 기록.
 * - 토큰 사용량은 입력 토큰만 추정 (출력은 스트리밍 중 미제공).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamAiSuggestionUseCase {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final AiUsageLimiter aiUsageLimiter;
    private final AiClientRouter aiClientRouter;
    private final PromptBuilder promptBuilder;
    private final TokenUsageTracker tokenUsageTracker;
    private final PromptRepository promptRepository;

    /** 모델별 예상 시간 fallback (초). durationMs 데이터 없을 때 사용 */
    private static final java.util.Map<String, Long> FALLBACK_SECONDS = java.util.Map.of(
            "claude-sonnet-4-6", 40L,
            "claude-opus-4-5",   60L,
            "grok-3",            20L,
            "gpt-4o",            30L,
            "gpt-4o-mini",       20L,
            "gemini-2.0-flash",  15L
    );

    /**
     * SSE 스트리밍 Flux를 반환한다. 구독 시 AI API를 호출하고 토큰을 순서대로 emit한다.
     * 첫 번째 이벤트로 "__estimated__:{초}" 를 emit — 프론트에서 카운트다운에 활용.
     * 스트리밍 완료 시 DB 저장 + 토큰 사용량 기록을 수행한다 (cold publisher).
     *
     * 주의: doOnComplete 는 리액터 스레드에서 실행되므로 @Transactional 이 적용되지 않는다.
     * 완료 콜백은 별도 @Transactional 메서드(saveResult)로 위임한다.
     */
    @Transactional(readOnly = true)
    public Flux<String> stream(Long postId, Long memberId, AiSuggestionRequest request) {
        log.info("[StreamAI][1] 요청 수신 postId={} memberId={} model={}", postId, memberId, request.getModel());
        aiUsageLimiter.check(memberId);

        Post post = postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        String contentToImprove = (request.getTempContent() != null && !request.getTempContent().isBlank())
                ? request.getTempContent()
                : post.getContent();

        String effectiveExtraPrompt = resolveExtraPrompt(request);

        // Claude 200K 컨텍스트 - max_tokens(16000) 예약 → 입력 상한 약 184K 토큰 ≈ 736,000 chars
        // content가 너무 크면 후반부를 잘라 안전하게 처리 (400 Bad Request 방지)
        String safeContent = truncateIfTooLong(contentToImprove, 600_000);
        if (safeContent.length() < contentToImprove.length()) {
            log.warn("[StreamAI] content 길이 초과로 트리밍 original={}chars truncated={}chars",
                    contentToImprove.length(), safeContent.length());
        }

        String prompt = promptBuilder.build(post.getContentType(), safeContent, effectiveExtraPrompt);
        AiClientRouter.RouteResult route = aiClientRouter.route(post.getContentType(), request.getModel(), member);
        log.info("[StreamAI][2] 라우팅 완료 model={} promptLen={}", route.model(), prompt.length());

        // 예상 시간 계산: DB 평균 → 없으면 모델별 fallback
        long estimatedSec = resolveEstimatedSeconds(route.model());
        log.info("[StreamAI][3] estimatedSec={}", estimatedSec);

        StringBuilder accumulated = new StringBuilder();
        AtomicLong startMs = new AtomicLong(System.currentTimeMillis());
        AtomicLong tokenCount = new AtomicLong(0);

        String finalEffectiveExtraPrompt = effectiveExtraPrompt;
        Flux<String> estimatedEvent = Flux.just("__estimated__:" + estimatedSec);
        Flux<String> tokenStream = route.client().streamComplete(prompt, route.model(), route.apiKey())
                .doOnSubscribe(s -> log.info("[StreamAI][4] AI API 구독 시작 (streamComplete 호출)"))
                .doOnNext(token -> {
                    accumulated.append(token);
                    long cnt = tokenCount.incrementAndGet();
                    if (cnt == 1) log.info("[StreamAI][5] 첫 토큰 수신");
                    if (cnt % 100 == 0) log.info("[StreamAI][5] 토큰 {}개 수신, 누적 {}자", cnt, accumulated.length());
                })
                .doOnComplete(() -> log.info("[StreamAI][6] 토큰 스트림 완료 총 {}개, {}자", tokenCount.get(), accumulated.length()))
                .doOnError(e -> log.error("[StreamAI][ERR] 스트리밍 실패 postId={} memberId={}: {}", postId, memberId, e.getMessage(), e));

        // DB 저장 완료 후 __done__ 신호 emit — 프론트가 done을 받는 시점에 DB에 데이터가 존재함을 보장
        Flux<String> saveAndDone = Flux.defer(() -> {
            long durationMs = System.currentTimeMillis() - startMs.get();
            log.info("[StreamAI][7] DB 저장 시작 durationMs={} textLen={}", durationMs, accumulated.length());
            try {
                String rawText = accumulated.toString();
                String parsedTitle = parseTitle(rawText);
                String parsedTags = parseTags(rawText);
                String cleanContent = removeAiAuthorLine(removeTagsLine(removeFirstHeading(rawText)));
                saveResult(postId, memberId, cleanContent,
                        route.model(), finalEffectiveExtraPrompt, durationMs, prompt.length(), parsedTitle, parsedTags);
                log.info("[StreamAI][8] DB 저장 완료 → __done__ emit suggestedTitle={} suggestedTags={}", parsedTitle, parsedTags);
            } catch (Exception e) {
                log.error("[StreamAI][ERR] 완료 후 저장 실패 postId={} memberId={}: {}", postId, memberId, e.getMessage(), e);
            }
            return Flux.just("__done__");
        });

        return estimatedEvent.concatWith(tokenStream).concatWith(saveAndDone);
    }

    private long resolveEstimatedSeconds(String model) {
        try {
            Double avg = aiSuggestionRepository.findAvgDurationMsByModel(model);
            if (avg != null && avg > 0) {
                return Math.max(1L, avg.longValue() / 1000);
            }
        } catch (Exception ignored) {}
        return FALLBACK_SECONDS.getOrDefault(model, 30L);
    }

    /**
     * 스트리밍 완료 후 DB 저장 + 사용량 기록.
     * 별도 @Transactional 메서드로 분리하여 리액터 스레드에서도 트랜잭션이 적용된다.
     * post.markAiSuggested() 는 이미 AI_SUGGESTED 상태면 멱등 처리한다.
     */
    @Transactional
    public void saveResult(Long postId, Long memberId, String text, String model,
                           String extraPrompt, long durationMs, int promptLength,
                           String suggestedTitle, String suggestedTags) {
        aiUsageLimiter.increment(memberId);
        tokenUsageTracker.record(memberId, model,
                (long) (promptLength / 4), (long) (text.length() / 4));

        // 이전 제안과 내용이 동일하면 중복 저장 생략
        boolean isDuplicate = aiSuggestionRepository
                .findTopByPostIdOrderByCreatedAtDesc(postId)
                .map(prev -> prev.getSuggestedContent().equals(text))
                .orElse(false);
        if (isDuplicate) {
            log.info("[StreamAI] 이전 제안과 동일한 내용 — 중복 저장 생략 postId={}", postId);
            return;
        }

        AiSuggestion suggestion = AiSuggestion.createWithDuration(
                postId, memberId, text, model, extraPrompt, durationMs, suggestedTitle, suggestedTags);
        aiSuggestionRepository.save(suggestion);

        // 이미 AI_SUGGESTED 상태인 경우 상태 전이 생략 (멱등)
        postRepository.findByIdAndMemberId(postId, memberId).ifPresent(post -> {
            if (post.getStatus() != PostStatus.AI_SUGGESTED) {
                post.markAiSuggested();
            }
        });
    }

    /**
     * AI 응답 첫 줄이 "# 제목" 형식이면 제목을 추출한다.
     * 그렇지 않으면 null 반환.
     */
    private String parseTitle(String text) {
        if (text == null || text.isBlank()) return null;
        String firstLine = text.stripLeading().lines().findFirst().orElse("").trim();
        if (firstLine.startsWith("# ")) {
            String title = firstLine.substring(2).trim();
            return title.isBlank() ? null : title;
        }
        return null;
    }

    /**
     * 첫 줄이 "# 제목" 형식이면 제거한 본문을 반환한다.
     * 제목이 없으면 원문 그대로 반환.
     */
    private String removeFirstHeading(String text) {
        if (text == null) return text;
        String stripped = text.stripLeading();
        if (!stripped.startsWith("# ")) return text;
        int newline = stripped.indexOf('\n');
        if (newline == -1) return "";
        return stripped.substring(newline + 1).stripLeading();
    }

    /**
     * 응답 텍스트에서 "TAGS: tag1,tag2,..." 줄을 찾아 쉼표 구분 태그 문자열을 반환한다.
     * 없으면 null 반환.
     */
    private String parseTags(String text) {
        if (text == null || text.isBlank()) return null;
        for (String line : text.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("TAGS:")) {
                String raw = trimmed.substring(5).trim();
                if (raw.isBlank()) return null;
                // 공백 제거, 소문자 정규화, 3~10개 범위 검증
                String[] parts = raw.split(",");
                java.util.List<String> tags = new java.util.ArrayList<>();
                for (String part : parts) {
                    String tag = part.trim().toLowerCase();
                    if (!tag.isBlank()) tags.add(tag);
                }
                if (tags.size() < 3) return null;
                if (tags.size() > 10) tags = tags.subList(0, 10);
                return String.join(",", tags);
            }
        }
        return null;
    }

    /**
     * 응답 텍스트에서 "TAGS: ..." 줄을 제거한 본문을 반환한다.
     */
    private String removeTagsLine(String text) {
        if (text == null) return null;
        return text.lines()
                .filter(line -> !line.trim().startsWith("TAGS:"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * "이 글은 {모델명}이 작성을 도왔습니다." 형태의 AI 저자 줄을 제거한다.
     * 인용 형식(> ...) 유무 무관하게 제거.
     * post.content에 누적되면 재요청 시 프롬프트가 점점 커지는 문제를 방지한다.
     */
    private String removeAiAuthorLine(String text) {
        if (text == null) return null;
        return text.lines()
                .filter(line -> !line.trim().matches("^>?\\s*이 글은 .+이 작성을 도왔습니다\\.?\\s*$"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * content 길이가 maxChars를 초과하면 앞부분 maxChars 문자만 유지한다.
     * 뒷부분을 잘라내는 이유: 뒤쪽은 AI 저자 줄·태그 줄 등 노이즈가 많고,
     * 앞부분에 핵심 내용이 집중되는 경향이 있기 때문이다.
     */
    private String truncateIfTooLong(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) return content;
        return content.substring(0, maxChars);
    }

    private String resolveExtraPrompt(AiSuggestionRequest request) {
        String extraPrompt = request.getExtraPrompt();
        if (request.getPromptId() == null) return extraPrompt;

        Prompt customPrompt = promptRepository.findById(request.getPromptId())
                .orElseThrow(() -> new NotFoundException("프롬프트를 찾을 수 없습니다."));
        customPrompt.incrementUsageCount();
        String customContent = customPrompt.getContent();
        return (extraPrompt != null && !extraPrompt.isBlank())
                ? customContent + "\n" + extraPrompt
                : customContent;
    }
}
