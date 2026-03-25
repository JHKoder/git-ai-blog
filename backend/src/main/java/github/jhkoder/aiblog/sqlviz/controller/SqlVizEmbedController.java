package github.jhkoder.aiblog.sqlviz.controller;

import github.jhkoder.aiblog.common.ApiResponse;
import github.jhkoder.aiblog.sqlviz.dto.SqlVizResponse;
import github.jhkoder.aiblog.sqlviz.usecase.GetSqlVizEmbedUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/embed/sqlviz")
@RequiredArgsConstructor
public class SqlVizEmbedController {

    private final GetSqlVizEmbedUseCase embedUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SqlVizResponse>> getEmbed(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(embedUseCase.execute(id)));
    }
}
