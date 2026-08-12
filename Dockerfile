# ===== Build stage =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 빌드 스크립트 먼저 복사 → 의존성 레이어 캐싱 (소스만 바뀔 땐 재다운로드 안 함)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

# 소스 복사 후 실행 가능한 jar 빌드
COPY src src
RUN ./gradlew clean bootJar copyRuntimeClasspath --no-daemon

# ===== Runtime stage =====
# Java 21과 Playwright가 모두 지원하는 Ubuntu 24.04를 고정한다.
# unpinned Temurin 이미지는 새 Ubuntu로 이동해 Playwright 지원 범위를 벗어날 수 있다.
FROM eclipse-temurin:21-jre-noble AS runtime
WORKDIR /app

# 애플리케이션과 같은 1.55.0 CLI로 정확히 맞는 Chromium 및 Linux 의존성을 설치한다.
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
COPY --from=builder /app/build/runtime-classpath /tmp/runtime-classpath
RUN java -cp "/tmp/runtime-classpath/*" com.microsoft.playwright.CLI install --with-deps chromium \
    && rm -rf /tmp/runtime-classpath /var/lib/apt/lists/*

# plain jar를 껐으므로 build/libs 에는 실행 가능한 jar 하나만 존재
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# 컨테이너에 할당된 메모리에 맞춰 힙 자동 조정 (t2.micro 등 소형 인스턴스 대비)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
