package org.apache.ctakes.icu.cc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.ctakes.core.util.annotation.IdentifiedAnnotationUtil;
import org.apache.ctakes.core.util.annotation.OntologyConceptUtil;
import org.apache.ctakes.core.util.doc.DocIdUtil;
import org.apache.ctakes.icu.type.CultureResultMention;
import org.apache.ctakes.icu.type.FeedingMention;
import org.apache.ctakes.icu.type.GcsMention;
import org.apache.ctakes.icu.type.ImagingStudyMention;
import org.apache.ctakes.icu.type.LabValueMention;
import org.apache.ctakes.icu.type.PlanItem;
import org.apache.ctakes.icu.type.UrineOutputMention;
import org.apache.ctakes.icu.type.VascularAccessMention;
import org.apache.ctakes.icu.type.VentSupportMention;
import org.apache.ctakes.icu.util.LexiconLoader;
import org.apache.ctakes.icu.util.SentenceIndex;
import org.apache.ctakes.typesystem.type.refsem.Event;
import org.apache.ctakes.typesystem.type.refsem.EventProperties;
import org.apache.ctakes.typesystem.type.refsem.MedicationDosage;
import org.apache.ctakes.typesystem.type.refsem.MedicationFrequency;
import org.apache.ctakes.typesystem.type.refsem.MedicationRoute;
import org.apache.ctakes.typesystem.type.refsem.MedicationStrength;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.relation.DegreeOfTextRelation;
import org.apache.ctakes.typesystem.type.relation.LocationOfTextRelation;
import org.apache.ctakes.typesystem.type.relation.RelationArgument;
import org.apache.ctakes.typesystem.type.textsem.DiseaseDisorderMention;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.LabMention;
import org.apache.ctakes.typesystem.type.textsem.MedicationDosageModifier;
import org.apache.ctakes.typesystem.type.textsem.MedicationFrequencyModifier;
import org.apache.ctakes.typesystem.type.textsem.MedicationMention;
import org.apache.ctakes.typesystem.type.textsem.MedicationRouteModifier;
import org.apache.ctakes.typesystem.type.textsem.MedicationStrengthModifier;
import org.apache.ctakes.typesystem.type.textsem.ProcedureMention;
import org.apache.ctakes.typesystem.type.textsem.SignSymptomMention;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.jcas.tcas.Annotation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Assembles {@link HandoverDocument} JSON from stock cTAKES annotations plus ICU slot types.
 */
final public class HandoverAssembler {

   static private final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

   static private final Set<String> ABX = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/antibiotics.txt" );
   static private final Set<String> PRESSORS = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/vasopressors.txt" );
   static private final Set<String> SEDATIVES = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/sedatives.txt" );
   static private final Set<String> GI_MEDS = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/gi_meds.txt" );
   static private final Set<String> DIURETICS = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/diuretics.txt" );
   static private final Set<String> ANTICOAG = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/anticoagulants.txt" );
   static private final Set<String> THROMBOLYTICS = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/thrombolytics.txt" );
   static private final Set<String> ANALGESICS = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/analgesics.txt" );
   static private final Set<String> STEROIDS = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/steroids.txt" );
   static private final Set<String> ANTIHYPERTENSIVES
         = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/antihypertensives.txt" );
   static private final Set<String> FLUIDS = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/fluids.txt" );
   static private final List<LexiconLoader.CodedEntry> DRUG_LEXICON;
   static private final List<LexiconLoader.CodedEntry> ACCESS_LEXICON
         = LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/access_devices.txt" );

   static {
      final List<LexiconLoader.CodedEntry> drugs = new ArrayList<>();
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/antibiotics.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/vasopressors.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/anticoagulants.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/sedatives.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/analgesics.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/steroids.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/antihypertensives.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/fluids.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/gi_meds.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/diuretics.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/thrombolytics.txt" ) );
      DRUG_LEXICON = Collections.unmodifiableList( drugs );
   }

   static private final Pattern IMAGING = Pattern.compile(
         "\\b(?:CT|MRI|MR|echo(?:cardiogram)?|ultrasonography|ultrasound|US|X[-\\s]?ray|XR|CXR|radiograph)\\b",
         Pattern.CASE_INSENSITIVE );

   /** Intervening text allowed when merging adjacent situation events. */
   static private final Pattern ADJACENT_GAP = Pattern.compile(
         "^[\\s,;/-]*(?:(?:and)[\\s,;/-]*)?$",
         Pattern.CASE_INSENSITIVE );

   /** Characters after a med mention to scan for dose / route / frequency. */
   static private final int MED_ATTR_WINDOW = 48;
   /** Characters before a med mention to scan for a route or dose said ahead of the name. */
   static private final int MED_ATTR_WINDOW_BEFORE = 32;

   /**
    * Where one item's dosing words end. A full stop followed by whitespace counts whether or
    * not a Sentence ends there: the sentence detector fuses "…(nil by mouth). She is on
    * pantoprazole BID." into one sentence. A comma between digits ("5,000 units") is part of
    * a number, not a break. The joining words start the next item or clause.
    */
   static private final Pattern PHRASE_BREAK = Pattern.compile(
         "(?i)[.!?](?=\\s|$)|[;\\n\\r]|(?<!\\d),|,(?!\\d)|\\s[&+]\\s"
               + "|\\b(?:and|plus|then|but|on|if|when|while|until|unless|because)\\b" );

   static private final Pattern FINDING_CONTINUATION = Pattern.compile(
         "^(?:it\\s+)?(?:showed|revealed|demonstrated|found|reported|with)\\b",
         Pattern.CASE_INSENSITIVE );

   /** A number as it is said: {@code 5}, {@code 0.1}, {@code 5,000}. */
   static private final String NUMBER = "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?";

   static private final Pattern NUMBER_PATTERN = Pattern.compile( NUMBER );

   /**
    * Units a dose is said in. Litres are left out on purpose: "nasal cannula 2 litres" is an
    * oxygen flow, not a dose of anything. Units only Drug NER knows (mEq, mmol) still arrive
    * through its strength, checked against the number said here.
    */
   static private final String DOSE_UNIT
         = "(?:micrograms?|milligrams?|millilit(?:er|re)s?|grams?|units?"
               + "|mcg|µg|μg|ug|mg|ml|gm|g|u)(?!\\p{L})";

   /** {@code /h}, {@code /kg/min}, {@code per hour}, {@code per kg per minute}, {@code an hour}. */
   static private final String DOSE_RATE
         = "(?:(?:\\s*/\\s*|\\s+(?:per|an?)\\s+)"
               + "(?:kilograms?|kg|minutes?|min|hours?|hrs?|h|day|dose)(?!\\p{L}))";

   /**
    * ICU dose/rate as said in a dosing phrase, e.g. {@code 5 mg/h}, {@code 2 g}, {@code 30 ml},
    * {@code 2 to 4 mg}, {@code 0.1 mcg/kg/min}, {@code 100 ml per hour} — or a bare number.
    * Groups: 1 the number, 2 the upper end of a range, 3 the unit when one was said.
    */
   static private final Pattern QUANTITY = Pattern.compile(
         "(?i)(?<![\\w.,/])(" + NUMBER + ")(?:\\s*(?:-|–|to)\\s*(" + NUMBER + "))?"
               + "(?:\\s*(" + DOSE_UNIT + ")" + DOSE_RATE + "{0,3})?" );

   /** A number that counts time or occasions is not a dose: "every 8 hours", "for 5 days". */
   static private final Pattern NOT_A_QUANTITY_AFTER = Pattern.compile(
         "(?i)\\s*-?\\s*(?:hourly|hours?|hrs?|h|minutes?|mins?|days?|weeks?|months?|years?"
               + "|times?|doses?|am|pm|percent)\\b|\\s*%|:\\d|\\p{L}" );

   /** "q 8", "every 8", "day 3", "x 3" — and "from 0.1 to 0.2", a change rather than a dose. */
   static private final Pattern NOT_A_QUANTITY_BEFORE = Pattern.compile(
         "(?i)\\b(?:every|q|day|x|from)\\s*$" );

   /** "15 mg/L" is a level, not a dose: the slash goes on to a unit no rate is said in. */
   static private final Pattern CONCENTRATION_AFTER = Pattern.compile( "\\s*/\\s*\\p{L}" );

   static private final String ROUTE_WORDS
         = "IVP|IV|PO|SQ|SC|IM|NGT|NG|SL|PR|oral(?:ly)?|by\\s+mouth|intravenous(?:ly)?"
               + "|subcutaneous(?:ly)?|intramuscular(?:ly)?|sublingual(?:ly)?|rectal(?:ly)?";

   static private final Pattern WINDOW_ROUTE = Pattern.compile(
         "(?i)\\b(" + ROUTE_WORDS + ")\\b" );

   /** "twice daily", "three times a day" — longest first, so they are never read as "daily". */
   static private final String COUNT_PER_DAY
         = "(once|twice|thrice)\\s+(?:daily|(?:a|per)\\s+day)"
               + "|(one|two|three|four|\\d+)\\s+times?\\s+(?:daily|(?:a|per)\\s+day)";

   static private final String FREQUENCY_WORDS
         = COUNT_PER_DAY + "|q\\d+h?|bid|tid|qid|od|bd|tds|(?<!\\btimes?\\s{1,3})daily|prn|as\\s+needed"
               + "|every\\s+\\d+\\s+(?:hours?|hrs?)|/24|/12|/8";

   static private final Pattern WINDOW_FREQUENCY = Pattern.compile(
         "(?i)\\b(" + FREQUENCY_WORDS + ")\\b" );

   static private final Pattern COUNT_PER_DAY_WHOLE = Pattern.compile(
         "(?i)^(?:" + COUNT_PER_DAY + ")$" );

