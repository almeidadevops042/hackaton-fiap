# FIAP X - Microservices Video Processor (Kotlin/Spring Boot)
# Makefile for development and deployment

.PHONY: help build up down logs clean test dev-build dev-up kotlin-build kotlin-test

# Default target
help:
	@echo "FIAP X - Microservices Video Processor (Kotlin/Spring Boot)"
	@echo ""
	@echo "Available targets:"
	@echo "  build        - Build all Docker images"
	@echo "  up           - Start all services"
	@echo "  down         - Stop all services"
	@echo "  logs         - View logs from all services"
	@echo "  logs-follow  - Follow logs from all services"
	@echo "  clean        - Clean up containers, images, and volumes"
	@echo "  test         - Run health checks on all services"
	@echo "  dev-build    - Build services for development"
	@echo "  dev-up       - Start services in development mode"
	@echo "  stats        - Show container stats"
	@echo "  shell-redis  - Open Redis CLI"
	@echo "  kotlin-build - Build all Kotlin services locally"
	@echo "  kotlin-test  - Run tests for all Kotlin services"

# Build all services
build:
	@echo "Building all Docker images..."
	docker compose build

# Start all services
up:
	@echo "Starting all services..."
	docker compose up -d
	@echo "Services started!"
	@echo "Frontend: http://localhost"
	@echo "API Gateway: http://localhost:8080"

# Stop all services
down:
	@echo "Stopping all services..."
	docker compose down

# View logs
logs:
	docker compose logs

# Follow logs
logs-follow:
	docker compose logs -f

# Clean up everything
clean:
	@echo "Cleaning up containers, images, and volumes..."
	docker compose down -v --rmi all --remove-orphans
	docker system prune -f

# Health check all services
test:
	@echo "Running health checks..."
	@echo "Testing API Gateway..."
	@curl -f http://localhost:8080/health || echo "Gateway failed"
	@echo "Testing Upload Service..."
	@curl -f http://localhost:8081/health || echo "Upload failed"
	@echo "Testing Processing Service..."
	@curl -f http://localhost:8082/health || echo "Processing failed"
	@echo "Testing Storage Service..."
	@curl -f http://localhost:8083/health || echo "Storage failed"
	@echo "Testing Notification Service..."
	@curl -f http://localhost:8084/health || echo "Notification failed"
	@echo "All health checks completed!"

# Development build
dev-build:
	@echo "Building for development..."
	docker compose -f docker-compose.yml -f docker-compose.dev.yml build

# Development up
dev-up:
	@echo "Starting development environment..."
	docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# Build Kotlin services locally
kotlin-build:
	@echo "Building Kotlin services locally..."
	@echo "Building Gateway Service..."
	@cd services/gateway && ./gradlew build -x test
	@echo "Building Upload Service..."
	@cd services/upload && ./gradlew build -x test
	@echo "Building Processing Service..."
	@cd services/processing && ./gradlew build -x test
	@echo "Building Storage Service..."
	@cd services/storage && ./gradlew build -x test
	@echo "Building Notification Service..."
	@cd services/notification && ./gradlew build -x test
	@echo "Building Auth Service..."
	@cd services/auth && ./gradlew build -x test
	@echo "All Kotlin services built successfully!"

# Test Kotlin services
kotlin-test:
	@echo "Running tests for Kotlin services..."
	@echo "Testing Gateway Service..."
	@cd services/gateway && ./gradlew test
	@echo "Testing Upload Service..."
	@cd services/upload && ./gradlew test
	@echo "Testing Processing Service..."
	@cd services/processing && ./gradlew test
	@echo "Testing Storage Service..."
	@cd services/storage && ./gradlew test
	@echo "Testing Notification Service..."
	@cd services/notification && ./gradlew test
	@echo "All tests completed!"

# End-to-End Tests
e2e-test:
	@echo "Running End-to-End tests..."
	@./test-e2e.sh

# Performance Tests
performance-test:
	@echo "Running Performance tests..."
	@./test-performance.sh

# All Tests (Unit + E2E + Performance)
all-tests: kotlin-test
	@echo "Unit tests completed!"
	@echo "Note: E2E and Performance tests require services to be running."
	@echo "To run E2E tests: make e2e-test"
	@echo "To run Performance tests: make performance-test"

# All Tests with Services (requires services to be running)
all-tests-with-services: kotlin-test e2e-test performance-test
	@echo "All tests completed!"

# Show container stats
stats:
	docker compose ps
	@echo ""
	docker stats --no-stream

# Redis CLI
shell-redis:
	docker compose exec redis redis-cli

# Individual service management
up-gateway:
	docker compose up -d gateway

up-upload:
	docker compose up -d upload-service

up-processing:
	docker compose up -d processing-service

up-storage:
	docker compose up -d storage-service

up-notification:
	docker compose up -d notification-service

# Logs for individual services
logs-gateway:
	docker compose logs -f gateway

logs-upload:
	docker compose logs -f upload-service

logs-processing:
	docker compose logs -f processing-service

logs-storage:
	docker compose logs -f storage-service

logs-notification:
	docker compose logs -f notification-service

# Scale services
scale-processing:
	docker compose up -d --scale processing-service=3

scale-upload:
	docker compose up -d --scale upload-service=2

# Backup and restore Redis data
backup-redis:
	@echo "Backing up Redis data..."
	docker compose exec redis redis-cli BGSAVE
	docker cp $$(docker compose ps -q redis):/data/dump.rdb ./backup-$$(date +%Y%m%d_%H%M%S).rdb

# Production deployment
deploy-prod:
	@echo "Deploying to production..."
	docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# Stop production
stop-prod:
	docker compose -f docker-compose.yml -f docker-compose.prod.yml down

# Kotlin development commands
kotlin-run-gateway:
	@echo "Running Gateway Service locally..."
	@cd services/gateway && ./gradlew bootRun

kotlin-run-upload:
	@echo "Running Upload Service locally..."
	@cd services/upload && ./gradlew bootRun

kotlin-run-processing:
	@echo "Running Processing Service locally..."
	@cd services/processing && ./gradlew bootRun

kotlin-run-storage:
	@echo "Running Storage Service locally..."
	@cd services/storage && ./gradlew bootRun

kotlin-run-notification:
	@echo "Running Notification Service locally..."
	@cd services/notification && ./gradlew bootRun

# Clean Kotlin builds
kotlin-clean:
	@echo "Cleaning Kotlin builds..."
	@cd services/gateway && ./gradlew clean
	@cd services/upload && ./gradlew clean
	@cd services/processing && ./gradlew clean
	@cd services/storage && ./gradlew clean
	@cd services/notification && ./gradlew clean
	@echo "All Kotlin builds cleaned!" 