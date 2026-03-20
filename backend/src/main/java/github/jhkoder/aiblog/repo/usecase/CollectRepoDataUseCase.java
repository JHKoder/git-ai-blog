package github.jhkoder.aiblog.repo.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.github.GitHubClient;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.repo.domain.*;
import github.jhkoder.aiblog.repo.dto.PrSummaryResponse;
import github.jhkoder.aiblog.repo.dto.RepoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectRepoDataUseCase {

    private final RepoRepository repoRepository;
    private final PostRepository postRepository;
    private final RepoCollectHistoryRepository historyRepository;
    private final GitHubClient gitHubClient;
    private final MemberRepository memberRepository;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public RepoResponse execute(Long repoId, Long memberId, String wikiPage) {
        Repo repo = repoRepository.findByIdAndMemberId(repoId, memberId)
                .orElseThrow(() -> new NotFoundException("레포를 찾을 수 없습니다."));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        gitHubClient.withMemberPat(member.getGithubToken());
        try {
            return switch (repo.getCollectType()) {
                case COMMIT -> collectCommits(repo, memberId);
                case PR -> collectPrs(repo, memberId);
                case WIKI -> collectWiki(repo, memberId, wikiPage);
                case README -> collectReadme(repo, memberId);
            };
        } finally {
            gitHubClient.clearMemberPat();
        }
    }

    private RepoResponse collectCommits(Repo repo, Long memberId) {
        List<GitHubClient.CommitInfo> commits = gitHubClient.fetchBlogCommits(repo.getOwner(), repo.getRepoName());

        List<String> newMessages = new ArrayList<>();
        List<String> newShas = new ArrayList<>();

        for (GitHubClient.CommitInfo commit : commits) {
            if (historyRepository.existsByRepoIdAndRefTypeAndRefId(repo.getId(), CollectType.COMMIT, commit.sha())) {
                log.debug("커밋 중복 스킵: {}", commit.sha());
                continue;
            }
            newMessages.add(commit.message());
            newShas.add(commit.sha());
        }

        if (commits.isEmpty()) {
            log.info("수집할 [blog] 커밋 없음: {}/{}", repo.getOwner(), repo.getRepoName());
            return RepoResponse.from(repo);
        }

        if (newMessages.isEmpty()) {
            log.info("수집할 새 커밋 없음 (모두 중복): {}/{}", repo.getOwner(), repo.getRepoName());
            return RepoResponse.from(repo);
        }

        String content = "# 커밋 목록\n\n- " + String.join("\n- ", newMessages);
        String title = repo.getOwner() + "/" + repo.getRepoName() + " 커밋 기록";
        Post post = Post.create(memberId, title, content, ContentType.CODING);
        postRepository.save(post);

        // 히스토리 저장 + GitHub 댓글 등록
        for (int i = 0; i < newShas.size(); i++) {
            String sha = newShas.get(i);
            historyRepository.save(RepoCollectHistory.of(repo.getId(), CollectType.COMMIT, sha, post.getId()));
            String postUrl = frontendUrl + "/posts/" + post.getId();
            gitHubClient.addCommitComment(repo.getOwner(), repo.getRepoName(), sha,
                    "📝 이 커밋을 기반으로 블로그 게시글이 작성되었습니다. → " + postUrl);
        }

        return RepoResponse.from(repo);
    }

    private RepoResponse collectPrs(Repo repo, Long memberId) {
        List<GitHubClient.PrInfo> prs = gitHubClient.fetchBlogPrs(repo.getOwner(), repo.getRepoName());

        List<String> newContents = new ArrayList<>();
        List<Integer> newNumbers = new ArrayList<>();

        for (GitHubClient.PrInfo pr : prs) {
            String prKey = String.valueOf(pr.number());
            if (historyRepository.existsByRepoIdAndRefTypeAndRefId(repo.getId(), CollectType.PR, prKey)) {
                log.debug("PR 중복 스킵: #{}", pr.number());
                continue;
            }
            String prHeader = pr.number() > 0 ? "## PR #" + pr.number() + ": " + pr.title() : "## " + pr.title();
            String bodySection;
            if (pr.body().isBlank()) {
                String commitSummary = gitHubClient.fetchPrCommitSummary(pr.owner(), pr.repo(), pr.number());
                bodySection = commitSummary.isBlank() ? "" : "\n\n" + commitSummary;
            } else {
                bodySection = "\n\n" + pr.body();
            }
            newContents.add(prHeader + bodySection);
            newNumbers.add(pr.number());
        }

        if (prs.isEmpty()) {
            log.info("수집할 blog 라벨 PR 없음: {}/{}", repo.getOwner(), repo.getRepoName());
            return RepoResponse.from(repo);
        }

        if (newContents.isEmpty()) {
            log.info("수집할 새 PR 없음 (모두 중복): {}/{}", repo.getOwner(), repo.getRepoName());
            return RepoResponse.from(repo);
        }

        String content = "# PR 목록\n\n" + String.join("\n\n---\n\n", newContents);
        String prNums = newNumbers.stream()
                .filter(n -> n > 0)
                .map(n -> "#" + n)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String title = repo.getOwner() + "/" + repo.getRepoName() + " PR 리뷰"
                + (prNums.isBlank() ? "" : " (" + prNums + ")");
        Post post = Post.create(memberId, title, content, ContentType.CODING);
        postRepository.save(post);

        // 히스토리 저장 + GitHub 댓글 등록
        for (int number : newNumbers) {
            historyRepository.save(RepoCollectHistory.of(repo.getId(), CollectType.PR, String.valueOf(number), post.getId()));
            String postUrl = frontendUrl + "/posts/" + post.getId();
            gitHubClient.addPrComment(repo.getOwner(), repo.getRepoName(), number,
                    "📝 이 PR을 기반으로 블로그 게시글이 작성되었습니다. → " + postUrl);
        }

        return RepoResponse.from(repo);
    }

    private RepoResponse collectWiki(Repo repo, Long memberId, String wikiPage) {
        String page = (wikiPage != null && !wikiPage.isBlank()) ? wikiPage : "Home";

        // SHA 캐싱으로 변경 없으면 스킵
        String latestSha = gitHubClient.fetchWikiLatestSha(repo.getOwner(), repo.getRepoName());
        String cacheKey = page + ":" + (latestSha != null ? latestSha : "unknown");

        if (latestSha != null && latestSha.equals(repo.getWikiLastSha())) {
            // 같은 SHA라도 다른 페이지면 수집
            if (historyRepository.existsByRepoIdAndRefTypeAndRefId(repo.getId(), CollectType.WIKI, cacheKey)) {
                log.info("Wiki 변경 없음 스킵: {}/{} page={}", repo.getOwner(), repo.getRepoName(), page);
                return RepoResponse.from(repo);
            }
        }

        String pageContent = gitHubClient.fetchWikiPage(repo.getOwner(), repo.getRepoName(), page);
        String title = repo.getOwner() + "/" + repo.getRepoName() + " Wiki - " + page;
        Post post = Post.create(memberId, title, pageContent, ContentType.DOCUMENT);
        postRepository.save(post);

        historyRepository.save(RepoCollectHistory.of(repo.getId(), CollectType.WIKI, cacheKey, post.getId()));
        if (latestSha != null) repo.updateWikiSha(latestSha);

        return RepoResponse.from(repo);
    }

    private RepoResponse collectReadme(Repo repo, Long memberId) {
        String content = gitHubClient.fetchReadme(repo.getOwner(), repo.getRepoName());
        String title = repo.getOwner() + "/" + repo.getRepoName() + " README";
        Post post = Post.create(memberId, title, content, ContentType.DOCUMENT);
        postRepository.save(post);
        return RepoResponse.from(repo);
    }

    /** PR 타입 레포의 모든 closed PR 목록 조회 (이미 수집 여부 포함) */
    public List<PrSummaryResponse> getPrList(Long repoId, Long memberId) {
        Repo repo = repoRepository.findByIdAndMemberId(repoId, memberId)
                .orElseThrow(() -> new NotFoundException("레포를 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        gitHubClient.withMemberPat(member.getGithubToken());
        try {
            List<GitHubClient.PrSummary> prs = gitHubClient.fetchAllPrs(repo.getOwner(), repo.getRepoName());
            return prs.stream()
                    .map(pr -> {
                        boolean collected = historyRepository.existsByRepoIdAndRefTypeAndRefId(
                                repo.getId(), CollectType.PR, String.valueOf(pr.number()));
                        return PrSummaryResponse.of(pr, collected);
                    })
                    .toList();
        } finally {
            gitHubClient.clearMemberPat();
        }
    }

    /** 선택한 PR 번호 목록으로 게시글 작성 (PR 본문 읽어서 개별 게시글 생성) */
    @Transactional
    public RepoResponse collectSelectedPrs(Long repoId, Long memberId, List<Integer> prNumbers) {
        Repo repo = repoRepository.findByIdAndMemberId(repoId, memberId)
                .orElseThrow(() -> new NotFoundException("레포를 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        gitHubClient.withMemberPat(member.getGithubToken());
        try {
            // 전체 PR 목록에서 선택된 번호만 필터
            List<GitHubClient.PrInfo> allPrs = gitHubClient.fetchBlogPrsOrAll(repo.getOwner(), repo.getRepoName(), Set.copyOf(prNumbers));

            List<String> newContents = new ArrayList<>();
            List<Integer> newNumbers = new ArrayList<>();

            for (GitHubClient.PrInfo pr : allPrs) {
                if (!prNumbers.contains(pr.number())) continue;
                String prKey = String.valueOf(pr.number());
                if (historyRepository.existsByRepoIdAndRefTypeAndRefId(repo.getId(), CollectType.PR, prKey)) {
                    log.debug("PR 중복 스킵: #{}", pr.number());
                    continue;
                }
                String prHeader = "## PR #" + pr.number() + ": " + pr.title();
                String bodySection;
                if (pr.body().isBlank()) {
                    // body가 없으면 커밋 분석으로 대체
                    String commitSummary = gitHubClient.fetchPrCommitSummary(pr.owner(), pr.repo(), pr.number());
                    bodySection = commitSummary.isBlank() ? "" : "\n\n" + commitSummary;
                } else {
                    bodySection = "\n\n" + pr.body();
                }
                newContents.add(prHeader + bodySection);
                newNumbers.add(pr.number());
            }

            if (newContents.isEmpty()) {
                log.info("수집할 새 PR 없음 (모두 중복 또는 없음): {}/{}", repo.getOwner(), repo.getRepoName());
                return RepoResponse.from(repo);
            }

            String content = "# PR 목록\n\n" + String.join("\n\n---\n\n", newContents);
            String prNums = newNumbers.stream().map(n -> "#" + n).reduce((a, b) -> a + ", " + b).orElse("");
            String title = repo.getOwner() + "/" + repo.getRepoName() + " PR 리뷰 (" + prNums + ")";
            Post post = Post.create(memberId, title, content, ContentType.CODING);
            postRepository.save(post);

            for (int number : newNumbers) {
                historyRepository.save(RepoCollectHistory.of(repo.getId(), CollectType.PR, String.valueOf(number), post.getId()));
                String postUrl = frontendUrl + "/posts/" + post.getId();
                gitHubClient.addPrComment(repo.getOwner(), repo.getRepoName(), number,
                        "📝 이 PR을 기반으로 블로그 게시글이 작성되었습니다. → " + postUrl);
            }

            return RepoResponse.from(repo);
        } finally {
            gitHubClient.clearMemberPat();
        }
    }

    public List<String> getWikiPageList(Long repoId, Long memberId) {
        Repo repo = repoRepository.findByIdAndMemberId(repoId, memberId)
                .orElseThrow(() -> new NotFoundException("레포를 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
        gitHubClient.withMemberPat(member.getGithubToken());
        try {
            return gitHubClient.fetchWikiPageList(repo.getOwner(), repo.getRepoName());
        } finally {
            gitHubClient.clearMemberPat();
        }
    }
}
