# Runtime image for ctakes-tiny-rest (ICU handover + no-UMLS dictionary).
# Build the WAR on the host first:  ./ctakes-tiny-rest-war/docker/build-image.sh
FROM eclipse-temurin:18-jre-jammy

ARG TOMCAT_VERSION=10.1.26

ENV CATALINA_HOME=/opt/tomcat
ENV PATH="${CATALINA_HOME}/bin:${PATH}"
ENV JAVA_OPTS="-Xmx6g -Djava.awt.headless=true"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates unzip \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL "https://archive.apache.org/dist/tomcat/tomcat-10/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" \
       | tar -xz -C /opt \
    && mv "/opt/apache-tomcat-${TOMCAT_VERSION}" "${CATALINA_HOME}" \
    && rm -rf "${CATALINA_HOME}/webapps/docs" "${CATALINA_HOME}/webapps/examples" \
             "${CATALINA_HOME}/webapps/host-manager" "${CATALINA_HOME}/webapps/manager"

WORKDIR ${CATALINA_HOME}

# Deploy at context root so endpoints are /service/process
COPY ctakes-tiny-rest-war/target/ctakes_tiny_rest.war webapps/ROOT.war

# Pipeline + dictionary are resolved relative to Tomcat's working directory.
COPY ctakes-tiny-rest/src/user/resources/TinyRestPipeline.piper ./
COPY resources/ ./resources/

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
  CMD curl -sf -X POST "http://localhost:8080/service/process?format=cui" -d "test" || exit 1

CMD ["catalina.sh", "run"]
