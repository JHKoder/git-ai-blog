# ==================== Stage 1: Build ====================
FROM node:20-alpine AS builder

WORKDIR /app

# 의존성 설치 (캐싱 활용)
COPY package*.json ./
RUN npm ci --only=production=false

# 소스 코드 복사
COPY . ./

# 빌드 실행
RUN npm run build

# ==================== Stage 2: Production (Nginx) ====================
FROM nginx:alpine

RUN rm -rf /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/default.conf

# builder 단계에서 빌드된 dist만 복사 (중요!)
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]