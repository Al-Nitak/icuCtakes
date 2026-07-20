package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.GcsMention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects Glasgow Coma Scale values, including under-sedation cues.
 */
@PipeBitInfo(
      name = "GCS Annotator",
      description = "Detects GCS numeric scores in clinical text.",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class GcsAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( GcsAnnotator.class );

   static private final Pattern GCS_PATTERN = Pattern.compile(
         "\\bGCS\\b(?:\\s+(?:under\\s+sedation|sedated))?\\s*"
               + "(?:(?:is|of|about|=|:|dropped\\s+to|fell\\s+to|to)?\\s*){0,3}(\\d{1,2})",
         Pattern.CASE_INSENSITIVE );

   static private final Pattern SEDATION_NEAR = Pattern.compile(
         "under\\s+sedation|sedated", Pattern.CASE_INSENSITIVE );

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final String text = jCas.getDocumentText();
      if ( text == null || text.isEmpty() ) {
         return;
      }
      final Matcher matcher = GCS_PATTERN.matcher( text );
      while ( matcher.find() ) {
         final int value = Integer.parseInt( matcher.group( 1 ) );
         if ( value < 3 || value > 15 ) {
            continue;
         }
         final GcsMention mention = new GcsMention( jCas, matcher.start(), matcher.end() );
         mention.setValue( value );
         final String window = text.substring( Math.max( 0, matcher.start() - 20 ),
               Math.min( text.length(), matcher.end() + 20 ) );
         mention.setUnderSedation( SEDATION_NEAR.matcher( window ).find()
               || SEDATION_NEAR.matcher( matcher.group( 0 ) ).find() );
         mention.addToIndexes();
      }
      LOGGER.debug( "GCS annotation complete." );
   }
}
