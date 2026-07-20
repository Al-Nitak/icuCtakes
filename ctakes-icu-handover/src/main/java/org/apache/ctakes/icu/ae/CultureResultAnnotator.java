package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.CultureResultMention;
import org.apache.ctakes.icu.util.LexiconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.StringArray;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects culture site / organism / sensitivity cues in narrative text.
 */
@PipeBitInfo(
      name = "Culture Result Annotator",
      description = "Detects microbiology culture site, organism, and sensitivity tokens.",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class CultureResultAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( CultureResultAnnotator.class );

   static private final List<Pattern> SITES = LexiconLoader.loadPatterns(
         "org/apache/ctakes/icu/data/culture_sites.txt" );
   static private final List<Pattern> ORGANISMS = LexiconLoader.loadPatterns(
         "org/apache/ctakes/icu/data/organisms.txt" );
   static private final Pattern CULTURE_CUE = Pattern.compile(
         "\\bcultures?\\b|\\bgrowing\\b|\\bsensitive\\b|\\bresistant\\b|\\borganism\\b",
         Pattern.CASE_INSENSITIVE );
   static private final Pattern SENS = Pattern.compile(
         "\\b([A-Za-z]{2,12})\\s*[-–]\\s*([SRIri])\\b" );

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final String text = jCas.getDocumentText();
      if ( text == null || text.isEmpty() ) {
         return;
      }
      if ( !CULTURE_CUE.matcher( text ).find() ) {
         // Still try organism mentions; many notes omit the word "culture"
         annotateOrganisms( jCas, text );
         return;
      }
      annotateOrganisms( jCas, text );
      LOGGER.debug( "Culture result annotation complete." );
   }

   static private void annotateOrganisms( final JCas jCas, final String text ) {
      for ( Pattern orgPattern : ORGANISMS ) {
         final Matcher orgMatcher = orgPattern.matcher( text );
         while ( orgMatcher.find() ) {
            final int begin = Math.max( 0, orgMatcher.start() - 60 );
            final int end = Math.min( text.length(), orgMatcher.end() + 80 );
            final String window = text.substring( begin, end );
            final CultureResultMention mention
                  = new CultureResultMention( jCas, orgMatcher.start(), orgMatcher.end() );
            mention.setOrganism( orgMatcher.group().trim() );
            mention.setSite( findFirst( SITES, window ) );
            final List<String> sens = new ArrayList<>();
            final Matcher sensMatcher = SENS.matcher( window );
            while ( sensMatcher.find() ) {
               sens.add( sensMatcher.group( 1 ) + "-" + sensMatcher.group( 2 ).toUpperCase() );
            }
            if ( !sens.isEmpty() ) {
               final StringArray arr = new StringArray( jCas, sens.size() );
               for ( int i = 0; i < sens.size(); i++ ) {
                  arr.set( i, sens.get( i ) );
               }
               mention.setSensitivities( arr );
            }
            mention.addToIndexes();
         }
      }
   }

   static private String findFirst( final List<Pattern> patterns, final String window ) {
      for ( Pattern pattern : patterns ) {
         final Matcher m = pattern.matcher( window );
         if ( m.find() ) {
            return m.group().trim();
         }
      }
      return null;
   }
}
