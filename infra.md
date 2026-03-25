# 인프라 / 배포

> 초기 셋업 및 구성 변경 절차.
> 실시간 운영/장애 대응 → [`monitoring.md`](monitoring.md)

---

## 서버 정보

- IP: `168.107.26.27` / 도메인: `git-ai-blog.kr`
- OS: Ubuntu 20.04 / 사양: 2CPU / 16GB RAM / SSH 키: `/private`

---

## Docker Compose 구성

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

---

## 환경변수 정책

| 프로파일 | 방식 |
|---------|------|
| `local` | 환경변수 없음, H2, CI도 local |
| `dev` | 해당 PC에 `JASYPT_ENCRYPTOR_PASSWORD` 직접 설정, Docker PostgreSQL + Redis |
| `prod` | 원격 서버에 `JASYPT_ENCRYPTOR_PASSWORD` + `SPRING_PROFILES_ACTIVE=prod` 직접 설정 |

- `.env` 파일 사용 금지 — 각 PC/서버에서 직접 환경변수 설정
- `JASYPT_ENCRYPTOR_PASSWORD` 미설정 시 backend 기동 실패로 배포 차단 (fail-fast)
- dev DB 접속 정보: `localhost:5432/aiblog`, `sa/sa`

---

## GitHub Actions CI/CD 흐름

```
sub branch push → PR 생성
  → [test.yml]       local 프로파일 테스트 (실패 시 merge 차단)
  → [auto-label.yml] feat/fix/hotfix 등 자동 태깅
→ Squash Merge to main
  → [deploy.yml]     Docker build (arm64) → Docker Hub push → OCI 롤링 배포
```

### 스마트 재빌드 정책 (`check-prev-result` job)

| 이전 결과 | 이번 실행 |
|---------|---------|
| 실패 | 파일 변경 없어도 강제 재빌드 |
| skipped | 가장 최근 실제 결과 조회 후 판단 |
| 성공 | 파일 변경 없으면 skip |
| 기록 없음 | 무조건 빌드 |

---

## HTTPS 인증서 초기 발급 (서버 초기 셋업 시)

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

---

## GitHub OAuth App 설정

| 환경 | Callback URL |
|-----|-------------|
| local | `http://localhost:8080/login/oauth2/code/github` |
| dev | `http://localhost:8080/login/oauth2/code/github` |
| prod | `https://git-ai-blog.kr/login/oauth2/code/github` |
