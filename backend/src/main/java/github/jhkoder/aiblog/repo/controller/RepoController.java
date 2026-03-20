package github.jhkoder.aiblog.repo.controller;

import github.jhkoder.aiblog.common.ApiResponse;
import github.jhkoder.aiblog.repo.dto.PrCollectRequest;
import github.jhkoder.aiblog.repo.dto.PrSummaryResponse;
import github.jhkoder.aiblog.repo.dto.RepoAddRequest;
import github.jhkoder.aiblog.repo.dto.RepoResponse;
import github.jhkoder.aiblog.repo.usecase.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final GetRepoListUseCase getRepoListUseCase;
    private final AddRepoUseCase addRepoUseCase;
    private final DeleteRepoUseCase deleteRepoUseCase;
    private final CollectRepoDataUseCase collectRepoDataUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RepoResponse>>> list(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(getRepoListUseCase.execute(memberId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RepoResponse>> add(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody RepoAddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(addRepoUseCase.execute(memberId, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        deleteRepoUseCase.execute(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{id}/wiki-pages")
    public ResponseEntity<ApiResponse<List<String>>> wikiPages(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(collectRepoDataUseCase.getWikiPageList(id, memberId)));
    }

    @GetMapping("/{id}/prs")
    public ResponseEntity<ApiResponse<List<PrSummaryResponse>>> prList(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(collectRepoDataUseCase.getPrList(id, memberId)));
    }

    @PostMapping("/{id}/collect")
    public ResponseEntity<ApiResponse<RepoResponse>> collect(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @RequestParam(required = false) String wikiPage) {
        return ResponseEntity.ok(ApiResponse.ok(collectRepoDataUseCase.execute(id, memberId, wikiPage)));
    }

    @PostMapping("/{id}/collect-prs")
    public ResponseEntity<ApiResponse<RepoResponse>> collectPrs(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody PrCollectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(collectRepoDataUseCase.collectSelectedPrs(id, memberId, request.prNumbers())));
    }
}
