# Frontend — AI Blog Automation

> React 18 · TypeScript · Vite 5

---

## 기술 스택

| 영역       | 기술                                        |
|----------|-------------------------------------------|
| 프레임워크    | React 18 + TypeScript + Vite 5            |
| 상태관리     | Zustand + immer 미들웨어                      |
| HTTP     | Axios (JWT interceptor 자동 주입)             |
| 스타일      | CSS Modules + CSS 변수 (다크/라이트 모드)          |
| 라우팅      | React Router v7                           |
| Markdown | react-markdown + remark-gfm + Mermaid     |
| SQL 에디터  | @monaco-editor/react                      |
| 플로우 그래프  | @xyflow/react (ReactFlow)                 |

---

## 폴더 구조

```
src/
├── api/
│   ├── axiosInstance.ts      기본 URL /api, JWT 인터셉터, 401 → 로그아웃 (default export)
│   ├── postApi.ts
│   ├── suggestionApi.ts
│   ├── memberApi.ts
│   ├── repoApi.ts
│   ├── promptApi.ts
│   └── sqlvizApi.ts          create / getList / delete / getEmbed
├── store/
│   ├── authStore.ts          token (localStorage 'ai_blog_token'), setToken, logout
│   ├── postStore.ts          posts, currentPost, pagination, fetchPosts, fetchPost
│   ├── suggestionStore.ts    latest, history, accept(낙관적 업데이트), reject, clear
│   └── sqlvizStore.ts        widgets[], loading, fetchWidgets/createWidget/deleteWidget
├── types/
│   ├── member.ts
│   ├── post.ts
│   ├── suggestion.ts
│   ├── repo.ts
│   └── sqlviz.ts             SqlVizWidget, SimulationStep/Result, enum + label 상수
├── hooks/
│   ├── useDraft.ts
│   └── useTheme.ts
├── components/
│   ├── Layout/
│   ├── PostCard/
│   ├── AiSuggestionPanel/    AI 개선 요청 + 커스텀 프롬프트 선택 + 수락/거절
│   ├── StatusBadge/
│   ├── Modal/ConfirmModal/
│   ├── TagInput/
│   ├── ImageGenButton/
│   ├── MarkdownRenderer/     ReactMarkdown + remark-gfm 공통 래퍼 (MermaidBlock 통합)
│   ├── MermaidBlock/         동적 import + mermaid.render() + error fallback
│   └── Visualization/
│       ├── SqlEditor/        Monaco Editor SQL 문법 강조, 다크/라이트 연동
│       ├── ConcurrencyTimeline/ 트랜잭션 타임라인, 재생/정지/스크러빙
│       ├── ExecutionFlow/    ReactFlow 노드-엣지 그래프
│       └── EmbedGenerator/  %%[sqlviz-id] + iframe 코드 클립보드 복사
├── pages/
│   ├── LoginPage/
│   ├── PostListPage/
│   ├── PostDetailPage/
│   ├── PostCreatePage/
│   ├── PostEditPage/
│   ├── ProfilePage/
│   ├── RepoListPage/
│   ├── SqlVizPage/            위젯 생성/관리/미리보기 (인증 필요)
│   └── SqlVizEmbedPage/       공개 임베드 단독 페이지 (인증 불필요)
└── router/AppRouter.tsx       `/sqlviz` (PrivateRoute), `/embed/sqlviz/:id` (공개) 등록
```

---

## 타입 정의

### member.ts

```typescript
interface Member {
    id: number
    username: string
    avatarUrl?: string
    hasHashnodeConnection: boolean
    hasClaudeApiKey: boolean
    hasGrokApiKey: boolean
    hasGptApiKey: boolean
    hasGeminiApiKey: boolean
    hasGithubToken: boolean
    aiDailyLimit: number | null       // 전체 일일 한도 (null이면 서버 기본값 사용)
    claudeDailyLimit: number | null   // Claude 모델별 한도
    grokDailyLimit: number | null     // Grok 모델별 한도
    gptDailyLimit: number | null      // GPT 모델별 한도
    geminiDailyLimit: number | null   // Gemini 모델별 한도
}

interface HashnodeConnectRequest {
    token: string;
    publicationId: string
}

interface ApiKeyUpdateRequest {
    claudeApiKey?: string
    grokApiKey?: string
    gptApiKey?: string
    geminiApiKey?: string
    githubToken?: string
    aiDailyLimit?: number         // 전체 AI 일일 한도
    claudeDailyLimit?: number     // Claude 모델별 한도
    grokDailyLimit?: number       // Grok 모델별 한도
    gptDailyLimit?: number        // GPT 모델별 한도
    geminiDailyLimit?: number     // Gemini 모델별 한도
}
```

