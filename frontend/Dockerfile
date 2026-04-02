FROM node:20-alpine AS builder
WORKDIR /app

# package 파일 먼저 복사 → 의존성 캐싱 극대화
COPY package*.json ./
RUN npm ci --prefer-offline --only=production=false

# 소스 복사 및 빌드
COPY . ./
RUN npm run build

# ==================== Production Stage ====================
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]