FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .

# Make gradlew executable
RUN chmod +x ./gradlew

# Download dependencies
RUN ./gradlew dependencies

# Copy source code
COPY src src

# Build the application
RUN ./gradlew build -x test

# Create runtime image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar
COPY --from=0 /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8085

# Run the application
CMD ["java", "-jar", "app.jar"] 