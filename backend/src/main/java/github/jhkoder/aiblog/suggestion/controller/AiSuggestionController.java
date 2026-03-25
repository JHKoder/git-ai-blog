package github.jhkoder.aiblog.suggestion.controller;

import github.jhkoder.aiblog.common.ApiResponse;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionRequest;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionResponse;
import github.jhkoder.aiblog.suggestion.usecase.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-suggestions")
@RequiredArgsConstructor
public class AiSuggestionController {

    private final RequestAiSuggestionUseCase requestAiSuggestionUseCase;
    private final GetLatestSuggestionUseCase getLatestSuggestionUseCase;
    private final GetSuggestionHistoryUseCase getSuggestionHistoryUseCase;
    private final AcceptSuggestionUseCase acceptSuggestionUseCase;
    private final RejectSuggestionUseCase rejectSuggestionUseCase;

    @PostMapping("/{postId}")
    public ResponseEntity<ApiResponse<AiSuggestionResponse>> request(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long postId,
            @Valid @RequestBody(required = false) AiSuggestionRequest request) {
        if (request == null) request = new AiSuggestionRequest();
        return ResponseEntity.ok(ApiResponse.ok(requestAiSuggestionUseCase.execute(postId, memberId, request)));
    }

    @GetMapping("/{postId}/latest")
    public ResponseEntity<ApiResponse<AiSuggestionResponse>> latest(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(ApiResponse.ok(getLatestSuggestionUseCase.execute(postId, memberId)));
    }

    @GetMapping("/{postId}/history")
    public ResponseEntity<ApiResponse<List<AiSuggestionResponse>>> history(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(ApiResponse.ok(getSuggestionHistoryUseCase.execute(postId, memberId)));
    }

    @PostMapping("/{postId}/{id}/accept")
    public ResponseEntity<ApiResponse<AiSuggestionResponse>> accept(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long postId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(acceptSuggestionUseCase.execute(postId, id, memberId)));
    }

    @PostMapping("/{postId}/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long postId,
            @PathVariable Long id) {
        rejectSuggestionUseCase.execute(postId, id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
