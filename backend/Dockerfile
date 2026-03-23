FROM eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /build

ENV GRADLE_USER_HOME=/cache

COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew

# wrapper 캐싱
RUN --mount=type=cache,target=/cache \
    ./gradlew --version --no-daemon

# dependency 캐싱 (src 없는 상태에서 빌드 실패는 정상 — || true로 무시하고 캐시 유지)
RUN --mount=type=cache,target=/cache \
    ./gradlew build -x test --no-daemon || true

# source
COPY src src

# 실제 빌드
RUN --mount=type=cache,target=/cache \
    ./gradlew bootJar -x test --no-daemon

# -------- runtime --------
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN useradd -m -u 1000 appuser

COPY --from=builder /build/build/libs/*.jar app.jar
RUN chown appuser:appuser app.jar

USER appuser
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
