# Use the official Eclipse Temurin JRE 21 image
FROM eclipse-temurin:21-jre-noble

ARG COMMIT_HASH
ARG VERSION

LABEL maintainer="dein-name@example.com" \
      de.gematik.app="VSDM-Client Example" \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION

STOPSIGNAL SIGTERM

RUN apt-get update \
  && apt-get install --no-install-recommends -y curl \
  && rm -rf /var/lib/apt/lists/*

# Set the working directory inside the container
WORKDIR /app

# Default USERID and GROUPID
ARG USERID=10000
ARG GROUPID=10000

# Run as User (not root)
USER $USERID:$GROUPID

# Kopiert das gebaute JAR-File in den Container (flache Hierarchie durch tmpdocker)
COPY --chown=$USERID:$GROUPID vsdm-client*.jar /app/vsdm-client.jar

# Expose the port that the application runs on
EXPOSE 8080

CMD ["sh", "-c", "exec java -jar vsdm-client.jar"]