   /** Drug NER often mis-parses "every 8 hours" as "3.0 day". */
   static private final Pattern BROKEN_DAY_FREQ = Pattern.compile(
         "(?i)^\\d+(?:\\.\\d+)?\\s*days?$" );

   /** Drug NER puts a count of its own ahead of the frequency: "2.0 BID", "2.0 q12h". */
   static private final Pattern NER_FREQ_COUNT = Pattern.compile( "^\\d+(?:\\.\\d+)?\\s*" );

   /** A time unit Drug NER kept without its count ("h", "hr") says nothing about how often. */
   static private final Pattern NER_FREQ_FRAGMENT = Pattern.compile(
         "(?i)^(?:h|hrs?|hours?|mins?|minutes?|days?|weeks?|months?)$" );

   static private final Pattern EVERY_HOURS = Pattern.compile(
         "(?i)\\bevery\\s+(\\d+)\\s+(?:hours?|hrs?)\\b" );

   /** A route said just ahead of the drug: "IV paracetamol", "oral vancomycin". */
   static private final Pattern ROUTE_AT_END = Pattern.compile(
         "(?i)(?<![\\w.])(" + ROUTE_WORDS + ")\\s*$" );

   /** A dose said just ahead of the drug: "40 mg of furosemide", "plus 700 ml normal saline". */
   static private final Pattern QUANTITY_AT_END = Pattern.compile(
         "(?i)(?<![\\w.,/])((" + NUMBER + ")(?:\\s*(?:-|–|to)\\s*(" + NUMBER + "))?\\s*("
               + DOSE_UNIT + ")" + DOSE_RATE + "{0,3})(\\s+of)?\\s*$" );

   static private final Pattern FREQUENCY_AT_END = Pattern.compile(
         "(?i)\\b(?:" + FREQUENCY_WORDS + ")\\s*$" );

   static private final Pattern NUMBER_AT_END = Pattern.compile( "\\d\\s*$" );

   /**
    * What may stand to the left of a dose that belongs to the drug after it: the start of a
    * clause, or a word that cannot itself take a dose.
    */
   static private final Pattern LEFT_ANCHOR = Pattern.compile(
         "(?i)(?:[,;:(.!?\\n]|\\b(?:and|plus|then|or|but|with|on|gave|give|given|giving|start|started"
               + "|continue|continued|receive|received|receiving|add|added|administer|administered"
               + "|got|had))\\s*$" );

   static private final Set<String> JUNK_MED_TEXTS = setOf(
         "oral", "gel", "tablet", "tablets", "capsule", "capsules", "infusion",
         "analgesic", "analgesics", "medication", "medications", "drug", "drugs",
         "dose", "dosage", "form", "solution", "cream", "ointment" );

   /** Content-free event hits that clutter the Situation timeline. */
   static private final Set<String> JUNK_EVENT_TEXTS = setOf(
         "history", "normal", "scan", "level of consciousness", "consciousness" );

   static private final Set<String> JUNK_EVENT_CUIS = setOf(
         "C0262926", "C0231683", "C0034606", "C0234425" );

   static private final Set<String> BACKGROUND_SECTIONS = setOf(
         "background", "past medical history", "pmh", "pmhx", "patient history", "clinical history" );
   static private final Set<String> ASSESSMENT_SECTIONS = setOf(
         "assessment", "impression", "diagnosis", "problem list", "final diagnosis", "principle diagnosis" );
   static private final Set<String> CNS_SECTIONS = setOf( "cns", "neurological", "neuro" );
   static private final Set<String> CVS_SECTIONS = setOf( "cvs", "cardiovascular", "cardio" );
   static private final Set<String> RESP_SECTIONS = setOf( "respiratory", "pulm", "pulmonary", "resp" );
   static private final Set<String> GI_SECTIONS = setOf( "gi", "gastrointestinal", "abdomen" );
   static private final Set<String> GU_SECTIONS = setOf( "gu", "genitourinary", "renal" );
   static private final Set<String> HEME_SECTIONS = setOf( "heme", "hematologic", "hematology", "coag" );

   private HandoverAssembler() {
   }

   static public String createJson( final JCas jCas ) {
      return GSON.toJson( createDocument( jCas ) );
   }

   static public String createJson( final JCas jCas, final SentenceIndex sentenceIndex ) {
      return GSON.toJson( createDocument( jCas, sentenceIndex ) );
   }

   static public HandoverDocument createDocument( final JCas jCas ) {
      return createDocument( jCas, SentenceIndex.of( jCas ) );
   }

