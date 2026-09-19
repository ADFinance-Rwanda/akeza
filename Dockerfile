# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --create-home app

WORKDIR /app
COPY --from=build /app/target/multi-tenant-management-0.0.1-SNAPSHOT.jar app.jar
RUN chown app:app app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=8s --start-period=60s --retries=5 \
    CMD curl -fsS --max-time 5 http://localhost:8080/ready || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
