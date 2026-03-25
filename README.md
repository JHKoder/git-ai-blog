# AI Blog Automation

> GitHub 활동을 AI가 분석해 기술 블로그 글을 자동 작성하고 Hashnode에 발행합니다.

<br>

## Overview

GitHub에 PR·커밋·README를 올리는 순간, Webhook이 트리거되어 AI가 내용을 분석하고 기술 블로그 포스트를 자동 생성합니다.
생성된 글은 대시보드에서 검토·수정·이미지 생성 후 Hashnode에 원클릭 발행됩니다.

```
GitHub PR/Commit/README → Webhook → AI 분석 → 블로그 초안 → 검토/편집 → Hashnode 발행
직접 글 작성 → AI 개선 요청 → 수락/거절 → Hashnode 발행
```

<br>

## Key Features

| 기능 | 설명 |
|---|---|
| **GitHub Webhook 연동** | PR·커밋·README·Wiki 이벤트를 수신해 자동 콘텐츠 수집 |
| **멀티 AI 라우팅** | Claude / GPT / Gemini / Grok 중 ContentType별 자동 라우팅 또는 수동 선택 |
| **AI 글쓰기 제안** | 작성 중인 글에 AI가 개선안을 제안, 수락(내용 적용)/거절 가능 |
| **커스텀 프롬프트** | 사용자 정의 프롬프트 등록(최대 30개), 인기 공개 프롬프트 탐색 |
| **AI 이미지 생성** | DALL·E 또는 Gemini Imagen으로 커버 이미지 생성 후 Cloudinary 업로드 |
| **Mermaid 다이어그램** | AI가 생성한 Mermaid 코드블록을 SVG 다이어그램으로 자동 렌더링 |
| **SQL Visualization Widget** | 데드락·Dirty Read 등 동시성 시나리오를 인터랙티브 타임라인/플로우로 시각화 |
| **Hashnode 자동 발행** | GraphQL API를 통해 태그·커버이미지 포함 발행, AI 메타정보 자동 첨부 |
| **JWT + GitHub OAuth2** | GitHub 소셜 로그인, JWT(24h) + Redis 리프레시 토큰(30일) |
| **암호화 저장** | API Key는 AES-GCM + Jasypt로 DB에 암호화 저장 |
| **Rate Limit 보호** | Redis 기반 AI 모델별 일일 토큰·요청 사용량 추적 및 한도 설정 |
| **Resilience4j** | AI 외부 API 장애 시 Circuit Breaker 자동 차단 |

<br>

## Tech Stack

### Backend

- **Java 25** / **Spring Boot 4.0.3** / Gradle 9.3
- Spring Security · OAuth2 · JWT (jjwt 0.12)
- Spring Data JPA · PostgreSQL · H2 (local/test)
- Spring WebFlux (WebClient) — 비동기 외부 API 호출
- Redis — 리프레시 토큰, Rate Limit 캐시, AI 사용량 추적
- Resilience4j — Circuit Breaker
- Jasypt + AES-GCM — 민감 정보 암호화
- Cloudinary — 이미지 호스팅
- springdoc-openapi — Swagger UI (`/swagger-ui/index.html`)

### Frontend

- **React 18** + **TypeScript** / Vite 5
- React Router v7 / Zustand (immer 미들웨어) / Axios
- react-markdown + remark-gfm — GFM 전체 지원
- Mermaid — 다이어그램 렌더링 (동적 import)
- Monaco Editor — SQL 에디터
- @xyflow/react (ReactFlow) — SQL 실행 흐름 그래프

### Infra

- Docker Compose (backend / frontend / redis / certbot)
- Nginx + Let's Encrypt (HTTPS, 6시간마다 자동 갱신 감지)
- GitHub Actions CI/CD (스마트 재빌드 정책 포함)
- OCI 단일 서버 (2CPU/16GB), 도메인: `git-ai-blog.kr`

<br>

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                          Client                             │
│                React + TypeScript (Vite)                    │
│   PostList · PostDetail(Mermaid) · SqlVizPage · Profile     │
└───────────────────────┬─────────────────────────────────────┘
                        │ REST API / JWT
┌───────────────────────▼─────────────────────────────────────┐
│                Spring Boot 4 (Java 25)                      │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌────────────┐  ┌──────────┐  │
│  │  Webhook │  │   Post   │  │ AI Suggest │  │  SqlViz  │  │
│  │  Handler │  │   CRUD   │  │   Engine   │  │  Widget  │  │
│  └────┬─────┘  └────┬─────┘  └────┬───────┘  └────┬─────┘  │
│       │              │             │                │        │
│  ┌────▼──────────────▼─────────────▼────────────────▼────┐  │
│  │                  AI Client Router                     │  │
│  │         Claude · GPT · Gemini · Grok                  │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────────┐    │
│  │PostgreSQL│  │  Redis   │  │  Cloudinary / Hashnode  │    │
│  │   JPA    │  │  Cache   │  │  GraphQL API            │    │
│  └──────────┘  └──────────┘  └────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

