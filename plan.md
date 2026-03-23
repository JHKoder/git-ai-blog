# AI Blog Automation — 프로젝트 계획서

> 작성일: 2026-03-20 / 최종 수정: 2026-03-23
> 개발자: 1인 개인 프로젝트 / 목표 사용자: 최대 100명

**상세 문서:**
- 백엔드 → [`backend/claude.md`](backend/claude.md)
- 프론트엔드 → [`frontend/claude.md`](frontend/claude.md)
- 운영/모니터링 → [`monitoring.md`](monitoring.md)

---

## 1. 프로젝트 개요

GitHub 활동(커밋, PR, README 등)을 자동 수집해 Claude / Grok / GPT / Gemini AI로 블로그 글을 개선하고 Hashnode에 발행하는 자동화 시스템.

**두 가지 흐름:**
1. GitHub 레포 → 데이터 수집 → 글 초안 → AI 개선 → Hashnode 발행
2. 직접 글 작성 → AI 개선 → Hashnode 발행

---

## 2. 기술 스택 요약

| 영역 | 기술 |
|------|------|
| 백엔드 | Spring Boot 4.0.3, Java 25, Gradle 9.3.1 |
| 프론트 | React 18 + TypeScript + Vite 5 |
| DB | H2 (local) / PostgreSQL Supabase (dev/prod) |
| 캐시 | Redis (AI 사용량, Rate Limit, JWT blacklist) |
| 암호화 | Jasypt `PBEWithMD5AndDES` + AES-256-GCM (DB 컬럼) |
| 인증 | GitHub OAuth2 + JWT (Access 24h / Refresh 30일) |
| 컨테이너 | Docker Compose (backend, frontend, redis, certbot) |
| CI/CD | GitHub Actions → OCI 서버 롤링 배포 |
| 인프라 | OCI 단일 서버 (2CPU/16GB), 도메인: `git-ai-blog.kr` |
| 웹서버 | Nginx — HTTPS (Let's Encrypt 자동 갱신) + reverse proxy |

---

## 3. 인프라 / 배포

### 서버 정보
- IP: `168.107.26.27` / 도메인: `git-ai-blog.kr`
- OS: Ubuntu 20.04 / 사양: 2CPU / 16GB RAM / SSH 키: `/private`

### Docker Compose 구성
```
backend  — Spring Boot prod, JASYPT_ENCRYPTOR_PASSWORD, prepareThreshold=0
frontend — Nginx + React, 80/443, certbot_data(ro) + certbot_www 마운트
           docker-entrypoint.sh: 6시간마다 인증서 변경 감지 → nginx reload
redis    — AOF 영속성, 헬스체크 포함
certbot  — 12시간마다 certbot renew --webroot (frontend 무중단)
```

**외부 볼륨 (external: true):** `certbot_data`, `certbot_www`
**내부 볼륨:** `redis_data`

> **주의**: `docker-compose.yml` 변경 시 서버에 수동 복사 필요
> ```bash
> scp -i /path/to/key docker-compose.yml opc@168.107.26.27:/home/opc/app/docker-compose.yml
> ```

### 환경변수 정책
| 프로파일 | 방식 |
|---------|------|
| `local` | 환경변수 없음, H2, CI도 local |
| `dev` | `application-dev.yml` + `JASYPT_ENCRYPTOR_PASSWORD` |
| `prod` | `application-prod.yml` + `JASYPT_ENCRYPTOR_PASSWORD` (서버 관리) |

서버 `.env`는 단 두 개만 허용:
```
JASYPT_ENCRYPTOR_PASSWORD=...
SPRING_PROFILES_ACTIVE=prod
```

### GitHub Actions CI/CD 흐름
```
sub branch push → PR 생성
  → [test.yml]      local 프로파일 테스트 (실패 시 merge 차단)
  → [auto-label.yml] feat/fix/hotfix 등 자동 태깅
→ Squash Merge to main
  → [deploy.yml]    Docker build (arm64) → Docker Hub push → OCI 롤링 배포
```

**스마트 재빌드 정책** (`check-prev-result` job):

| 이전 결과 | 이번 실행 |
|---------|---------|
| 실패 | 파일 변경 없어도 강제 재빌드 |
| skipped | 가장 최근 실제 결과 조회 후 판단 |
| 성공 | 파일 변경 없으면 skip |
| 기록 없음 | 무조건 빌드 |

### HTTPS 인증서 초기 발급 (서버 초기 셋업 시)
```bash
docker compose -f /home/opc/app/docker-compose.yml stop frontend
docker run --rm \
  -v certbot_data:/etc/letsencrypt \
  -v certbot_www:/var/www/certbot \
  -p 80:80 \
  certbot/certbot certonly --standalone \
  -d git-ai-blog.kr --email jeonghun.kang.dev@gmail.com \
  --agree-tos --non-interactive
docker compose -f /home/opc/app/docker-compose.yml up -d frontend
```

### GitHub OAuth App 설정
| 환경 | Callback URL |
|------|-------------|
| local | `http://localhost:8080/login/oauth2/code/github` |
| prod | `https://git-ai-blog.kr/login/oauth2/code/github` |

---

## 4. 개선 필요 항목

### 인프라 / 배포
- [x] backend Docker Compose 설정 파일 경로 오류 수정
- [x] 배포 서버 GitHub 로그인 502 수정 (nginx `/login/` proxy 추가)
- [x] HTTPS 연결 완료 — `https://git-ai-blog.kr` 정상 접속
- [x] CI 스마트 재빌드 정책 구현 (`check-prev-result` job)
- [x] GitHub OAuth `redirect_uri` 오류 해결 (prod yml 명시)
- [x] backend `unhealthy` 해결 (Actuator 추가, SecurityConfig permitAll)
- [x] backend Dockerfile 레이어 캐시 최적화 + `.dockerignore` 추가
- [x] `Member.githubClientId/Secret` 필드 제거 (백엔드 + 프론트엔드 완료)
- [x] PostgreSQL prepared statement 충돌 해결 (`prepareThreshold: 0`)

### 운영 / 모니터링
- [x] 모니터링 가이드 문서 작성 (`monitoring.md`)

### 테스트
- [x] Controller 테스트 (`PostControllerTest`, `MemberControllerTest`)
- [x] Repository 통합 테스트 (4개 — H2 기반)
- [x] 도메인 단위 테스트 (`PostDomainTest`, `MemberDomainTest`, `WebhookSignatureVerifierTest`)
- [x] UseCase 단위 테스트 (`CreatePostUseCaseTest`, `ImportHashnodePostUseCaseTest`, `AiClientRouterTest`)
- [x] Spring Boot 4 테스트 환경 구성 (TestRedisConfig, OAuth2 mock)

---

## 5. 알려진 이슈 & 해결 기록 (주요)

| 문제 | 해결 |
|------|------|
| `prepared statement "S_1" already exists` | `prepareThreshold: 0` (prod yml) |
| JASYPT 특수문자 shell 해석 | `JASYPT_ENCRYPTOR_PASSWORD='...'` 작은따옴표 |
| backend `unhealthy` | Actuator 추가 |
| QEMU arm64 curl segfault | wget으로 HEALTHCHECK 변경 |
| `Post.tags` LazyInitializationException | `List.copyOf(post.getTags())` |
| SyncHashnode Duplicate key | `Collectors.toMap` mergeFunction 추가 |
| frontend conf.d 비어있어 443 거부 | GHA `--no-cache`, 서버 docker-compose.yml scp 복사 |
| 최초 인증서 없이 nginx 즉시 종료 | certbot standalone 발급 후 frontend 재기동 순서 필수 |
| Hashnode INVALID_QUERY | GraphQL 쿼리에 변수 직접 인라인 |
| rollup 바이너리 누락 (반복) | CI에서 `rm -f package-lock.json` 후 install |

> 전체 이슈 기록 → `backend/claude.md`, `frontend/claude.md` 참고

---

## 6. 개발 규칙

- 구현 중 새로 발견한 내용(버그, 설계 결정, 특이사항)은 `research.md`에 기록
- Jasypt 암호화는 AI가 직접 수행하지 않음 — jasypt online tool에서 수동 암호화 후 yml에 붙여넣기
- `any` / `unknown` 타입 사용 금지 (프론트엔드)
- 작업 완료 시 해당 문서의 체크박스 완료 표시
