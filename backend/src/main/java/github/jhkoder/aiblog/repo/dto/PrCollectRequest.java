package github.jhkoder.aiblog.repo.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PrCollectRequest(@NotEmpty List<Integer> prNumbers) {}
