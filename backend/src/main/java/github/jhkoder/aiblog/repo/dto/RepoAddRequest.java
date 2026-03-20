package github.jhkoder.aiblog.repo.dto;

import github.jhkoder.aiblog.repo.domain.CollectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class RepoAddRequest {
    @NotBlank(message = "저장소 소유자는 필수입니다.")
    @Size(min = 1, max = 100, message = "저장소 소유자는 1자 이상 100자 이하여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "저장소 소유자는 영문, 숫자, 하이픈, 언더스코어, 점만 허용됩니다.")
    private String owner;

    @NotBlank(message = "저장소 이름은 필수입니다.")
    @Size(min = 1, max = 100, message = "저장소 이름은 1자 이상 100자 이하여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "저장소 이름은 영문, 숫자, 하이픈, 언더스코어, 점만 허용됩니다.")
    private String repoName;

    @NotNull(message = "수집 타입은 필수입니다.")
    private CollectType collectType;
}
