# AI Blog — 리서치 노트

> 작성일: 2026-03-20
> 대상 환경: Spring Boot 4 + React + Java 25, OCI 단일 서버 (2CPU/16GB)

---

## 1. ChatGPT (OpenAI) API 연동

### 기본 정보

| 항목 | 값 |
|------|-----|
| Base URL | `https://api.openai.com/v1` |
| 인증 | `Authorization: Bearer $OPENAI_API_KEY` 헤더 |
| 텍스트 엔드포인트 | `POST /v1/chat/completions` |
| 이미지 엔드포인트 | `POST /v1/images/generations` (DALL-E 3) |

### Rate Limit 응답 헤더

```
x-ratelimit-limit-requests
x-ratelimit-limit-tokens
x-ratelimit-remaining-requests
x-ratelimit-remaining-tokens
x-ratelimit-reset-requests
x-ratelimit-reset-tokens
```
→ 기존 Claude/Grok과 동일하게 `RateLimitCache`에 저장 가능

### 모델 목록

| 모델 | 입력 | 출력 | 컨텍스트 | 추천 용도 |
|------|------|------|----------|----------|
| `gpt-4o` | $2.50/M | $10/M | 128K | 멀티모달, 비전 |
| `gpt-4o-mini` | $0.15/M | $0.60/M | 128K | **블로그 텍스트 생성 추천** (가성비) |
| `gpt-4` | $30/M | $60/M | 8K | 고품질 추론 (고비용) |

→ 텍스트 개선은 `gpt-4o-mini`, 이미지 생성은 DALL-E 3 사용 권장

### 텍스트 API 요청/응답

**Request:**
```json
{
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "user", "content": "블로그 글을 개선해줘: ..." }
  ],
  "max_tokens": 2000,
  "temperature": 0.7
}
```

**Response:**
```json
{
  "choices": [{ "message": { "content": "개선된 내용..." } }],
  "usage": {
    "prompt_tokens": 45,
    "completion_tokens": 1234,
    "total_tokens": 1279
  }
}
```
→ `usage.prompt_tokens` = input, `usage.completion_tokens` = output → `TokenUsageTracker`에 기록

### 이미지 생성 API (DALL-E 3)

**Request:**
```json
{
  "model": "dall-e-3",
  "prompt": "기술 블로그 헤더 이미지",
  "size": "1024x1024",
  "quality": "hd",
  "style": "vivid",
  "n": 1,
  "response_format": "url"
}
```

**Response:**
```json
{
  "data": [{ "url": "https://..." }]
}
```

- `size`: `1024x1024` | `1792x1024` | `1024x1792`
- `quality`: `standard` | `hd`
- `response_format`: `url` | `b64_json`

### Spring Boot WebClient 연동 방식

현재 프로젝트 방식(`WebClient.Builder` + `exchangeToMono`)과 동일하게 구현:

```java
// GptClient.java 구조 (ClaudeClient와 동일 패턴)
String responseBody = webClientBuilder.build()
    .post()
    .uri(baseUrl + "/v1/chat/completions")
    .header("Authorization", "Bearer " + key)
    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
    .bodyValue(jsonBody)
    .exchangeToMono(res -> {
        // Rate limit 헤더 파싱
        headersRef.set(res.headers().asHttpHeaders());
        return res.bodyToMono(String.class);
    })
    .block();

// 응답 파싱
String text = response.get("choices").get(0).get("message").get("content").asText();
long inputTokens  = usage.path("prompt_tokens").asLong(0);
long outputTokens = usage.path("completion_tokens").asLong(0);
```

### AiClientRouter 라우팅 추가 계획

```java
// GptClient 상수
public static final String GPT_4O       = "gpt-4o";
public static final String GPT_4O_MINI  = "gpt-4o-mini";

// AiClientRouter - GPT 라우팅
if (model.startsWith("gpt")) {
    return new RouteResult(gptClient, model, member.getGptApiKey());
}

// 이미지 생성은 GPT 모델 선택 시에만 활성화
// ImageUsageLimiter → GPT 전용으로 변경
```

---

## 2. Docker + GitHub Actions 롤링 배포

### Dockerfile — Spring Boot 백엔드 (멀티스테이지)

```dockerfile
# Stage 1: 빌드
FROM eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /build
COPY gradle ./gradle
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: 런타임
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN useradd -m -u 1000 appuser
COPY --from=builder /build/build/libs/aiblog-*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
```

