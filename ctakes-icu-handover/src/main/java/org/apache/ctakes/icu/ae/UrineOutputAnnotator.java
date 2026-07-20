package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.UrineOutputMention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects urine output (UOP) measurements.
 */
@PipeBitInfo(
      name = "Urine Output Annotator",
      description = "Detects urine output values and units.",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class UrineOutputAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( UrineOutputAnnotator.class );

   static private final Pattern UOP = Pattern.compile(
         "\\b(?:UOP|urine\\s+output|urine)\\s*(?:of|=|:)?\\s*(\\d+(?:\\.\\d+)?)\\s*"
               + "(ml/?h(?:r)?|ml/?hr|cc/?h(?:r)?|ml|cc)\\b",
         Pattern.CASE_INSENSITIVE );

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final String text = jCas.getDocumentText();
      if ( text == null || text.isEmpty() ) {
         return;
      }
      final Matcher matcher = UOP.matcher( text );
      while ( matcher.find() ) {
         final UrineOutputMention mention = new UrineOutputMention( jCas, matcher.start(), matcher.end() );
         mention.setValue( Float.parseFloat( matcher.group( 1 ) ) );
         mention.setUnit( normalizeUnit( matcher.group( 2 ) ) );
         mention.addToIndexes();
      }
      LOGGER.debug( "Urine output annotation complete." );
   }

   static private String normalizeUnit( final String raw ) {
      final String t = raw.toLowerCase().replace( "cc", "ml" );
      if ( t.contains( "h" ) ) {
         return "ml/h";
      }
      return "ml";
   }
}