   static public HandoverDocument createDocument( final JCas jCas, final SentenceIndex sentenceIndex ) {
      final HandoverDocument doc = new HandoverDocument();
      doc.documentId = DocIdUtil.getDocumentID( jCas );
      final String docText = jCas.getDocumentText();
      final SentenceIndex sentences = sentenceIndex != null ? sentenceIndex : SentenceIndex.of( jCas );

      final Map<IdentifiedAnnotation, List<String>> locations = buildLocationIndex( jCas );
      final List<Segment> segments = new ArrayList<>( JCasUtil.select( jCas, Segment.class ) );
      doc.sentences = new ArrayList<>( sentences.sentences() );

      for ( Segment segment : segments ) {
         final HandoverDocument.SectionDto s = new HandoverDocument.SectionDto();
         s.id = segment.getId();
         s.preferredText = segment.getPreferredText();
         s.tagText = segment.getTagText();
         s.begin = segment.getBegin();
         s.end = segment.getEnd();
         doc.sections.add( s );
      }

      for ( DiseaseDisorderMention dd : JCasUtil.select( jCas, DiseaseDisorderMention.class ) ) {
         final HandoverDocument.ConceptDto concept = toConcept( dd, locations, sentences );
         if ( IdentifiedAnnotationUtil.isHistoric( dd ) || inSections( dd, segments, BACKGROUND_SECTIONS ) ) {
            doc.background.conditions.add( concept );
         }
         if ( inSections( dd, segments, ASSESSMENT_SECTIONS )
               || ( !IdentifiedAnnotationUtil.isNegated( dd ) && !IdentifiedAnnotationUtil.isHistoric( dd ) ) ) {
            // Prefer assessment section; also keep non-negated disorders as candidate problems
            if ( inSections( dd, segments, ASSESSMENT_SECTIONS )
                  || doc.assessment.problems.stream().noneMatch( p -> overlaps( p, concept ) ) ) {
               if ( inSections( dd, segments, ASSESSMENT_SECTIONS )
                     || !IdentifiedAnnotationUtil.isNegated( dd ) ) {
                  doc.assessment.problems.add( concept );
               }
            }
         }
      }

      for ( ImagingStudyMention study : JCasUtil.select( jCas, ImagingStudyMention.class ) ) {
         if ( imagingOverlaps( doc, study.getBegin(), study.getEnd() ) ) {
            continue;
         }
         doc.imaging.add( toImagingFromStudy( jCas, study, locations, sentences ) );
      }

      for ( ProcedureMention proc : JCasUtil.select( jCas, ProcedureMention.class ) ) {
         final HandoverDocument.ConceptDto concept = toConcept( proc, locations, sentences );
         if ( IdentifiedAnnotationUtil.isHistoric( proc ) || inSections( proc, segments, BACKGROUND_SECTIONS ) ) {
            doc.background.priorProcedures.add( concept );
         }
         final HandoverDocument.EventDto event = toEvent( null, proc, sentences );
         if ( !isJunkEvent( event ) ) {
            doc.situation.events.add( event );
         }
         if ( !imagingOverlaps( doc, proc.getBegin(), proc.getEnd() ) ) {
            maybeAddImaging( jCas, doc, proc, locations, sentences );
         }
      }

      for ( SignSymptomMention ss : JCasUtil.select( jCas, SignSymptomMention.class ) ) {
         final HandoverDocument.ConceptDto concept = toConcept( ss, locations, sentences );
         final HandoverDocument.EventDto event = toEvent( jCas, ss, sentences );
         if ( !isJunkEvent( event ) ) {
            doc.situation.events.add( event );
         }
         if ( inSections( ss, segments, CNS_SECTIONS ) ) {
            doc.systems.cns.findings.add( concept );
         }
         if ( inSections( ss, segments, HEME_SECTIONS )
               || textLooksCoag( ss.getCoveredText() ) ) {
            doc.systems.hematology.coagStatus.add( concept );
         }
      }

      for ( LabMention lab : JCasUtil.select( jCas, LabMention.class ) ) {
         if ( textLooksCoag( lab.getCoveredText() ) ) {
            doc.systems.hematology.coagStatus.add( toConcept( lab, locations, sentences ) );
         }
      }

      // Medications by class / section — also keep full list under access.medications
      final List<HandoverDocument.MedDto> allMeds = new ArrayList<>();
      final List<MedicationMention> medMentions
            = new ArrayList<>( JCasUtil.select( jCas, MedicationMention.class ) );
      for ( MedicationMention med : medMentions ) {
         final HandoverDocument.MedDto medDto
               = toMed( med, segments, docText, sentences, previousMentionEnd( med, medMentions ) );
         if ( isJunkMedication( medDto ) || !keepMedication( medDto ) ) {
            continue;
         }
         allMeds.add( medDto );
         final String drugClass = medDto.drugClass;
         if ( "antibiotic".equals( drugClass ) || "antifungal".equals( drugClass ) ) {
            doc.access.antibiotics.add( medDto );
         }
         if ( "sedative".equals( drugClass ) || "analgesic".equals( drugClass )
               || inSections( med, segments, CNS_SECTIONS ) ) {
            doc.systems.cns.medications.add( medDto );
         }
         if ( "vasopressor".equals( drugClass ) || "antihypertensive".equals( drugClass )
               || inSections( med, segments, CVS_SECTIONS ) ) {
            doc.systems.cvs.medications.add( medDto );
         }
         if ( inSections( med, segments, RESP_SECTIONS ) ) {
            doc.systems.respiratory.medications.add( medDto );
         }
         if ( "gi".equals( drugClass ) || inSections( med, segments, GI_SECTIONS ) ) {
            doc.systems.gi.medications.add( medDto );
         }
         if ( "diuretic".equals( drugClass ) || "fluid".equals( drugClass )
               || inSections( med, segments, GU_SECTIONS ) ) {
            doc.systems.gu.medications.add( medDto );
         }
         if ( "anticoagulant".equals( drugClass ) ) {
            doc.systems.hematology.anticoagulants.add( medDto );
         }
      }
      doc.access.medications = dedupeMedications( allMeds );

      for ( LabValueMention labVal : JCasUtil.select( jCas, LabValueMention.class ) ) {
         final HandoverDocument.LabDto dto = new HandoverDocument.LabDto();
         dto.name = labVal.getLabName();
         dto.value = labVal.getValue();
         dto.unit = labVal.getUnit();
         dto.text = labVal.getCoveredText();
         dto.preferredText = labVal.getPreferredText();
         dto.cui = labVal.getCui();
         dto.codingScheme = labVal.getCodingScheme();
         dto.code = labVal.getCode();
         dto.begin = labVal.getBegin();
         dto.end = labVal.getEnd();
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
         doc.systems.labs.add( dto );
      }

      // CVS status: only emit when evidenced. A bare label "unknown" (no offsets) is
      // promoted by some consumers into a phantom span at transcript[0:7] ("Regardi"…).
      final boolean onPressors = doc.systems.cvs.medications.stream()
            .anyMatch( m -> "vasopressor".equals( m.drugClass ) );
      if ( onPressors ) {
         doc.systems.cvs.status.put( "label", "on_pressors" );
         doc.systems.cvs.status.put( "evidence", "vasopressor medication detected" );
      }

      // ICU slots
      for ( GcsMention gcs : JCasUtil.select( jCas, GcsMention.class ) ) {
         final HandoverDocument.GcsDto dto = new HandoverDocument.GcsDto();
         dto.value = gcs.getValue();
         dto.underSedation = gcs.getUnderSedation();
         dto.text = gcs.getCoveredText();
         dto.begin = gcs.getBegin();
         dto.end = gcs.getEnd();
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
         // keep last / most recent by offset
         doc.systems.cns.gcs = dto;
      }

      for ( VentSupportMention vent : JCasUtil.select( jCas, VentSupportMention.class ) ) {
         final HandoverDocument.VentDto dto = new HandoverDocument.VentDto();
         dto.mode = vent.getMode();
         dto.text = vent.getCoveredText();
         dto.begin = vent.getBegin();
         dto.end = vent.getEnd();
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
         if ( vent.getRr() != 0.0f ) {
            dto.settings.put( "rr", vent.getRr() );
         }
         if ( vent.getTv() != 0.0f ) {
            dto.settings.put( "tv", vent.getTv() );
         }
         if ( vent.getPeep() != 0.0f ) {
            dto.settings.put( "peep", vent.getPeep() );
         }
         if ( vent.getFio2() != 0.0f ) {
            dto.settings.put( "fio2", vent.getFio2() );
         }
         if ( vent.getFlow() != 0.0f ) {
            dto.settings.put( "flow", vent.getFlow() );
         }
         dto.o2Device = vent.getMode();
         doc.systems.respiratory.support = dto;
      }

      for ( FeedingMention feed : JCasUtil.select( jCas, FeedingMention.class ) ) {
         final HandoverDocument.FeedingDto dto = new HandoverDocument.FeedingDto();
         dto.route = feed.getRoute();
         dto.formula = feed.getFormula();
         dto.text = feed.getCoveredText();
         dto.begin = feed.getBegin();
         dto.end = feed.getEnd();
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
         if ( doc.systems.gi.feeding == null
               || (dto.route != null && doc.systems.gi.feeding.route == null)
               || (dto.formula != null && doc.systems.gi.feeding.formula == null) ) {
            if ( doc.systems.gi.feeding != null ) {
               if ( dto.route == null ) {
                  dto.route = doc.systems.gi.feeding.route;
               }
               if ( dto.formula == null ) {
                  dto.formula = doc.systems.gi.feeding.formula;
               }
            }
            doc.systems.gi.feeding = dto;
         }
      }

      for ( UrineOutputMention uop : JCasUtil.select( jCas, UrineOutputMention.class ) ) {
         final HandoverDocument.UopDto dto = new HandoverDocument.UopDto();
         dto.value = uop.getValue();
         dto.unit = uop.getUnit();
         dto.text = uop.getCoveredText();
         dto.begin = uop.getBegin();
         dto.end = uop.getEnd();
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
         doc.systems.gu.urineOutput = dto;
      }

      for ( CultureResultMention culture : JCasUtil.select( jCas, CultureResultMention.class ) ) {
         final HandoverDocument.CultureDto dto = new HandoverDocument.CultureDto();
         dto.site = culture.getSite();
         dto.organism = culture.getOrganism();
         dto.status = culture.getStatus();
         dto.preferredText = culture.getPreferredText();
         dto.cui = culture.getCui();
         dto.codingScheme = culture.getCodingScheme();
         dto.code = culture.getCode();
         dto.text = culture.getCoveredText();
         dto.begin = culture.getBegin();
         dto.end = culture.getEnd();
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
         final StringArray sens = culture.getSensitivities();
         if ( sens != null ) {
            for ( int i = 0; i < sens.size(); i++ ) {
               dto.sensitivities.add( sens.get( i ) );
            }
         }
         doc.systems.hematology.cultures.add( dto );
      }

      for ( VascularAccessMention line : JCasUtil.select( jCas, VascularAccessMention.class ) ) {
         final HandoverDocument.LineDto dto = new HandoverDocument.LineDto();
         dto.type = line.getAccessType();
         dto.text = line.getCoveredText();
         dto.begin = line.getBegin();
         dto.end = line.getEnd();
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
         if ( line.getInsertTime() != null ) {
            dto.insertDate = new HandoverDocument.TimeDto();
            dto.insertDate.text = line.getInsertTime().getCoveredText();
         }
         fillLineFromLexicon( dto );
         doc.access.lines.add( dto );
      }

      // Plan
      for ( Segment segment : segments ) {
         if ( sectionName( segment ).contains( "plan" ) ) {
            doc.plan.sectionText = segment.getCoveredText();
            break;
         }
      }
      for ( PlanItem item : JCasUtil.select( jCas, PlanItem.class ) ) {
         final HandoverDocument.PlanItemDto dto = new HandoverDocument.PlanItemDto();
         dto.text = item.getActionText() != null ? item.getActionText() : item.getCoveredText();
         dto.begin = item.getBegin();
         dto.end = item.getEnd();
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
         doc.plan.items.add( dto );
      }

      // Plan-owned sentences must not also appear as Situation / Assessment.
      dropPlanOverlaps( doc );

      doc.situation.events = mergeAdjacentSituationEvents( jCas, doc.situation.events );
      // Re-resolve sentence ids after merge so the id matches the final merged span.
      for ( HandoverDocument.EventDto event : doc.situation.events ) {
         event.sentenceId = sentences.idFor( event.begin, event.end );
      }
      doc.situation.events.sort( Comparator.comparingInt( e -> e.begin ) );
      doc.imaging = dedupeImaging( doc.imaging );
      for ( HandoverDocument.ImagingDto img : doc.imaging ) {
         img.findings = dedupeConceptsByOverlap( img.findings );
      }
      dedupeAssessment( doc );
      return doc;
   }

   /** Remove Situation events / Assessment problems that overlap a PlanItem span. */
   static private void dropPlanOverlaps( final HandoverDocument doc ) {
      if ( doc.plan.items == null || doc.plan.items.isEmpty() ) {
         return;
      }
      if ( doc.situation.events != null && !doc.situation.events.isEmpty() ) {
         final List<HandoverDocument.EventDto> kept = new ArrayList<>();
         for ( HandoverDocument.EventDto event : doc.situation.events ) {
            if ( !overlapsAnyPlanItem( doc, event.begin, event.end ) ) {
               kept.add( event );
            }
         }
         doc.situation.events = kept;
      }
      if ( doc.assessment.problems != null && !doc.assessment.problems.isEmpty() ) {
         final List<HandoverDocument.ConceptDto> kept = new ArrayList<>();
         for ( HandoverDocument.ConceptDto problem : doc.assessment.problems ) {
            if ( !overlapsAnyPlanItem( doc, problem.begin, problem.end ) ) {
               kept.add( problem );
            }
         }
         doc.assessment.problems = kept;
      }
   }

   static private boolean overlapsAnyPlanItem( final HandoverDocument doc,
                                               final int begin,
                                               final int end ) {
      for ( HandoverDocument.PlanItemDto item : doc.plan.items ) {
         if ( begin < item.end && item.begin < end ) {
            return true;
         }
      }
      return false;
   }

   static private void maybeAddImaging( final JCas jCas,
                                        final HandoverDocument doc,
                                        final ProcedureMention proc,
                                        final Map<IdentifiedAnnotation, List<String>> locations,
                                        final SentenceIndex sentences ) {
      final String text = proc.getCoveredText();
      if ( text == null || !IMAGING.matcher( text ).find() ) {
         return;
      }
      final HandoverDocument.ImagingDto img = new HandoverDocument.ImagingDto();
      img.procedureText = text;
      img.begin = proc.getBegin();
      img.end = proc.getEnd();
      img.sentenceId = sentences.idFor( img.begin, img.end );
      img.modality = detectModality( text );
      img.bodySite = parseBodySiteFromText( text );
      final List<String> locs = locations.get( proc );
      if ( img.bodySite == null && locs != null && !locs.isEmpty() ) {
         img.bodySite = locs.get( 0 );
      }
      attachSentenceFindings( jCas, img, locations, sentences );
      for ( TimeMention time : JCasUtil.select( jCas, TimeMention.class ) ) {
         if ( Math.abs( time.getBegin() - proc.getBegin() ) < 100 ) {
            img.date = new HandoverDocument.TimeDto();
            img.date.text = time.getCoveredText();
            break;
         }
      }
      doc.imaging.add( img );
   }