> `MaxRAMPercentage=75.0` → 16GB 서버에서 Spring Boot에 약 12GB 할당

### Dockerfile — 프론트엔드 (React + Nginx)

```dockerfile
# Stage 1: 빌드
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --prefer-offline --no-audit
COPY . .
RUN npm run build

# Stage 2: Nginx 서빙
FROM nginx:alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### Docker Compose 구성

```yaml
services:
  backend:
    build: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATA_REDIS_HOST: redis
    env_file:
      - /private/.env        # 서버의 암호화된 .env (이미지에 포함 안 함)
    depends_on:
      redis:
        condition: service_healthy
    restart: unless-stopped

  frontend:
    build: ./frontend
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - certbot_data:/etc/letsencrypt:ro
      - certbot_www:/var/www/certbot:ro
    depends_on:
      - backend
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
    restart: unless-stopped

  certbot:
    image: certbot/certbot:latest
    volumes:
      - certbot_data:/etc/letsencrypt
      - certbot_www:/var/www/certbot
    restart: unless-stopped

volumes:
  redis_data:
  certbot_data:
  certbot_www:
```

> `.env` 파일은 `/private/.env`에서 런타임 주입 (`env_file`). Docker 이미지에 절대 포함 금지.

### GitHub Actions CI/CD 흐름

```
sub branch push
  ↓
PR 생성
  ├── [테스트 workflow] commit마다 테스트 실행 → 실패 시 merge 차단
  ├── [라벨링 workflow] PR 내용 감지 → 태그 자동 부착
  └── Squash and Merge → main
        ↓
[배포 workflow] Docker build → OCI SSH → 롤링 배포
```

### GitHub Actions — 테스트 검증 (PR 보호)

**`.github/workflows/test.yml`**
```yaml
name: Test on PR

on:
  pull_request:
    branches: [main]

jobs:
  test-backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
      - name: Run tests
        run: cd backend && ./gradlew test --no-daemon

  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - run: cd frontend && npm ci && npm run build
```

→ Branch Protection Rule에서 이 workflow 통과 필수로 설정 → merge 차단

### GitHub Actions — OCI 롤링 배포

**`.github/workflows/deploy.yml`**
```yaml
name: Deploy to OCI

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build & Push Docker Images
        run: |
          echo "${{ secrets.DOCKER_PASSWORD }}" | \
            docker login -u "${{ secrets.DOCKER_USERNAME }}" --password-stdin
          docker build -t myrepo/aiblog-backend:${{ github.sha }} ./backend
          docker build -t myrepo/aiblog-frontend:${{ github.sha }} ./frontend
          docker push myrepo/aiblog-backend:${{ github.sha }}
          docker push myrepo/aiblog-frontend:${{ github.sha }}

      - name: Deploy via SSH (Rolling)
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.OCI_HOST }}
          username: ubuntu
          key: ${{ secrets.OCI_SSH_KEY }}
          script: |
            cd ~/aiblog
            docker compose pull backend frontend
            docker compose up -d --no-deps backend frontend
            sleep 15
            docker compose ps
            docker image prune -f
```

### GitHub Secrets 목록

| Secret | 설명 |
|--------|------|
| `OCI_HOST` | OCI 서버 IP |
| `OCI_SSH_KEY` | SSH 개인키 (`/private` 경로의 키) |
| `DOCKER_USERNAME` | Docker Hub 유저명 |
| `DOCKER_PASSWORD` | Docker Hub 토큰 |
| `JWT_SECRET`, `CLAUDE_API_KEY`, `GROK_API_KEY`, `OPENAI_API_KEY` 등 | 앱 환경변수 |

---

## 3. Redis 전환 (In-memory → Redis)

### 의존성 (build.gradle)

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

### application.yml 설정

```yaml
spring:
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      timeout: 2000
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

### AiUsageLimiter Redis 전환 핵심

현재 `ConcurrentHashMap<Long, AtomicInteger>` → Redis `INCR` + TTL 방식:

```java
// key: "ai_usage:{memberId}:{yyyy-MM-dd}"
String key = String.format("ai_usage:%d:%s", memberId, LocalDate.now());

// 증가
redisTemplate.opsForValue().increment(key);

// TTL 설정 (자정까지 남은 초)
long secondsUntilMidnight = ChronoUnit.SECONDS.between(
    LocalDateTime.now(),
    LocalDate.now().plusDays(1).atStartOfDay()
);
redisTemplate.expire(key, Duration.ofSeconds(secondsUntilMidnight));

// 조회
String count = redisTemplate.opsForValue().get(key);
```

