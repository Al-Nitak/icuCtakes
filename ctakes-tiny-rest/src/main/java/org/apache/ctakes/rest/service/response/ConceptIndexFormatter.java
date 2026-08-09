package org.apache.ctakes.rest.service.response;

import com.google.gson.GsonBuilder;
import org.apache.ctakes.core.util.annotation.IdentifiedAnnotationUtil;
import org.apache.ctakes.core.util.annotation.OntologyConceptUtil;
import org.apache.ctakes.core.util.annotation.SemanticTui;
import org.apache.ctakes.icu.util.SentenceIndex;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.textsem.AnatomicalSiteMention;
import org.apache.ctakes.typesystem.type.textsem.DiseaseDisorderMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textsem.MedicationMention;
import org.apache.ctakes.typesystem.type.textsem.ProcedureMention;
import org.apache.ctakes.typesystem.type.textsem.SignSymptomMention;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deduplicated CUI rollup for knowledge search / drug interaction / differential diagnosis.
 * Each entry carries occurrence offsets and sentenceIds joinable to the shared sentence index.
 */
final public class ConceptIndexFormatter implements ResponseFormatter {

   static private final List<Class<? extends IdentifiedAnnotation>> WANTED_CLASSES = Arrays.asList(
         DiseaseDisorderMention.class,
         SignSymptomMention.class,
         ProcedureMention.class,
         AnatomicalSiteMention.class,
         MedicationMention.class
   );

   private final SentenceIndex sentenceIndex;

   public ConceptIndexFormatter() {
      this( null );
   }

   public ConceptIndexFormatter( final SentenceIndex sentenceIndex ) {
      this.sentenceIndex = sentenceIndex;
   }

   @Override
   public String getResultText( final JCas jCas ) throws AnalysisEngineProcessException {
      final SentenceIndex sentences = sentenceIndex != null ? sentenceIndex : SentenceIndex.of( jCas );
      final Map<String, ConceptEntry> byCui = new LinkedHashMap<>();

      for ( IdentifiedAnnotation ann : JCasUtil.select( jCas, IdentifiedAnnotation.class ) ) {
         if ( !WANTED_CLASSES.contains( ann.getClass() ) ) {
            continue;
         }
         final UmlsConcept umls = OntologyConceptUtil.getUmlsConceptStream( ann ).findFirst().orElse( null );
         if ( umls == null || umls.getCui() == null || umls.getCui().isBlank() ) {
            continue;
         }
         final String cui = umls.getCui();
         ConceptEntry entry = byCui.get( cui );
         if ( entry == null ) {
            entry = new ConceptEntry();
            entry.cui = cui;
            entry.preferredText = textOrNull( umls.getPreferredText() );
            entry.codingScheme = textOrNull( umls.getCodingScheme() );
            entry.code = textOrNull( umls.getCode() );
            final SemanticTui semanticTui = SemanticTui.getTui( umls );
            entry.tui = textOrNull( umls.getTui() );
            entry.semanticGroup = semanticTui.getGroupName();
            entry.semanticType = semanticTui.getSemanticType();
            byCui.put( cui, entry );
         }
         entry.typeSet.add( ann.getClass().getSimpleName() );
         final Occurrence occ = new Occurrence();
         occ.begin = ann.getBegin();
         occ.end = ann.getEnd();
         occ.text = ann.getCoveredText();
         occ.sentenceId = sentences.idFor( ann.getBegin(), ann.getEnd() );
         occ.negated = booleanOrNull( IdentifiedAnnotationUtil.isNegated( ann ) );
         occ.uncertain = booleanOrNull( IdentifiedAnnotationUtil.isUncertain( ann ) );
         occ.historic = booleanOrNull( IdentifiedAnnotationUtil.isHistoric( ann ) );
         entry.occurrences.add( occ );
         if ( occ.sentenceId != null ) {
            entry.sentenceIdSet.add( occ.sentenceId );
         }
      }

      final List<ConceptEntry> concepts = new ArrayList<>( byCui.values() );
      for ( ConceptEntry entry : concepts ) {
         entry.count = entry.occurrences.size();
         entry.sentenceIds = new ArrayList<>( entry.sentenceIdSet );
         entry.sentenceIds.sort( Integer::compareTo );
         entry.types = new ArrayList<>( entry.typeSet );
         entry.types.sort( String::compareTo );
         entry.sentenceIdSet = null;
         entry.typeSet = null;
      }
      concepts.sort( Comparator
            .comparing( (ConceptEntry c) -> c.semanticGroup == null ? "" : c.semanticGroup )
            .thenComparing( c -> c.preferredText == null ? "" : c.preferredText )
            .thenComparing( c -> c.cui ) );

      return new GsonBuilder()
            .setPrettyPrinting()
            .create()
            .toJson( concepts );
   }

   static private Boolean booleanOrNull( final boolean value ) {
      return value ? Boolean.TRUE : null;
   }

   static private String textOrNull( final String text ) {
      return text == null || text.isEmpty() ? null : text;
   }

   static final class ConceptEntry {
      public String cui;
      public String preferredText;
      public String codingScheme;
      public String code;
      public String tui;
      public String semanticGroup;
      public String semanticType;
      public List<String> types = new ArrayList<>();
      public int count;
      public List<Integer> sentenceIds = new ArrayList<>();
      public List<Occurrence> occurrences = new ArrayList<>();

      transient Set<String> typeSet = new LinkedHashSet<>();
      transient Set<Integer> sentenceIdSet = new TreeSet<>();
   }

   static final class Occurrence {
      public int begin;
      public int end;
      public String text;
      public Integer sentenceId;
      public Boolean negated;
      public Boolean uncertain;
      public Boolean historic;
   }
}
