package github.jhkoder.aiblog.sqlviz.controller;

import github.jhkoder.aiblog.common.ApiResponse;
import github.jhkoder.aiblog.sqlviz.dto.SqlVizCreateRequest;
import github.jhkoder.aiblog.sqlviz.dto.SqlVizPageResponse;
import github.jhkoder.aiblog.sqlviz.dto.SqlVizResponse;
import github.jhkoder.aiblog.sqlviz.usecase.CreateSqlVizWidgetUseCase;
import github.jhkoder.aiblog.sqlviz.usecase.DeleteSqlVizWidgetUseCase;
import github.jhkoder.aiblog.sqlviz.usecase.GetSqlVizListUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sqlviz")
@RequiredArgsConstructor
public class SqlVizController {

    private final CreateSqlVizWidgetUseCase createUseCase;
    private final GetSqlVizListUseCase listUseCase;
    private final DeleteSqlVizWidgetUseCase deleteUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<SqlVizResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SqlVizCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(createUseCase.execute(memberId, req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SqlVizPageResponse>> list(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(listUseCase.execute(memberId, page, size)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        deleteUseCase.execute(id, memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
