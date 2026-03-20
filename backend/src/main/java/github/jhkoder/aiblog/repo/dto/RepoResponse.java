package github.jhkoder.aiblog.repo.dto;

import github.jhkoder.aiblog.repo.domain.CollectType;
import github.jhkoder.aiblog.repo.domain.Repo;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RepoResponse {
    private Long id;
    private String owner;
    private String repoName;
    private CollectType collectType;
    private LocalDateTime createdAt;

    public static RepoResponse from(Repo repo) {
        return RepoResponse.builder()
                .id(repo.getId())
                .owner(repo.getOwner())
                .repoName(repo.getRepoName())
                .collectType(repo.getCollectType())
                .createdAt(repo.getCreatedAt())
                .build();
    }
}
