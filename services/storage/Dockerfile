FROM openjdk:17-jdk-alpine AS build

WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY gradlew ./

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build the application
RUN ./gradlew build --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create directories
RUN mkdir -p uploads outputs

# Copy the built jar
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8083

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"] 