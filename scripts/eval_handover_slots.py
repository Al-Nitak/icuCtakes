#!/usr/bin/env python3
"""Score handover JSON against gold slot expectations.

Usage:
  python3 scripts/eval_handover_slots.py GOLD.json HANDOVER.json
  python3 scripts/eval_handover_slots.py --dir ctakes-icu-handover/src/test/resources/org/apache/ctakes/icu/eval/gold OUT/

Gold schema (per file):
{
  "antibiotics": [{"text": "vancomycin", "route": "PO"}],
  "cultures": [{"organism": "Pseudomonas", "site": "blood", "sensContains": ["sensitive"]}],
  "imaging": [{"modality": "CT", "mustFind": ["ischemic"], "mustNotFind": ["UTI"]}],
  "labs": [{"name": "creatinine", "value": "3"}],
  "medications": [{"text": "tramadol", "drugClass": "analgesic", "dose": "50 mg", "frequency": "q8h"}]
}
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


def norm(s: str | None) -> str:
    return (s or "").strip().lower()


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def antibiotics(doc: dict) -> list[dict]:
    return (doc.get("access") or {}).get("antibiotics") or []


def medications(doc: dict) -> list[dict]:
    return (doc.get("access") or {}).get("medications") or []


def cultures(doc: dict) -> list[dict]:
    return ((doc.get("systems") or {}).get("hematology") or {}).get("cultures") or []


def imaging(doc: dict) -> list[dict]:
    return doc.get("imaging") or []


def labs(doc: dict) -> list[dict]:
    return ((doc.get("systems") or {}).get("labs") or [])


def score_antibiotics(gold: list[dict], actual: list[dict]) -> tuple[int, int, int, list[str]]:
    tp = fp = fn = 0
    errors: list[str] = []
    matched = set()
    for g in gold:
        want_text = norm(g.get("text"))
        want_route = g.get("route")
        hit = None
        best = None
        for i, a in enumerate(actual):
            if i in matched:
                continue
            if want_text in norm(a.get("text")) or want_text in norm(a.get("preferredText")):
                if want_route and norm(a.get("route")) == norm(want_route):
                    hit = (i, a)
                    break
                if best is None:
                    best = (i, a)
        if hit is None:
            hit = best
        if hit is None:
            fn += 1
            errors.append(f"antibiotic miss: {g}")
            continue
        matched.add(hit[0])
        tp += 1
        if want_route:
            ar = norm(hit[1].get("route"))
            wr = norm(want_route)
            if ar != wr and not (wr == "po" and ar in {"po", "oral"}):
                errors.append(f"antibiotic route: expected {want_route}, got {hit[1].get('route')}")
    fp += max(0, len(actual) - len(matched))
    return tp, fp, fn, errors


def score_cultures(gold: list[dict], actual: list[dict]) -> tuple[int, int, int, list[str]]:
    tp = fp = fn = 0
    errors: list[str] = []
    matched = set()
    for g in gold:
        want_org = norm(g.get("organism"))
        hit = None
        for i, a in enumerate(actual):
            if i in matched:
                continue
            org = norm(a.get("organism") or a.get("preferredText") or a.get("text"))
            if want_org and want_org in org:
                hit = (i, a)
                break
        if hit is None:
            fn += 1
            errors.append(f"culture miss: {g}")
            continue
        matched.add(hit[0])
        tp += 1
        a = hit[1]
        if g.get("site"):
            site = norm(a.get("site"))
            if g["site"] not in site:
                errors.append(f"culture site: expected {g['site']}, got {a.get('site')}")
        for token in g.get("sensContains") or []:
            sens = " ".join(a.get("sensitivities") or []).lower()
            if token.lower() not in sens:
                errors.append(f"culture sens: expected contains {token}, got {a.get('sensitivities')}")
    fp += max(0, len(actual) - len(matched))
    return tp, fp, fn, errors


def score_imaging(gold: list[dict], actual: list[dict]) -> tuple[int, int, int, list[str]]:
    tp = fp = fn = 0
    errors: list[str] = []
    matched = set()
    for g in gold:
        want_mod = norm(g.get("modality"))
        hit = None
        for i, a in enumerate(actual):
            if i in matched:
                continue
            if norm(a.get("modality")) == want_mod or want_mod in norm(a.get("modality")):
                hit = (i, a)
                break
        if hit is None:
            fn += 1
            errors.append(f"imaging miss modality={g.get('modality')}")
            continue
        matched.add(hit[0])
        tp += 1
        findings = " ".join(
            (f.get("text") or "") for f in (hit[1].get("findings") or [])
        ).lower()
        for token in g.get("mustFind") or []:
            proc = norm(hit[1].get("procedureText"))
            if token.lower() not in findings and token.lower() not in proc:
                errors.append(f"imaging mustFind {token!r} missing in {findings[:80]!r}")
        for token in g.get("mustNotFind") or []:
            if token.lower() in findings:
                errors.append(f"imaging mustNotFind {token!r} present in findings")
    fp += max(0, len(actual) - len(matched))
    return tp, fp, fn, errors


def score_labs(gold: list[dict], actual: list[dict]) -> tuple[int, int, int, list[str]]:
    tp = fp = fn = 0
    errors: list[str] = []
    matched = set()
    for g in gold:
        want_name = norm(g.get("name"))
        want_val = str(g.get("value", "")).strip()
        hit = None
        for i, a in enumerate(actual):
            if i in matched:
                continue
            name = norm(a.get("name") or a.get("preferredText") or a.get("text"))
            if want_name in name or name in want_name:
                hit = (i, a)
                break
            pref = norm(a.get("preferredText"))
            if want_name == "crp" and ("crp" in name or "c-reactive" in pref):
                hit = (i, a)
                break
        if hit is None:
            fn += 1
            errors.append(f"lab miss: {g}")
            continue
        matched.add(hit[0])
        tp += 1
        val = str(hit[1].get("value") or "").strip()
        if want_val and val != want_val:
            errors.append(f"lab value: expected {want_val}, got {val}")
    fp += max(0, len(actual) - len(matched))
    return tp, fp, fn, errors


def score_medications(gold: list[dict], actual: list[dict]) -> tuple[int, int, int, list[str]]:
    """Score access.medications; FP only counted for unmatched gold misses (recall-focused)."""
    tp = fp = fn = 0
    errors: list[str] = []
    matched = set()
    for g in gold:
        want_text = norm(g.get("text"))
        hit = None
        for i, a in enumerate(actual):
            if i in matched:
                continue
            hay = norm(a.get("text")) + " " + norm(a.get("preferredText"))
            if want_text and want_text in hay:
                hit = (i, a)
                break
        if hit is None:
            fn += 1
            errors.append(f"medication miss: {g}")
            continue
        matched.add(hit[0])
        tp += 1
        a = hit[1]
        if g.get("drugClass") and norm(a.get("drugClass")) != norm(g.get("drugClass")):
            errors.append(
                f"medication class: {g.get('text')} expected {g.get('drugClass')}, got {a.get('drugClass')}"
            )
        if g.get("dose"):
            dose = norm(a.get("dose") or a.get("strength"))
            if norm(g["dose"]) not in dose:
                errors.append(f"medication dose: {g.get('text')} expected {g.get('dose')}, got {a.get('dose')}")
        if g.get("frequency"):
            freq = norm(a.get("frequency"))
            want_f = norm(g["frequency"])
            if want_f not in freq and freq not in want_f:
                errors.append(
                    f"medication freq: {g.get('text')} expected {g.get('frequency')}, got {a.get('frequency')}"
                )
        if g.get("route") and norm(a.get("route")) != norm(g.get("route")):
            errors.append(f"medication route: {g.get('text')} expected {g.get('route')}, got {a.get('route')}")
    # Do not penalize extra detected meds for precision on this slot
    return tp, fp, fn, errors


def prf(tp: int, fp: int, fn: int) -> tuple[float, float, float]:
    p = tp / (tp + fp) if tp + fp else 1.0
    r = tp / (tp + fn) if tp + fn else 1.0
    f1 = 2 * p * r / (p + r) if p + r else 0.0
    return p, r, f1


def eval_one(gold_path: Path, handover_path: Path) -> dict[str, Any]:
    gold = load_json(gold_path)
    doc = load_json(handover_path)
    results: dict[str, Any] = {"gold": str(gold_path), "handover": str(handover_path), "slots": {}}
    all_errors: list[str] = []

    for name, scorer, getter in [
        ("antibiotics", score_antibiotics, antibiotics),
        ("medications", score_medications, medications),
        ("cultures", score_cultures, cultures),
        ("imaging", score_imaging, imaging),
        ("labs", score_labs, labs),
    ]:
        g = gold.get(name) or []
        a = getter(doc)
        # Skip empty gold unless the slot key is explicitly present (medications=[] means skip)
        if name not in gold and not g and not a:
            results["slots"][name] = {"skipped": True}
            continue
        if not g:
            results["slots"][name] = {"skipped": True}
            continue
        tp, fp, fn, errs = scorer(g, a)
        p, r, f1 = prf(tp, fp, fn)
        results["slots"][name] = {
            "tp": tp, "fp": fp, "fn": fn,
            "precision": round(p, 3), "recall": round(r, 3), "f1": round(f1, 3),
            "errors": errs,
        }
        all_errors.extend(errs)

    results["ok"] = len(all_errors) == 0
    results["errors"] = all_errors
    return results


def main() -> int:
    ap = argparse.ArgumentParser(description="Score handover JSON vs gold slots")
    ap.add_argument("gold", nargs="?", help="Gold JSON file")
    ap.add_argument("handover", nargs="?", help="Handover JSON output")
    ap.add_argument("--dir", nargs=2, metavar=("GOLD_DIR", "OUT_DIR"),
                    help="Score all gold/*.json against OUT/<name>.handover.json")
    args = ap.parse_args()

    if args.dir:
        gold_dir, out_dir = Path(args.dir[0]), Path(args.dir[1])
        failed = 0
        for gold_path in sorted(gold_dir.glob("*.json")):
            stem = gold_path.stem
            candidates = [
                out_dir / f"{stem}.handover.json",
                out_dir / f"{stem}.txt.handover.json",
                out_dir / f"process_handover.pretty.json",
                out_dir / f"{stem}.json",
            ]
            handover_path = next((p for p in candidates if p.exists()), None)
            if handover_path is None:
                # fallback: any file containing stem and .handover.json
                matches = sorted(out_dir.glob(f"*{stem}*.handover.json"))
                handover_path = matches[0] if matches else None
            if handover_path is None:
                print(f"SKIP {stem}: no handover output in {out_dir}")
                continue
            res = eval_one(gold_path, handover_path)
            status = "PASS" if res["ok"] else "FAIL"
            print(f"{status} {stem}")
            for slot, data in res.get("slots", {}).items():
                if data.get("skipped"):
                    continue
                print(f"  {slot}: P={data['precision']} R={data['recall']} F1={data['f1']}")
                for e in data.get("errors", []):
                    print(f"    - {e}")
            if not res["ok"]:
                failed += 1
        return 1 if failed else 0

    if not args.gold or not args.handover:
        ap.print_help()
        return 2

    res = eval_one(Path(args.gold), Path(args.handover))
    print(json.dumps(res, indent=2))
    return 0 if res["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