> `githubClientId/Secret`은 제거됨 — 사용자별 OAuth App 미지원

### post.ts

```typescript
type PostStatus = 'DRAFT' | 'AI_SUGGESTED' | 'ACCEPTED' | 'PUBLISHED'
type ContentType = 'ALGORITHM' | 'CODING' | 'CS' | 'TEST' | 'AUTOMATION' | 'DOCUMENT' | 'CODE_REVIEW' | 'ETC'

interface Post {
    id,
    title,
    content,
    contentType,
    status,
    hashnodeId?,
    hashnodeUrl?,
    tags,
    viewCount,
    createdAt,
    updatedAt
}

interface PostPage {
    content: Post[],
    totalElements,
    totalPages,
    number,
    size
}

interface AiUsage {
    used: number;
    limit: number;
    remaining: number
    sonnetInputTokens: number;
    sonnetOutputTokens: number
    claudeTokenLimit: number;
    claudeTokenRemaining: number
    claudeRequestLimit: number;
    claudeRequestRemaining: number
    imageDailyUsed: number;
    imageDailyLimit: number
    imageDailyRemaining: number;
    imagePerPostLimit: number
    grokInputTokens: number;
    grokOutputTokens: number
    grokTokenLimit: number;
    grokTokenRemaining: number
    grokRequestLimit: number;
    grokRequestRemaining: number
}
```

### suggestion.ts

```typescript
interface AiSuggestion {
    id,
    postId,
    suggestedContent,
    model,
    extraPrompt?,
    createdAt
}

interface AiSuggestionRequest {
    model?: string;
    extraPrompt?: string;
    tempContent?: string;
    promptId?: number         // 커스텀 프롬프트 ID (선택)
}
```

### repo.ts

```typescript
type CollectType = 'COMMIT' | 'PR' | 'WIKI' | 'README'

interface Repo {
    id,
    owner,
    repoName,
    collectType,
    createdAt
}

interface RepoAddRequest {
    owner: string;
    repoName: string;
    collectType: CollectType
}

interface PrSummary {
    number,
    title,
    hasBlogLabel,
    alreadyCollected
}
```

---

## API 클라이언트

### axiosInstance.ts

- `baseURL: /api`
- 요청 인터셉터: `Authorization: Bearer <token>` 주입
- 응답 인터셉터: 401 → `authStore.logout()` + `/login` 리다이렉트

### postApi.ts

```typescript
create(data)
POST / posts
getList(page, size, tag ?)
GET / posts
getDetail(id)
GET / posts / {id}
update(id, data)
PUT / posts / {id}
delete (id)
DELETE / posts / {id}
publish(id)
POST / posts / {id}
/publish
importFromHashnode()
POST / posts /
import

-hashnode
syncHashnode()
POST / posts / sync - hashnode
getAiUsage()
GET / posts / ai - usage
generateImage(prompt, model)
POST / posts / {id}
/generate-image
```

### memberApi.ts

```typescript
getMe()
GET / members / me
connectHashnode(data)
POST / members / hashnode - connect
disconnectHashnode()
DELETE / members / hashnode - connect
updateApiKeys(data)
PATCH / members / api - keys
getAiUsage()
GET / posts / ai - usage
```

### suggestionApi.ts

```typescript
request(postId, data)
POST / ai - suggestions / {postId}
getLatest(postId)
GET / ai - suggestions / {postId}
/latest
getHistory(postId)
GET / ai - suggestions / {postId}
/history
accept(postId, id)
POST / ai - suggestions / {postId}
/{id}/
accept
reject(postId, id)
POST / ai - suggestions / {postId}
/{id}/
reject
```

