# Stage 1: 빌드
FROM gradle:8.12-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle bootJar -x test --no-daemon

# Stage 2: 실행
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