<br>

## Project Structure

```
git-ai-blog/
├── backend/
│   └── src/main/java/github/jhkoder/aiblog/
│       ├── infra/
│       │   ├── ai/          # AI 클라이언트 라우터 (Claude·GPT·Gemini·Grok)
│       │   ├── github/      # Webhook 수신 & 서명 검증
│       │   ├── hashnode/    # GraphQL 빌더 & 발행 클라이언트
│       │   └── image/       # 이미지 생성 (DALL·E / Gemini) + Cloudinary
│       ├── post/            # 포스트 도메인 (생성·편집·발행·동기화)
│       ├── repo/            # GitHub 레포 연동 & 수집 이력
│       ├── suggestion/      # AI 글쓰기 제안 (요청·수락·거절)
│       ├── sqlviz/          # SQL 동시성 시나리오 가상 시뮬레이션 + 임베드
│       ├── prompt/          # 커스텀 프롬프트 관리
│       ├── member/          # 회원·API Key·일일 한도 관리
│       ├── security/        # JWT 필터·OAuth2·리프레시 토큰
│       └── config/          # 보안 설정·암호화·Redis·WebClient
├── frontend/
│   └── src/
│       ├── pages/           # PostList·PostDetail·PostCreate·PostEdit
│       │                    # Profile·RepoList·SqlVizPage·SqlVizEmbedPage
│       ├── components/
│       │   ├── MarkdownRenderer/   # ReactMarkdown + remark-gfm 공통 래퍼
│       │   ├── MermaidBlock/       # Mermaid SVG 렌더링
│       │   ├── Visualization/      # SqlEditor·ConcurrencyTimeline·ExecutionFlow·EmbedGenerator
│       │   ├── AiSuggestionPanel/  # AI 개선 요청 + 수락/거절
│       │   ├── ImageGenButton/     # 커버 이미지 생성
│       │   └── TagInput/
│       ├── api/             # 백엔드 API 클라이언트 모음
│       └── store/           # Zustand 전역 상태 (auth·post·suggestion·sqlviz)
├── .github/
│   └── workflows/           # CI: test / deploy / auto-label
└── docker-compose.yml       # 전체 서비스 오케스트레이션
```

<br>

## Getting Started

### Prerequisites

- Java 25+ (Gradle toolchain 자동 관리)
- Node.js 20+
- Docker & Docker Compose

### 환경 변수

```yaml
# DB
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/aiblog
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=

# JWT
JWT_SECRET=

# GitHub OAuth2
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
GITHUB_WEBHOOK_SECRET=

# Cloudinary
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=

# Jasypt 암호화 마스터 키
JASYPT_ENCRYPTOR_PASSWORD=

# Frontend URL (CORS)
FRONTEND_URL=http://localhost:5173
```

> **local 프로파일**: `JASYPT_ENCRYPTOR_PASSWORD` 없이 H2로 기동 가능 (기본값 내장)

### 로컬 실행

```bash
# 백엔드 (Redis + PostgreSQL Docker 자동 기동)
cd backend
./gradlew serverRun

# 프론트엔드
cd frontend
npm install
npm run dev      # http://localhost:5173
```

### Docker 전체 실행

```bash
docker compose up -d
```

<br>

## CI/CD

```
sub branch push → PR 생성
  → [test.yml]       local 프로파일 테스트 (실패 시 merge 차단)
  → [auto-label.yml] feat/fix/hotfix 등 자동 태깅
→ Squash Merge to main
  → [deploy.yml]     Docker build (arm64) → Docker Hub push → OCI 롤링 배포
```

**스마트 재빌드 정책**: 이전 빌드 결과에 따라 파일 변경 없으면 빌드 skip, 이전 실패 시 강제 재빌드.

<br>

## Security

- GitHub Webhook 요청은 HMAC-SHA256 서명 검증
- 사용자 API Key는 AES-GCM 암호화 후 DB 저장 (Jasypt)
- JWT 액세스 토큰(24h) + Redis 리프레시 토큰(30일) 이중 인증
- Rate Limit: Redis로 AI 모델별 일일 토큰·요청 사용량 추적
- SQL Viz: SQL 직접 실행 금지 — 순수 Java 로직 가상 시뮬레이션만 사용

<br>

## License

MIT
