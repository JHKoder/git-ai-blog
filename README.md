# AI Blog Automation

> GitHub PR을 커밋하면, AI가 블로그 글을 자동으로 작성하고 Hashnode에 발행합니다.

<br>

## Overview

개발자가 GitHub에 Pull Request를 올리는 순간, Webhook이 트리거되어 AI가 PR 내용을 분석하고 기술 블로그 포스트를 자동 생성합니다. 생성된 글은 대시보드에서 검토·수정·이미지 생성 후
Hashnode로 원클릭 발행됩니다.

```
GitHub PR → Webhook → AI 분석 → 블로그 초안 생성 → 검토/편집 → Hashnode 발행
```

<br>

## Key Features

| 기능                      | 설명                                                      |
|-------------------------|---------------------------------------------------------|
| **GitHub Webhook 연동**   | PR 이벤트를 수신해 자동으로 콘텐츠 수집                                 |
| **멀티 AI 라우팅**           | GPT / Claude / Gemini / Grok 중 사용 가능한 AI로 자동 전환         |
| **AI 이미지 생성**           | DALL·E 또는 Gemini Imagen으로 커버 이미지 자동 생성 후 Cloudinary 업로드 |
| **AI 글쓰기 제안**           | 작성 중인 글에 대해 AI가 개선안을 제안, 수락/거절 가능                       |
| **Hashnode 자동 발행**      | GraphQL API를 통해 태그·커버이미지 포함 발행                          |
| **JWT + GitHub OAuth2** | GitHub 소셜 로그인, JWT 인증, Redis 리프레시 토큰                    |
| **암호화 저장**              | API Key는 AES-GCM + Jasypt로 DB에 암호화 저장                   |
| **Rate Limit 보호**       | Redis 기반 토큰 사용량 추적으로 과금 방지                              |
| **Resilience4j**        | AI 외부 API 장애 시 Circuit Breaker 자동 차단                    |

<br>

## Tech Stack

### Backend

- **Java 25** / **Spring Boot 4.0**
- Spring Security · OAuth2 · JWT (jjwt 0.12)
- Spring Data JPA · PostgreSQL · H2 (dev)
- Spring WebFlux (WebClient) — 비동기 외부 API 호출
- Redis — 리프레시 토큰, Rate Limit 캐시
- Resilience4j — Circuit Breaker
- Jasypt + AES-GCM — 민감 정보 암호화
- Cloudinary — 이미지 호스팅

### Frontend

- **React 18** + **TypeScript**
- Vite / React Router v6 / Zustand / Axios
- react-markdown (포스트 미리보기)

### Infra

- Docker Compose (backend / frontend / redis / certbot)
- Nginx + Let's Encrypt (HTTPS)
- GitHub Actions CI/CD (자동 빌드 → 서버 배포)

<br>

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        Client                           │
│              React + TypeScript (Vite)                  │
└───────────────────────┬─────────────────────────────────┘
                        │ REST API / JWT
┌───────────────────────▼─────────────────────────────────┐
│               Spring Boot 4 (Java 25)                   │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────┐    │
│  │  Webhook │  │   Post   │  │   AI Suggestion    │    │
│  │  Handler │  │   CRUD   │  │      Engine        │    │
│  └────┬─────┘  └────┬─────┘  └────────┬───────────┘    │
│       │              │                 │                 │
│  ┌────▼─────────────▼─────────────────▼───────────┐    │
│  │              AI Client Router                   │    │
│  │      GPT · Claude · Gemini · Grok               │    │
│  └─────────────────────────────────────────────────┘    │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐      │
│  │PostgreSQL│  │  Redis   │  │  Cloudinary /    │      │
│  │   JPA    │  │  Cache   │  │  Hashnode API    │      │
│  └──────────┘  └──────────┘  └──────────────────┘      │
└─────────────────────────────────────────────────────────┘
```

<br>

## Project Structure

```
git-ai-blog/
├── backend/
│   └── src/main/java/github/jhkoder/aiblog/
│       ├── infra/
│       │   ├── ai/          # AI 클라이언트 라우터 (GPT·Claude·Gemini·Grok)
│       │   ├── github/      # Webhook 수신 & 서명 검증
│       │   ├── hashnode/    # GraphQL 빌더 & 발행 클라이언트
│       │   └── image/       # 이미지 생성 (DALL·E / Gemini) + Cloudinary
│       ├── post/            # 포스트 도메인 (생성·편집·발행·동기화)
│       ├── repo/            # GitHub 레포 연동 & 수집 이력
│       ├── suggestion/      # AI 글쓰기 제안 (요청·수락·거절)
│       ├── member/          # 회원·API Key 관리
│       ├── security/        # JWT 필터·OAuth2·리프레시 토큰
│       └── config/          # 보안 설정·암호화·Redis·WebClient
├── frontend/
│   └── src/
│       ├── pages/           # PostList·PostCreate·PostEdit·PostDetail·Profile·RepoList
│       ├── components/      # AiSuggestionPanel·ImageGenButton·TagInput·PostCard
│       ├── api/             # 백엔드 API 클라이언트 모음
│       └── store/           # Zustand 전역 상태 (auth·post·suggestion)
├── .github/
│   └── workflows/           # CI: test, deploy, auto-label
└── docker-compose.yml       # 전체 서비스 오케스트레이션
```

<br>

## Getting Started

### Prerequisites

- Java 25+
- Node.js 20+
- Docker & Docker Compose
- PostgreSQL (또는 Docker로 대체 가능)
- Redis

### 환경 변수

```yml
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

### 로컬 실행

```bash
# 백엔드
cd backend
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun

# 프론트엔드
cd frontend
npm install
npm run dev
```

### Docker로 전체 실행

```bash
docker compose up -d
```

<br>

## CI/CD

GitHub Actions로 다음 파이프라인이 자동 실행됩니다.

```
Push / PR → 테스트 → Docker 이미지 빌드 → 서버 SSH 배포
```

- `test.yml` — Gradle 단위 테스트
- `deploy.yml` — main 브랜치 push 시 자동 배포
- `auto-label.yml` — PR 파일 경로 기반 라벨 자동 부여

<br>

## Security

- GitHub Webhook 요청은 HMAC-SHA256 서명 검증
- 사용자 API Key는 AES-GCM 암호화 후 DB 저장 (Jasypt)
- JWT 액세스 토큰 + Redis 리프레시 토큰으로 이중 인증
- Rate Limit: Redis로 AI API 호출 횟수·토큰 사용량 추적

<br>

## License

MIT
