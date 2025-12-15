# Runtime stage - uses pre-built JAR
FROM eclipse-temurin:21-jre-alpine

# Add non-root user
RUN addgroup -g 1000 vectis && \
    adduser -u 1000 -G vectis -s /bin/sh -D vectis

WORKDIR /app

# Copy the pre-built executable JAR (Spring Boot fat JAR)
COPY target/vectis-client-*-exec.jar app.jar

# Copy connector JARs (sftp, s3) - use wildcard to handle empty dir
COPY connector[s]/ connectors/

# Create directories for data
RUN mkdir -p /data /app/data /app/connectors && \
    chown -R vectis:vectis /data /app

# Switch to non-root user
USER vectis

# Expose ports
# 8080 - REST API
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=8080 -jar app.jar serve"]
