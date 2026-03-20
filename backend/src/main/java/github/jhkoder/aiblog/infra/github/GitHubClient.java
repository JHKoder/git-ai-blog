package github.jhkoder.aiblog.infra.github;

import github.jhkoder.aiblog.common.exception.ExternalApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubClient {

    private static final String GITHUB_API_URL = "https://api.github.com";
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${github.pat:}")
    private String pat;

    /** 요청별 PAT override — 회원 PAT를 임시로 설정 (ThreadLocal 기반) */
    private static final ThreadLocal<String> PAT_OVERRIDE = new ThreadLocal<>();

    public void withMemberPat(String memberPat) {
        if (memberPat != null && !memberPat.isBlank()) {
            PAT_OVERRIDE.set(memberPat);
        }
    }

    public void clearMemberPat() {
        PAT_OVERRIDE.remove();
    }

    private String currentPat() {
        String override = PAT_OVERRIDE.get();
        return (override != null && !override.isBlank()) ? override : pat;
    }

    /** 커밋 중 메시지에 [blog] 태그가 포함된 것만 수집 */
    public List<String> fetchBlogCommitMessages(String owner, String repo) {
        try {
            String json = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/commits?per_page=100", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<String> messages = new ArrayList<>();
            JsonNode response = objectMapper.readTree(json);
            if (response != null && response.isArray()) {
                for (JsonNode commit : response) {
                    String message = commit.path("commit").path("message").asText();
                    if (message.toLowerCase().contains("[blog]")) {
                        messages.add(message);
                    }
                }
            }
            // [blog] 태그 없으면 전체 반환 (fallback)
            if (messages.isEmpty()) {
                JsonNode response2 = objectMapper.readTree(json);
                if (response2 != null && response2.isArray()) {
                    for (JsonNode commit : response2) {
                        String message = commit.path("commit").path("message").asText();
                        if (!message.isBlank()) messages.add(message);
                    }
                }
            }
            return messages;
        } catch (Exception e) {
            throw new ExternalApiException("GitHub 커밋 수집 실패: " + e.getMessage(), e);
        }
    }

    /** blog 라벨이 달린 closed PR만 수집 */
    public List<String> fetchBlogPrTitles(String owner, String repo) {
        try {
            String json = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/pulls?state=closed&per_page=100&labels=blog", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<String> titles = new ArrayList<>();
            JsonNode response = objectMapper.readTree(json);
            if (response != null && response.isArray()) {
                for (JsonNode pr : response) {
                    // labels 배열에 "blog" 있는지 확인
                    boolean hasBlogLabel = false;
                    for (JsonNode label : pr.path("labels")) {
                        if ("blog".equalsIgnoreCase(label.path("name").asText())) {
                            hasBlogLabel = true;
                            break;
                        }
                    }
                    if (!hasBlogLabel) continue;
                    String title = pr.path("title").asText();
                    String body = pr.path("body").asText();
                    if (!title.isBlank()) titles.add(title + (body.isBlank() ? "" : "\n" + body));
                }
            }
            // blog 라벨 없으면 전체 반환 (fallback)
            if (titles.isEmpty()) {
                JsonNode response2 = objectMapper.readTree(json);
                if (response2 != null && response2.isArray()) {
                    for (JsonNode pr : response2) {
                        String title = pr.path("title").asText();
                        String body = pr.path("body").asText();
                        if (!title.isBlank()) titles.add(title + (body.isBlank() ? "" : "\n" + body));
                    }
                }
            }
            return titles;
        } catch (Exception e) {
            throw new ExternalApiException("GitHub PR 수집 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Wiki 페이지 목록 조회
     * GitHub REST/GraphQL API는 wiki 별도 git 저장소를 지원하지 않으므로
     * git clone --depth=1 으로 임시 클론 후 .md 파일 목록을 반환한다.
     */
    public List<String> fetchWikiPageList(String owner, String repo) {
        java.io.File tmpDir = null;
        try {
            tmpDir = java.nio.file.Files.createTempDirectory("wiki-clone-").toFile();
            String wikiUrl = "https://" + currentPat() + "@github.com/" + owner + "/" + repo + ".wiki.git";

            Process clone = new ProcessBuilder(
                    "git", "clone", "--depth=1", "--quiet", wikiUrl, tmpDir.getAbsolutePath()
            ).redirectErrorStream(true).start();

            boolean finished = clone.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished || clone.exitValue() != 0) {
                log.warn("Wiki clone 실패 (exit={})", finished ? clone.exitValue() : "timeout");
                return List.of("Home");
            }

            // .md 파일 목록 수집 (_(언더스코어) 시작 파일 제외)
            List<String> pages = new ArrayList<>();
            java.io.File[] files = tmpDir.listFiles(f ->
                    f.isFile() && f.getName().endsWith(".md") && !f.getName().startsWith("_")
            );
            if (files != null) {
                for (java.io.File f : files) {
                    // 파일명에서 .md 제거, 하이픈을 공백으로 변환해서 표시명 생성
                    String name = f.getName().replace(".md", "");
                    pages.add(name);
                }
                pages.sort(String::compareTo);
            }
            if (pages.isEmpty()) pages.add("Home");
            return pages;
        } catch (Exception e) {
            log.warn("Wiki 페이지 목록 조회 실패: {}", e.getMessage());
            return List.of("Home");
        } finally {
            if (tmpDir != null) {
                deleteDir(tmpDir);
            }
        }
    }

    private void deleteDir(java.io.File dir) {
        try {
            java.nio.file.Files.walk(dir.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {}
    }

    /** 특정 Wiki 페이지 내용 가져오기 (git clone 방식) */
    public String fetchWikiPage(String owner, String repo, String pageName) {
        java.io.File tmpDir = null;
        try {
            tmpDir = java.nio.file.Files.createTempDirectory("wiki-page-").toFile();
            String wikiUrl = "https://" + currentPat() + "@github.com/" + owner + "/" + repo + ".wiki.git";

            Process clone = new ProcessBuilder(
                    "git", "clone", "--depth=1", "--quiet", wikiUrl, tmpDir.getAbsolutePath()
            ).redirectErrorStream(true).start();

            boolean finished = clone.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished || clone.exitValue() != 0) {
                return "# " + pageName + "\n\n(Wiki 클론 실패)";
            }

            // 파일명 매칭: 정확히 일치하거나 대소문자 무시
            java.io.File[] files = tmpDir.listFiles(f ->
                    f.isFile() && f.getName().endsWith(".md")
            );
            if (files != null) {
                for (java.io.File f : files) {
                    String nameWithoutExt = f.getName().replace(".md", "");
                    if (nameWithoutExt.equals(pageName) || nameWithoutExt.equalsIgnoreCase(pageName)) {
                        return java.nio.file.Files.readString(f.toPath());
                    }
                }
            }
            return "# " + pageName + "\n\n(해당 페이지를 찾을 수 없습니다.)";
        } catch (Exception e) {
            log.warn("Wiki 페이지 조회 실패 [{}]: {}", pageName, e.getMessage());
            return "# " + pageName + "\n\n(Wiki 페이지를 가져오는 중 오류가 발생했습니다.)";
        } finally {
            if (tmpDir != null) deleteDir(tmpDir);
        }
    }

    public String fetchReadme(String owner, String repo) {
        try {
            String jsonResponse = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/readme", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (jsonResponse != null) {
                JsonNode node = objectMapper.readTree(jsonResponse);
                String encodedContent = node.path("content").asText().replace("\n", "");
                return new String(Base64.getDecoder().decode(encodedContent));
            }
            return "";
        } catch (Exception e) {
            throw new ExternalApiException("GitHub README 수집 실패: " + e.getMessage(), e);
        }
    }

    /** Webhook 등록 — 성공 시 webhook ID 반환, 실패 시 null */
    public Long registerWebhook(String owner, String repo, String webhookUrl, String secret) {
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "name", "web",
                    "active", true,
                    "events", new String[]{"push", "pull_request"},
                    "config", java.util.Map.of(
                            "url", webhookUrl,
                            "content_type", "json",
                            "secret", secret,
                            "insecure_ssl", "0"
                    )
            ));
            String response = webClientBuilder.build()
                    .post()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/hooks", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode node = objectMapper.readTree(response);
            return node.path("id").asLong();
        } catch (Exception e) {
            log.warn("Webhook 등록 실패 [{}/{}]: {}", owner, repo, e.getMessage());
            return null;
        }
    }

    /** Webhook 삭제 */
    public void deleteWebhook(String owner, String repo, Long webhookId) {
        try {
            webClientBuilder.build()
                    .delete()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/hooks/{hookId}", owner, repo, webhookId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.warn("Webhook 삭제 실패 [{}/{}] hookId={}: {}", owner, repo, webhookId, e.getMessage());
        }
    }

    /** 커밋에 댓글 등록 */
    public void addCommitComment(String owner, String repo, String sha, String body) {
        try {
            String reqBody = objectMapper.writeValueAsString(java.util.Map.of("body", body));
            webClientBuilder.build()
                    .post()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/commits/{sha}/comments", owner, repo, sha)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(reqBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.warn("커밋 댓글 등록 실패 [{}]: {}", sha, e.getMessage());
        }
    }

    /** PR에 댓글 등록 */
    public void addPrComment(String owner, String repo, int prNumber, String body) {
        try {
            String reqBody = objectMapper.writeValueAsString(java.util.Map.of("body", body));
            webClientBuilder.build()
                    .post()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/issues/{number}/comments", owner, repo, prNumber)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(reqBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.warn("PR 댓글 등록 실패 [#{}]: {}", prNumber, e.getMessage());
        }
    }

    /** Wiki 저장소의 최신 커밋 SHA 조회 (변경 감지용) */
    public String fetchWikiLatestSha(String owner, String repo) {
        try {
            Process proc = new ProcessBuilder(
                    "git", "ls-remote",
                    "https://" + currentPat() + "@github.com/" + owner + "/" + repo + ".wiki.git",
                    "HEAD"
            ).redirectErrorStream(true).start();
            boolean done = proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!done || proc.exitValue() != 0) return null;
            String output = new String(proc.getInputStream().readAllBytes()).trim();
            return output.isEmpty() ? null : output.split("\\s+")[0];
        } catch (Exception e) {
            log.warn("Wiki SHA 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /** [blog] 태그 커밋 목록 — SHA 포함해서 반환 */
    public List<CommitInfo> fetchBlogCommits(String owner, String repo) {
        try {
            String json = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/commits?per_page=100", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<CommitInfo> commits = new ArrayList<>();
            JsonNode response = objectMapper.readTree(json);
            if (response != null && response.isArray()) {
                for (JsonNode c : response) {
                    String sha = c.path("sha").asText();
                    String message = c.path("commit").path("message").asText();
                    if (message.toLowerCase().contains("[blog]")) {
                        commits.add(new CommitInfo(sha, message));
                    }
                }
            }
            return commits;
        } catch (Exception e) {
            throw new ExternalApiException("GitHub 커밋 수집 실패: " + e.getMessage(), e);
        }
    }

    /** 레포의 모든 closed PR 목록 반환 (blog 라벨 유무 포함).
     *  blog 라벨 PR이 하나도 없거나 결과가 비어있으면 인증 사용자가 작성한 PR로 폴백.
     *  등록된 owner가 실제 org와 다를 수 있으므로 폴백 시 repoName만으로 Search. */
    public List<PrSummary> fetchAllPrs(String owner, String repo) {
        String username = fetchAuthenticatedUsername();
        try {
            String json = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/pulls?state=closed&per_page=100", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<PrSummary> result = new ArrayList<>();
            JsonNode response = objectMapper.readTree(json);
            if (response != null && response.isArray()) {
                for (JsonNode pr : response) {
                    int number = pr.path("number").asInt();
                    String title = pr.path("title").asText();
                    if (title.isBlank()) continue;
                    boolean hasBlogLabel = false;
                    for (JsonNode label : pr.path("labels")) {
                        if ("blog".equalsIgnoreCase(label.path("name").asText())) {
                            hasBlogLabel = true;
                            break;
                        }
                    }
                    result.add(new PrSummary(number, title, hasBlogLabel));
                }
            }

            // 결과가 비어있거나 blog 라벨 PR이 하나도 없으면 사용자 기준 폴백
            boolean hasAnyBlogLabel = result.stream().anyMatch(PrSummary::hasBlogLabel);
            if (result.isEmpty() || !hasAnyBlogLabel) {
                if (username != null) {
                    List<PrSummary> byAuthor = fetchPrsByAuthor(owner, repo, username);
                    if (!byAuthor.isEmpty()) return byAuthor;
                }
            }
            return result;
        } catch (Exception e) {
            // 레포 접근 실패(404 등)이면 사용자 기준으로 폴백 시도
            log.warn("레포 직접 조회 실패, 사용자 기준 폴백: {}/{} - {}", owner, repo, e.getMessage());
            if (username != null) {
                return fetchPrsByAuthor(owner, repo, username);
            }
            throw new ExternalApiException("GitHub PR 목록 조회 실패: " + e.getMessage(), e);
        }
    }

    /** 현재 인증된 GitHub 사용자명 조회 */
    private String fetchAuthenticatedUsername() {
        try {
            String json = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode node = objectMapper.readTree(json);
            String login = node != null ? node.path("login").asText() : null;
            return (login != null && !login.isBlank()) ? login : null;
        } catch (Exception e) {
            log.warn("GitHub 사용자 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /** 특정 레포에서 author가 작성한 closed PR 목록 반환 (Search API) */
    private List<PrSummary> fetchPrsByAuthor(String owner, String repo, String author) {
        try {
            String q = "is:pr+repo:" + owner + "/" + repo + "+author:" + author + "+is:closed";
            String json = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/search/issues?q=" + q + "&per_page=100")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<PrSummary> result = new ArrayList<>();
            JsonNode response = objectMapper.readTree(json);
            JsonNode items = response != null ? response.path("items") : null;
            if (items != null && items.isArray()) {
                for (JsonNode pr : items) {
                    int number = pr.path("number").asInt();
                    String title = pr.path("title").asText();
                    if (title.isBlank()) continue;
                    boolean hasBlogLabel = false;
                    for (JsonNode label : pr.path("labels")) {
                        if ("blog".equalsIgnoreCase(label.path("name").asText())) {
                            hasBlogLabel = true;
                            break;
                        }
                    }
                    result.add(new PrSummary(number, title, hasBlogLabel));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("GitHub Search PR 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /** 선택된 PR 번호에 해당하는 PR 정보 반환 (직접 조회 → Search API 폴백) */
    public List<PrInfo> fetchBlogPrsOrAll(String owner, String repo, Set<Integer> targetNumbers) {
        try {
            String json = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/pulls?state=closed&per_page=100", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<PrInfo> result = new ArrayList<>();
            JsonNode response = objectMapper.readTree(json);
            if (response != null && response.isArray()) {
                for (JsonNode pr : response) {
                    int number = pr.path("number").asInt();
                    if (!targetNumbers.contains(number)) continue;
                    String title = pr.path("title").asText();
                    String body = pr.path("body").asText("").strip();
                    if (!title.isBlank()) result.add(new PrInfo(number, title, body, owner, repo));
                }
            }
            // 직접 조회로 못 찾은 PR은 Search API로 보완 (org 레포 등)
            if (result.size() < targetNumbers.size()) {
                Set<Integer> found = new java.util.HashSet<>();
                result.forEach(p -> found.add(p.number()));
                String username = fetchAuthenticatedUsername();
                if (username != null) {
                    List<PrInfo> bySearch = fetchPrInfosByAuthor(owner, repo, username, targetNumbers, found);
                    result.addAll(bySearch);
                }
            }
            return result;
        } catch (Exception e) {
            // org 레포 접근 실패 시 Search API 폴백
            String username = fetchAuthenticatedUsername();
            if (username != null) {
                return fetchPrInfosByAuthor(owner, repo, username, targetNumbers, Set.of());
            }
            throw new ExternalApiException("GitHub PR 수집 실패: " + e.getMessage(), e);
        }
    }

    /** Search API로 PR 상세 정보 조회 */
    private List<PrInfo> fetchPrInfosByAuthor(String owner, String repo, String author,
                                               Set<Integer> targetNumbers, Set<Integer> alreadyFound) {
        try {
            String q = "is:pr+repo:" + owner + "/" + repo + "+author:" + author + "+is:closed";
            String json = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/search/issues?q=" + q + "&per_page=100")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<PrInfo> result = new ArrayList<>();
            JsonNode response = objectMapper.readTree(json);
            JsonNode items = response != null ? response.path("items") : null;
            if (items != null && items.isArray()) {
                for (JsonNode pr : items) {
                    int number = pr.path("number").asInt();
                    if (!targetNumbers.contains(number) || alreadyFound.contains(number)) continue;
                    // Search API의 repository_url에서 실제 owner/repo 추출
                    String repoUrl = pr.path("repository_url").asText();
                    String[] parts = repoUrl.split("/repos/");
                    String actualOwnerRepo = parts.length > 1 ? parts[1] : owner + "/" + repo;
                    String actualOwner = actualOwnerRepo.contains("/") ? actualOwnerRepo.split("/")[0] : owner;
                    String actualRepo = actualOwnerRepo.contains("/") ? actualOwnerRepo.split("/")[1] : repo;
                    String title = pr.path("title").asText();
                    String body = pr.path("body").asText("").strip();
                    if (!title.isBlank()) result.add(new PrInfo(number, title, body, actualOwner, actualRepo));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Search API PR 상세 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /** blog 라벨 PR 목록 — PR 번호 포함해서 반환 */
    public List<PrInfo> fetchBlogPrs(String owner, String repo) {
        try {
            String json = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/pulls?state=closed&per_page=100", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<PrInfo> prs = new ArrayList<>();
            JsonNode response = objectMapper.readTree(json);
            if (response != null && response.isArray()) {
                for (JsonNode pr : response) {
                    boolean hasBlogLabel = false;
                    for (JsonNode label : pr.path("labels")) {
                        if ("blog".equalsIgnoreCase(label.path("name").asText())) {
                            hasBlogLabel = true;
                            break;
                        }
                    }
                    if (!hasBlogLabel) continue;
                    int number = pr.path("number").asInt();
                    String title = pr.path("title").asText();
                    String body = pr.path("body").asText("").strip();
                    if (!title.isBlank()) prs.add(new PrInfo(number, title, body, owner, repo));
                }
            }
            return prs;
        } catch (Exception e) {
            throw new ExternalApiException("GitHub PR 수집 실패: " + e.getMessage(), e);
        }
    }

    /** PR의 커밋 목록과 각 커밋의 파일 변경사항을 텍스트로 구성 */
    public String fetchPrCommitSummary(String owner, String repo, int prNumber) {
        try {
            // 커밋 목록
            String commitsJson = webClientBuilder.build()
                    .get()
                    .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/pulls/{prNumber}/commits?per_page=50",
                            owner, repo, prNumber)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode commits = objectMapper.readTree(commitsJson);
            if (commits == null || !commits.isArray() || commits.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("## 커밋 분석\n\n");

            for (JsonNode commit : commits) {
                String sha = commit.path("sha").asText();
                String message = commit.path("commit").path("message").asText();
                String firstLine = message.contains("\n") ? message.substring(0, message.indexOf('\n')) : message;
                sb.append("### ").append(firstLine).append("\n");

                // 커밋 상세 (변경 파일)
                try {
                    String commitJson = webClientBuilder.build()
                            .get()
                            .uri(GITHUB_API_URL + "/repos/{owner}/{repo}/commits/{sha}", owner, repo, sha)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentPat())
                            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

                    JsonNode commitDetail = objectMapper.readTree(commitJson);
                    JsonNode files = commitDetail != null ? commitDetail.path("files") : null;
                    if (files != null && files.isArray()) {
                        for (JsonNode file : files) {
                            String filename = file.path("filename").asText();
                            String status = file.path("status").asText();
                            int additions = file.path("additions").asInt();
                            int deletions = file.path("deletions").asInt();
                            sb.append("- `").append(filename).append("` (")
                              .append(status).append(", +").append(additions)
                              .append("/-").append(deletions).append(")\n");
                            String patch = file.path("patch").asText();
                            if (!patch.isBlank() && patch.length() < 1500) {
                                sb.append("```diff\n").append(patch).append("\n```\n");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("커밋 상세 조회 실패: {} - {}", sha, e.getMessage());
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("PR 커밋 목록 조회 실패: {}/{} #{} - {}", owner, repo, prNumber, e.getMessage());
            return "";
        }
    }

    public record CommitInfo(String sha, String message) {}
    /** owner/repo는 실제 GitHub 레포 위치 (등록된 owner와 다를 수 있음 — org 레포 등) */
    public record PrInfo(int number, String title, String body, String owner, String repo) {}
    public record PrSummary(int number, String title, boolean hasBlogLabel) {}
}