→ `@Scheduled` 자정 초기화 불필요 (TTL이 자동 만료)

### RateLimitCache Redis 전환 핵심

현재 `ConcurrentHashMap<String, RateLimitInfo>` → Redis Hash:

```java
// key: "rl_cache:{memberId}:{provider}"
// TTL: 1분 (rate limit 정보는 짧게 유지)
String key = String.format("rl_cache:%d:%s", memberId, provider);
redisTemplate.opsForHash().putAll(key, Map.of(
    "tokenLimit", info.tokenLimit(),
    "tokenRemaining", info.tokenRemaining(),
    "requestLimit", info.requestLimit(),
    "requestRemaining", info.requestRemaining()
));
redisTemplate.expire(key, Duration.ofMinutes(5));
```

### TokenUsageTracker Redis 전환 핵심

```java
// key: "token_usage:{memberId}:{model}:input" / ":output"
// TTL: 월 1일 초기화 → 월말까지 남은 초
String inputKey  = String.format("token_usage:%d:%s:input", memberId, model);
String outputKey = String.format("token_usage:%d:%s:output", memberId, model);
redisTemplate.opsForValue().increment(inputKey, inputTokens);
redisTemplate.opsForValue().increment(outputKey, outputTokens);
```

---

## 4. Let's Encrypt 자동 갱신

### OCI Ubuntu 서버 초기 설정

```bash
sudo apt install -y certbot

# 인증서 최초 발급
sudo certbot certonly --standalone \
  -d your-domain.com \
  --agree-tos --non-interactive \
  --email your@email.com

# 갱신 검증 (dry run)
sudo certbot renew --dry-run
```

### Nginx HTTPS 설정

```nginx
# HTTP → HTTPS 리다이렉트
server {
    listen 80;
    server_name your-domain.com;
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}

# HTTPS
server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate     /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    add_header Strict-Transport-Security "max-age=31536000" always;

    # React SPA
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    # API 프록시
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # OAuth
    location /oauth2/ {
        proxy_pass http://localhost:8080;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 자동 갱신 설정 (systemd — Ubuntu 기본 포함)

```bash
# 갱신 타이머 확인
sudo systemctl list-timers certbot

# Nginx 자동 reload hook 등록
sudo tee /etc/letsencrypt/renewal-hooks/post/nginx-reload.sh << 'EOF'
#!/bin/bash
nginx -s reload
EOF
sudo chmod +x /etc/letsencrypt/renewal-hooks/post/nginx-reload.sh
```

→ Ubuntu에 certbot 설치 시 systemd 타이머 자동 등록 (별도 cron 불필요)

### Docker Compose + Certbot 방식

```yaml
certbot:
  image: certbot/certbot:latest
  volumes:
    - certbot_data:/etc/letsencrypt
    - certbot_www:/var/www/certbot
  entrypoint: |
    /bin/sh -c '
    certbot certonly --webroot -w /var/www/certbot \
      -d ${DOMAIN} --agree-tos --non-interactive \
      --email ${CERTBOT_EMAIL} --keep-until-expiring
    # 매 12시간마다 갱신 시도
    trap exit TERM; while :; do certbot renew; sleep 12h & wait; done
    '
