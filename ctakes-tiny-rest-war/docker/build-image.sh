#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  fi
fi

MODULES="ctakes-icu-handover,ctakes-examples,ctakes-user-resources,ctakes-tiny-rest,ctakes-tiny-rest-war"
DICT_XML="resources/org/apache/ctakes/dictionary/lookup/fast/sno_rx_16ab.xml"
DICT_DB="resources/org/apache/ctakes/dictionary/lookup/fast/sno_rx_16ab"

echo "==> Cleaning Tiny REST stack ..."
"${REPO_ROOT}/scripts/clean-tiny-rest.sh"

# Dictionary is gitignored — install from zip when missing.
if [[ ! -f "${DICT_XML}" || ! -d "${DICT_DB}" ]]; then
  echo "==> sno_rx dictionary missing; installing ..."
  "${REPO_ROOT}/scripts/install-sno-rx-dictionary.sh"
fi

if [[ ! -f "${DICT_XML}" ]]; then
  echo "UMLS dictionary descriptor missing after install:" >&2
  echo "  ${DICT_XML}" >&2
  exit 1
fi
if [[ ! -d "${DICT_DB}" ]]; then
  echo "UMLS dictionary DB missing after install:" >&2
  echo "  ${DICT_DB}/" >&2
  exit 1
fi
if [[ ! -f ctakes-tiny-rest/src/user/resources/TinyRestPipeline.prod.piper ]]; then
  echo "Prod piper missing: ctakes-tiny-rest/src/user/resources/TinyRestPipeline.prod.piper" >&2
  exit 1
fi

echo "==> Building + installing modules (fresh jars into WAR) ..."
# install (not just package) so ctakes-tiny-rest-war embeds rebuilt dependency jars
# from this reactor / local .m2, including HandoverAssembler fixes.
mvn -pl "${MODULES}" install -DskipTests -q

if [[ ! -f ctakes-tiny-rest-war/target/ctakes_tiny_rest.war ]]; then
  echo "WAR not found at ctakes-tiny-rest-war/target/ctakes_tiny_rest.war" >&2
  exit 1
fi

echo "==> Building Docker image ctakes-tiny-rest:latest (prod piper) ..."
docker build -f ctakes-tiny-rest-war/docker/Dockerfile -t ctakes-tiny-rest:latest .

echo "==> Done. Start with UMLS API key:"
echo "  export CTAKES_UMLS_APIKEY=your_uts_api_key"
echo "  docker compose -f ctakes-tiny-rest-war/docker/docker-compose.yml up"
