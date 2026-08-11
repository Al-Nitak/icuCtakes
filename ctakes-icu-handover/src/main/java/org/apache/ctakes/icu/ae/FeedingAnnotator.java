package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.FeedingMention;
import org.apache.ctakes.icu.util.LexiconLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects feeding route and formula product mentions.
 */
@PipeBitInfo(
      name = "Feeding Annotator",
      description = "Detects feeding route (NGT/PEG/TPN/PO) and formula products.",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class FeedingAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( FeedingAnnotator.class );

   static private final Pattern ROUTE = Pattern.compile(
         "\\b(?:NGT|NG\\s*tube|nasogastric|PEG|OG\\s*tube|TPN|PO|per\\s*os|enteral(?:\\s+feeds?)?|"
               + "tube\\s+feeds?)\\b",
         Pattern.CASE_INSENSITIVE );

   static private final Set<String> FORMULAS
         = LexiconLoader.loadCodedTokenSet( "org/apache/ctakes/icu/data/feeding_formulas.txt" );

   /** Word-boundary patterns for formula tokens (avoids "vitally" → Vital). */
   static private final List<Pattern> FORMULA_PATTERNS = buildFormulaPatterns();

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final String text = jCas.getDocumentText();
      if ( text == null || text.isEmpty() ) {
         return;
      }

      final Matcher routeMatcher = ROUTE.matcher( text );
      while ( routeMatcher.find() ) {
         final FeedingMention mention = new FeedingMention( jCas, routeMatcher.start(), routeMatcher.end() );
         mention.setRoute( normalizeRoute( routeMatcher.group() ) );
         final String window = text.substring( Math.max( 0, routeMatcher.start() - 40 ),
               Math.min( text.length(), routeMatcher.end() + 60 ) );
         mention.setFormula( findFormula( window ) );
         mention.addToIndexes();
      }

      // Formula-only mentions — whole-word matches only.
      for ( Pattern formulaPattern : FORMULA_PATTERNS ) {
         final Matcher matcher = formulaPattern.matcher( text );
         while ( matcher.find() ) {
            final FeedingMention mention = new FeedingMention( jCas, matcher.start(), matcher.end() );
            mention.setFormula( text.substring( matcher.start(), matcher.end() ) );
            mention.addToIndexes();
         }
      }
      LOGGER.debug( "Feeding annotation complete." );
   }

   static private List<Pattern> buildFormulaPatterns() {
      final List<Pattern> patterns = new ArrayList<>();
      for ( String formula : FORMULAS ) {
         if ( formula == null || formula.isEmpty() ) {
            continue;
         }
         patterns.add( formulaPattern( formula ) );
      }
      return List.copyOf( patterns );
   }

   /** Public for unit tests: whole-word match for a lexicon formula token. */
   static public Pattern formulaPattern( final String formula ) {
      return Pattern.compile( "\\b" + Pattern.quote( formula ) + "\\b", Pattern.CASE_INSENSITIVE );
   }

   /** True when {@code formula} appears as a whole word in {@code text}. */
   static public boolean matchesFormulaToken( final String text, final String formula ) {
      if ( text == null || formula == null || formula.isEmpty() ) {
         return false;
      }
      return formulaPattern( formula ).matcher( text ).find();
   }

   static private String normalizeRoute( final String raw ) {
      final String t = raw.toUpperCase().replaceAll( "\\s+", " " );
      if ( t.contains( "NGT" ) || t.contains( "NASOGASTRIC" ) || t.contains( "NG TUBE" ) ) {
         return "NGT";
      }
      if ( t.contains( "PEG" ) ) {
         return "PEG";
      }
      if ( t.contains( "TPN" ) ) {
         return "TPN";
      }
      if ( t.contains( "PO" ) || t.contains( "PER OS" ) ) {
         return "PO";
      }
      if ( t.contains( "OG" ) ) {
         return "OGT";
      }
      return "enteral";
   }

   static private String findFormula( final String window ) {
      for ( String formula : FORMULAS ) {
         if ( matchesFormulaToken( window, formula ) ) {
            return formula;
         }
      }
      return null;
   }
}
