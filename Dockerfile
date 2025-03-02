FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# ✅ 8. 빌드된 JAR 파일 복사
COPY build/libs/myapp-0.0.1-SNAPSHOT.jar app.jar

# ✅ 9. 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]