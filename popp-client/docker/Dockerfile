# Use the official Eclipse Temurin JRE 21 Alpine-based image
FROM eclipse-temurin:21-jre-noble

ARG COMMIT_HASH
ARG VERSION

LABEL de.gematik.vendor="gematik GmbH" \
      maintainer="software-development@gematik.de" \
      de.gematik.app="Gematik POPP-Client Example" \
      de.gematik.git-repo-name="https://github.com/gematik/popp-sample-code.git" \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION

STOPSIGNAL SIGTERM

RUN apt-get update \
  && apt-get install --no-install-recommends -y curl \
  && rm -rf /var/lib/apt/lists/*

HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD curl -f http://localhost:8081/qrcode/static || exit 1

# Set the working directory inside the container
WORKDIR /app

# Default USERID and GROUPID
ARG USERID=10000
ARG GROUPID=10000

# Run as User (not root)
USER $USERID:$GROUPID

ARG JAR_PATH=popp-client*.jar
COPY --chown=$USERID:$GROUPID ${JAR_PATH} /app/popp-client.jar

# Expose the port that the application runs on
EXPOSE 8081
EXPOSE 9001

CMD ["sh", "-c", "exec java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:9001 -jar popp-client.jar"]
