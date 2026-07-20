#NAME||HEADER_REGEX
# ICU handover section headers (used with BsvRegexSectionizer). Includes common clinical sections plus handover-specific labels.

Background||^[\t ]*BACKGROUND[\t ]*:?[\t ]*$
Situation||^[\t ]*SITUATION[\t ]*:?[\t ]*$
Assessment||^[\t ]*(?:ASSESSMENT|A\/P|IMPRESSION)[\t ]*:?[\t ]*$
CNS||^[\t ]*(?:CNS|NEURO(?:LOGICAL)?)[\t ]*:?[\t ]*$
CVS||^[\t ]*(?:CVS|CARDIO(?:VASCULAR)?|CARDIOVASCULAR)[\t ]*:?[\t ]*$
Respiratory||^[\t ]*(?:RESP(?:IRATORY)?|PULM(?:ONARY)?)[\t ]*:?[\t ]*$
Gastrointestinal||^[\t ]*(?:GI|GASTRO(?:INTESTINAL)?)[\t ]*:?[\t ]*$
Genitourinary||^[\t ]*(?:GU|GENITOURINARY|RENAL)[\t ]*:?[\t ]*$
Hematologic||^[\t ]*(?:HEME|HEMATOLOG(?:IC|Y)|COAG)[\t ]*:?[\t ]*$
Culture||^[\t ]*CULTURES?[\t ]*:?[\t ]*$
LINE||^[\t ]*(?:LINES?|ACCESS|VASCULAR ACCESS)[\t ]*:?[\t ]*$
Abx||^[\t ]*(?:ABX|ANTIBIOTICS?(?:\/ANTIFUNGALS?)?)[\t ]*:?[\t ]*$
Imaging||^[\t ]*(?:IMAGING|RADIOLOGY|STUDIES)[\t ]*:?[\t ]*$
Plan||^[\t ]*(?:ASSESSMENT AND )?PLAN[\t ]*:?[\t ]*$
Past Medical History||^[\t ]*(?:(?:PMHX?)|(?:HISTORY OF (?:THE )?PAST ILLNESS)|(?:PAST MEDICAL HISTORY)|(?:BACKGROUND))[\t ]*:?[\t ]*$
History of Present Illness||^[\t ]*(?:(?:CC\/HPI:)|(?:S:)|(?:(?:HISTORY OF (?:THE )?(?:PRESENT |PHYSICAL )?ILLNESS)(?: \(HPI(?:, PROBLEM BY PROBLEM)?\))?[\t ]*:?))[\t ]*$
Hospital Course||^[\t ]*(?:BRIEF|HISTORY|HX)? ?HOSPITAL COURSE[\t ]*:?[\t ]*$
Medications||^[\t ]*(?:CURRENT )?MEDICATIONS?[\t ]*:?[\t ]*$
Problem List||^[\t ]*(?:SIGNIFICANT )?PROBLEMS?(?: LIST)?[\t ]*:?[\t ]*$
Diagnosis||^[\t ]*DIAGNOS(?:I|E)S[\t ]*:?[\t ]*$
Vital Signs||^[\t ]*VITAL(?:S|(?: (?:SIGNS|NOTES)))[\t ]*:?[\t ]*$
