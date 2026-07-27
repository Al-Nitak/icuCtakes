# ICU cTAKES handover module

## Coded ICU lexicons
Files under [`data/`](src/main/resources/org/apache/ctakes/icu/data/) use
`pattern||preferred||CUI||codingScheme||code` (trailing fields optional):

| File | Role |
|------|------|
| `organisms.txt` | Culture taxa (SNOMED) |
| `antibiotics.txt`, `vasopressors.txt`, `anticoagulants.txt`, `sedatives.txt`, `gi_meds.txt`, `diuretics.txt` | Drug class + RxNorm fill-in |
| `access_devices.txt` | Vascular lines (SNOMED) |
| `feeding_formulas.txt` | Formula product names (preferred; coding optional) |
| `culture_sites.txt` | Site patterns |
| `imaging.txt` | CT/MRI/CXR/US/echo study patterns |
| `labs.txt` | P0 lab names (creatinine, CRP, INR, …) |
| `thrombolytics.txt` | tPA/alteplase (separate from anticoagulants) |

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

Production drug recall: use `TinyRestPipeline.prod.piper` with `sno_rx_16ab`; local
`ctakesicd2015_no_umls` misses many Rx mentions.

[`LexiconLoader`](src/main/java/org/apache/ctakes/icu/util/LexiconLoader.java) exposes
`loadCodedEntries`, `loadCodedTokenSet`, and `matchCoded`.
[`HandoverAssembler`](src/main/java/org/apache/ctakes/icu/cc/HandoverAssembler.java)
classifies drugs from coded token sets and fills missing `cui` / `codingScheme` / `code`
on meds and access lines from the ICU lexicons when UMLS is thin.

## Culture organisms
[`CultureResultAnnotator`](src/main/java/org/apache/ctakes/icu/ae/CultureResultAnnotator.java)
writes coded fields onto `CultureResultMention`; the assembler copies them to
`systems.hematology.cultures[]` (`cui`, `codingScheme`, `code`, `preferredText`).

Phenotype tokens in the local window (`ESBL`, `CRE`, `MRSA`, `VRE`, `MDR`, …) are appended to
`sensitivities` for downstream CDC deep-links.

Rebuild this module and redeploy Tiny REST after lexicon/type changes:

```bash
mvn -pl ctakes-icu-handover -am package -DskipTests
```
