#!/usr/bin/env bash
# Hit every Tiny REST formatter; write raw + pretty sidecars + tiny HTML link pages.
# HTML pages intentionally do NOT embed payloads — Chrome hangs on long <pre> lines.
set -euo pipefail

cd /Users/albiruni/Alnitak/medical/ctakes
NOTE="${NOTE:-output/ctakes-in/grammer_fixed.txt}"
OUT="${OUT:-output/ctakes-out/explore/07_rest_all_formats}"
BASE="${BASE:-http://127.0.0.1:8081/service/process}"
mkdir -p "$OUT"

# Write pretty sidecar(s) + a small HTML page of links only (no embedded body).
to_html() {
  local title="$1"
  local src="$2"
  local dest="$3"
  python3 - "$title" "$src" "$dest" <<'PY'
import html, json, pathlib, sys
from xml.dom import minidom

title, src, dest = sys.argv[1:4]
src_path = pathlib.Path(src)
raw = src_path.read_text(encoding="utf-8", errors="replace")
raw_name = src_path.name
suffix = src_path.suffix.lower()
bytes_n = len(raw.encode("utf-8"))

links = [f'<li><a href="{html.escape(raw_name)}"><code>{html.escape(raw_name)}</code></a> (raw, {bytes_n:,} bytes)</li>']
pretty_name = None
mode = "raw"

if suffix == ".json":
    try:
        pretty = json.dumps(json.loads(raw), indent=2, ensure_ascii=False) + "\n"
        pretty_path = src_path.with_name(src_path.stem + ".pretty.json")
        pretty_path.write_text(pretty, encoding="utf-8")
        pretty_name = pretty_path.name
        mode = "pretty-json"
        links.append(
            f'<li><a href="{html.escape(pretty_name)}"><code>{html.escape(pretty_name)}</code></a> '
            f'(pretty JSON, {pretty_path.stat().st_size:,} bytes) — open this in Chrome</li>'
        )
    except Exception as e:
        links.append(f"<li>pretty-json failed: {html.escape(str(e))}</li>")
elif suffix in {".xmi", ".xml"}:
    try:
        pretty = minidom.parseString(raw.encode("utf-8")).toprettyxml(indent="  ")
        pretty = "\n".join(line for line in pretty.splitlines() if line.strip()) + "\n"
        pretty_path = src_path.with_name(src_path.stem + ".pretty.xmi")
        pretty_path.write_text(pretty, encoding="utf-8")
        pretty_name = pretty_path.name
        mode = "pretty-xml"
        links.append(
            f'<li><a href="{html.escape(pretty_name)}"><code>{html.escape(pretty_name)}</code></a> '
            f'(pretty XML, {pretty_path.stat().st_size:,} bytes)</li>'
        )
    except Exception as e:
        links.append(f"<li>pretty-xml failed: {html.escape(str(e))}</li>")

# Tiny safe snippet: first 12 short lines only (hard-wrapped).
snippet_src = pretty_name and src_path.with_name(pretty_name) or src_path
try:
    text = snippet_src.read_text(encoding="utf-8", errors="replace")
except Exception:
    text = raw
lines = []
for line in text.splitlines()[:12]:
    if len(line) > 120:
        line = line[:117] + "..."
    lines.append(line)
snippet = html.escape("\n".join(lines) + ("\n…" if text.count("\n") > 12 else ""))

pathlib.Path(dest).write_text(
    f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>{html.escape(title)}</title>
<style>
  body {{ font: 15px/1.5 system-ui, sans-serif; margin: 24px; background: #f6f8fb; color: #1b2430; max-width: 720px; }}
  a {{ color: #0b5fff; }}
  .meta {{ color: #5a6a7a; font-size: 13px; }}
  ul {{ padding-left: 1.2rem; }}
  pre {{ background: #0f1419; color: #c9d4e0; padding: 12px; border-radius: 8px;
         font: 12px/1.4 ui-monospace, Menlo, monospace; overflow: auto;
         white-space: pre-wrap; word-break: break-word; max-height: 240px; }}
  .warn {{ background: #fff8e6; border: 1px solid #f0d78c; padding: 10px 12px; border-radius: 8px; }}
</style>
</head>
<body>
<p><a href="index.html">← all formats</a></p>
<h1>{html.escape(title)}</h1>
<p class="meta">mode={html.escape(mode)}</p>
<div class="warn">Full payloads are <strong>not</strong> embedded here (Chrome hangs on large/long-line HTML).
Open the pretty/raw file links below instead.</div>
<ul>
{''.join(links)}
</ul>
<h2>Tiny snippet</h2>
<pre>{snippet}</pre>
</body>
</html>
""",
    encoding="utf-8",
)
print(f"HTML {dest} (links-only, {mode})")
PY
}

declare -a HTML_ITEMS=()

for fmt in fhir pretty property umls cui xmi handover map; do
  case "$fmt" in
    fhir|umls|handover|map) ext=json ;;
    xmi) ext=xmi ;;
    *) ext=txt ;;
  esac
  raw="$OUT/process_${fmt}.${ext}"
  html_out="$OUT/process_${fmt}.html"
  echo "=== $fmt ==="
  code=$(curl -sS -m 300 -o "$raw" -w "%{http_code}" \
    -X POST "$BASE?format=${fmt}" \
    -H 'Content-Type: text/plain; charset=utf-8' \
    --data-binary @"$NOTE")
  bytes=$(wc -c < "$raw" | tr -d ' ')
  echo "HTTP $code  $bytes bytes"
  to_html "format=${fmt}" "$raw" "$html_out"
  HTML_ITEMS+=("$fmt|process_${fmt}.${ext}|process_${fmt}.html|$code|$bytes")
done

raw="$OUT/process_default.json"
html_out="$OUT/process_default.html"
echo "=== default ==="
code=$(curl -sS -m 300 -o "$raw" -w "%{http_code}" \
  -X POST "$BASE" \
  -H 'Content-Type: text/plain; charset=utf-8' \
  --data-binary @"$NOTE")
bytes=$(wc -c < "$raw" | tr -d ' ')
echo "HTTP $code  $bytes bytes"
to_html "format=default (fhir)" "$raw" "$html_out"
HTML_ITEMS+=("default|process_default.json|process_default.html|$code|$bytes")

python3 - "$OUT" "${HTML_ITEMS[@]}" <<'PY'
import html, pathlib, sys
out = pathlib.Path(sys.argv[1])
rows = []
for item in sys.argv[2:]:
    fmt, raw_name, html_name, code, nbytes = item.split("|", 4)
    ok = code == "200" and int(nbytes) > 0
    status = "ok" if ok else "fail"
    stem = pathlib.Path(raw_name).stem
    suffix = pathlib.Path(raw_name).suffix
    pretty = None
    if suffix == ".json":
        p = out / f"{stem}.pretty.json"
        if p.is_file():
            pretty = p.name
    elif suffix == ".xmi":
        p = out / f"{stem}.pretty.xmi"
        if p.is_file():
            pretty = p.name
    pretty_cell = f"<a href='{html.escape(pretty)}'>pretty</a>" if pretty else "—"
    rows.append(
        f"<tr class='{status}'>"
        f"<td>{html.escape(fmt)}</td>"
        f"<td>{html.escape(code)}</td>"
        f"<td>{html.escape(nbytes)}</td>"
        f"<td><a href='{html.escape(raw_name)}'>raw</a></td>"
        f"<td>{pretty_cell}</td>"
        f"<td><a href='{html.escape(html_name)}'>info</a></td>"
        f"</tr>"
    )
(out / "index.html").write_text(
    f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>cTAKES REST formatters</title>
<style>
  body {{ font: 15px/1.5 system-ui, sans-serif; margin: 24px; background: #f6f8fb; color: #1b2430; }}
  h1 {{ margin: 0 0 8px; }}
  p {{ color: #5a6a7a; }}
  table {{ border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden;
           box-shadow: 0 1px 3px rgba(0,0,0,.08); }}
  th, td {{ padding: 10px 14px; border-bottom: 1px solid #e6ebf2; text-align: left; }}
  th {{ background: #eef3f9; }}
  tr.fail td {{ background: #fff1f0; }}
  a {{ color: #0b5fff; }}
  .warn {{ background: #fff8e6; border: 1px solid #f0d78c; padding: 10px 12px; border-radius: 8px; max-width: 640px; }}
</style>
</head>
<body>
<h1>cTAKES REST formatters</h1>
<p class="warn">Open <strong>pretty</strong> or <strong>raw</strong> files — do not open large JSON/XMI inside an HTML <code>&lt;pre&gt;</code> (Chrome will hang).</p>
<table>
  <thead><tr><th>Format</th><th>HTTP</th><th>Bytes</th><th>Raw</th><th>Pretty</th><th>Info</th></tr></thead>
  <tbody>
    {''.join(rows)}
  </tbody>
</table>
</body>
</html>
""",
    encoding="utf-8",
)
print(f"Wrote {out / 'index.html'}")
PY

echo
echo "Done. Prefer: $OUT/index.html → click pretty/raw (not embedding)."
echo "Or open JSON directly, e.g. open $OUT/process_handover.pretty.json"
