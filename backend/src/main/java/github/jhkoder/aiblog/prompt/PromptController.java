package github.jhkoder.aiblog.prompt;

import github.jhkoder.aiblog.common.ApiResponse;
import github.jhkoder.aiblog.prompt.dto.PromptRequest;
import github.jhkoder.aiblog.prompt.dto.PromptResponse;
import github.jhkoder.aiblog.prompt.usecase.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Prompt", description = "커스텀 프롬프트 관리 API")
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final CreatePromptUseCase createPromptUseCase;
    private final GetMyPromptsUseCase getMyPromptsUseCase;
    private final UpdatePromptUseCase updatePromptUseCase;
    private final DeletePromptUseCase deletePromptUseCase;
    private final GetPopularPromptsUseCase getPopularPromptsUseCase;

    @Operation(summary = "내 프롬프트 목록 조회 (사용 횟수 내림차순)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PromptResponse>>> getMyPrompts(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(getMyPromptsUseCase.execute(memberId)));
    }

    @Operation(summary = "프롬프트 생성 (최대 30개)")
    @PostMapping
    public ResponseEntity<ApiResponse<PromptResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PromptRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(createPromptUseCase.execute(memberId, request)));
    }

    @Operation(summary = "프롬프트 수정")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PromptResponse>> update(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PromptRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(updatePromptUseCase.execute(id, memberId, request)));
    }

    @Operation(summary = "프롬프트 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId) {
        deletePromptUseCase.execute(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "공개 프롬프트 인기순 조회")
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<PromptResponse>>> getPopular() {
        return ResponseEntity.ok(ApiResponse.ok(getPopularPromptsUseCase.execute()));
    }

    @Operation(summary = "특정 사용자의 공개 프롬프트 인기순 조회")
    @GetMapping("/members/{memberId}/popular")
    public ResponseEntity<ApiResponse<List<PromptResponse>>> getPopularByMember(
            @PathVariable Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(getPopularPromptsUseCase.executeByMember(memberId)));
    }
}
