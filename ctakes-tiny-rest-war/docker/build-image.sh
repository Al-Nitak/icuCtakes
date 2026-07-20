#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  fi
fi

echo "==> Building WAR ..."
mvn -pl ctakes-tiny-rest,ctakes-icu-handover,ctakes-user-resources,ctakes-tiny-rest-war \
  package -DskipTests -q

if [[ ! -f ctakes-tiny-rest-war/target/ctakes_tiny_rest.war ]]; then
  echo "WAR not found at ctakes-tiny-rest-war/target/ctakes_tiny_rest.war" >&2
  exit 1
fi

if [[ ! -f resources/org/apache/ctakes/dictionary/lookup/fast/sno_rx_16ab_no_umls.xml ]]; then
  echo "Dictionary resources missing. Run:" >&2
  echo "  mvn -pl ctakes-dictionary-lookup-fast process-resources -DskipTests" >&2
  exit 1
fi

echo "==> Building Docker image ctakes-tiny-rest:latest ..."
docker build -f ctakes-tiny-rest-war/docker/Dockerfile -t ctakes-tiny-rest:latest .

echo "==> Done. Start with:"
echo "  docker compose -f ctakes-tiny-rest-war/docker/docker-compose.yml up"
