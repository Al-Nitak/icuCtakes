#!/usr/bin/env bash
# Clean Tiny REST / ICU handover build artifacts so production images pick up
# code and piper changes (avoids stale .m2 / target jars and HSQLDB locks).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${REPO_ROOT}"

MODULES=(
  ctakes-icu-handover
  ctakes-examples
  ctakes-user-resources
  ctakes-tiny-rest
  ctakes-tiny-rest-war
)
MODULE_CSV="$(IFS=,; echo "${MODULES[*]}")"

echo "==> Removing HSQLDB lock files ..."
find resources/org/apache/ctakes/dictionary/lookup/fast \
  -name '*.lck' -print -delete 2>/dev/null || true

echo "==> Syncing TinyRestPipeline.piper copies from canonical source ..."
# Note: ctakes-tiny-rest-war/resources is a symlink to ../resources — do not write pipers there.
CANONICAL="ctakes-tiny-rest/src/user/resources/TinyRestPipeline.piper"
if [[ -f "${CANONICAL}" ]]; then
  cp "${CANONICAL}" TinyRestPipeline.piper
  cp "${CANONICAL}" ctakes-tiny-rest-war/TinyRestPipeline.piper
  # Classpath copy — FileLocator prefers this over the filesystem piper.
  cp "${CANONICAL}" ctakes-examples/src/main/resources/TinyRestPipeline.piper
fi

PROD_CANONICAL="ctakes-tiny-rest/src/user/resources/TinyRestPipeline.prod.piper"
if [[ -f "${PROD_CANONICAL}" ]]; then
  cp "${PROD_CANONICAL}" ctakes-tiny-rest-war/TinyRestPipeline.prod.piper
fi

echo "==> Maven clean (${MODULE_CSV}) ..."
mvn -pl "${MODULE_CSV}" clean -q

echo "==> Clean complete."
