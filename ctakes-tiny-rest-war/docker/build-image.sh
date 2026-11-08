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

echo "==> Cleaning Tiny REST stack ..."
"${REPO_ROOT}/scripts/clean-tiny-rest.sh"

# The UMLS sno_rx_16ab dictionary is NOT baked into the image (see
# .dockerignore) — it's mounted at runtime from the ctakes_dictionary Docker
# volume, set up once with setup-dictionary-volume.sh.
#
# The clean above also wipes resources/org/apache/ctakes/dictionary (parent
# pom clean filesets). The image doesn't need it, but the local dev pipers
# (sno_rx_16ab_no_umls.xml) do — put it back from the zip when we have one.
DICT_DB="resources/org/apache/ctakes/dictionary/lookup/fast/sno_rx_16ab"
if [[ ! -d "${DICT_DB}" && -f "${REPO_ROOT}/sno_rx_16ab.zip" ]]; then
  echo "==> Reinstalling local sno_rx dictionary for dev pipers ..."
  "${REPO_ROOT}/scripts/install-sno-rx-dictionary.sh"
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

echo "==> Building Docker image ghcr.io/al-nitak/ctakes-tiny-rest:latest (prod piper) ..."
# Same name docker-compose.yml pulls from GHCR, so a local build is used as-is.
docker build -f ctakes-tiny-rest-war/docker/Dockerfile \
  -t ctakes-tiny-rest:latest -t ghcr.io/al-nitak/ctakes-tiny-rest:latest .

echo "==> Done. Start with UMLS API key:"
echo "  export CTAKES_UMLS_APIKEY=your_uts_api_key"
echo "  docker compose -f ctakes-tiny-rest-war/docker/docker-compose.yml up"
