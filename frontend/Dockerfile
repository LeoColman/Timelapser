FROM node:18 as build

# Set working directory
WORKDIR /app

# Copy gradle files first for better layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY frontend/build.gradle.kts frontend/

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies
RUN ./gradlew frontend:dependencies

# Copy the rest of the source code
COPY frontend/src frontend/src

# Build the application
RUN ./gradlew frontend:build

# Use Nginx to serve the static files
FROM nginx:alpine

# Copy the built files from the build stage
COPY --from=build /app/frontend/build/distributions /usr/share/nginx/html

# Create a default nginx configuration
RUN echo 'server { \
    listen 80; \
    server_name localhost; \
    \
    location / { \
        root /usr/share/nginx/html; \
        index index.html; \
        try_files $uri $uri/ /index.html; \
    } \
    \
    location /api/ { \
        proxy_pass http://backend:9090/; \
        proxy_set_header Host $host; \
        proxy_set_header X-Real-IP $remote_addr; \
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; \
        proxy_set_header X-Forwarded-Proto $scheme; \
    } \
}' > /etc/nginx/conf.d/default.conf

# Expose the port the app runs on
EXPOSE 80

# Command to run Nginx in the foreground
CMD ["nginx", "-g", "daemon off;"]
