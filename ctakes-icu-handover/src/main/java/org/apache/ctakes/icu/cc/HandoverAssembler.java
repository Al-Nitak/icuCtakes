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
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.jcas.tcas.Annotation;

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
   static private final List<LexiconLoader.CodedEntry> DRUG_LEXICON;
   static private final List<LexiconLoader.CodedEntry> ACCESS_LEXICON
         = LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/access_devices.txt" );

   static {
      final List<LexiconLoader.CodedEntry> drugs = new ArrayList<>();
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/antibiotics.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/vasopressors.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/anticoagulants.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/sedatives.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/gi_meds.txt" ) );
      drugs.addAll( LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/diuretics.txt" ) );
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
   /** Characters before a med mention to scan for route (e.g. oral vancomycin). */
   static private final int MED_ATTR_WINDOW_BEFORE = 32;

   static private final Pattern FINDING_CONTINUATION = Pattern.compile(
         "^(?:it\\s+)?(?:showed|revealed|demonstrated|found|reported|with)\\b",
         Pattern.CASE_INSENSITIVE );

   /**
    * ICU dose/rate immediately after a drug name, e.g. {@code 5 mg/h}, {@code 2 g},
    * {@code 0.1 mcg/kg/min}.
    */
   static private final Pattern WINDOW_DOSE = Pattern.compile(
         "(?i)(?:^|[\\s:=])(\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|µg|ug|g|units?|u)"
               + "(?:\\s*/\\s*(?:h|hr|hour|kg(?:\\s*/\\s*min)?|min))?)" );

   static private final Pattern WINDOW_ROUTE = Pattern.compile(
         "(?i)\\b(IVP|IV|PO|SQ|SC|IM|NGT|NG|SL|PR|oral|by\\s+mouth)\\b" );

   static private final Pattern WINDOW_FREQUENCY = Pattern.compile(
         "(?i)\\b(q\\d+h?|bid|tid|qid|od|bd|tds|daily|prn|as\\s+needed|/24|/12|/8)\\b" );

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

   static public HandoverDocument createDocument( final JCas jCas ) {
      final HandoverDocument doc = new HandoverDocument();
      doc.documentId = DocIdUtil.getDocumentID( jCas );
      final String docText = jCas.getDocumentText();

      final Map<IdentifiedAnnotation, List<String>> locations = buildLocationIndex( jCas );
      final List<Segment> segments = new ArrayList<>( JCasUtil.select( jCas, Segment.class ) );

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
         final HandoverDocument.ConceptDto concept = toConcept( dd, locations );
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
         doc.imaging.add( toImagingFromStudy( jCas, study, locations ) );
      }

      for ( ProcedureMention proc : JCasUtil.select( jCas, ProcedureMention.class ) ) {
         final HandoverDocument.ConceptDto concept = toConcept( proc, locations );
         if ( IdentifiedAnnotationUtil.isHistoric( proc ) || inSections( proc, segments, BACKGROUND_SECTIONS ) ) {
            doc.background.priorProcedures.add( concept );
         }
         final HandoverDocument.EventDto event = toEvent( proc );
         doc.situation.events.add( event );
         if ( !imagingOverlaps( doc, proc.getBegin(), proc.getEnd() ) ) {
            maybeAddImaging( jCas, doc, proc, locations );
         }
      }

      for ( SignSymptomMention ss : JCasUtil.select( jCas, SignSymptomMention.class ) ) {
         final HandoverDocument.ConceptDto concept = toConcept( ss, locations );
         doc.situation.events.add( toEvent( jCas, ss ) );
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
            doc.systems.hematology.coagStatus.add( toConcept( lab, locations ) );
         }
      }

      // Medications by class / section
      for ( MedicationMention med : JCasUtil.select( jCas, MedicationMention.class ) ) {
         final HandoverDocument.MedDto medDto = toMed( med, segments, docText );
         final String drugClass = medDto.drugClass;
         if ( "antibiotic".equals( drugClass ) || "antifungal".equals( drugClass ) ) {
            doc.access.antibiotics.add( medDto );
         }
         if ( "sedative".equals( drugClass ) || inSections( med, segments, CNS_SECTIONS ) ) {
            doc.systems.cns.medications.add( medDto );
         }
         if ( "vasopressor".equals( drugClass ) || inSections( med, segments, CVS_SECTIONS ) ) {
            doc.systems.cvs.medications.add( medDto );
         }
         if ( inSections( med, segments, RESP_SECTIONS ) ) {
            doc.systems.respiratory.medications.add( medDto );
         }
         if ( "gi".equals( drugClass ) || inSections( med, segments, GI_SECTIONS ) ) {
            doc.systems.gi.medications.add( medDto );
         }
         if ( "diuretic".equals( drugClass ) || inSections( med, segments, GU_SECTIONS ) ) {
            doc.systems.gu.medications.add( medDto );
         }
         if ( "anticoagulant".equals( drugClass ) ) {
            doc.systems.hematology.anticoagulants.add( medDto );
         }
      }

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
         doc.systems.labs.add( dto );
      }

      // CVS status inference
      final boolean onPressors = doc.systems.cvs.medications.stream()
            .anyMatch( m -> "vasopressor".equals( m.drugClass ) );
      doc.systems.cvs.status.put( "label", onPressors ? "on_pressors" : "unknown" );
      doc.systems.cvs.status.put( "evidence", onPressors ? "vasopressor medication detected" : "" );

      // ICU slots
      for ( GcsMention gcs : JCasUtil.select( jCas, GcsMention.class ) ) {
         final HandoverDocument.GcsDto dto = new HandoverDocument.GcsDto();
         dto.value = gcs.getValue();
         dto.underSedation = gcs.getUnderSedation();
         dto.text = gcs.getCoveredText();
         dto.begin = gcs.getBegin();
         dto.end = gcs.getEnd();
         // keep last / most recent by offset
         doc.systems.cns.gcs = dto;
      }

      for ( VentSupportMention vent : JCasUtil.select( jCas, VentSupportMention.class ) ) {
         final HandoverDocument.VentDto dto = new HandoverDocument.VentDto();
         dto.mode = vent.getMode();
         dto.text = vent.getCoveredText();
         dto.begin = vent.getBegin();
         dto.end = vent.getEnd();
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
         doc.plan.items.add( dto );
      }

      doc.situation.events = mergeAdjacentSituationEvents( jCas, doc.situation.events );
      doc.situation.events.sort( Comparator.comparingInt( e -> e.begin ) );
      doc.imaging = dedupeImaging( doc.imaging );
      dedupeAssessment( doc );
      return doc;
   }

   static private void maybeAddImaging( final JCas jCas,
                                        final HandoverDocument doc,
                                        final ProcedureMention proc,
                                        final Map<IdentifiedAnnotation, List<String>> locations ) {
      final String text = proc.getCoveredText();
      if ( text == null || !IMAGING.matcher( text ).find() ) {
         return;
      }
      final HandoverDocument.ImagingDto img = new HandoverDocument.ImagingDto();
      img.procedureText = text;
      img.begin = proc.getBegin();
      img.end = proc.getEnd();
      img.modality = detectModality( text );
      img.bodySite = parseBodySiteFromText( text );
      final List<String> locs = locations.get( proc );
      if ( img.bodySite == null && locs != null && !locs.isEmpty() ) {
         img.bodySite = locs.get( 0 );
      }
      attachSentenceFindings( jCas, img, locations );
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
         final Map<IdentifiedAnnotation, List<String>> locations ) {
      final HandoverDocument.ImagingDto img = new HandoverDocument.ImagingDto();
      img.modality = study.getModality() != null ? study.getModality() : detectModality( study.getCoveredText() );
      img.procedureText = study.getCoveredText() != null ? study.getCoveredText().trim() : null;
      img.begin = study.getBegin();
      img.end = study.getEnd();
      img.bodySite = parseBodySiteFromText( study.getCoveredText() );
      attachSentenceFindings( jCas, img, locations );
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
         final Map<IdentifiedAnnotation, List<String>> locations ) {
      final List<Sentence> sentences = new ArrayList<>( JCasUtil.select( jCas, Sentence.class ) );
      Sentence primary = null;
      for ( Sentence sentence : sentences ) {
         if ( img.begin < sentence.getEnd() && img.end > sentence.getBegin() ) {
            primary = sentence;
            break;
         }
      }
      if ( primary == null ) {
         return;
      }
      attachCoveredFindings( jCas, img, primary.getBegin(), primary.getEnd(), locations );
      final int idx = sentences.indexOf( primary );
      if ( idx >= 0 && idx + 1 < sentences.size() ) {
         final Sentence next = sentences.get( idx + 1 );
         final String nextText = next.getCoveredText();
         if ( nextText != null && FINDING_CONTINUATION.matcher( nextText.trim() ).find() ) {
            attachCoveredFindings( jCas, img, next.getBegin(), next.getEnd(), locations );
         }
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
         final Map<IdentifiedAnnotation, List<String>> locations ) {
      for ( DiseaseDisorderMention dd : JCasUtil.select( jCas, DiseaseDisorderMention.class ) ) {
         if ( dd.getBegin() >= begin && dd.getEnd() <= end ) {
            addFindingUnique( img, toConcept( dd, locations ) );
         }
      }
      for ( SignSymptomMention ss : JCasUtil.select( jCas, SignSymptomMention.class ) ) {
         if ( ss.getBegin() >= begin && ss.getEnd() <= end ) {
            addFindingUnique( img, toConcept( ss, locations ) );
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
                                                         final Map<IdentifiedAnnotation, List<String>> locations ) {
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
      return dto;
   }

   static private HandoverDocument.EventDto toEvent( final EventMention mention ) {
      return toEvent( null, mention );
   }

   /**
    * Builds an event DTO. When {@code jCas} is provided and the mention has a DegreeOf
    * severity relation, the span is expanded to cover both finding and modifier.
    */
   static private HandoverDocument.EventDto toEvent( final JCas jCas, final EventMention mention ) {
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
      return dto;
   }

   static private HandoverDocument.MedDto toMed( final MedicationMention med,
                                                 final List<Segment> segments,
                                                 final String docText ) {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      dto.text = med.getCoveredText();
      dto.begin = med.getBegin();
      dto.end = med.getEnd();
      dto.sectionId = med.getSegmentID();
      dto.preferredText = IdentifiedAnnotationUtil.getPreferredText( med );
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
      // Drug NER modifiers are usually absent on Tiny REST — fill from text after the span.
      if ( isBlank( dto.dose ) || isBlank( dto.frequency ) || isBlank( dto.route ) ) {
         fillMedAttributesFromWindow( dto, windowAfter( docText, med.getEnd(), MED_ATTR_WINDOW ) );
      }
      if ( isBlank( dto.route ) ) {
         fillMedAttributesFromBeforeWindow( dto, windowBefore( docText, med.getBegin(), MED_ATTR_WINDOW_BEFORE ) );
      }
      inferRouteFromMedText( dto );
      if ( isBlank( dto.dose ) && !isBlank( dto.strength ) ) {
         dto.dose = dto.strength.trim();
      }
      dto.drugClass = classifyDrug( med.getCoveredText(), dto.preferredText );
      return dto;
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
         final Matcher doseMatcher = WINDOW_DOSE.matcher( window );
         if ( doseMatcher.find() ) {
            dto.dose = doseMatcher.group( 1 ).replaceAll( "\\s+", " " ).trim();
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
            String freq = freqMatcher.group( 1 ).replaceAll( "\\s+", " " ).trim();
            if ( freq.equalsIgnoreCase( "as needed" ) ) {
               freq = "PRN";
            } else if ( freq.equalsIgnoreCase( "prn" ) ) {
               freq = "PRN";
            } else if ( !freq.startsWith( "/" ) ) {
               // keep q24h / daily casing light — normalize common tokens
               if ( freq.equalsIgnoreCase( "daily" ) ) {
                  freq = "daily";
               } else {
                  freq = freq.toLowerCase( Locale.ROOT );
               }
            }
            dto.frequency = freq;
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
    * Parse route from text immediately before a medication mention (e.g. oral vancomycin).
    */
   static void fillMedAttributesFromBeforeWindow( final HandoverDocument.MedDto dto, final String window ) {
      if ( dto == null || isBlank( window ) || !isBlank( dto.route ) ) {
         return;
      }
      final Matcher routeMatcher = WINDOW_ROUTE.matcher( window );
      String route = null;
      while ( routeMatcher.find() ) {
         route = routeMatcher.group( 1 );
      }
      if ( route != null ) {
         dto.route = normalizeRoute( route );
      }
   }

   static private String normalizeRoute( final String route ) {
      if ( route == null ) {
         return null;
      }
      final String r = route.replaceAll( "\\s+", " " ).trim();
      if ( r.equalsIgnoreCase( "oral" ) || r.equalsIgnoreCase( "by mouth" ) ) {
         return "PO";
      }
      return r.toUpperCase( Locale.ROOT );
   }

   static private String windowAfter( final String docText, final int end, final int length ) {
      if ( docText == null || end < 0 || end >= docText.length() ) {
         return "";
      }
      final int stop = Math.min( docText.length(), end + Math.max( 0, length ) );
      return docText.substring( end, stop );
   }

   static private String windowBefore( final String docText, final int begin, final int length ) {
      if ( docText == null || begin <= 0 ) {
         return "";
      }
      final int start = Math.max( 0, begin - Math.max( 0, length ) );
      return docText.substring( start, begin );
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

   static private String classifyDrug( final String text, final String preferred ) {
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

   static private String extractDose( final MedicationMention med ) {
      final MedicationDosageModifier mod = med.getMedicationDosage();
      if ( mod == null || !(mod.getNormalizedForm() instanceof MedicationDosage) ) {
         return null;
      }
      return ( (MedicationDosage) mod.getNormalizedForm() ).getValue();
   }

   static private String extractStrength( final MedicationMention med ) {
      final MedicationStrengthModifier mod = med.getMedicationStrength();
      if ( mod == null || !(mod.getNormalizedForm() instanceof MedicationStrength) ) {
         return null;
      }
      final MedicationStrength s = (MedicationStrength) mod.getNormalizedForm();
      return (s.getNumber() == null ? "" : s.getNumber()) + (s.getUnit() == null ? "" : " " + s.getUnit());
   }

   static private String extractFrequency( final MedicationMention med ) {
      final MedicationFrequencyModifier mod = med.getMedicationFrequency();
      if ( mod == null || !(mod.getNormalizedForm() instanceof MedicationFrequency) ) {
         return null;
      }
      final MedicationFrequency f = (MedicationFrequency) mod.getNormalizedForm();
      return (f.getNumber() == null ? "" : f.getNumber()) + (f.getUnit() == null ? "" : " " + f.getUnit());
   }

   static private String extractRoute( final MedicationMention med ) {
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

   static private boolean overlaps( final HandoverDocument.ConceptDto a, final HandoverDocument.ConceptDto b ) {
      return a.begin == b.begin && a.end == b.end;
   }

   static private void dedupeAssessment( final HandoverDocument doc ) {
      final Set<String> seen = new HashSet<>();
      final List<HandoverDocument.ConceptDto> unique = new ArrayList<>();
      for ( HandoverDocument.ConceptDto p : doc.assessment.problems ) {
         final String key = p.begin + ":" + p.end + ":" + p.text;
         if ( seen.add( key ) ) {
            unique.add( p );
         }
      }
      doc.assessment.problems = unique;
   }

   static private Set<String> setOf( final String... values ) {
      return Arrays.stream( values ).collect( Collectors.toCollection( HashSet::new ) );
   }
}
