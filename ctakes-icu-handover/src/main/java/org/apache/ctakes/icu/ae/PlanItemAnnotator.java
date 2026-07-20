package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.PlanItem;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates PlanItem annotations from Plan section sentences, or imperative cues if no Plan section.
 */
@PipeBitInfo(
      name = "Plan Item Annotator",
      description = "Extracts plan / next-step items from Plan sections or imperative cues.",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class PlanItemAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( PlanItemAnnotator.class );

   static private final Pattern IMPERATIVE = Pattern.compile(
         "^(?:continue|start|stop|hold|increase|decrease|wean|extubate|consult|follow|monitor|"
               + "repeat|obtain|order|keep|maintain|titrate|d/c|discontinue)\\b",
         Pattern.CASE_INSENSITIVE );

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      boolean fromSection = false;
      for ( Segment segment : JCasUtil.select( jCas, Segment.class ) ) {
         if ( !isPlanSection( segment ) ) {
            continue;
         }
         fromSection = true;
         for ( Sentence sentence : JCasUtil.selectCovered( jCas, Sentence.class, segment ) ) {
            addPlanItem( jCas, sentence.getBegin(), sentence.getEnd(), sentence.getCoveredText() );
         }
      }
      if ( !fromSection ) {
         for ( Sentence sentence : JCasUtil.select( jCas, Sentence.class ) ) {
            final String text = sentence.getCoveredText().trim();
            if ( IMPERATIVE.matcher( text ).find() ) {
               addPlanItem( jCas, sentence.getBegin(), sentence.getEnd(), text );
            }
         }
      }
      LOGGER.debug( "Plan item annotation complete." );
   }

   static private boolean isPlanSection( final Segment segment ) {
      final String id = safe( segment.getId() );
      final String pref = safe( segment.getPreferredText() );
      final String tag = safe( segment.getTagText() );
      return containsPlan( id ) || containsPlan( pref ) || containsPlan( tag );
   }

   static private boolean containsPlan( final String value ) {
      return value.toLowerCase( Locale.ROOT ).contains( "plan" );
   }

   static private String safe( final String value ) {
      return value == null ? "" : value;
   }

   static private void addPlanItem( final JCas jCas, final int begin, final int end, final String text ) {
      if ( text == null || text.trim().isEmpty() ) {
         return;
      }
      final PlanItem item = new PlanItem( jCas, begin, end );
      item.setActionText( text.trim() );
      item.addToIndexes();
   }
}
