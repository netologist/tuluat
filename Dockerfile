# Stage 1: Build stage
FROM eclipse-temurin:24-jdk AS builder
WORKDIR /build

# Copy Maven descriptor, wrapper scripts, and source code
COPY pom.xml .
COPY mvnw .
COPY .mvn ./.mvn
COPY src ./src

# Grant execute permissions to mvnw and build production JAR
RUN chmod +x ./mvnw && ./mvnw package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:24-jre AS runner
WORKDIR /app

# Create non-root system user for Kubernetes security compliance
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Copy built JAR artifact from builder stage
COPY --from=builder /build/target/k8s-crd-ai-operator-1.0.0-SNAPSHOT.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# Configure JVM options with Java preview features & Virtual Threads
ENTRYPOINT ["java", "--enable-preview", "-Dspring.threads.virtual.enabled=true", "-jar", "app.jar"]
