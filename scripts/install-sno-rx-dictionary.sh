#!/usr/bin/env bash
# Install sno_rx_16ab HSQLDB + descriptors under
#   resources/org/apache/ctakes/dictionary/lookup/fast/
#
# Usage:
#   ./scripts/install-sno-rx-dictionary.sh [path/to/sno_rx_16ab.zip]
# Default zip: ./sno_rx_16ab.zip at repo root.
#
# Also writes sno_rx_16ab_no_umls.xml (local/dev; skips UTS license check).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${REPO_ROOT}/resources/org/apache/ctakes/dictionary/lookup/fast"
ZIP="${1:-${REPO_ROOT}/sno_rx_16ab.zip}"

if [[ ! -f "${ZIP}" ]]; then
  echo "sno_rx_16ab.zip not found: ${ZIP}" >&2
  echo "Download from https://sourceforge.net/projects/ctakesresources/files/sno_rx_16ab.zip" >&2
  echo "and place it at the repo root, or pass the path:" >&2
  echo "  $0 /path/to/sno_rx_16ab.zip" >&2
  exit 1
fi

echo "==> Installing sno_rx_16ab from ${ZIP} ..."
tmpdir="$(mktemp -d)"
trap 'rm -rf "${tmpdir}"' EXIT
unzip -q "${ZIP}" -d "${tmpdir}"

if [[ ! -f "${tmpdir}/sno_rx_16ab/sno_rx_16ab.xml" ]]; then
  echo "Unexpected zip layout: missing sno_rx_16ab/sno_rx_16ab.xml" >&2
  exit 1
fi
if [[ ! -d "${tmpdir}/sno_rx_16ab/sno_rx_16ab" ]]; then
  echo "Unexpected zip layout: missing sno_rx_16ab/sno_rx_16ab/ DB dir" >&2
  exit 1
fi

mkdir -p "${DEST}"
cp "${tmpdir}/sno_rx_16ab/sno_rx_16ab.xml" "${DEST}/sno_rx_16ab.xml"
rm -rf "${DEST}/sno_rx_16ab"
mv "${tmpdir}/sno_rx_16ab/sno_rx_16ab" "${DEST}/sno_rx_16ab"

# Local/dev descriptor: same DB, no NIH UTS license check.
cat > "${DEST}/sno_rx_16ab_no_umls.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!-- Local-only dictionary descriptor: skips UMLS license checks.
     Uses the same sno_rx_16ab HSQLDB as the default dictionary. -->
<lookupSpecification>
<dictionaries>
   <dictionary>
      <name>sno_rx_16abTerms</name>
      <implementationName>org.apache.ctakes.dictionary.lookup2.dictionary.JdbcRareWordDictionary</implementationName>
      <properties>
         <property key="jdbcDriver" value="org.hsqldb.jdbcDriver"/>
         <property key="jdbcUrl" value="jdbc:hsqldb:file:resources/org/apache/ctakes/dictionary/lookup/fast/sno_rx_16ab/sno_rx_16ab"/>
         <property key="jdbcUser" value="sa"/>
         <property key="jdbcPass" value=""/>
         <property key="rareWordTable" value="cui_terms"/>
      </properties>
   </dictionary>
</dictionaries>

<conceptFactories>
   <conceptFactory>
      <name>sno_rx_16abConcepts</name>
      <implementationName>org.apache.ctakes.dictionary.lookup2.concept.JdbcConceptFactory</implementationName>
      <properties>
         <property key="jdbcDriver" value="org.hsqldb.jdbcDriver"/>
         <property key="jdbcUrl" value="jdbc:hsqldb:file:resources/org/apache/ctakes/dictionary/lookup/fast/sno_rx_16ab/sno_rx_16ab"/>
         <property key="jdbcUser" value="sa"/>
         <property key="jdbcPass" value=""/>
         <property key="tuiTable" value="tui"/>
         <property key="prefTermTable" value="prefTerm"/>
         <property key="rxnormTable" value="long"/>
         <property key="snomedct_usTable" value="long"/>
      </properties>
   </conceptFactory>
</conceptFactories>

<dictionaryConceptPairs>
   <dictionaryConceptPair>
      <name>sno_rx_16abPair</name>
      <dictionaryName>sno_rx_16abTerms</dictionaryName>
      <conceptFactoryName>sno_rx_16abConcepts</conceptFactoryName>
   </dictionaryConceptPair>
</dictionaryConceptPairs>

<rareWordConsumer>
   <name>Term Consumer</name>
   <implementationName>org.apache.ctakes.dictionary.lookup2.consumer.DefaultTermConsumer</implementationName>
   <properties>
         <property key="codingScheme" value="sno_rx_16ab"/>
   </properties>
</rareWordConsumer>

</lookupSpecification>
EOF

# Drop stale HSQLDB locks
find "${DEST}/sno_rx_16ab" -name '*.lck' -delete 2>/dev/null || true

echo "==> Installed:"
echo "  ${DEST}/sno_rx_16ab.xml"
echo "  ${DEST}/sno_rx_16ab_no_umls.xml"
echo "  ${DEST}/sno_rx_16ab/ (HSQLDB)"
