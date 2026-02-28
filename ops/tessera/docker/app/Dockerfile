# --- Build stage ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Cache dependencies
COPY build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY gradlew ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test && \
    mv build/libs/*.jar build/libs/tessera.jar

# --- Runtime stage ---
FROM eclipse-temurin:25-jre

LABEL maintainer="omnisource.live"
LABEL description="Tessera – Spatial Data Integration Platform"

RUN groupadd -r tessera && useradd -r -g tessera tessera

WORKDIR /app
COPY --from=build /app/build/libs/tessera.jar tessera.jar

RUN mkdir -p /var/lib/tessera && chown tessera:tessera /var/lib/tessera
USER tessera

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "tessera.jar"]