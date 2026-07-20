#!/usr/bin/env bash
# Install ctakesicd2015 HSQLDB under resources/org/apache/ctakes/dictionary/lookup/fast/
#
# Usage:
#   ./scripts/install-icd-dictionary.sh /path/to/ctakesicd2015.zip
#   ./scripts/install-icd-dictionary.sh /path/to/unzipped/ctakesicd2015-folder
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="${REPO_ROOT}/resources/org/apache/ctakes/dictionary/lookup/fast/ctakesicd2015"
MARKER="${TARGET}/ctakesicd2015.script"

if [[ $# -ge 1 ]]; then
  SRC="$1"
elif [[ -d "${REPO_ROOT}/scripts/ctakesicd2015" ]]; then
  SRC="${REPO_ROOT}/scripts/ctakesicd2015"
  echo "==> No path given; using ${SRC}"
else
  echo "Usage: $0 [ctakesicd2015.zip-or-directory]" >&2
  echo "  Default: scripts/ctakesicd2015/ if present" >&2
  exit 1
fi
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

if [[ -f "$SRC" && "$SRC" == *.zip ]]; then
  echo "==> Unzipping $SRC"
  unzip -q "$SRC" -d "$WORKDIR"
  SRC="$WORKDIR"
fi

# Find ctakesicd2015.script inside the tree.
SCRIPT="$(find "$SRC" -name 'ctakesicd2015.script' -print -quit 2>/dev/null || true)"
if [[ -z "$SCRIPT" ]]; then
  echo "Could not find ctakesicd2015.script under $SRC" >&2
  echo "Expected layout: .../ctakesicd2015/ctakesicd2015.script" >&2
  exit 1
fi

DB_DIR="$(dirname "$SCRIPT")"
echo "==> Installing from ${DB_DIR}"
rm -rf "$TARGET"
mkdir -p "$(dirname "$TARGET")"
cp -R "$DB_DIR" "$TARGET"

if [[ ! -f "$MARKER" ]]; then
  echo "Install failed: missing $MARKER" >&2
  exit 1
fi

# Optional: copy official lookup xml if present beside the db.
OFFICIAL_XML="$(find "$SRC" -name 'ctakesicd2015.xml' -print -quit 2>/dev/null || true)"
if [[ -n "$OFFICIAL_XML" ]]; then
  cp "$OFFICIAL_XML" "${REPO_ROOT}/resources/org/apache/ctakes/dictionary/lookup/fast/ctakesicd2015.xml"
  echo "==> Also copied official descriptor -> resources/.../ctakesicd2015.xml"
fi

echo "==> Upgrading HSQLDB 1.8 -> 2.7 for cTAKES ..."
"${REPO_ROOT}/scripts/upgrade-icd-dictionary.sh" "$TARGET"

echo "==> ctakesicd2015 installed."
echo "    Test with:"
echo "      mvn -pl ctakes-examples exec:exec -DrunExploreIcdDictionary \\"
echo "        -Dexplore.input=output/ctakes-in/sample_note.txt \\"
echo "        -Dexplore.output=output/ctakes-out/icd_test"
