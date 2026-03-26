package github.jhkoder.aiblog.suggestion.controller;

import github.jhkoder.aiblog.common.ApiResponse;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionRequest;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionResponse;
import github.jhkoder.aiblog.suggestion.usecase.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai-suggestions")
@RequiredArgsConstructor
public class AiSuggestionController {

    private final RequestAiSuggestionUseCase requestAiSuggestionUseCase;
    private final StreamAiSuggestionUseCase streamAiSuggestionUseCase;
    private final GetLatestSuggestionUseCase getLatestSuggestionUseCase;
    private final GetSuggestionHistoryUseCase getSuggestionHistoryUseCase;
    private final AcceptSuggestionUseCase acceptSuggestionUseCase;
    private final RejectSuggestionUseCase rejectSuggestionUseCase;

    @PostMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> request(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long postId,
            @Valid @RequestBody(required = false) AiSuggestionRequest request) {
        if (request == null) request = new AiSuggestionRequest();
        requestAiSuggestionUseCase.execute(postId, memberId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok());
    }

    /**
     * SSE 스트리밍 AI 개선 요청.
     * 토큰을 실시간으로 emit하며, 완료 후 DB에 저장한다.
     * nginx: proxy_buffering off 설정 필요.
     *
     * 에러는 SSE error 이벤트로 클라이언트에 전달하고 스트림을 종료한다.
     * (예외를 컨테이너로 전파하면 응답이 이미 커밋된 상태에서 처리 불가 문제 발생)
     */
    @PostMapping(value = "/{postId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long postId,
            @Valid @RequestBody(required = false) AiSuggestionRequest request) {
        if (request == null) request = new AiSuggestionRequest();
        AiSuggestionRequest finalRequest = request;
        return streamAiSuggestionUseCase.stream(postId, memberId, finalRequest)
                .map(token -> {
                    if (token.startsWith("__estimated__:")) {
                        return ServerSentEvent.<String>builder()
                                .event("estimated")
                                .data(token.substring("__estimated__:".length()))
                                .build();
                    }
                    if (token.equals("__done__")) {
                        return ServerSentEvent.<String>builder()
                                .event("done")
                                .data("[DONE]")
                                .build();
                    }
                    return ServerSentEvent.<String>builder()
                            .event("token")
                            .data(token)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("[SSE] 스트리밍 오류 postId={} memberId={}: {}", postId, memberId, e.getMessage());
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(e.getMessage())
                            .build());
                });
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
