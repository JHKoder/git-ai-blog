package github.jhkoder.aiblog.post.controller;

import github.jhkoder.aiblog.common.ApiResponse;
import github.jhkoder.aiblog.infra.ai.AiUsageLimiter;
import github.jhkoder.aiblog.infra.ai.ImageUsageLimiter;
import github.jhkoder.aiblog.infra.ai.TokenUsageTracker;
import github.jhkoder.aiblog.infra.ai.RateLimitCache;
import github.jhkoder.aiblog.infra.ai.ClaudeClient;
import github.jhkoder.aiblog.infra.ai.GrokClient;
import github.jhkoder.aiblog.post.dto.*;
import github.jhkoder.aiblog.post.usecase.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final CreatePostUseCase createPostUseCase;
    private final GetPostListUseCase getPostListUseCase;
    private final GetPostDetailUseCase getPostDetailUseCase;
    private final UpdatePostUseCase updatePostUseCase;
    private final DeletePostUseCase deletePostUseCase;
    private final PublishPostUseCase publishPostUseCase;
    private final ImportHashnodePostUseCase importHashnodePostUseCase;
    private final SyncHashnodePostsUseCase syncHashnodePostsUseCase;
    private final GenerateImageUseCase generateImageUseCase;
    private final AiUsageLimiter aiUsageLimiter;
    private final ImageUsageLimiter imageUsageLimiter;
    private final TokenUsageTracker tokenUsageTracker;
    private final RateLimitCache rateLimitCache;

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PostCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(createPostUseCase.execute(memberId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PostListResponse>>> list(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String tag) {
        return ResponseEntity.ok(ApiResponse.ok(getPostListUseCase.execute(memberId, page, size, tag)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> detail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(getPostDetailUseCase.execute(id, memberId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody PostUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(updatePostUseCase.execute(id, memberId, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        deletePostUseCase.execute(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<PostResponse>> publish(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(publishPostUseCase.execute(id, memberId)));
    }

    @PostMapping("/import-hashnode")
    public ResponseEntity<ApiResponse<List<PostResponse>>> importFromHashnode(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(importHashnodePostUseCase.execute(memberId)));
    }

    @PostMapping("/sync-hashnode")
    public ResponseEntity<ApiResponse<SyncHashnodePostsUseCase.SyncResult>> syncHashnode(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(syncHashnodePostsUseCase.execute(memberId)));
    }

    @PostMapping("/generate-image")
    public ResponseEntity<ApiResponse<String>> generateImage(
            @AuthenticationPrincipal Long memberId,
            @RequestBody java.util.Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        String model  = body.getOrDefault("model", "");
        if (prompt.isBlank()) return ResponseEntity.badRequest().body(ApiResponse.error("prompt가 필요합니다."));
        if (model.isBlank())  return ResponseEntity.badRequest().body(ApiResponse.error("model이 필요합니다."));
        return ResponseEntity.ok(ApiResponse.ok(generateImageUseCase.execute(memberId, prompt, model)));
    }

    @GetMapping("/ai-usage")
    public ResponseEntity<ApiResponse<AiUsageResponse>> aiUsage(@AuthenticationPrincipal Long memberId) {
        TokenUsageTracker.ModelUsage sonnet = tokenUsageTracker.getUsage(memberId, ClaudeClient.SONNET);
        TokenUsageTracker.ModelUsage grok   = tokenUsageTracker.getUsage(memberId, GrokClient.GROK_3);
        RateLimitCache.RateLimitInfo claudeRl = rateLimitCache.get(memberId, "claude");
        RateLimitCache.RateLimitInfo grokRl   = rateLimitCache.get(memberId, "grok");

        AiUsageResponse usage = AiUsageResponse.builder()
                .used(aiUsageLimiter.getUsedCount(memberId))
                .limit(aiUsageLimiter.getDailyLimit())
                .remaining(aiUsageLimiter.getRemainingCount(memberId))
                .sonnetInputTokens(sonnet.inputTokens())
                .sonnetOutputTokens(sonnet.outputTokens())
                .claudeTokenLimit(claudeRl.tokenLimit())
                .claudeTokenRemaining(claudeRl.tokenRemaining())
                .claudeRequestLimit(claudeRl.requestLimit())
                .claudeRequestRemaining(claudeRl.requestRemaining())
                .imageDailyUsed(imageUsageLimiter.getDailyUsed(memberId))
                .imageDailyLimit(ImageUsageLimiter.MAX_PER_DAY)
                .imageDailyRemaining(imageUsageLimiter.getDailyRemaining(memberId))
                .imagePerPostLimit(ImageUsageLimiter.MAX_PER_POST)
                .grokInputTokens(grok.inputTokens())
                .grokOutputTokens(grok.outputTokens())
                .grokTokenLimit(grokRl.tokenLimit())
                .grokTokenRemaining(grokRl.tokenRemaining())
                .grokRequestLimit(grokRl.requestLimit())
                .grokRequestRemaining(grokRl.requestRemaining())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(usage));
    }
}
