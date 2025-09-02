# -------- Build stage --------
FROM eclipse-temurin:20-jdk AS builder

# Set working directory for the build
WORKDIR /workspace

# Copy Gradle wrapper and top-level build files first for better caching
RUN ls
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts ./
COPY build.gradle.kts ./
COPY backend/build.gradle.kts ./backend/

# Ensure Gradle wrapper is executable
RUN chmod +x ./gradlew

# Pre-fetch backend dependencies to leverage Docker layer caching (non-fatal if no cache)
RUN ./gradlew --no-daemon backend:dependencies || true

# Copy the backend sources
COPY backend ./backend

# Build an application distribution without running tests (force rebuild to avoid stale cache)
RUN ./gradlew --no-daemon clean backend:installDist -x test --no-build-cache --rerun-tasks --refresh-dependencies

# -------- Runtime stage --------
FROM eclipse-temurin:20-jre

# Install FFmpeg (runtime dependency)
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

# Set working directory inside the container
WORKDIR /app

# Copy the built distribution from the builder stage
COPY --from=builder /workspace/backend/build/install/backend /app

# Create directories for frames and output (also mounted via compose)
RUN mkdir -p /app/frames /app/output

# Useful default timezone (can be overridden via env)
ENV TZ=UTC

# Default command to run the application
CMD ["/app/bin/backend"]
