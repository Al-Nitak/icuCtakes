# ICU cTAKES handover module

## Coded ICU lexicons
Files under [`data/`](src/main/resources/org/apache/ctakes/icu/data/) use
`pattern||preferred||CUI||codingScheme||code` (trailing fields optional):

| File | Role / `drugClass` |
|------|------|
| `organisms.txt` | Culture taxa (SNOMED) |
| `antibiotics.txt` | antibiotic / antifungal |
| `vasopressors.txt` | vasopressor |
| `anticoagulants.txt` | anticoagulant |
| `thrombolytics.txt` | thrombolytic (tPA; not anticoag) |
| `sedatives.txt` | sedative |
| `analgesics.txt` | analgesic (tramadol, paracetamol, lidocaine, …) |
| `steroids.txt` | steroid (dexamethasone, …) |
| `antihypertensives.txt` | antihypertensive |
| `fluids.txt` | fluid (normal saline, …) |
| `gi_meds.txt` | gi (PPI, ondansetron; ASR alias `zivo` → Zofran) |
| `diuretics.txt` | diuretic |
| `access_devices.txt` | Vascular lines (SNOMED) |
| `feeding_formulas.txt` | Formula product names |
| `culture_sites.txt` | Site patterns |
| `imaging.txt` | CT/MRI/CXR/US/echo study patterns |
| `labs.txt` | P0 lab names (creatinine, CRP, INR, …) |

[`LexiconMedicationAnnotator`](src/main/java/org/apache/ctakes/icu/ae/LexiconMedicationAnnotator.java)
creates `MedicationMention` spans from these lexicons when dictionary NER missed them.
[`HandoverAssembler`](src/main/java/org/apache/ctakes/icu/cc/HandoverAssembler.java)
puts **all** meds in `access.medications[]` (with `drugClass`, dose, route, frequency)
and still buckets into antibiotics / CNS / CVS / etc.

## Tiny REST pipeline

| Piper | Dictionary | Drug NER |
|-------|------------|----------|
| `TinyRestPipeline.piper` (local) | `sno_rx_16ab_no_umls` (SNOMED+RxNorm, no UTS check) | yes + ICU lexicon meds |
| `TinyRestPipeline.prod.piper` | `sno_rx_16ab` (SNOMED+RxNorm + UTS license check) | yes + ICU lexicon meds |

Both need the unpacked HSQLDB at
`resources/org/apache/ctakes/dictionary/lookup/fast/sno_rx_16ab/`.
Prod also needs a UTS API key (`-Dctakes.umls_apikey=…`).
Lexicon annotator still covers ASR aliases and ICU-only names not in RxNorm.

Unnamed phrases (“prophylactic antibiotics”, “hypertension management”) cannot invent a
drug name — only explicit names are annotated.

## `format=map` consumer contract (Rails)

`POST /service/process?format=map` returns `ctakes.information-map/v1`:

```json
{
  "schema": "ctakes.information-map/v1",
  "handover": { "access": { "medications": [ ... ] }, ... },
  "entities": { ... },
  "cuiCounts": { ... },
  "fhir": { ... },
  "views": { "pretty": "...", "property": "..." }
}
```

- Use **`handover`** for clinical UI (including `access.medications`).
- Use **`entities`** for raw UMLS spans (same payload as `format=umls`).
- Do **not** treat the top-level map body as the old umls-only JSON.
- Rails error `no usable umls response` usually means the client parsed the wrong shape
  or got an HTTP/empty failure — read `entities` / check status, not the map root as umls.

## Eval harness

Gold notes + expected slots live under
[`src/test/resources/org/apache/ctakes/icu/eval/`](src/test/resources/org/apache/ctakes/icu/eval/).

```bash
# Unit tests
mvn -pl ctakes-icu-handover test

# End-to-end on eval fixtures (ExploreIcuHandoverRunner + scorer)
mvn -pl ctakes-icu-handover,ctakes-examples -am install -DskipTests
mvn -pl ctakes-examples exec:exec -DrunExploreIcuHandover \
  -Dexplore.input=ctakes-icu-handover/src/test/resources/org/apache/ctakes/icu/eval/notes \
  -Dexplore.output=output/ctakes-out/eval_handover
python3 scripts/eval_handover_slots.py --dir \
  ctakes-icu-handover/src/test/resources/org/apache/ctakes/icu/eval/gold \
  output/ctakes-out/eval_handover

# Or against live Tiny REST (server on 8081)
scripts/eval_handover.sh
```

Rebuild and redeploy Tiny REST after lexicon/pipeline changes:

```bash
mvn -pl ctakes-tiny-rest,ctakes-icu-handover,ctakes-user-resources,ctakes-tiny-rest-war \
  package -DskipTests
```