### repoApi.ts

```typescript
getList()
GET / repos
add(data)
POST / repos
delete (id)
DELETE / repos / {id}
collect(id, wikiPage ?)
POST / repos / {id}
/collect
getPrList(id)
GET / repos / {id}
/prs
collectPrs(id, prNumbers)
POST / repos / {id}
/collect-prs
```

---

## 상태관리 (Zustand)

### authStore

- `token: string | null` — localStorage `ai_blog_token`에 persist
- `isAuthenticated: boolean`
- `setToken(token)` / `logout()`

### postStore (immer)

- `posts: Post[]`, `currentPost: Post | null`
- `totalPages`, `currentPage`, `activeTag`, `loading`
- `fetchPosts(page, tag?)` — 태그 필터 유지
- `fetchPost(id)` / `clearCurrentPost()`

### suggestionStore (immer)

- `latestSuggestion`, `history[]`, `loading`
- `accept(postId, id)` — 낙관적 업데이트 후 실패 시 롤백
- `reject(postId, id)` / `fetchLatest(postId)` / `fetchHistory(postId)` / `clear()`

### sqlvizStore

- `widgets: SqlVizWidget[]`, `loading: boolean`
- `fetchWidgets()` / `createWidget(req)` (목록 맨 앞 추가) / `deleteWidget(id)`

---

## 타입 안전성 규칙

- `any` / `unknown` 타입 사용 금지
- catch 블록: `catch (e: unknown)` 후 타입 단언으로 접근
- 모든 API 응답은 명시적 타입으로 정의

---

## 개발 환경 실행

```bash
cd frontend && npm run dev      # http://localhost:5173
npx tsc --noEmit               # 타입 체크
npm run build                  # 프로덕션 빌드
```

---

## 주요 이슈 해결 기록

| 문제                             | 원인                               | 해결                                       |
|--------------------------------|----------------------------------|------------------------------------------|
| zustand immer 빌드 실패            | `immer` peer dependency 누락       | `immer: ^10.0.0` 추가                      |
| rollup 바이너리 누락 (반복)            | npm optional dep 버그              | CI에서 `rm -f package-lock.json` 후 install |
| QEMU arm64 illegal instruction | `node:20-alpine` musl + QEMU 비호환 | `node:20-slim` (debian)으로 교체             |
| GHA 캐시로 nginx.conf 누락          | 이전 빌드 캐시 재사용                     | frontend 빌드에 `--no-cache` 추가             |
| 다크모드 텍스트 안 보임                  | 하드코딩 색상                          | CSS 변수 `var(--text)` 교체                  |
| Hashnode 발행 버튼 위치             | ACCEPTED 상태 하단에만 있어 접근 불편         | PostDetailPage 상단 actions에 추가 (모든 상태), PostEditPage "저장 후 발행" 추가 |
| GFM 문법 미렌더링                   | `remark-gfm` 플러그인 미설치             | `remark-gfm` 설치 + `MarkdownRenderer` 공통 컴포넌트로 통합 |
| Mermaid 코드블록 원문 출력             | 렌더링 컴포넌트 없음                       | `MermaidBlock` (동적 import + mermaid.render()) + `MarkdownRenderer` 통합 |
| npm 캐시 권한 오류 (`EACCES`)        | npm cache 디렉터리 권한 문제              | `npm install --cache /tmp/npm-cache` 로 우회 |
| `import { api }` named import 오류 | `axiosInstance.ts`가 default export  | `import api from './axiosInstance'` 로 수정 |

**Hashnode 발행 전 프론트 검증:**
- 제목 6자 미만 → `toast.error` (Hashnode API 최소 길이 요구사항)
- 백엔드에서도 동일 검증 후 422 반환 (이중 방어)

**MarkdownRenderer 사용 패턴:**
- `PostDetailPage`, `AiSuggestionPanel` 양쪽 모두 `<MarkdownRenderer content={...} />` 사용
- `ReactMarkdown` + `remark-gfm` 직접 사용 금지 (MarkdownRenderer로 통일)
- Mermaid 언어 감지: ` ```mermaid ` 코드블록 → `MermaidBlock` 자동 분기
