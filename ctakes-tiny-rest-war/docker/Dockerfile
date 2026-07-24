# Production image: ICU handover + UMLS sno_rx_16ab (TinyRestPipeline.prod.piper only).
# Build the WAR on the host first:  ./ctakes-tiny-rest-war/docker/build-image.sh
FROM amazoncorretto:17

ARG TOMCAT_VERSION=10.1.57

ENV CATALINA_HOME=/opt/tomcat
ENV PATH="${CATALINA_HOME}/bin:${PATH}"
# Prod piper only. Pass UMLS key at runtime: -e CTAKES_UMLS_APIKEY=...
ENV CTAKES_REST_PIPER=TinyRestPipeline.prod.piper
ENV JAVA_OPTS="-Xmx6g -Djava.awt.headless=true -Dctakes.rest.piper=TinyRestPipeline.prod.piper"

# AL2023 already has curl-minimal; installing full curl conflicts with it.
RUN yum install -y ca-certificates tar gzip unzip \
    && yum clean all \
    && curl -fsSL "https://archive.apache.org/dist/tomcat/tomcat-10/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" \
       | tar -xz -C /opt \
    && mv "/opt/apache-tomcat-${TOMCAT_VERSION}" "${CATALINA_HOME}" \
    && rm -rf "${CATALINA_HOME}/webapps/docs" "${CATALINA_HOME}/webapps/examples" \
             "${CATALINA_HOME}/webapps/host-manager" "${CATALINA_HOME}/webapps/manager" \
             "${CATALINA_HOME}/webapps/ROOT"

WORKDIR ${CATALINA_HOME}

# Deploy at context root so endpoints are /service/process.
# Must remove stock webapps/ROOT/ first (done above), or Tomcat serves that
# directory and ignores ROOT.war — which caused /service/process 404s.
COPY ctakes-tiny-rest-war/target/ctakes_tiny_rest.war webapps/ROOT.war

# Prod piper only (no local TinyRestPipeline.piper). Dictionary under resources/.
COPY ctakes-tiny-rest/src/user/resources/TinyRestPipeline.prod.piper ./
COPY resources/ ./resources/

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=3 \
  CMD curl -sf -X POST "http://localhost:8080/service/process?format=cui" -d "test" || exit 1

CMD ["catalina.sh", "run"]
