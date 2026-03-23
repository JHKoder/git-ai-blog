FROM eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /build

# Gradle 캐시 위치
ENV GRADLE_USER_HOME=/cache

# 1. wrapper + 설정 먼저
COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew

# 2. wrapper 다운로드 + dependency 캐시
RUN --mount=type=cache,target=/cache \
    ./gradlew --version --no-daemon

RUN --mount=type=cache,target=/cache \
    ./gradlew dependencies --no-daemon

# 3. 소스 복사
COPY src src

# 4. 실제 빌드
RUN --mount=type=cache,target=/cache \
    ./gradlew bootJar -x test --no-daemon

# -------- runtime --------
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

RUN useradd -m -u 1000 appuser

COPY --from=builder /build/build/libs/*.jar app.jar

RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
