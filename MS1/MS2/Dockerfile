FROM eclipse-temurin:17-jdk-jammy

# Tools needed to fetch and import certificates
RUN apt-get update && apt-get install -y curl unzip openssl ca-certificates && rm -rf /var/lib/apt/lists/*
 
# Trust the certificates presented by the proxy for Gradle/Maven hosts (demo only)
RUN for h in services.gradle.org downloads.gradle.org plugins.gradle.org repo.maven.apache.org repo1.maven.org; do \
      echo | openssl s_client -showcerts -connect $h:443 2>/dev/null \
      | awk -v h=$h '/BEGIN CERT/{n++} n{print > ("/tmp/" h "-" n ".crt")}'; \
    done; \
    for c in /tmp/*.crt; do \
      keytool -importcert -noprompt -trustcacerts -alias "$(basename $c)" \
        -file "$c" -cacerts -storepass changeit || true; \
    done

# Set working directory
WORKDIR /app

# Copy the entire project
COPY . .

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application
RUN ./gradlew clean build -x test

# Expose port
EXPOSE 8082

# Run the application
# CMD ["java", "-jar", "build/libs/demo2-docker-0.0.1.jar"]

CMD ["java", "-jar", "-Dspring.profiles.active=docker", "build/libs/demo2-docker-0.0.1.jar"]
