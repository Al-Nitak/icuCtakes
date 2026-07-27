package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.LabValueMention;
import org.apache.ctakes.icu.util.LexiconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects common ICU lab names with numeric values (creatinine, CRP, INR, etc.).
 */
@PipeBitInfo(
      name = "Lab Value Annotator",
      description = "Detects P0 ICU lab tests with numeric values.",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class LabValueAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( LabValueAnnotator.class );

   static private final List<LexiconLoader.CodedEntry> LABS = LexiconLoader.loadCodedEntries(
         "org/apache/ctakes/icu/data/labs.txt" );

   static private final Pattern VALUE_AFTER = Pattern.compile(
         "(?i)^\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)(?:\\s*(mg/dl|mmol/l|g/dl|mg/l|u/l|iu/l|%|k/uL|x10\\^9/l))?\\b" );

   static private final Pattern RISING_TO = Pattern.compile(
         "(?i)^\\s*(?:was\\s+)?(?:rising\\s+to|up\\s+to)\\s*(\\d+(?:\\.\\d+)?)\\b" );

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final String text = jCas.getDocumentText();
      if ( text == null || text.isEmpty() ) {
         return;
      }
      final List<int[]> spans = new ArrayList<>();
      for ( LexiconLoader.CodedEntry entry : LABS ) {
         final Matcher matcher = entry.pattern.matcher( text );
         while ( matcher.find() ) {
            final int begin = matcher.start();
            int spanEnd = matcher.end();
            if ( overlaps( spans, begin, spanEnd ) ) {
               continue;
            }
            String value = null;
            String unit = null;
            final String after = text.substring( spanEnd, Math.min( text.length(), spanEnd + 40 ) );
            final Matcher valueMatcher = VALUE_AFTER.matcher( after );
            if ( valueMatcher.find() ) {
               value = valueMatcher.group( 1 );
               unit = emptyToNull( valueMatcher.group( 2 ) );
               spanEnd = spanEnd + valueMatcher.end();
            } else {
               final Matcher risingAfter = RISING_TO.matcher( after );
               if ( risingAfter.find() ) {
                  value = risingAfter.group( 1 );
                  spanEnd = spanEnd + risingAfter.end();
               } else {
                  final int windowBegin = Math.max( 0, begin - 30 );
                  final String before = text.substring( windowBegin, begin );
                  final Matcher risingMatcher = RISING_TO.matcher( before );
                  if ( risingMatcher.find() ) {
                     value = risingMatcher.group( 1 );
                  }
               }
            }
            if ( value == null ) {
               continue;
            }
            final LabValueMention mention = new LabValueMention( jCas, begin, spanEnd );
            mention.setLabName( entry.preferredText );
            mention.setPreferredText( entry.preferredText );
            mention.setValue( value );
            mention.setUnit( unit );
            mention.setCui( entry.cui );
            mention.setCodingScheme( entry.codingScheme );
            mention.setCode( entry.code );
            mention.addToIndexes();
            spans.add( new int[]{ begin, spanEnd } );
         }
      }
      LOGGER.debug( "Lab value annotation complete ({}).", spans.size() );
   }

   static private boolean overlaps( final List<int[]> spans, final int begin, final int end ) {
      for ( int[] span : spans ) {
         if ( begin < span[ 1 ] && end > span[ 0 ] ) {
            return true;
         }
      }
      return false;
   }

   static private String emptyToNull( final String value ) {
      return value == null || value.isEmpty() ? null : value;
   }
}
