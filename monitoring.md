# 서버 모니터링 가이드

> 방식: SSH 접속 후 직접 확인 (운영 서버: `opc@168.107.26.27`)

---

## 1. SSH 접속

```bash
ssh -i /path/to/private_key opc@168.107.26.27
```

---

## 2. 컨테이너 상태 확인

```bash
# 전체 컨테이너 상태 (STATUS, PORTS 포함)
docker compose -f /home/opc/app/docker-compose.yml ps

# 빠른 확인
docker ps
```

**정상 상태:**
```
NAME              STATUS
app-backend-1     Up X hours (healthy)
app-frontend-1    Up X hours
app-redis-1       Up X hours (healthy)
```

**비정상 상태 예시:**
- `Restarting (1) X seconds ago` → 컨테이너 시작 실패, 로그 확인 필요
- `Exited (1)` → 크래시 후 중단

---

## 3. 로그 확인

```bash
# backend 실시간 로그 (Ctrl+C로 종료)
docker compose -f /home/opc/app/docker-compose.yml logs -f backend

# 최근 100줄만
docker compose -f /home/opc/app/docker-compose.yml logs --tail=100 backend

# frontend(nginx) 로그
docker compose -f /home/opc/app/docker-compose.yml logs -f frontend

# redis 로그
docker compose -f /home/opc/app/docker-compose.yml logs --tail=50 redis
```

### Nginx 접근/오류 로그

```bash
# nginx 접근 로그 (컨테이너 내부)
docker exec app-frontend-1 tail -f /var/log/nginx/access.log

# nginx 오류 로그
docker exec app-frontend-1 tail -f /var/log/nginx/error.log
```

---

## 4. 주요 오류 패턴 및 대응

### backend Restarting 반복

```bash
# 로그 확인
docker compose -f /home/opc/app/docker-compose.yml logs --tail=50 backend
```

**원인 및 대응:**

| 원인 | 로그 패턴 | 대응 |
|------|-----------|------|
| `JASYPT_ENCRYPTOR_PASSWORD` 누락 | `Failed to decrypt...` | `/home/opc/private/.env` 확인 |
| DB 연결 실패 | `Connection refused` / `Unable to acquire JDBC Connection` | Supabase 상태 확인 |
| Redis 연결 실패 | `Unable to connect to Redis` | `docker ps`로 redis 컨테이너 확인 |
| 설정 파일 없음 | `no configuration file provided` | `/home/opc/app/docker-compose.yml` 존재 여부 확인 |

### 502 Bad Gateway

```bash
# nginx → backend 연결 확인
docker exec app-frontend-1 wget -q -O- http://backend:8080/actuator/health
```

- backend 컨테이너가 `healthy` 상태인지 확인
- 아니면 backend 로그 확인 후 재시작

### GitHub 로그인 502

- `/login/oauth2/code/github` 경로가 nginx에서 backend로 프록시되는지 확인
- backend 컨테이너 상태 확인 (`healthy` 여부)

---

## 5. 컨테이너 재시작

```bash
# backend만 재시작
docker compose -f /home/opc/app/docker-compose.yml restart backend

# 전체 재시작
docker compose -f /home/opc/app/docker-compose.yml restart

# 이미지 새로 pull 후 재시작 (배포)
docker compose -f /home/opc/app/docker-compose.yml pull backend frontend
JASYPT_ENCRYPTOR_PASSWORD='...' docker compose -f /home/opc/app/docker-compose.yml up -d --no-deps backend frontend
```

---

## 6. 디스크 / 리소스 확인

```bash
# 디스크 사용량
df -h

# 메모리 사용량
free -h

# CPU / 메모리 실시간 (컨테이너별)
docker stats

# 오래된 이미지 정리
docker image prune -f
```

---

## 7. SSL 인증서 확인

```bash
# 인증서 만료일 확인
docker compose -f /home/opc/app/docker-compose.yml run --rm certbot certificates

# 수동 갱신 (만료 30일 전 자동 갱신되나, 긴급 시)
docker compose -f /home/opc/app/docker-compose.yml run --rm certbot renew
docker compose -f /home/opc/app/docker-compose.yml exec frontend nginx -s reload
```

**자동 갱신 크론** (매일 03:00):
```bash
crontab -l  # 크론 등록 확인
```

---

## 8. `/home/opc/private/.env` 확인

```bash
cat /home/opc/private/.env
```

**필수 항목:**
```
JASYPT_ENCRYPTOR_PASSWORD=...
SPRING_PROFILES_ACTIVE=prod
```
