# ✅ 1. Java 21 JDK 기반 빌드 스테이지
FROM eclipse-temurin:21-jdk AS build

# ✅ 2. 작업 디렉토리 설정
WORKDIR /app

# ✅ 3. Gradle Wrapper 및 프로젝트 파일 복사 (빌드 캐시 활용을 위해 단계적으로 복사)
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

# ✅ 4. 실행 권한 추가 (Linux 환경 필요, Windows에서는 무시됨)
RUN chmod +x gradlew

# ✅ 5. Gradle 의존성 먼저 다운로드 (빌드 속도 최적화)
RUN ./gradlew dependencies --no-daemon

# ✅ 6. 소스 코드 복사 후 빌드 실행 (테스트 제외)
COPY src src
RUN ./gradlew clean build -x test --no-daemon --stacktrace

# ✅ 7. Java 21 JRE 기반 실행 스테이지
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# ✅ 8. 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# ✅ 9. 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
