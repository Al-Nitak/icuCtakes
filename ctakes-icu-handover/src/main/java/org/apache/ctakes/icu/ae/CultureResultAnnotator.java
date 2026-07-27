package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.CultureResultMention;
import org.apache.ctakes.icu.util.LexiconLoader;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
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
         "\\bcultures?\\b|\\bgrowing\\b|\\bsensitive\\b|\\bresistant\\b|\\borganism\\b|\\bmultisensitive\\b",
         Pattern.CASE_INSENSITIVE );
   static private final Pattern SENS = Pattern.compile(
         "\\b([A-Za-z]{2,12})\\s*[-–]\\s*([SRIri])\\b" );
   static private final Pattern PHENOTYPE = Pattern.compile(
         "\\b(ESBL|CRE|MRSA|VRE|MDR|XDR|KPC|NDM|OXA[-\\s]?48)\\b",
         Pattern.CASE_INSENSITIVE );
   static private final Pattern NARRATIVE_SENS = Pattern.compile(
         "\\b(multisensitive|pan-?sensitive|sensitive|resistant)\\b",
         Pattern.CASE_INSENSITIVE );
   static private final Pattern SENS_TO = Pattern.compile(
         "\\bsensitive\\s+to\\s+([A-Za-z][A-Za-z0-9\\- ]{1,24})\\b",
         Pattern.CASE_INSENSITIVE );
   static private final Pattern RESIST_TO = Pattern.compile(
         "\\bresistant\\s+to\\s+([A-Za-z][A-Za-z0-9\\- ]{1,24})\\b",
         Pattern.CASE_INSENSITIVE );
   static private final Pattern PENDING_CULTURE = Pattern.compile(
         "\\bcultures?\\s+(?:were\\s+)?sent\\b|\\bcultures?\\s+pending\\b|\\bno\\s+growth\\b",
         Pattern.CASE_INSENSITIVE );

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final String text = jCas.getDocumentText();
      if ( text == null || text.isEmpty() ) {
         return;
      }
      annotateOrganisms( jCas, text );
      annotatePendingCultures( jCas, text );
      LOGGER.debug( "Culture result annotation complete." );
   }

   static private void annotateOrganisms( final JCas jCas, final String text ) {
      final List<Segment> segments = new ArrayList<>( JCasUtil.select( jCas, Segment.class ) );
      final boolean hasCultureCue = CULTURE_CUE.matcher( text ).find();
      final List<Sentence> sentences = new ArrayList<>( JCasUtil.select( jCas, Sentence.class ) );

      for ( LexiconLoader.CodedEntry entry : ORGANISMS ) {
         final Matcher orgMatcher = entry.pattern.matcher( text );
         while ( orgMatcher.find() ) {
            final int matchBegin = orgMatcher.start();
            final int matchEnd = orgMatcher.end();
            if ( !allowMatch( segments, matchBegin, matchEnd, hasCultureCue ) ) {
               continue;
            }
            final String window = sentenceContext( jCas, sentences, matchBegin, matchEnd, text );
            final CultureResultMention mention
                  = new CultureResultMention( jCas, matchBegin, matchEnd );
            final String span = orgMatcher.group().trim();
            mention.setOrganism( entry.preferredText != null ? entry.preferredText : span );
            mention.setPreferredText( entry.preferredText );
            mention.setCui( entry.cui );
            mention.setCodingScheme( entry.codingScheme );
            mention.setCode( entry.code );
            mention.setSite( findSite( window, sentences, matchBegin, matchEnd ) );

            final List<String> sens = extractSensitivities( window );
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

   static private void annotatePendingCultures( final JCas jCas, final String text ) {
      final Matcher matcher = PENDING_CULTURE.matcher( text );
      while ( matcher.find() ) {
         final String hit = matcher.group().toLowerCase( Locale.ROOT );
         final String status;
         if ( hit.contains( "no growth" ) ) {
            status = "no_growth";
         } else if ( hit.contains( "pending" ) ) {
            status = "pending";
         } else {
            status = "sent";
         }
         final CultureResultMention mention = new CultureResultMention( jCas, matcher.start(), matcher.end() );
         mention.setStatus( status );
         mention.setPreferredText( matcher.group().trim() );
         mention.addToIndexes();
      }
   }

   static public List<String> extractSensitivities( final String window ) {
      final List<String> sens = new ArrayList<>();
      if ( window == null || window.isEmpty() ) {
         return sens;
      }
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
      final Matcher narrativeMatcher = NARRATIVE_SENS.matcher( window );
      while ( narrativeMatcher.find() ) {
         final String token = narrativeMatcher.group( 1 ).toLowerCase( Locale.ROOT );
         if ( !containsSensToken( sens, token ) ) {
            sens.add( token );
         }
      }
      final Matcher sensToMatcher = SENS_TO.matcher( window );
      while ( sensToMatcher.find() ) {
         final String drug = sensToMatcher.group( 1 ).trim();
         sens.add( "sensitive to " + drug );
      }
      final Matcher resistToMatcher = RESIST_TO.matcher( window );
      while ( resistToMatcher.find() ) {
         final String drug = resistToMatcher.group( 1 ).trim();
         sens.add( "resistant to " + drug );
      }
      return sens;
   }

   static private boolean containsSensToken( final List<String> sens, final String token ) {
      for ( String s : sens ) {
         if ( s.equalsIgnoreCase( token ) || s.toLowerCase( Locale.ROOT ).contains( token ) ) {
            return true;
         }
      }
      return false;
   }

   static private String sentenceContext( final JCas jCas,
                                          final List<Sentence> sentences,
                                          final int begin,
                                          final int end,
                                          final String text ) {
      for ( Sentence sentence : sentences ) {
         if ( begin >= sentence.getBegin() && end <= sentence.getEnd() ) {
            return sentence.getCoveredText();
         }
         if ( begin < sentence.getEnd() && end > sentence.getBegin() ) {
            return sentence.getCoveredText();
         }
      }
      final int ctxBegin = Math.max( 0, begin - 60 );
      final int ctxEnd = Math.min( text.length(), end + 120 );
      return text.substring( ctxBegin, ctxEnd );
   }

   static private String findSite( final String window,
                                   final List<Sentence> sentences,
                                   final int begin,
                                   final int end ) {
      String site = findFirst( SITES, window );
      if ( site != null ) {
         return site;
      }
      final int idx = sentenceIndex( sentences, begin, end );
      if ( idx > 0 ) {
         site = findFirst( SITES, sentences.get( idx - 1 ).getCoveredText() );
      }
      if ( site == null && idx >= 0 && idx + 1 < sentences.size() ) {
         site = findFirst( SITES, sentences.get( idx + 1 ).getCoveredText() );
      }
      return site;
   }

   static private int sentenceIndex( final List<Sentence> sentences, final int begin, final int end ) {
      for ( int i = 0; i < sentences.size(); i++ ) {
         final Sentence s = sentences.get( i );
         if ( begin < s.getEnd() && end > s.getBegin() ) {
            return i;
         }
      }
      return -1;
   }

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
      if ( window == null ) {
         return null;
      }
      for ( Pattern pattern : patterns ) {
         final Matcher m = pattern.matcher( window );
         if ( m.find() ) {
            return m.group().trim();
         }
      }
      return null;
   }
}
