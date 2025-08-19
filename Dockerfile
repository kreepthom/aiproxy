# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY aiproxy-common/pom.xml aiproxy-common/
COPY aiproxy-auth/pom.xml aiproxy-auth/
COPY aiproxy-core/pom.xml aiproxy-core/
COPY aiproxy-admin/pom.xml aiproxy-admin/
COPY aiproxy-api/pom.xml aiproxy-api/

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY aiproxy-common/src aiproxy-common/src
COPY aiproxy-auth/src aiproxy-auth/src
COPY aiproxy-core/src aiproxy-core/src
COPY aiproxy-admin/src aiproxy-admin/src
COPY aiproxy-api/src aiproxy-api/src

# Build the application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy the built JAR file
COPY --from=builder /app/aiproxy-api/target/*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]