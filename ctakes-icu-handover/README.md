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
