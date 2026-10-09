# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home jobforge
COPY --from=build /workspace/target/jobforge-ai-service-*.jar /app/app.jar
EXPOSE 8090
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