   static private HandoverDocument.ImagingDto toImagingFromStudy(
         final JCas jCas,
         final ImagingStudyMention study,
         final Map<IdentifiedAnnotation, List<String>> locations,
         final SentenceIndex sentences ) {
      final HandoverDocument.ImagingDto img = new HandoverDocument.ImagingDto();
      img.modality = study.getModality() != null ? study.getModality() : detectModality( study.getCoveredText() );
      img.procedureText = study.getCoveredText() != null ? study.getCoveredText().trim() : null;
      img.begin = study.getBegin();
      img.end = study.getEnd();
      img.sentenceId = sentences.idFor( img.begin, img.end );
      img.bodySite = parseBodySiteFromText( study.getCoveredText() );
      attachSentenceFindings( jCas, img, locations, sentences );
      for ( TimeMention time : JCasUtil.selectCovered( jCas, TimeMention.class, study ) ) {
         img.date = new HandoverDocument.TimeDto();
         img.date.text = time.getCoveredText();
         break;
      }
      return img;
   }

   static private void attachSentenceFindings(
         final JCas jCas,
         final HandoverDocument.ImagingDto img,
         final Map<IdentifiedAnnotation, List<String>> locations,
         final SentenceIndex sentences ) {
      final Integer primaryId = sentences.idFor( img.begin, img.end );
      final HandoverDocument.SentenceDto primary = sentences.get( primaryId );
      if ( primary == null ) {
         return;
      }
      attachCoveredFindings( jCas, img, primary.begin, primary.end, locations, sentences );
      final HandoverDocument.SentenceDto next = sentences.next( primaryId );
      if ( next != null && next.text != null
            && FINDING_CONTINUATION.matcher( next.text.trim() ).find() ) {
         attachCoveredFindings( jCas, img, next.begin, next.end, locations, sentences );
      }
   }

   static private List<HandoverDocument.ImagingDto> dedupeImaging(
         final List<HandoverDocument.ImagingDto> imaging ) {
      if ( imaging.size() <= 1 ) {
         return imaging;
      }
      final List<HandoverDocument.ImagingDto> sorted = new ArrayList<>( imaging );
      sorted.sort( ( a, b ) -> {
         final int lenA = a.end - a.begin;
         final int lenB = b.end - b.begin;
         if ( lenB != lenA ) {
            return Integer.compare( lenB, lenA );
         }
         return Integer.compare( a.begin, b.begin );
      } );
      final List<HandoverDocument.ImagingDto> kept = new ArrayList<>();
      for ( HandoverDocument.ImagingDto img : sorted ) {
         boolean overlaps = false;
         for ( HandoverDocument.ImagingDto existing : kept ) {
            if ( img.begin < existing.end && img.end > existing.begin ) {
               overlaps = true;
               break;
            }
         }
         if ( !overlaps ) {
            kept.add( img );
         }
      }
      kept.sort( Comparator.comparingInt( i -> i.begin ) );
      return kept;
   }

   static private String parseBodySiteFromText( final String text ) {
      if ( text == null ) {
         return null;
      }
      final Matcher matcher = Pattern.compile(
            "(?i)(?:CT|MRI|MR)\\s+(brain|head|chest|abd(?:omen)?|spine|pulmonary(?:\\s+angio)?|angio)\\b" )
            .matcher( text );
      if ( matcher.find() ) {
         return matcher.group( 1 ).toLowerCase( Locale.ROOT );
      }
      return null;
   }

   static private void attachCoveredFindings(
         final JCas jCas,
         final HandoverDocument.ImagingDto img,
         final int begin,
         final int end,
         final Map<IdentifiedAnnotation, List<String>> locations,
         final SentenceIndex sentences ) {
      for ( DiseaseDisorderMention dd : JCasUtil.select( jCas, DiseaseDisorderMention.class ) ) {
         if ( dd.getBegin() >= begin && dd.getEnd() <= end ) {
            addFindingUnique( img, toConcept( dd, locations, sentences ) );
         }
      }
      for ( SignSymptomMention ss : JCasUtil.select( jCas, SignSymptomMention.class ) ) {
         if ( ss.getBegin() >= begin && ss.getEnd() <= end ) {
            addFindingUnique( img, toConcept( ss, locations, sentences ) );
         }
      }
   }

   static private void addFindingUnique( final HandoverDocument.ImagingDto img,
                                         final HandoverDocument.ConceptDto concept ) {
      if ( concept == null || concept.text == null ) {
         return;
      }
      for ( HandoverDocument.ConceptDto existing : img.findings ) {
         if ( concept.text.equalsIgnoreCase( existing.text )
               || ( concept.begin == existing.begin && concept.end == existing.end ) ) {
            return;
         }
      }
      img.findings.add( concept );
   }

   static private boolean imagingOverlaps( final HandoverDocument doc, final int begin, final int end ) {
      for ( HandoverDocument.ImagingDto img : doc.imaging ) {
         if ( begin < img.end && end > img.begin ) {
            return true;
         }
      }
      return false;
   }

   static private String detectModality( final String text ) {
      if ( text == null ) {
         return "imaging";
      }
      final String t = text.toUpperCase( Locale.ROOT );
      if ( t.contains( "CT" ) ) {
         return "CT";
      }
      if ( t.contains( "MRI" ) || t.matches( ".*\\bMR\\b.*" ) ) {
         return "MRI";
      }
      if ( t.contains( "ECHO" ) ) {
         return "echo";
      }
      if ( t.contains( "X-RAY" ) || t.contains( "XRAY" ) || t.contains( "XR" ) || t.contains( "CXR" )
            || t.contains( "RADIOGRAPH" ) ) {
         return "X-ray";
      }
      if ( t.contains( "ULTRASOUND" ) || t.contains( "ULTRASONOGRAPHY" ) || t.contains( "POCUS" ) ) {
         return "US";
      }
      return "imaging";
   }

   static private Map<IdentifiedAnnotation, List<String>> buildLocationIndex( final JCas jCas ) {
      final Map<IdentifiedAnnotation, List<String>> map = new HashMap<>();
      for ( LocationOfTextRelation rel : JCasUtil.select( jCas, LocationOfTextRelation.class ) ) {
         final RelationArgument arg1 = rel.getArg1();
         final RelationArgument arg2 = rel.getArg2();
         if ( arg1 == null || arg2 == null ) {
            continue;
         }
         final Annotation a1 = arg1.getArgument();
         final Annotation a2 = arg2.getArgument();
         IdentifiedAnnotation entity = null;
         Annotation location = null;
         if ( a1 instanceof IdentifiedAnnotation && !(a1 instanceof org.apache.ctakes.typesystem.type.textsem.AnatomicalSiteMention) ) {
            entity = (IdentifiedAnnotation) a1;
            location = a2;
         } else if ( a2 instanceof IdentifiedAnnotation ) {
            entity = (IdentifiedAnnotation) a2;
            location = a1;
         }
         if ( entity != null && location != null ) {
            map.computeIfAbsent( entity, k -> new ArrayList<>() ).add( location.getCoveredText() );
         }
      }
      return map;
   }

   static private HandoverDocument.ConceptDto toConcept( final IdentifiedAnnotation ann,
                                                         final Map<IdentifiedAnnotation, List<String>> locations,
                                                         final SentenceIndex sentences ) {
      final HandoverDocument.ConceptDto dto = new HandoverDocument.ConceptDto();
      dto.text = ann.getCoveredText();
      dto.begin = ann.getBegin();
      dto.end = ann.getEnd();
      dto.type = ann.getClass().getSimpleName();
      dto.polarity = ann.getPolarity();
      dto.uncertainty = ann.getUncertainty();
      dto.subject = ann.getSubject();
      dto.historyOf = ann.getHistoryOf();
      dto.sectionId = ann.getSegmentID();
      dto.preferredText = IdentifiedAnnotationUtil.getPreferredText( ann );
      final UmlsConcept umls = OntologyConceptUtil.getUmlsConceptStream( ann ).findFirst().orElse( null );
      if ( umls != null ) {
         dto.cui = umls.getCui();
         dto.codingScheme = umls.getCodingScheme();
         dto.code = umls.getCode();
         if ( dto.preferredText == null || dto.preferredText.isEmpty() ) {
            dto.preferredText = umls.getPreferredText();
         }
      }
      final List<String> locs = locations.get( ann );
      if ( locs != null && !locs.isEmpty() ) {
         dto.locations = new ArrayList<>( locs );
      }
      if ( sentences != null ) {
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
      }
      return dto;
   }

   static private HandoverDocument.EventDto toEvent( final EventMention mention ) {
      return toEvent( null, mention, null );
   }

   /**
    * Builds an event DTO. When {@code jCas} is provided and the mention has a DegreeOf
    * severity relation, the span is expanded to cover both finding and modifier.
    */
   static private HandoverDocument.EventDto toEvent( final JCas jCas, final EventMention mention ) {
      return toEvent( jCas, mention, null );
   }

