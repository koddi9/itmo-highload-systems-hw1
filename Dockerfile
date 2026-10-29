FROM maven:3.10.0-eclipse-temurin-25-noble AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:25.0.4_7-jre-noble
# curl нужен для healthcheck в compose.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
