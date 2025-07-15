# Multi-stage build
FROM golang:1.21-alpine AS builder

WORKDIR /app

# Copy go mod files
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main .

# Final stage
FROM alpine:latest

# Install FFmpeg and ca-certificates
RUN apk --no-cache add ffmpeg ca-certificates

WORKDIR /root/

# Copy binary from builder stage
COPY --from=builder /app/main .

# Create necessary directories
RUN mkdir -p processing outputs

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/health || exit 1

# Run the binary
CMD ["./main"] 