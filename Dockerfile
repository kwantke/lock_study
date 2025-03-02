FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# ✅ 8. 빌드된 JAR 파일 복사
COPY build/libs/myapp.jar app.jar

COPY src/main/resources/application-${ENVIRONMENT}.yaml /app/config/application.yaml

# ✅ 9. 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=/app/config/application.yaml"]