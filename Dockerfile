# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 라운드 경계 판정이 LocalDateTime.now()(= JVM 기본 시간대)를 쓰므로 컨테이너 시간대를 KST로 고정한다.
# TZ가 비면 베이스 이미지 기본값인 UTC로 떠서 로컬(compose)과 AWS가 9시간 어긋난다.
# OS tzdata 유무와 무관하게 동작하도록 -Duser.timezone도 함께 준다(JDK 자체 tzdb 사용).
ENV TZ=Asia/Seoul

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/app/app.jar"]
