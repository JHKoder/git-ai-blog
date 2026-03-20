package github.jhkoder.aiblog.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class HashnodeConnectRequest {
    @NotBlank(message = "Hashnode 토큰은 필수입니다.")
    @Size(min = 1, max = 500, message = "Hashnode 토큰은 1자 이상 500자 이하여야 합니다.")
    private String token;

    @NotBlank(message = "Hashnode Publication ID는 필수입니다.")
    @Size(min = 1, max = 200, message = "Hashnode Publication ID는 1자 이상 200자 이하여야 합니다.")
    private String publicationId;
}