   static private HandoverDocument.EventDto toEvent( final JCas jCas, final EventMention mention,
                                                    final SentenceIndex sentences ) {
      final HandoverDocument.EventDto dto = new HandoverDocument.EventDto();
      int begin = mention.getBegin();
      int end = mention.getEnd();
      if ( jCas != null && mention instanceof SignSymptomMention ) {
         final int[] span = expandWithSeverity( (SignSymptomMention) mention );
         begin = span[ 0 ];
         end = span[ 1 ];
      }
      dto.begin = begin;
      dto.end = end;
      dto.text = coveredText( jCas, mention, begin, end );
      dto.type = mention.getClass().getSimpleName();
      final UmlsConcept umls = OntologyConceptUtil.getUmlsConceptStream( mention ).findFirst().orElse( null );
      if ( umls != null ) {
         dto.cui = umls.getCui();
      }
      final Event event = mention.getEvent();
      if ( event != null ) {
         final EventProperties props = event.getProperties();
         if ( props != null ) {
            dto.docTimeRel = props.getDocTimeRel();
         }
      }
      if ( sentences != null ) {
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
      }
      return dto;
   }

   /**
    * Expands a sign/symptom span to include its DegreeOf severity modifier when present.
    */
   static private int[] expandWithSeverity( final SignSymptomMention ss ) {
      int begin = ss.getBegin();
      int end = ss.getEnd();
      final DegreeOfTextRelation severity = ss.getSeverity();
      if ( severity == null ) {
         return new int[]{ begin, end };
      }
      for ( Annotation arg : relationArgs( severity ) ) {
         if ( arg == null ) {
            continue;
         }
         begin = Math.min( begin, arg.getBegin() );
         end = Math.max( end, arg.getEnd() );
      }
      return new int[]{ begin, end };
   }

   static private Annotation[] relationArgs( final DegreeOfTextRelation rel ) {
      final RelationArgument a1 = rel.getArg1();
      final RelationArgument a2 = rel.getArg2();
      return new Annotation[]{
            a1 != null ? a1.getArgument() : null,
            a2 != null ? a2.getArgument() : null
      };
   }

   static private String coveredText( final JCas jCas, final EventMention mention,
                                      final int begin, final int end ) {
      if ( jCas != null && (begin != mention.getBegin() || end != mention.getEnd()) ) {
         final String docText = jCas.getDocumentText();
         if ( docText != null && begin >= 0 && end <= docText.length() && begin <= end ) {
            return docText.substring( begin, end );
         }
      }
      return mention.getCoveredText();
   }

   static private List<HandoverDocument.EventDto> mergeAdjacentSituationEvents(
         final JCas jCas,
         final List<HandoverDocument.EventDto> events ) {
      return mergeAdjacentSituationEvents( jCas != null ? jCas.getDocumentText() : null, events );
   }

   /**
    * Merges overlapping/nested and immediately adjacent situation events into single phrases.
    * Keeps cui/type/docTimeRel from the longest constituent.
    * Package-visible for unit tests.
    */
   static List<HandoverDocument.EventDto> mergeAdjacentSituationEvents(
         final String docText,
         final List<HandoverDocument.EventDto> events ) {
      if ( events == null || events.size() <= 1 ) {
         return events;
      }
      final List<HandoverDocument.EventDto> sorted = new ArrayList<>( events );
      sorted.sort( Comparator
            .comparingInt( (HandoverDocument.EventDto e) -> e.begin )
            .thenComparingInt( e -> e.end ) );

      final List<HandoverDocument.EventDto> merged = new ArrayList<>();
      HandoverDocument.EventDto current = copyEvent( sorted.get( 0 ) );
      int currentLen = current.end - current.begin;

      for ( int i = 1; i < sorted.size(); i++ ) {
         final HandoverDocument.EventDto next = sorted.get( i );
         if ( shouldMergeEvents( docText, current, next ) ) {
            final int nextLen = next.end - next.begin;
            current.begin = Math.min( current.begin, next.begin );
            current.end = Math.max( current.end, next.end );
            if ( nextLen > currentLen ) {
               current.cui = next.cui;
               current.type = next.type;
               current.docTimeRel = next.docTimeRel;
               current.time = next.time;
               currentLen = nextLen;
            }
            current.text = substringOrJoin( docText, current );
         } else {
            current.text = substringOrJoin( docText, current );
            merged.add( current );
            current = copyEvent( next );
            currentLen = current.end - current.begin;
         }
      }
      current.text = substringOrJoin( docText, current );
      merged.add( current );
      return merged;
   }

   static private boolean shouldMergeEvents( final String docText,
                                             final HandoverDocument.EventDto a,
                                             final HandoverDocument.EventDto b ) {
      // Overlap or containment (assumes a.begin <= b.begin after sort)
      if ( b.begin < a.end ) {
         return true;
      }
      if ( docText == null || a.end > b.begin || a.end < 0 || b.begin > docText.length() ) {
         return a.end >= b.begin;
      }
      final String gap = docText.substring( a.end, b.begin );
      return ADJACENT_GAP.matcher( gap ).matches();
   }

   static private String substringOrJoin( final String docText, final HandoverDocument.EventDto e ) {
      if ( docText != null && e.begin >= 0 && e.end <= docText.length() && e.begin <= e.end ) {
         return docText.substring( e.begin, e.end );
      }
      return e.text;
   }

   static private HandoverDocument.EventDto copyEvent( final HandoverDocument.EventDto src ) {
      final HandoverDocument.EventDto dto = new HandoverDocument.EventDto();
      dto.text = src.text;
      dto.type = src.type;
      dto.cui = src.cui;
      dto.docTimeRel = src.docTimeRel;
      dto.time = src.time;
      dto.begin = src.begin;
      dto.end = src.end;
      dto.sentenceId = src.sentenceId;
      return dto;
   }

   static private HandoverDocument.MedDto toMed( final MedicationMention med,
                                                 final List<Segment> segments,
                                                 final String docText,
                                                 final SentenceIndex sentences,
                                                 final int previousDrugEnd ) {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      dto.text = med.getCoveredText();
      dto.begin = med.getBegin();
      dto.end = med.getEnd();
      dto.sectionId = med.getSegmentID();
      dto.preferredText = IdentifiedAnnotationUtil.getPreferredText( med );
      dto.polarity = IdentifiedAnnotationUtil.isNegated( med ) ? -1 : 1;
      final UmlsConcept umls = OntologyConceptUtil.getUmlsConceptStream( med ).findFirst().orElse( null );
      if ( umls != null ) {
         dto.cui = umls.getCui();
         dto.codingScheme = umls.getCodingScheme();
         dto.code = umls.getCode();
         if ( dto.preferredText == null || dto.preferredText.isEmpty() ) {
            dto.preferredText = umls.getPreferredText();
         }
      }
      fillMedFromLexicon( dto );
      dto.dose = extractDose( med );
      dto.strength = extractStrength( med );
      dto.frequency = extractFrequency( med );
      dto.route = extractRoute( med );
      int floor = 0;
      int ceiling = docText == null ? 0 : docText.length();
      if ( sentences != null ) {
         dto.sentenceId = sentences.idFor( dto.begin, dto.end );
         final HandoverDocument.SentenceDto sentence = sentences.get( dto.sentenceId );
         if ( sentence != null ) {
            floor = sentence.begin;
            ceiling = sentence.end;
         }
      }
      attachSpokenAttributes( dto, docText, floor, ceiling, previousDrugEnd );
      dto.drugClass = classifyDrug( med.getCoveredText(), dto.preferredText );
      return dto;
   }

   /** Where the nearest medication mention ahead of {@code med} ends, or -1. */
   static private int previousMentionEnd( final MedicationMention med, final Collection<MedicationMention> all ) {
      int end = -1;
      for ( MedicationMention other : all ) {
         if ( other != med && other.getEnd() <= med.getBegin() && other.getEnd() > end ) {
            end = other.getEnd();
         }
      }
      return end;
   }

   /**
    * Settle a medication's dose, strength, route and frequency on what was said about that drug.
    * <p>
    * Drug NER attaches modifiers by sentence and keeps no offsets for them, so its values can
    * come from any item in the sentence: "give pantoprazole BID, start IV fluid D5LR at 100 ml
    * per hour" comes back as pantoprazole 100, IV. Each value therefore has to be found in the
    * drug's own phrase — the words after the name up to the end of that item
    * ({@link #ownPhraseAfter}), or a route / dose said just ahead of it ({@code readAhead}).
    * A value with no such evidence is dropped: a blank field is filled at review, a value moved
    * from another item is a wrong clinical value that looks right.
    * </p>
    *
    * @param dto             begin / end / text set, and Drug NER's values in dose / strength /
    *                        frequency / route
    * @param floor           where the drug's sentence begins
    * @param ceiling         where the drug's sentence ends
    * @param previousDrugEnd where the nearest medication mention ahead of this one ends, or -1
    */
   static void attachSpokenAttributes( final HandoverDocument.MedDto dto, final String docText,
                                       final int floor, final int ceiling, final int previousDrugEnd ) {
      if ( dto == null || docText == null ) {
         return;
      }
      final String after = ownPhraseAfter( docText, dto.end, ceiling, dto.text );
      final SpokenAhead ahead = readAhead( docText, dto.begin, floor, previousDrugEnd );
      final SpokenQuantity quantity = firstQuantity( after );
      // The first quantity after the name is the dose; one said ahead of it counts only without.
      final SpokenQuantity said = quantity != null ? quantity : ahead.quantity;
      if ( !saysNumber( dto.dose, said, after ) ) {
         dto.dose = null;
      }
      if ( !saysStrength( dto.strength, said, after ) ) {
         dto.strength = null;
      }
      // As said, with its unit: Drug NER keeps "30 ml" as a bare "30".
      if ( said != null && said.text != null ) {
         dto.dose = said.text;
      }
      final String nerFrequency = dto.frequency;
      dto.frequency = null;
      dto.route = null;
      fillMedAttributesFromWindow( dto, after );
      // Prefer explicit "every N hours" over Drug NER day counts.
      preferEveryHoursFrequency( dto, after );
      // A frequency only Drug NER knows ("weekly") stands when the drug's own words hold it.
      if ( isBlank( dto.frequency ) && saidAsWord( after, nerFrequency ) ) {
         dto.frequency = nerFrequency;
      }
      if ( isBlank( dto.route ) ) {
         dto.route = ahead.route;
      }
      inferRouteFromMedText( dto );
      if ( isBlank( dto.dose ) && !isBlank( dto.strength ) ) {
         dto.dose = dto.strength.trim();
      }
   }

