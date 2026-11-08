#!/usr/bin/env bash
# One-time setup (run once per host, e.g. on the VPS): create the
# ctakes_dictionary Docker volume and populate it with the UMLS sno_rx_16ab
# dictionary, so redeploys never need to re-fetch or re-bake the ~84MB
# dictionary into the image (see docker-compose.yml, build-image.sh).
#
# Usage: ./setup-dictionary-volume.sh [path/to/sno_rx_16ab.zip]
# Default zip: ./sno_rx_16ab.zip at repo root.
# Download from: https://sourceforge.net/projects/ctakesresources/files/sno_rx_16ab.zip
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ZIP="${1:-${REPO_ROOT}/sno_rx_16ab.zip}"
VOLUME=ctakes_dictionary

if [[ ! -f "${ZIP}" ]]; then
  echo "sno_rx_16ab.zip not found: ${ZIP}" >&2
  echo "Download from https://sourceforge.net/projects/ctakesresources/files/sno_rx_16ab.zip" >&2
  echo "and place it at the repo root, or pass the path:" >&2
  echo "  $0 /path/to/sno_rx_16ab.zip" >&2
  exit 1
fi

echo "==> Creating Docker volume '${VOLUME}' ..."
docker volume create "${VOLUME}" >/dev/null

echo "==> Extracting $(basename "${ZIP}") into '${VOLUME}' ..."
docker run --rm \
  -v "${VOLUME}:/dict" \
  -v "$(cd "$(dirname "${ZIP}")" && pwd):/src:ro" \
  alpine sh -c "
    set -e
    apk add --no-cache unzip >/dev/null
    rm -rf /tmp/sno_rx_16ab
    unzip -q -o '/src/$(basename "${ZIP}")' -d /tmp
    test -f /tmp/sno_rx_16ab/sno_rx_16ab.xml
    test -d /tmp/sno_rx_16ab/sno_rx_16ab
    cp /tmp/sno_rx_16ab/sno_rx_16ab.xml /dict/
    rm -rf /dict/sno_rx_16ab
    cp -r /tmp/sno_rx_16ab/sno_rx_16ab /dict/sno_rx_16ab
  "

echo "==> Done. Volume '${VOLUME}' is ready — docker-compose.yml mounts it automatically."
