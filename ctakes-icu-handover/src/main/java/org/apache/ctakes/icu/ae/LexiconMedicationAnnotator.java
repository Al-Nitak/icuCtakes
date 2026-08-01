package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.util.LexiconLoader;
import org.apache.ctakes.typesystem.type.refsem.UmlsConcept;
import org.apache.ctakes.typesystem.type.textsem.MedicationMention;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Creates {@link MedicationMention} spans from ICU drug lexicons when dictionary NER
 * missed them (common on ICD-lean local dictionaries).
 */
@PipeBitInfo(
      name = "Lexicon Medication Annotator",
      description = "Annotates ICU lexicon drug names as MedicationMention when absent.",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class LexiconMedicationAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( LexiconMedicationAnnotator.class );

   static private final List<LexiconLoader.CodedEntry> DRUGS;

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
      DRUGS = List.copyOf( drugs );
   }

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final String text = jCas.getDocumentText();
      if ( text == null || text.isEmpty() ) {
         return;
      }
      final List<MedicationMention> existing = new ArrayList<>( JCasUtil.select( jCas, MedicationMention.class ) );
      int created = 0;
      for ( LexiconLoader.CodedEntry entry : DRUGS ) {
         final Matcher matcher = entry.pattern.matcher( text );
         while ( matcher.find() ) {
            final int begin = matcher.start();
            final int end = matcher.end();
            if ( overlaps( existing, begin, end ) ) {
               continue;
            }
            final MedicationMention mention = new MedicationMention( jCas, begin, end );
            if ( entry.cui != null || entry.code != null ) {
               final UmlsConcept concept = new UmlsConcept( jCas );
               concept.setCui( entry.cui );
               concept.setCodingScheme( entry.codingScheme );
               concept.setCode( entry.code );
               concept.setPreferredText( entry.preferredText );
               final FSArray arr = new FSArray( jCas, 1 );
               arr.set( 0, concept );
               mention.setOntologyConceptArr( arr );
            }
            mention.addToIndexes();
            existing.add( mention );
            created++;
         }
      }
      LOGGER.debug( "Lexicon medication annotation complete ({} new).", created );
   }

   static private boolean overlaps( final List<MedicationMention> existing, final int begin, final int end ) {
      for ( MedicationMention m : existing ) {
         if ( begin < m.getEnd() && end > m.getBegin() ) {
            return true;
         }
      }
      return false;
   }
}
