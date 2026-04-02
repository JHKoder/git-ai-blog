# ==================== Stage 1: Build (Node) ====================
FROM node:20-alpine AS builder

WORKDIR /app

# package 파일 먼저 복사해서 의존성 캐싱 활용
COPY frontend/package*.json ./
RUN npm ci --only=production=false   # 또는 npm install

COPY frontend/ ./
RUN npm run build

# ==================== Stage 2: Production (Nginx) ====================
FROM nginx:alpine

# 불필요한 default conf 삭제
RUN rm -rf /etc/nginx/conf.d/default.conf

# nginx 설정 복사
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 빌드된 dist 폴더만 복사 (중요!)
COPY --from=builder /app/dist /usr/share/nginx/html

# 보안 강화 (선택, 하지만 추천)
RUN addgroup -g 101 -S nginx \
    && adduser -S -D -H -u 101 -h /var/cache/nginx -s /sbin/nologin -G nginx -g nginx nginx

EXPOSE 80

# nginx를 foreground로 실행
CMD ["nginx", "-g", "daemon off;"]