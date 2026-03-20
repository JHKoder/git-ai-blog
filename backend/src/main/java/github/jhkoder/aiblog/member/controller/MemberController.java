package github.jhkoder.aiblog.member.controller;

import github.jhkoder.aiblog.common.ApiResponse;
import github.jhkoder.aiblog.member.dto.ApiKeyUpdateRequest;
import github.jhkoder.aiblog.member.dto.HashnodeConnectRequest;
import github.jhkoder.aiblog.member.dto.MemberResponse;
import github.jhkoder.aiblog.member.usecase.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final GetMemberProfileUseCase getMemberProfileUseCase;
    private final ConnectHashnodeUseCase connectHashnodeUseCase;
    private final DisconnectHashnodeUseCase disconnectHashnodeUseCase;
    private final UpdateApiKeysUseCase updateApiKeysUseCase;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMe(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(getMemberProfileUseCase.execute(memberId)));
    }

    @PostMapping("/hashnode-connect")
    public ResponseEntity<ApiResponse<MemberResponse>> connectHashnode(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody HashnodeConnectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(connectHashnodeUseCase.execute(memberId, request)));
    }

    @DeleteMapping("/hashnode-connect")
    public ResponseEntity<ApiResponse<Void>> disconnectHashnode(@AuthenticationPrincipal Long memberId) {
        disconnectHashnodeUseCase.execute(memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/api-keys")
    public ResponseEntity<ApiResponse<MemberResponse>> updateApiKeys(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ApiKeyUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(updateApiKeysUseCase.execute(memberId, request)));
    }
}