   static void preferEveryHoursFrequency( final HandoverDocument.MedDto dto, final String window ) {
      if ( dto == null || isBlank( window ) ) {
         return;
      }
      final Matcher every = EVERY_HOURS.matcher( window );
      if ( every.find() ) {
         dto.frequency = "q" + every.group( 1 ) + "h";
      }
   }

   /**
    * The words after a drug's name that are about that drug: up to the end of its sentence, a
    * full stop, comma, joining word or the next drug — and no further than a second quantity,
    * which is the next item starting whether or not anything marks it ("fentanyl 100 mcg per
    * hour levetiracetam 500 mg BID").
    */
   static String ownPhraseAfter( final String docText, final int medEnd, final int ceiling,
                                 final String currentDrug ) {
      if ( docText == null || medEnd < 0 || medEnd >= docText.length() ) {
         return "";
      }
      final int sentenceEnd = Math.max( medEnd, Math.min( docText.length(), ceiling ) );
      int stop = Math.min( sentenceEnd, medEnd + MED_ATTR_WINDOW + 24 );
      // A window that stops short of the sentence must not end inside a word or number:
      // "…at 10" of "at 100 ml" is not a dose.
      while ( stop < sentenceEnd && stop > medEnd
            && !Character.isWhitespace( docText.charAt( stop ) )
            && !Character.isWhitespace( docText.charAt( stop - 1 ) ) ) {
         stop--;
      }
      String phrase = docText.substring( medEnd, stop );
      final Matcher brk = PHRASE_BREAK.matcher( phrase );
      if ( brk.find() ) {
         phrase = phrase.substring( 0, brk.start() );
      }
      phrase = truncateAtNextDrug( phrase, currentDrug );
      final SpokenQuantity first = nextQuantity( phrase, 0 );
      if ( first != null ) {
         final SpokenQuantity second = nextQuantity( phrase, first.end );
         if ( second != null ) {
            phrase = phrase.substring( 0, second.begin );
         }
      }
      return phrase;
   }

   /** A quantity as it was said in a drug's own phrase. */
   static private final class SpokenQuantity {
      /** With its unit — "30 ml", "100 ml per hour" — or null for a bare number. */
      private final String text;
      private final String unit;
      private final List<BigDecimal> numbers;
      private final int begin;
      private final int end;
      /** "15 mg/L": a level, never a dose. */
      private final boolean concentration;

      private SpokenQuantity( final String text, final String unit, final List<BigDecimal> numbers,
                              final int begin, final int end, final boolean concentration ) {
         this.text = text;
         this.unit = unit;
         this.numbers = numbers;
         this.begin = begin;
         this.end = end;
         this.concentration = concentration;
      }
   }

   /** The first quantity in a drug's own phrase, or null when none was said as a dose. */
   static private SpokenQuantity firstQuantity( final String phrase ) {
      final SpokenQuantity first = nextQuantity( phrase, 0 );
      return first == null || first.concentration ? null : first;
   }

   /** The next number at or after {@code from} that is said as a quantity, or null. */
   static private SpokenQuantity nextQuantity( final String phrase, final int from ) {
      if ( isBlank( phrase ) ) {
         return null;
      }
      final Matcher m = QUANTITY.matcher( phrase );
      int at = from;
      while ( at < phrase.length() && m.find( at ) ) {
         at = m.end();
         final String tail = phrase.substring( m.end() );
         final String unit = m.group( 3 );
         if ( NOT_A_QUANTITY_BEFORE.matcher( phrase.substring( 0, m.start() ) ).find()
               || ( unit == null && NOT_A_QUANTITY_AFTER.matcher( tail ).lookingAt() ) ) {
            continue;
         }
         final List<BigDecimal> numbers = new ArrayList<>();
         numbers.add( toNumber( m.group( 1 ) ) );
         if ( m.group( 2 ) != null ) {
            numbers.add( toNumber( m.group( 2 ) ) );
         }
         return new SpokenQuantity( unit == null ? null : squish( m.group() ), unit, numbers,
               m.start(), m.end(), unit != null && CONCENTRATION_AFTER.matcher( tail ).lookingAt() );
      }
      return null;
   }

   static private BigDecimal toNumber( final String text ) {
      return new BigDecimal( text.replace( ",", "" ) );
   }

   static private String squish( final String text ) {
      return text.replaceAll( "\\s+", " " ).trim();
   }

   /**
    * Is the number in a Drug NER value the one said as this drug's quantity? A value written
    * in words ("two units") has to be in the drug's own phrase, whole. Blank passes.
    */
   static private boolean saysNumber( final String value, final SpokenQuantity said, final String phrase ) {
      if ( isBlank( value ) ) {
         return true;
      }
      final Matcher num = NUMBER_PATTERN.matcher( value );
      if ( !num.find() ) {
         return saidAsWord( phrase, squish( value ) );
      }
      if ( said == null ) {
         return false;
      }
      final BigDecimal number = toNumber( num.group() );
      for ( BigDecimal spoken : said.numbers ) {
         if ( spoken.compareTo( number ) == 0 ) {
            return true;
         }
      }
      return false;
   }

   /**
    * Drug NER builds a strength from a number and a unit it finds separately — "enoxaparin
    * 40 mg SC paracetamol 1 g" comes back as enoxaparin "40 g" — so the unit has to be the
    * one said with that number as well.
    */
   static private boolean saysStrength( final String strength, final SpokenQuantity said, final String phrase ) {
      if ( !saysNumber( strength, said, phrase ) ) {
         return false;
      }
      if ( isBlank( strength ) || said == null || said.unit == null ) {
         return true;
      }
      final String unit = strength.trim().replaceFirst( "^[\\d.,\\s]+", "" );
      return unit.isEmpty() || unit.equalsIgnoreCase( said.unit );
   }

   static private boolean saidAsWord( final String phrase, final String word ) {
      if ( isBlank( phrase ) || isBlank( word ) ) {
         return false;
      }
      return Pattern.compile( "(?i)(?<!\\w)" + Pattern.quote( word.trim() ) + "(?!\\w)" )
                    .matcher( squish( phrase ) ).find();
   }

   /**
    * Stop attribute window before the next ICU drug name so dose/freq does not bleed
    * across "tramadol …, paracetamol …, and Zivo …".
    */
   static String truncateAtNextDrug( final String window, final String currentDrug ) {
      if ( isBlank( window ) ) {
         return "";
      }
      int cut = window.length();
      for ( LexiconLoader.CodedEntry entry : DRUG_LEXICON ) {
         final Matcher m = entry.pattern.matcher( window );
         while ( m.find() ) {
            final String hit = m.group();
            if ( currentDrug != null && hit.equalsIgnoreCase( currentDrug.trim() ) ) {
               continue;
            }
            if ( m.start() > 0 && m.start() < cut ) {
               cut = m.start();
            }
         }
      }
      return window.substring( 0, cut );
   }

   static private boolean isJunkMedication( final HandoverDocument.MedDto med ) {
      if ( med == null || isBlank( med.text ) ) {
         return true;
      }
      final String t = med.text.trim().toLowerCase( Locale.ROOT );
      if ( JUNK_MED_TEXTS.contains( t ) ) {
         return true;
      }
      final String pref = med.preferredText == null ? "" : med.preferredText.toLowerCase( Locale.ROOT );
      if ( pref.contains( "dosage form" ) || pref.equals( "gel" ) || pref.equals( "analgesics" )
            || pref.equals( "oral dosage form" ) || pref.equals( "today" ) || pref.equals( "air" )
            || pref.equals( "liquid" ) || pref.equals( "oxygen" )
            || pref.contains( "prophylaxis" ) || pref.equals( "antibiotics" ) ) {
         return true;
      }
      return "other".equals( med.drugClass ) && t.length() <= 4 && isBlank( med.cui );
   }

   /** Keep classified ICU drugs, or any span that matches an ICU drug lexicon entry. */
   static private boolean keepMedication( final HandoverDocument.MedDto med ) {
      if ( med == null ) {
         return false;
      }
      if ( med.drugClass != null && !"other".equals( med.drugClass ) ) {
         return true;
      }
      final String haystack = ((med.text == null ? "" : med.text) + " "
            + (med.preferredText == null ? "" : med.preferredText)).trim();
      return LexiconLoader.matchCoded( haystack, DRUG_LEXICON ) != null;
   }

   /**
    * Parse ICU dose / route / frequency from text immediately after a medication mention.
    * Only fills blank fields — never overwrites modifier-derived values.
    */
   static void fillMedAttributesFromWindow( final HandoverDocument.MedDto dto, final String window ) {
      if ( dto == null || isBlank( window ) ) {
         return;
      }
      if ( isBlank( dto.dose ) ) {
         // The first quantity, not the first one that happens to carry a unit: in
         // "2 to 4 mg" or "30 ml … 300 ml" a later number is not this drug's dose.
         final SpokenQuantity quantity = firstQuantity( window );
         if ( quantity != null && quantity.text != null ) {
            dto.dose = quantity.text;
         }
      }
      if ( isBlank( dto.route ) ) {
         final Matcher routeMatcher = WINDOW_ROUTE.matcher( window );
         if ( routeMatcher.find() ) {
            dto.route = normalizeRoute( routeMatcher.group( 1 ) );
         }
      }
      if ( isBlank( dto.frequency ) ) {
         final Matcher freqMatcher = WINDOW_FREQUENCY.matcher( window );
         if ( freqMatcher.find() ) {
            dto.frequency = canonicalFrequency( freqMatcher.group( 1 ) );
         }
      }
   }

