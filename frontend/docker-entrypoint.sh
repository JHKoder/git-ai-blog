#!/bin/sh
# nginx를 백그라운드로 실행하고, 인증서 변경 감지 시 reload
nginx -g "daemon off;" &
NGINX_PID=$!

CERT="/etc/letsencrypt/live/git-ai-blog.kr/fullchain.pem"
LAST_MOD=""

while kill -0 "$NGINX_PID" 2>/dev/null; do
    if [ -f "$CERT" ]; then
        MOD=$(stat -c %Y "$CERT" 2>/dev/null || stat -f %m "$CERT" 2>/dev/null)
        if [ "$MOD" != "$LAST_MOD" ] && [ -n "$LAST_MOD" ]; then
            echo "[certbot-watcher] Certificate changed, reloading nginx..."
            nginx -s reload
        fi
        LAST_MOD="$MOD"
    fi
    sleep 6h &
    wait $!
done
