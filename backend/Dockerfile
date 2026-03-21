FROM eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /build
COPY gradle ./gradle
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew  bootJar

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN useradd -m -u 1000 appuser
COPY --from=builder /build/build/libs/*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