   static void inferRouteFromMedText( final HandoverDocument.MedDto dto ) {
      if ( dto == null || !isBlank( dto.route ) || isBlank( dto.text ) ) {
         return;
      }
      final String t = dto.text.toLowerCase( Locale.ROOT );
      if ( t.contains( "oral" ) || t.contains( "by mouth" ) ) {
         dto.route = "PO";
      } else if ( t.matches( ".*\\biv\\b.*" ) ) {
         dto.route = "IV";
      }
   }

   /**
    * Parse a route or dose from text immediately before a medication mention (e.g. oral
    * vancomycin, 40 mg of furosemide). Only fills blank fields.
    */
   static void fillMedAttributesFromBeforeWindow( final HandoverDocument.MedDto dto, final String window ) {
      if ( dto == null || isBlank( window ) ) {
         return;
      }
      final SpokenAhead ahead = readAhead( window, false );
      if ( isBlank( dto.route ) ) {
         dto.route = ahead.route;
      }
      if ( isBlank( dto.dose ) && ahead.quantity != null ) {
         dto.dose = ahead.quantity.text;
      }
   }

   /** What was said about a drug just ahead of its name. */
   static private final class SpokenAhead {
      static private final SpokenAhead NONE = new SpokenAhead( null, null, 0 );

      private final String route;
      private final SpokenQuantity quantity;
      /** Where in the window these words begin. */
      private final int begin;

      private SpokenAhead( final String route, final SpokenQuantity quantity, final int begin ) {
         this.route = route;
         this.quantity = quantity;
         this.begin = begin;
      }
   }

   /**
    * The route / dose said just ahead of the drug at {@code medBegin}, read no further back
    * than its sentence, and not when those words directly follow another medication —
    * "levetiracetam IV paracetamol" gives paracetamol no route.
    */
   static private SpokenAhead readAhead( final String docText, final int medBegin, final int floor,
                                         final int previousDrugEnd ) {
      if ( docText == null || medBegin <= 0 || medBegin > docText.length() ) {
         return SpokenAhead.NONE;
      }
      final int from = Math.min( medBegin, Math.max( Math.max( 0, floor ), medBegin - MED_ATTR_WINDOW_BEFORE ) );
      final SpokenAhead ahead = readAhead( docText.substring( from, medBegin ), from <= floor );
      if ( ahead == SpokenAhead.NONE ) {
         return ahead;
      }
      final int saidFrom = from + ahead.begin;
      if ( previousDrugEnd >= 0 && previousDrugEnd <= saidFrom
            && docText.substring( previousDrugEnd, saidFrom ).trim().isEmpty() ) {
         return SpokenAhead.NONE;
      }
      return ahead;
   }

   /**
    * Leftwards from the name: routes and at most one dose, touching the name and each other.
    * Nothing is taken when what stands to their left shows they are the tail of the item
    * before — its frequency, its dose, a bare number, its name — or, for a dose, when nothing
    * ties it to this drug ("of", or a word that starts a clause).
    *
    * @param clauseStart the window begins where the sentence does
    */
   static private SpokenAhead readAhead( final String window, final boolean clauseStart ) {
      String head = window == null ? "" : window;
      String route = null;
      SpokenQuantity quantity = null;
      boolean bound = false;
      while ( true ) {
         final Matcher routeMatcher = ROUTE_AT_END.matcher( head );
         if ( routeMatcher.find() ) {
            if ( route == null ) {
               route = normalizeRoute( routeMatcher.group( 1 ) );
            }
            head = head.substring( 0, routeMatcher.start() );
            continue;
         }
         final Matcher doseMatcher = QUANTITY_AT_END.matcher( head );
         if ( quantity == null && doseMatcher.find() ) {
            final List<BigDecimal> numbers = new ArrayList<>();
            numbers.add( toNumber( doseMatcher.group( 2 ) ) );
            if ( doseMatcher.group( 3 ) != null ) {
               numbers.add( toNumber( doseMatcher.group( 3 ) ) );
            }
            quantity = new SpokenQuantity( squish( doseMatcher.group( 1 ) ), doseMatcher.group( 4 ),
                  numbers, doseMatcher.start(), doseMatcher.end(), false );
            bound = doseMatcher.group( 5 ) != null;
            head = head.substring( 0, doseMatcher.start() );
            continue;
         }
         break;
      }
      if ( route == null && quantity == null ) {
         return SpokenAhead.NONE;
      }
      if ( FREQUENCY_AT_END.matcher( head ).find() || NUMBER_AT_END.matcher( head ).find()
            || endsWithDrugName( head ) ) {
         return SpokenAhead.NONE;
      }
      if ( quantity != null && !bound
            && !( head.trim().isEmpty() ? clauseStart : LEFT_ANCHOR.matcher( head ).find() ) ) {
         return SpokenAhead.NONE;
      }
      return new SpokenAhead( route, quantity, head.length() );
   }

   static private boolean endsWithDrugName( final String text ) {
      final String head = text.replaceFirst( "\\s+$", "" );
      if ( head.isEmpty() ) {
         return false;
      }
      for ( LexiconLoader.CodedEntry entry : DRUG_LEXICON ) {
         final Matcher m = entry.pattern.matcher( head );
         while ( m.find() ) {
            if ( m.end() == head.length() ) {
               return true;
            }
         }
      }
      return false;
   }

   static private String normalizeRoute( final String route ) {
      if ( route == null ) {
         return null;
      }
      final String r = route.replaceAll( "\\s+", " " ).trim().toLowerCase( Locale.ROOT );
      if ( r.startsWith( "oral" ) || r.equals( "by mouth" ) ) {
         return "PO";
      }
      if ( r.startsWith( "intravenous" ) ) {
         return "IV";
      }
      if ( r.startsWith( "subcutaneous" ) ) {
         return "SC";
      }
      if ( r.startsWith( "intramuscular" ) ) {
         return "IM";
      }
      if ( r.startsWith( "sublingual" ) ) {
         return "SL";
      }
      if ( r.startsWith( "rectal" ) ) {
         return "PR";
      }
      return r.toUpperCase( Locale.ROOT );
   }

   /**
    * A frequency in the handover's vocabulary: "every 8 hours" → q8h, "twice daily" → bid,
    * "three times a day" → tid, "as needed" → PRN; anything else as said, in lower case.
    */
   static String canonicalFrequency( final String spoken ) {
      final String said = squish( spoken );
      if ( said.equalsIgnoreCase( "as needed" ) || said.equalsIgnoreCase( "prn" ) ) {
         return "PRN";
      }
      final Matcher every = EVERY_HOURS.matcher( said );
      if ( every.matches() ) {
         return "q" + every.group( 1 ) + "h";
      }
      if ( said.startsWith( "/" ) ) {
         return said;
      }
      final String lower = said.toLowerCase( Locale.ROOT );
      final Matcher count = COUNT_PER_DAY_WHOLE.matcher( lower );
      if ( count.matches() ) {
         switch ( count.group( 1 ) != null ? count.group( 1 ) : count.group( 2 ) ) {
            case "once":
            case "one":
            case "1":
               return "daily";
            case "twice":
            case "two":
            case "2":
               return "bid";
            case "thrice":
            case "three":
            case "3":
               return "tid";
            case "four":
            case "4":
               return "qid";
            default:
               return lower;
         }
      }
      return lower;
   }

   /**
    * Drug NER's frequency in the handover's vocabulary, or null when it kept only a fragment:
    * "2.0 BID" → bid, "day" → daily, "2.0 q12h" → q12h; "3.0 day", "h", "hr", "1.0" → null.
    */
   static String normalizeNerFrequency( final String raw ) {
      if ( isBlank( raw ) ) {
         return null;
      }
      final String trimmed = raw.trim();
      if ( BROKEN_DAY_FREQ.matcher( trimmed ).matches() ) {
         return null;
      }
      final String said = NER_FREQ_COUNT.matcher( trimmed ).replaceFirst( "" ).trim();
      if ( said.isEmpty() ) {
         return null;
      }
      if ( said.equalsIgnoreCase( "day" ) ) {
         return "daily";
      }
      if ( NER_FREQ_FRAGMENT.matcher( said ).matches() ) {
         return null;
      }
      return canonicalFrequency( said );
   }

   static private boolean isBlank( final String value ) {
      return value == null || value.trim().isEmpty();
   }

   /** When UMLS coding is thin, fill CUI/RxNorm from ICU drug lexicons. */
   static private void fillMedFromLexicon( final HandoverDocument.MedDto dto ) {
      final boolean missingCode = dto.cui == null || dto.cui.isEmpty()
            || dto.codingScheme == null || dto.codingScheme.isEmpty()
            || dto.code == null || dto.code.isEmpty();
      if ( !missingCode ) {
         return;
      }
      final String haystack = ((dto.text == null ? "" : dto.text) + " "
            + (dto.preferredText == null ? "" : dto.preferredText)).trim();
      final LexiconLoader.CodedEntry hit = LexiconLoader.matchCoded( haystack, DRUG_LEXICON );
      if ( hit == null ) {
         return;
      }
      if ( (dto.cui == null || dto.cui.isEmpty()) && hit.cui != null ) {
         dto.cui = hit.cui;
      }
      if ( (dto.codingScheme == null || dto.codingScheme.isEmpty()) && hit.codingScheme != null ) {
         dto.codingScheme = hit.codingScheme;
      }
      if ( (dto.code == null || dto.code.isEmpty()) && hit.code != null ) {
         dto.code = hit.code;
      }
      if ( (dto.preferredText == null || dto.preferredText.isEmpty()) && hit.preferredText != null ) {
         dto.preferredText = hit.preferredText;
      }
   }

