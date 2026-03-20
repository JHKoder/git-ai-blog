package github.jhkoder.aiblog.repo.dto;

import github.jhkoder.aiblog.infra.github.GitHubClient;

public record PrSummaryResponse(int number, String title, boolean hasBlogLabel, boolean alreadyCollected) {

    public static PrSummaryResponse of(GitHubClient.PrSummary pr, boolean alreadyCollected) {
        return new PrSummaryResponse(pr.number(), pr.title(), pr.hasBlogLabel(), alreadyCollected);
    }
}
