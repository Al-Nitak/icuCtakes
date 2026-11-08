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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
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

   /**
    * Plan section header regex — keep in sync with
    * {@code IcuHandoverSectionRegex.bsv} Plan line.
    */
   static final String PLAN_HEADER_REGEX =
         "^[\\t ]*(?:ASSESSMENT AND PLAN|CARE\\s+PLAN(?:\\s+FOR\\s+(?:TODAY|THE\\s+DAY))?|PLAN|"
               + "RECOMMENDATIONS?|TO ?DO|PENDING|NEXT STEPS)[\\t ]*:?(?:[\\t ]*$|\\s+)";

   static final Pattern PLAN_HEADER = Pattern.compile(
         PLAN_HEADER_REGEX, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE );

   /**
    * Leading narrative / imperative cues used when no Plan section is present.
    * Allows "We will/are/continue/start/keep …", "We have (not) initiated/started/added …",
    * and "For &lt;topic&gt;, we will/are/continue …"; avoids bare "Regarding …".
    */
   static final Pattern IMPERATIVE = Pattern.compile(
         "^(?:for\\s+[\\w-]{1,24}(?:\\s+[\\w-]{1,24}){0,3},\\s*)?we\\s+(?:will|are|continue|start|keep|give|insert|sent)\\b"
               + "|^we\\s+have\\s+(?:not\\s+)?(?:initiated|started|added)\\b"
               + "|^(?:continue|start|stop|hold|increase|decrease|wean|extubate|consult|follow|monitor|"
               + "repeat|obtain|order|keep|maintain|titrate|d/c|discontinue|initiate|add|administer|"
               + "insert|escalate)\\b",
         Pattern.CASE_INSENSITIVE );

   /** Package-visible for unit tests. */
   static boolean matchesPlanCue( final String text ) {
      if ( text == null ) {
         return false;
      }
      return IMPERATIVE.matcher( text.trim() ).find();
   }

   /** Package-visible for unit tests. */
   static boolean matchesPlanHeader( final String text ) {
      if ( text == null ) {
         return false;
      }
      return PLAN_HEADER.matcher( text ).find();
   }

   /** Package-visible for unit tests — strip a leading plan header from inline dictation. */
   static String stripPlanHeader( final String text ) {
      if ( text == null ) {
         return "";
      }
      final Matcher matcher = PLAN_HEADER.matcher( text.trim() );
      if ( matcher.find() && matcher.start() == 0 ) {
         return text.trim().substring( matcher.end() ).trim();
      }
      return text.trim();
   }

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      boolean fromSection = false;
      int addedFromSection = 0;
      final Set<String> seen = new HashSet<>();

      for ( Segment segment : JCasUtil.select( jCas, Segment.class ) ) {
         if ( !isPlanSection( segment ) ) {
            continue;
         }
         fromSection = true;
         for ( Sentence sentence : JCasUtil.select( jCas, Sentence.class ) ) {
            if ( !overlaps( sentence.getBegin(), sentence.getEnd(),
                  segment.getBegin(), segment.getEnd() ) ) {
               continue;
            }
            if ( addPlanSentence( jCas, sentence, seen ) ) {
               addedFromSection++;
            }
         }
      }

      // Cue fallback when there is no Plan section, or the section matched but
      // covered no sentences (same-line "Care plan for today: …").
      if ( !fromSection || addedFromSection == 0 ) {
         for ( Sentence sentence : JCasUtil.select( jCas, Sentence.class ) ) {
            final String text = sentence.getCoveredText().trim();
            if ( matchesPlanHeader( text ) || matchesPlanCue( text ) ) {
               addPlanSentence( jCas, sentence, seen );
            }
         }
      }
      LOGGER.debug( "Plan item annotation complete." );
   }

   static private boolean addPlanSentence( final JCas jCas,
                                           final Sentence sentence,
                                           final Set<String> seen ) {
      final String raw = sentence.getCoveredText();
      if ( raw == null ) {
         return false;
      }
      final String action = stripPlanHeader( raw );
      if ( action.isEmpty() ) {
         return false;
      }
      final String key = action.toLowerCase( Locale.ROOT );
      if ( !seen.add( key ) ) {
         return false;
      }
      // Span the full sentence so overlap checks against NER still work;
      // actionText carries the header-stripped plan line for the UI.
      addPlanItem( jCas, sentence.getBegin(), sentence.getEnd(), action );
      return true;
   }

   static private boolean overlaps( final int aBegin, final int aEnd,
                                    final int bBegin, final int bEnd ) {
      return aBegin < bEnd && bBegin < aEnd;
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
