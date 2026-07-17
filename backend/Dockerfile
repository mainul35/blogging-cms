# --- Build stage -------------------------------------------------------
# Gradle wrapper + build files copied first so dependency resolution is
# cached in its own Docker layer and only re-runs when they actually change,
# not on every source edit.
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S blogcms && adduser -S blogcms -G blogcms \
    && mkdir -p /app/uploads && chown -R blogcms:blogcms /app

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown blogcms:blogcms app.jar

USER blogcms
EXPOSE 8080

# app.upload.dir defaults to ./uploads (relative) -- APP_UPLOAD_DIR here
# repoints it at this absolute path so it's independent of Spring's working
# directory and lines up with the named volume mounted in docker-compose.
ENV APP_UPLOAD_DIR=/app/uploads

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
