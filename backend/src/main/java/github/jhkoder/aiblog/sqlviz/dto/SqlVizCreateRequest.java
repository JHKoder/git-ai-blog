package github.jhkoder.aiblog.sqlviz.dto;

import github.jhkoder.aiblog.sqlviz.domain.IsolationLevel;
import github.jhkoder.aiblog.sqlviz.domain.SqlVizScenario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SqlVizCreateRequest(
        @NotBlank String title,
        @NotEmpty @Size(max = 10) List<@NotBlank String> sqls,
        @NotNull SqlVizScenario scenario,
        @NotNull IsolationLevel isolationLevel
) {}
