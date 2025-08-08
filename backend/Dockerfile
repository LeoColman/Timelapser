FROM eclipse-temurin:20-jdk

# Install FFmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy gradle files first for better layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY backend/build.gradle.kts backend/

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies
RUN ./gradlew backend:dependencies

# Copy the rest of the source code
COPY backend/src backend/src

# Build the application
RUN ./gradlew backend:build

# Create directories for frames and output
RUN mkdir -p frames output


# Command to run the application
CMD ["./gradlew", "backend:run"]