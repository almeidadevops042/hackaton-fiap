# Multi-stage build for Spring Boot + Kotlin
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle.properties* ./

# Download dependencies
RUN gradle dependencies --no-daemon

# Copy source code
COPY src ./src

# Build the application
RUN gradle bootJar --no-daemon

# Final stage - Runtime
FROM openjdk:17-jdk-alpine

WORKDIR /app

# Install wget for health checks
RUN apk --no-cache add wget

# Copy JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"] 