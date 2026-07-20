package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.VascularAccessMention;
import org.apache.ctakes.icu.util.LexiconLoader;
import org.apache.ctakes.typesystem.type.textsem.TimeMention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects vascular access / lines and optionally links nearby TimeMentions.
 */
@PipeBitInfo(
      name = "Vascular Access Annotator",
      description = "Detects CVC, PICC, arterial line, HD catheter, peripheral IV.",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class VascularAccessAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( VascularAccessAnnotator.class );

   static private final Map<Pattern, String> ACCESS_PATTERNS
         = LexiconLoader.loadPatternLabels( "org/apache/ctakes/icu/data/access_devices.txt" );

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final String text = jCas.getDocumentText();
      if ( text == null || text.isEmpty() ) {
         return;
      }
      for ( Map.Entry<Pattern, String> entry : ACCESS_PATTERNS.entrySet() ) {
         final Matcher matcher = entry.getKey().matcher( text );
         while ( matcher.find() ) {
            final VascularAccessMention mention
                  = new VascularAccessMention( jCas, matcher.start(), matcher.end() );
            mention.setAccessType( entry.getValue() );
            linkNearbyTime( jCas, mention );
            mention.addToIndexes();
         }
      }
      LOGGER.debug( "Vascular access annotation complete." );
   }

   static private void linkNearbyTime( final JCas jCas, final VascularAccessMention mention ) {
      TimeMention closest = null;
      int bestDist = Integer.MAX_VALUE;
      for ( TimeMention time : JCasUtil.select( jCas, TimeMention.class ) ) {
         final int dist = Math.min(
               Math.abs( time.getBegin() - mention.getEnd() ),
               Math.abs( mention.getBegin() - time.getEnd() ) );
         if ( dist < 80 && dist < bestDist ) {
            bestDist = dist;
            closest = time;
         }
      }
      if ( closest != null ) {
         mention.setInsertTime( closest );
      }
   }
}
