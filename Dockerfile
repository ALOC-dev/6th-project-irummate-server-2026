# ---------- 1단계: 빌드 ----------
FROM gradle:8.10-jdk21 AS builder
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew clean bootJar --no-daemon -x test

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=builder /app/build/libs/*.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]