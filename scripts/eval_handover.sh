#!/usr/bin/env bash
# Run handover slot eval: POST notes to REST (or use pre-built JSON), score vs gold.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

EVAL_DIR="${EVAL_DIR:-ctakes-icu-handover/src/test/resources/org/apache/ctakes/icu/eval}"
NOTES_DIR="$EVAL_DIR/notes"
GOLD_DIR="$EVAL_DIR/gold"
OUT="${OUT:-output/ctakes-out/eval_handover}"
BASE="${BASE:-http://127.0.0.1:8081/service/process}"

mkdir -p "$OUT"

if [[ "${SKIP_REST:-}" != "1" ]]; then
  for note in "$NOTES_DIR"/*.txt; do
    stem="$(basename "$note" .txt)"
    echo "POST $stem ..."
    curl -sf -X POST "${BASE}?format=handover" \
      -H "Content-Type: text/plain; charset=utf-8" \
      --data-binary @"$note" \
      -o "$OUT/${stem}.handover.json"
    python3 - "$OUT/${stem}.handover.json" <<'PY'
import json, pathlib, sys
p = pathlib.Path(sys.argv[1])
raw = p.read_text(encoding="utf-8")
pretty = json.dumps(json.loads(raw), indent=2, ensure_ascii=False) + "\n"
p.with_suffix(".handover.pretty.json").write_text(pretty, encoding="utf-8")
PY
  done
fi

python3 scripts/eval_handover_slots.py --dir "$GOLD_DIR" "$OUT"
