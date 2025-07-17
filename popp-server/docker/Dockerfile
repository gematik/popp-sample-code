
# Use the official Eclipse Temurin JDK 21 Alpine-based image
FROM eclipse-temurin:21 AS build

ARG COMMIT_HASH
ARG VERSION

LABEL de.gematik.vendor="gematik GmbH" \
      maintainer="software-development@gematik.de" \
      de.gematik.app="Gematik POPP-Server Example" \
      de.gematik.git-repo-name="...popp-example-impl-global.git" \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION

STOPSIGNAL SIGTERM

HEALTHCHECK CMD curl --fail http://popp-server:8443/actuator/health | grep UP || exit 1

# Default USERID and GROUPID
ARG USERID=10000
ARG GROUPID=10000

# Set the working directory inside the container
WORKDIR /app

# Run as User (not root)
USER $USERID:$GROUPID

COPY --chown=$USERID:$GROUPID popp-server*.jar /app/popp-server.jar

ENV IDENTITIES_LOCATION=/app/identities
ENV SERVER_SSL_KEY_STORE=classpath:mykeystore.p12
ENV SERVER_SSL_KEY_ALIAS=ZertifikatAlias

# COPY identities /app/identities

# Expose the port that the application runs on
EXPOSE 8443

CMD ["sh", "-c", "exec java -jar -Dspring.profiles.active=dev popp-server.jar"]
