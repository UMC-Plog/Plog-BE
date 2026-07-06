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
RUN ./gradlew clean bootJar --no-daemon

# ===== Runtime stage =====
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# plain jar를 껐으므로 build/libs 에는 실행 가능한 jar 하나만 존재
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# 컨테이너에 할당된 메모리에 맞춰 힙 자동 조정 (t2.micro 등 소형 인스턴스 대비)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
