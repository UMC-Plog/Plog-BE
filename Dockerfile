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

# 서버에서 생성하는 한글 리포트 PDF 글꼴.
# 반드시 TrueType(.ttf) 이어야 한다 — PDFBox 의 서브셋터는 glyf 테이블만 다루기 때문에
# fonts-noto-cjk(NotoSansCJK-Regular.ttc, CFF 기반)를 쓰면 렌더링이
# "OTF fonts do not have a glyf table" 로 전부 실패한다. 서브셋을 끄면 폰트 전체(20MB+)가
# PDF 마다 박히므로 그것도 답이 아니다. NanumGothic 은 TrueType 이라 서브셋이 동작한다.
RUN apt-get update && apt-get install -y --no-install-recommends fonts-nanum \
    && rm -rf /var/lib/apt/lists/*

# plain jar를 껐으므로 build/libs 에는 실행 가능한 jar 하나만 존재
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# 컨테이너에 할당된 메모리에 맞춰 힙 자동 조정 (t2.micro 등 소형 인스턴스 대비)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
