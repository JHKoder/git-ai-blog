#!/bin/bash
# OCI 서버 초기 설정 스크립트
# 실행: bash server-setup.sh
# 대상: Ubuntu 20.04 (opc@168.107.26.27)

set -e

echo "=== [1/7] 시스템 업데이트 ==="
sudo apt-get update -y
sudo apt-get upgrade -y

echo "=== [2/7] Docker 설치 (Ubuntu 20.04) ==="
sudo apt-get install -y ca-certificates curl gnupg lsb-release
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker opc

echo "=== [3/7] 방화벽 포트 개방 (80, 443) ==="
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable

echo "=== [4/7] 디렉토리 구조 생성 ==="
mkdir -p /home/opc/app
mkdir -p /home/opc/private
mkdir -p /home/opc/app/certbot/www
mkdir -p /home/opc/app/certbot/conf

echo "=== [5/7] /private/.env 생성 ==="
cat > /home/opc/private/.env << 'ENVEOF'
JASYPT_ENCRYPTOR_PASSWORD=여기에_실제_비밀번호_입력
SPRING_PROFILES_ACTIVE=prod
ENVEOF

chmod 600 /home/opc/private/.env
echo "✓ /home/opc/private/.env 생성 완료"

echo "=== [6/7] docker-compose.yml 생성 ==="
cat > /home/opc/app/docker-compose.yml << 'DCEOF'
services:
  backend:
    image: jhkoders/aiblog-backend:latest
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATA_REDIS_HOST: redis
    env_file:
      - /home/opc/private/.env
    depends_on:
      redis:
        condition: service_healthy
    restart: unless-stopped
    networks:
      - app-net

  frontend:
    image: jhkoders/aiblog-frontend:latest
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - certbot_data:/etc/letsencrypt:ro
      - certbot_www:/var/www/certbot:ro
    depends_on:
      - backend
    restart: unless-stopped
    networks:
      - app-net

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3
    restart: unless-stopped
    networks:
      - app-net

  certbot:
    image: certbot/certbot:latest
    volumes:
      - certbot_data:/etc/letsencrypt
      - certbot_www:/var/www/certbot
    networks:
      - app-net

volumes:
  redis_data:
  certbot_data:
  certbot_www:

networks:
  app-net:
    driver: bridge
DCEOF

echo "✓ docker-compose.yml 생성 완료"

echo "=== [7/7] certbot 자동 갱신 크론 등록 ==="
(crontab -l 2>/dev/null; echo "0 3 * * * docker compose -f /home/opc/app/docker-compose.yml run --rm certbot renew --quiet && docker compose -f /home/opc/app/docker-compose.yml exec frontend nginx -s reload") | crontab -
echo "✓ 인증서 자동 갱신 크론 등록 완료"

echo ""
echo "=========================================="
echo "서버 초기 설정 완료"
echo ""
echo "다음 단계:"
echo "1. docker group 적용을 위해 재로그인 필요:"
echo "   exit 후 다시 SSH 접속"
echo ""
echo "2. Docker Hub 이미지 push 후 아래 실행:"
echo "   cd /home/opc/app && docker compose pull && docker compose up -d redis backend"
echo ""
echo "3. Let's Encrypt 인증서 최초 발급:"
echo "   docker compose up -d frontend"
echo "   docker compose run --rm certbot certonly --webroot \\"
echo "     --webroot-path=/var/www/certbot \\"
echo "     -d git-ai-blog.kr \\"
echo "     --email your@email.com --agree-tos --no-eff-email"
echo "   docker compose restart frontend"
echo "=========================================="