```

---

## 5. GitHub Actions PR 자동 라벨링

### 라벨 카테고리

| 라벨 | 트리거 조건 |
|------|------------|
| `[feature]` | `feature/*` 브랜치, PR 제목에 `feat:` |
| `[bug]` | `fix/*` 브랜치, PR 제목에 `fix:` |
| `[hotfix]` | `hotfix/*` 브랜치 |
| `[release]` | `release/*` 브랜치 |
| `[security]` | PR 제목/본문에 `security`, `CVE` |
| `frontend` | `frontend/**` 파일 변경 |
| `backend` | `backend/**` 파일 변경 |

### `.github/labeler.yml`

```yaml
feature:
  - head-branch: ['^feature/.*']

bug:
  - head-branch: ['^(bugfix|fix)/.*']

hotfix:
  - head-branch: ['^hotfix/.*']

release:
  - head-branch: ['^release/.*']

frontend:
  - changed-files:
      - any-glob-to-any-file: ['frontend/**']

backend:
  - changed-files:
      - any-glob-to-any-file: ['backend/**']
```

### `.github/workflows/auto-label.yml`

```yaml
name: Auto Label PR

on:
  pull_request:
    types: [opened, synchronize, reopened]

permissions:
  contents: read
  pull-requests: write

jobs:
  labeler:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/labeler@v5
        with:
          configuration-path: .github/labeler.yml

  smart-label:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/github-script@v7
        with:
          script: |
            const title = context.payload.pull_request.title;
            const body  = context.payload.pull_request.body || '';
            const labels = [];

            if (/^feat(\(.+\))?:/.test(title))     labels.push('feature');
            if (/^fix(\(.+\))?:/.test(title))      labels.push('bug');
            if (/HOTFIX/i.test(title))             labels.push('hotfix');
            if (/^release/i.test(title))           labels.push('release');
            if (/security|CVE/i.test(title + body)) labels.push('security');

            if (labels.length > 0) {
              await github.rest.issues.addLabels({
                issue_number: context.issue.number,
                owner: context.repo.owner,
                repo: context.repo.repo,
                labels
              });
            }
```

### Branch Protection Rule 설정 (GitHub 웹에서)

```
Settings → Branches → main → Add rule
- [x] Require a pull request before merging
  - [x] Require approvals: 0 (1인 프로젝트)
  - [x] Require linear history (Squash and merge 강제)
- [x] Require status checks to pass before merging
  - Required checks: test-backend, test-frontend
- [x] Restrict pushes that create files (force push 금지)
```

---

---

## 6. 구현 중 발견한 사항 (2026-03-20 plan.md 일괄 구현)

### 패키지 명 변경 시 주의사항
- `com.example.aiblog` → `github.jhkoder.aiblog` 변경 시 `application.yml`의 `logging.level` 경로도 함께 변경해야 한다.
- `build.gradle`의 `group` 필드도 `github.jhkoder`로 변경했다.
- Java 파일은 `cp -r` 후 sed로 일괄 변경, 기존 디렉토리는 `rm -rf`로 제거.

### AesGcmEncryptionConverter - Spring @Component + JPA @Converter 동시 사용 문제
- `@Component`와 `@Converter`를 동시에 사용하면 Spring이 Bean으로 관리하면서 `@Value` 주입이 동작한다.
- 단, JPA가 자동으로 AttributeConverter를 찾는 경우 Spring Context 밖에서 인스턴스화될 수 있음 — `@Convert(converter=...)` 명시 필요.
- 컬럼 길이를 암호화 후 길이에 맞게 늘려야 한다 (기존 500 → 1000~2000).

### Jasypt PBEWithHMACSHA512AndAES_256 주의사항
- `iv-generator-classname`을 `RandomIvGenerator`로 변경해야 함. (`NoIvGenerator`는 보안 취약)
- `StandardPBEStringEncryptor`에서도 `setIvGenerator(new RandomIvGenerator())`를 명시해야 적용됨.
- 이미 기존 암호화된 값이 있다면 기존 알고리즘으로 복호화 후 새 알고리즘으로 재암호화 필요.

### RateLimitCache Redis CSV 저장 방식
- RateLimitInfo record를 Redis에 저장할 때 JSON 라이브러리 대신 CSV(`"tokenLimit,tokenRemaining,requestLimit,requestRemaining"`)로 직렬화.
- Jackson ObjectMapper 의존성 없이 처리 가능하고, parse 오류 시 `RateLimitInfo.unknown()` 반환으로 안전하게 처리.

### ImageUsageLimiter - Monthly 제거
- plan.md에서 GPT 이미지는 daily: 10장, per post: 5장으로 변경됨 (월별 1200장 제거).
- `AiUsageResponse`와 `ProfilePage.tsx`에서도 monthly 필드를 제거해야 컴파일/TS 오류 없음.
- `PostController`에서 `imageUsageLimiter.getMonthlyUsed()` 참조 제거 필요.

### JWT Refresh Token 쿠키 Path 설계
- Refresh Token 쿠키 Path를 `/api/auth`로 제한해 매 요청마다 쿠키가 전송되는 것을 방지.
- `cookie.setSecure(request.isSecure())`: HTTPS 환경에서만 Secure 속성 설정 (로컬 개발 호환).

### GeminiClient API 구조
- Gemini REST API는 `/v1beta/models/{model}:generateContent?key={api_key}` 패턴.
- 응답 구조: `candidates[0].content.parts[0].text`
- 토큰 사용량: `usageMetadata.promptTokenCount` / `usageMetadata.candidatesTokenCount`
- Claude/Grok과 달리 Rate Limit 응답 헤더가 없음 — `rateLimitCache` 업데이트 생략.

### 테스트에서 Lombok @Getter + private 필드 설정
- Lombok으로 생성된 private 필드는 `ReflectionTestUtils.setField()`로 설정.
- 도메인 엔티티(`Post`, `Member`)의 `id`, `createdAt` 등 `@GeneratedValue` 필드도 동일하게 처리.

### AiClientRouter CODE_REVIEW 라우팅 변경
- 이전: CODE_REVIEW → Claude Opus
- 변경: CODE_REVIEW → Claude Sonnet (plan.md 정책 기준 Sonnet이 기본)
- Claude Opus는 `claude-opus-4-5`로 명시적 모델 선택 시에만 사용.

### Spring Boot 4.0.3 BOM에 spring-boot-starter-aop 누락
- `spring-boot-starter-aop`는 Spring Boot 4.0.3 BOM에 포함되지 않음.
- `spring-boot-starter-aop` 대신 `org.springframework:spring-aop`와 `org.springframework:spring-aspects`를 직접 선언해야 함.
- 두 의존성 모두 Spring BOM에서 버전 관리되므로 별도 버전 명시 불필요.
- Resilience4j 어노테이션(`@Retry`, `@CircuitBreaker`)이 동작하려면 AOP 관련 의존성이 필요.

### Resilience4j @Retry + @CircuitBreaker private 메서드 주의사항
- Spring AOP는 프록시 기반이므로 `private` 메서드에 `@Retry`, `@CircuitBreaker`를 붙여도 적용되지 않음.
- `ClaudeClient.callApi()`는 `private`이므로 어노테이션을 붙여도 실제로 동작하지 않음.
- 제대로 동작하려면 해당 메서드를 `public`으로 변경하거나, `completeWithUsage()`에 어노테이션을 붙여야 함.
- `GrokClient`, `GptClient`, `GeminiClient`의 `completeWithUsage()`는 `public`이므로 정상 동작.
- HashnodeClient의 `executeWithRetry()`도 `private`이므로 동일 문제 있음. 수동 retry 로직이 이미 있어 허용.

### ImageGenerationService — Gemini → GPT 전환
- 이전: `GeminiImageClient`로 이미지 생성 (Gemini 무료 티어 할당량 초과로 실패)
- 변경: `GptImageClient`로 DALL-E 3 이미지 생성 (GPT 모델 선택 시에만 활성화)
- `ImageGenerationService.resolveImagePlaceholders(content, model, apiKey)` 오버로드 추가
- 하위 호환을 위해 `resolveImagePlaceholders(content)`는 자리표시자만 제거하도록 유지

### ImageGenButton — selectedModel prop 추가
- GPT 모델이 아닐 때 버튼을 disabled 스타일(opacity 0.5)로 표시하고 클릭 시 toast 경고.
- `postApi.generateImage(prompt, model)` — model 파라미터 추가로 백엔드 model 검증 가능.
- PostCreatePage, PostEditPage에 이미지 생성 모델 선택 드롭다운 추가 (gpt-4o-mini / gpt-4o).

## 요약 — 구현 순서 제안

| 순서 | 작업 | 선행 조건 |
|------|------|----------|
| 1 | Redis 전환 (`AiUsageLimiter`, `RateLimitCache`, `TokenUsageTracker`) | Redis Docker 설정 |
| 2 | `GptClient` 구현 + `AiClientRouter` 라우팅 추가 | OpenAI API 키 |
| 3 | `ImageUsageLimiter` → GPT 전용으로 변경 | GptClient 완료 |
| 4 | Jasypt 알고리즘 업그레이드 + `/private/.env` 이관 | - |
| 5 | JWT Refresh Token (30일) 추가 | - |
| 6 | Dockerfile + Docker Compose 작성 | Redis 전환 완료 |
| 7 | OCI 서버 세팅 + Nginx + Let's Encrypt | 도메인 준비 |
| 8 | GitHub Actions 워크플로우 (테스트, 배포, 라벨링) | OCI 세팅 완료 |
| 9 | Branch Protection Rule 설정 | 워크플로우 완료 |
| 10 | API 유효성 검사 (`@Valid` 전수 적용) | - |
