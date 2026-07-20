#!/usr/bin/env bash
# Upgrade ctakesicd2015 from HSQLDB 1.8 to 2.7.x using cTAKES 4.0 conversion steps.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_DIR="${1:-${REPO_ROOT}/resources/org/apache/ctakes/dictionary/lookup/fast/ctakesicd2015}"
DB="${DB_DIR}/ctakesicd2015"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HSQL18="${HSQL18:-/tmp/hsqldb-1.8.0.10.jar}"
HSQL234="${HSQL234:-${HOME}/.m2/repository/org/hsqldb/hsqldb/2.3.4/hsqldb-2.3.4.jar}"
HSQL27="${HSQL27:-${HOME}/.m2/repository/org/hsqldb/hsqldb/2.7.3/hsqldb-2.7.3.jar}"

if [[ ! -f "${DB}.script" ]]; then
  echo "Missing ${DB}.script" >&2
  exit 1
fi

if [[ ! -f "$HSQL18" ]]; then
  echo "==> Downloading HSQLDB 1.8.0.10 ..."
  curl -fsSL -o "$HSQL18" \
    "https://repo1.maven.org/maven2/org/hsqldb/hsqldb/1.8.0.10/hsqldb-1.8.0.10.jar"
fi

for jar in "$HSQL234" "$HSQL27"; do
  if [[ ! -f "$jar" ]]; then
    ver="$(basename "$jar" | sed 's/hsqldb-//;s/.jar//')"
    echo "==> Fetching HSQLDB ${ver} via Maven ..."
    (cd "$REPO_ROOT" && mvn -q dependency:get -Dartifact=org.hsqldb:hsqldb:"${ver}")
  fi
done

run_phase() {
  local jar="$1"
  local phase="$2"
  javac -cp "$jar" "${SCRIPT_DIR}/UpgradeIcdDb.java"
  java -cp "${SCRIPT_DIR}:${jar}" UpgradeIcdDb "$DB" "$phase"
}

echo "==> Phase 1: HSQLDB 1.8 SET SCRIPTFORMAT TEXT + SHUTDOWN COMPACT"
run_phase "$HSQL18" 18

echo "==> Phase 2: HSQLDB 2.3.4 SHUTDOWN COMPACT"
run_phase "$HSQL234" 234

echo "==> Phase 3: HSQLDB 2.7.3 SHUTDOWN COMPACT"
run_phase "$HSQL27" 27

version="$(grep '^version=' "${DB}.properties" | cut -d= -f2)"
echo "==> ctakesicd2015 upgraded (version=${version})"
