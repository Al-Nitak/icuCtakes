package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.CultureResultMention;
import org.apache.ctakes.icu.util.LexiconLoader;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.StringArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects culture site / organism / sensitivity cues and attaches lexicon codes.
 */
@PipeBitInfo(
      name = "Culture Result Annotator",
      description = "Detects microbiology culture site, organism, sensitivity, and coding tokens.",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class CultureResultAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( CultureResultAnnotator.class );

   static private final List<Pattern> SITES = LexiconLoader.loadPatterns(
         "org/apache/ctakes/icu/data/culture_sites.txt" );
   static private final List<LexiconLoader.CodedEntry> ORGANISMS = LexiconLoader.loadCodedEntries(
         "org/apache/ctakes/icu/data/organisms.txt" );
   static private final Pattern CULTURE_CUE = Pattern.compile(
         "\\bcultures?\\b|\\bgrowing\\b|\\bsensitive\\b|\\bresistant\\b|\\borganism\\b",
         Pattern.CASE_INSENSITIVE );
   static private final Pattern SENS = Pattern.compile(
         "\\b([A-Za-z]{2,12})\\s*[-–]\\s*([SRIri])\\b" );
   static private final Pattern PHENOTYPE = Pattern.compile(
         "\\b(ESBL|CRE|MRSA|VRE|MDR|XDR|KPC|NDM|OXA[-\\s]?48)\\b",
         Pattern.CASE_INSENSITIVE );

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final String text = jCas.getDocumentText();
      if ( text == null || text.isEmpty() ) {
         return;
      }
      annotateOrganisms( jCas, text );
      LOGGER.debug( "Culture result annotation complete." );
   }

   static private void annotateOrganisms( final JCas jCas, final String text ) {
      final List<Segment> segments = new ArrayList<>( JCasUtil.select( jCas, Segment.class ) );
      final boolean hasCultureCue = CULTURE_CUE.matcher( text ).find();

      for ( LexiconLoader.CodedEntry entry : ORGANISMS ) {
         final Matcher orgMatcher = entry.pattern.matcher( text );
         while ( orgMatcher.find() ) {
            final int matchBegin = orgMatcher.start();
            final int matchEnd = orgMatcher.end();
            if ( !allowMatch( segments, matchBegin, matchEnd, hasCultureCue ) ) {
               continue;
            }
            final int begin = Math.max( 0, matchBegin - 60 );
            final int end = Math.min( text.length(), matchEnd + 80 );
            final String window = text.substring( begin, end );
            final CultureResultMention mention
                  = new CultureResultMention( jCas, matchBegin, matchEnd );
            final String span = orgMatcher.group().trim();
            mention.setOrganism( entry.preferredText != null ? entry.preferredText : span );
            mention.setPreferredText( entry.preferredText );
            mention.setCui( entry.cui );
            mention.setCodingScheme( entry.codingScheme );
            mention.setCode( entry.code );
            mention.setSite( findFirst( SITES, window ) );

            final List<String> sens = new ArrayList<>();
            final Matcher sensMatcher = SENS.matcher( window );
            while ( sensMatcher.find() ) {
               sens.add( sensMatcher.group( 1 ) + "-" + sensMatcher.group( 2 ).toUpperCase( Locale.ROOT ) );
            }
            final Matcher phenoMatcher = PHENOTYPE.matcher( window );
            while ( phenoMatcher.find() ) {
               final String pheno = phenoMatcher.group( 1 ).toUpperCase( Locale.ROOT ).replaceAll( "\\s+", "" );
               if ( !sens.contains( pheno ) ) {
                  sens.add( pheno );
               }
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

   /**
    * Prefer Culture / Hematologic sections when segmentized; always allow if culture cues
    * exist in the note or no segments are present.
    */
   static private boolean allowMatch( final List<Segment> segments,
                                      final int begin, final int end,
                                      final boolean hasCultureCue ) {
      if ( segments.isEmpty() ) {
         return true;
      }
      for ( Segment segment : segments ) {
         if ( begin < segment.getBegin() || end > segment.getEnd() ) {
            continue;
         }
         final String id = segmentId( segment );
         if ( id.contains( "culture" ) || id.contains( "hematolog" ) || id.contains( "heme" ) ) {
            return true;
         }
      }
      // Outside culture-ish sections: still allow when the note clearly discusses cultures.
      return hasCultureCue;
   }

   static private String segmentId( final Segment segment ) {
      final StringBuilder sb = new StringBuilder();
      if ( segment.getId() != null ) {
         sb.append( segment.getId().toLowerCase( Locale.ROOT ) );
      }
      if ( segment.getPreferredText() != null ) {
         sb.append( ' ' ).append( segment.getPreferredText().toLowerCase( Locale.ROOT ) );
      }
      return sb.toString();
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
