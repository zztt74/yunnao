FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml dependency:go-offline
COPY backend backend
RUN mvn -f backend/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 cloudbrain
COPY --from=build /workspace/backend/target/cloud-brain-backend-0.0.1-SNAPSHOT.jar app.jar
USER cloudbrain
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