   static private void fillLineFromLexicon( final HandoverDocument.LineDto dto ) {
      final String haystack = ((dto.text == null ? "" : dto.text) + " "
            + (dto.type == null ? "" : dto.type)).trim();
      final LexiconLoader.CodedEntry hit = LexiconLoader.matchCoded( haystack, ACCESS_LEXICON );
      if ( hit == null ) {
         return;
      }
      dto.preferredText = hit.preferredText;
      dto.cui = hit.cui;
      dto.codingScheme = hit.codingScheme;
      dto.code = hit.code;
   }

   /** Package-visible for unit tests. */
   static String classifyDrug( final String text, final String preferred ) {
      final String combined = ((text == null ? "" : text) + " " + (preferred == null ? "" : preferred))
            .toLowerCase( Locale.ROOT );
      if ( LexiconLoader.textContainsAny( combined, THROMBOLYTICS ) ) {
         return "thrombolytic";
      }
      if ( LexiconLoader.textContainsAny( combined, ABX ) ) {
         // crude antifungal check
         if ( combined.contains( "azole" ) || combined.contains( "fungin" ) || combined.contains( "amphotericin" )
               || combined.contains( "nystatin" ) ) {
            return "antifungal";
         }
         return "antibiotic";
      }
      if ( LexiconLoader.textContainsAny( combined, PRESSORS ) ) {
         return "vasopressor";
      }
      if ( LexiconLoader.textContainsAny( combined, SEDATIVES ) ) {
         return "sedative";
      }
      if ( LexiconLoader.textContainsAny( combined, ANALGESICS ) ) {
         return "analgesic";
      }
      if ( LexiconLoader.textContainsAny( combined, STEROIDS ) ) {
         return "steroid";
      }
      if ( LexiconLoader.textContainsAny( combined, ANTIHYPERTENSIVES ) ) {
         return "antihypertensive";
      }
      if ( LexiconLoader.textContainsAny( combined, FLUIDS ) ) {
         return "fluid";
      }
      if ( LexiconLoader.textContainsAny( combined, GI_MEDS ) ) {
         return "gi";
      }
      if ( LexiconLoader.textContainsAny( combined, DIURETICS ) ) {
         return "diuretic";
      }
      if ( LexiconLoader.textContainsAny( combined, ANTICOAG ) ) {
         return "anticoagulant";
      }
      return "other";
   }

   /** Prefer longer / better-coded spans when mentions overlap. */
   static List<HandoverDocument.MedDto> dedupeMedications( final List<HandoverDocument.MedDto> meds ) {
      if ( meds == null || meds.size() <= 1 ) {
         return meds == null ? new ArrayList<>() : new ArrayList<>( meds );
      }
      final List<HandoverDocument.MedDto> sorted = new ArrayList<>( meds );
      sorted.sort( ( a, b ) -> {
         final int lenA = a.end - a.begin;
         final int lenB = b.end - b.begin;
         if ( lenB != lenA ) {
            return Integer.compare( lenB, lenA );
         }
         final int codeA = isBlank( a.cui ) ? 0 : 1;
         final int codeB = isBlank( b.cui ) ? 0 : 1;
         if ( codeB != codeA ) {
            return Integer.compare( codeB, codeA );
         }
         return Integer.compare( a.begin, b.begin );
      } );
      final List<HandoverDocument.MedDto> kept = new ArrayList<>();
      for ( HandoverDocument.MedDto med : sorted ) {
         boolean overlaps = false;
         for ( HandoverDocument.MedDto existing : kept ) {
            if ( med.begin < existing.end && med.end > existing.begin ) {
               overlaps = true;
               break;
            }
         }
         if ( !overlaps ) {
            kept.add( med );
         }
      }
      kept.sort( Comparator.comparingInt( m -> m.begin ) );
      return kept;
   }

   /** CAS dosage modifier value — shared with UmlsJsonFormatter (map.entities). */
   static public String extractDose( final MedicationMention med ) {
      final MedicationDosageModifier mod = med.getMedicationDosage();
      if ( mod == null || !(mod.getNormalizedForm() instanceof MedicationDosage) ) {
         return null;
      }
      return ( (MedicationDosage) mod.getNormalizedForm() ).getValue();
   }

   /** CAS strength (number + unit) — shared with UmlsJsonFormatter (map.entities). */
   static public String extractStrength( final MedicationMention med ) {
      final MedicationStrengthModifier mod = med.getMedicationStrength();
      if ( mod == null || !(mod.getNormalizedForm() instanceof MedicationStrength) ) {
         return null;
      }
      final MedicationStrength s = (MedicationStrength) mod.getNormalizedForm();
      return (s.getNumber() == null ? "" : s.getNumber()) + (s.getUnit() == null ? "" : " " + s.getUnit());
   }

   /**
    * CAS frequency, normalised — shared with UmlsJsonFormatter (map.entities). Clears bogus
    * Drug NER "N day" values and the fragments it leaves behind; see {@link #normalizeNerFrequency}.
    */
   static public String extractFrequency( final MedicationMention med ) {
      final MedicationFrequencyModifier mod = med.getMedicationFrequency();
      if ( mod == null || !(mod.getNormalizedForm() instanceof MedicationFrequency) ) {
         return null;
      }
      final MedicationFrequency f = (MedicationFrequency) mod.getNormalizedForm();
      final String raw = (f.getNumber() == null ? "" : f.getNumber())
            + (f.getUnit() == null ? "" : " " + f.getUnit());
      return normalizeNerFrequency( raw );
   }

   /** CAS route modifier value — shared with UmlsJsonFormatter (map.entities). */
   static public String extractRoute( final MedicationMention med ) {
      final MedicationRouteModifier mod = med.getMedicationRoute();
      if ( mod == null || !(mod.getNormalizedForm() instanceof MedicationRoute) ) {
         return null;
      }
      return ( (MedicationRoute) mod.getNormalizedForm() ).getValue();
   }

   static private boolean inSections( final IdentifiedAnnotation ann,
                                      final Collection<Segment> segments,
                                      final Set<String> names ) {
      final String segId = ann.getSegmentID();
      if ( segId != null ) {
         for ( String name : names ) {
            if ( segId.toLowerCase( Locale.ROOT ).contains( name ) ) {
               return true;
            }
         }
      }
      for ( Segment segment : segments ) {
         if ( ann.getBegin() >= segment.getBegin() && ann.getEnd() <= segment.getEnd() ) {
            final String sn = sectionName( segment );
            for ( String name : names ) {
               if ( sn.contains( name ) ) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   static private String sectionName( final Segment segment ) {
      final StringBuilder sb = new StringBuilder();
      if ( segment.getId() != null ) {
         sb.append( segment.getId() ).append( ' ' );
      }
      if ( segment.getPreferredText() != null ) {
         sb.append( segment.getPreferredText() ).append( ' ' );
      }
      if ( segment.getTagText() != null ) {
         sb.append( segment.getTagText() );
      }
      return sb.toString().toLowerCase( Locale.ROOT );
   }

   static private boolean textLooksCoag( final String text ) {
      if ( text == null ) {
         return false;
      }
      final String t = text.toLowerCase( Locale.ROOT );
      return t.contains( "inr" ) || t.contains( "ptt" ) || t.contains( "platelet" )
            || t.contains( "coag" ) || t.contains( "aPTT".toLowerCase() );
   }

   static private boolean isJunkEvent( final HandoverDocument.EventDto event ) {
      if ( event == null || isBlank( event.text ) ) {
         return true;
      }
      final String t = event.text.trim().toLowerCase( Locale.ROOT );
      if ( JUNK_EVENT_TEXTS.contains( t ) ) {
         return true;
      }
      return event.cui != null && JUNK_EVENT_CUIS.contains( event.cui );
   }

   static private boolean overlaps( final HandoverDocument.ConceptDto a, final HandoverDocument.ConceptDto b ) {
      return a.begin == b.begin && a.end == b.end;
   }

   /** Prefer longer spans when assessment problems overlap / nest. */
   static private void dedupeAssessment( final HandoverDocument doc ) {
      doc.assessment.problems = dedupeConceptsByOverlap( doc.assessment.problems );
   }

   /**
    * Longest-span-first overlap filter — same shape as {@link #dedupeMedications} /
    * {@link #dedupeImaging}. Package-visible for unit tests.
    */
   static List<HandoverDocument.ConceptDto> dedupeConceptsByOverlap(
         final List<HandoverDocument.ConceptDto> concepts ) {
      if ( concepts == null || concepts.size() <= 1 ) {
         return concepts == null ? new ArrayList<>() : new ArrayList<>( concepts );
      }
      final List<HandoverDocument.ConceptDto> sorted = new ArrayList<>( concepts );
      sorted.sort( ( a, b ) -> {
         final int lenA = a.end - a.begin;
         final int lenB = b.end - b.begin;
         if ( lenB != lenA ) {
            return Integer.compare( lenB, lenA );
         }
         return Integer.compare( a.begin, b.begin );
      } );
      final List<HandoverDocument.ConceptDto> kept = new ArrayList<>();
      for ( HandoverDocument.ConceptDto concept : sorted ) {
         boolean overlaps = false;
         for ( HandoverDocument.ConceptDto existing : kept ) {
            if ( concept.begin < existing.end && concept.end > existing.begin ) {
               overlaps = true;
               break;
            }
         }
         if ( !overlaps ) {
            kept.add( concept );
         }
      }
      kept.sort( Comparator.comparingInt( c -> c.begin ) );
      return kept;
   }

   static private Set<String> setOf( final String... values ) {
      return Arrays.stream( values ).collect( Collectors.toCollection( HashSet::new ) );
   }
}
