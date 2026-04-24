FROM gradle:8.10-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY gradlew ./gradlew
COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
