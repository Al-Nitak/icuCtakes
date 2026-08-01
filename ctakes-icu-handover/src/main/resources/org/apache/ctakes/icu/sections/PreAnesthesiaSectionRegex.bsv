#NAME||HEADER_REGEX
# Pre-Anesthetic Record section headers (BsvRegexSectionizer). Mirrors smart-handover/preaanesthesia.pdf.

Diagnosis||^[\t ]*DIAGNOS(?:I|E)S?[\t ]*:?[\t ]*$
Proposed Operation||^[\t ]*PROPOSED OPERATION[\t ]*:?[\t ]*$
History||^[\t ]*(?:HISTORY|PMH|PAST MEDICAL HISTORY|PAST HISTORY)[\t ]*:?[\t ]*$
Drug Therapy||^[\t ]*(?:DRUG THERAPY(?: BEFORE SURGERY)?|CURRENT MEDICATIONS?|MEDICATIONS?)[\t ]*:?[\t ]*$
Physical Examination||^[\t ]*(?:PHYSICAL(?: EXAM(?:INATION)?)?|EXAMINATION)[\t ]*:?[\t ]*$
Airway Assessment||^[\t ]*AIRWAY(?: ASSESSMENT)?[\t ]*:?[\t ]*$
Vital Signs||^[\t ]*VITAL(?:S|(?: (?:SIGNS|NOTES)))[\t ]*:?[\t ]*$
Lab Works||^[\t ]*(?:LAB(?:\.?|ORATORY)?(?: WORKS?)?|INVESTIGATIONS?\s*/?\s*LAB)[\t ]*:?[\t ]*$
Investigations||^[\t ]*(?:INVESTIGATIONS?|STUDIES|IMAGING)[\t ]*:?[\t ]*$
Please Prepare||^[\t ]*PLEASE PREPARE[\t ]*:?[\t ]*$
ASA||^[\t ]*ASA[\t ]*:?[\t ]*$
Plan for Anesthesia||^[\t ]*(?:PLAN FOR ANESTHESIA|ANESTHESIA PLAN|ANAESTHESIA PLAN)[\t ]*:?[\t ]*$
Recommendation||^[\t ]*RECOMMENDATION[\t ]*:?[\t ]*$
Consent||^[\t ]*CONSENT[\t ]*:?[\t ]*$
Allergy||^[\t ]*ALLERG(?:Y|IES)[\t ]*:?[\t ]*$
Previous Operation||^[\t ]*PREVIOUS OPERATION(?:S)?[\t ]*:?[\t ]*$
