# Stage 1: Build the Maven multi-module EAR and WAR
FROM maven:3.9-eclipse-temurin-17 AS builder

# Tools needed to fetch and import certificates
RUN apt-get update && apt-get install -y curl openssl ca-certificates && rm -rf /var/lib/apt/lists/*

# Trust the certificates presented by the proxy for Maven hosts (demo only)
RUN for h in repo.maven.apache.org repo1.maven.org; do \
      echo | openssl s_client -showcerts -connect $h:443 2>/dev/null \
      | awk -v h=$h '/BEGIN CERT/{n++} n{print > ("/tmp/" h "-" n ".crt")}'; \
    done; \
    for c in /tmp/*.crt; do \
      keytool -importcert -noprompt -trustcacerts -alias "$(basename $c)" \
        -file "$c" -cacerts -storepass changeit || true; \
    done

WORKDIR /app
COPY pom.xml ./
COPY war/pom.xml ./war/
COPY ear/pom.xml ./ear/

# Pre-fetch dependencies
RUN mvn dependency:go-offline -B -DskipTests || true

# Copy source code
COPY war/src ./war/src
COPY ear/src ./ear/src

# Build EAR package
RUN mvn clean package -DskipTests

# Stage 2: Runtime IBM WebSphere Liberty Application Server
FROM icr.io/appcafe/websphere-liberty:full-java17-openj9-ubi

USER root

# Copy custom WebSphere server.xml
COPY server.xml /config/server.xml

# Copy generated EAR to WebSphere Liberty dropins folder
COPY --from=builder /app/ear/target/legacy-cm-ear.ear /config/dropins/

# Ensure correct file permissions for liberty user
RUN chown -R 1001:0 /config/

USER 1001

EXPOSE 8080

# Start IBM WebSphere Liberty in foreground
CMD ["/opt/ibm/wlp/bin/server", "run", "defaultServer"]
