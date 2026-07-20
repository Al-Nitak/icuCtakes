package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.VentSupportMention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.jcas.JCas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects ventilation / oxygen support mode and common settings.
 */
@PipeBitInfo(
      name = "Vent Support Annotator",
      description = "Detects vent/O2 modes and settings (RR, TV, FiO2, flow, PEEP).",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class VentSupportAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( VentSupportAnnotator.class );

   static private final Pattern MODE_PATTERN = Pattern.compile(
         "\\b(?:SIMV|AC|A/?C|CMV|PCV|PSV|CPAP|BIPAP|BiPAP|HFNC|high[\\s\\-]?flow(?:\\s+oxygen)?|"
               + "mechanical\\s+ventilation|room\\s+air|\\bNC\\b|nasal\\s+cannula|NIV)\\b",
         Pattern.CASE_INSENSITIVE );

   static private final Pattern RR = Pattern.compile(
         "(?:set\\s+)?(?:RR|respiratory\\s+rate)\\s*(?:of|=|:)?\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE );
   static private final Pattern TV = Pattern.compile(
         "(?:tidal\\s+volume|TV|Vt)\\s*(?:of|=|:)?\\s*(\\d{2,4})", Pattern.CASE_INSENSITIVE );
   static private final Pattern FIO2 = Pattern.compile(
         "FiO2\\s*(?:of|=|:)?\\s*(\\d{1,3})\\s*%?", Pattern.CASE_INSENSITIVE );
   static private final Pattern FLOW = Pattern.compile(
         "(?:flow(?:\\s+rate)?)\\s*(?:of|=|:)?\\s*(\\d{1,3})\\s*(?:L(?:/?min)?)", Pattern.CASE_INSENSITIVE );
   static private final Pattern PEEP = Pattern.compile(
         "PEEP\\s*(?:of|=|:)?\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE );

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final String text = jCas.getDocumentText();
      if ( text == null || text.isEmpty() ) {
         return;
      }

      final Matcher modeMatcher = MODE_PATTERN.matcher( text );
      boolean any = false;
      while ( modeMatcher.find() ) {
         any = true;
         final int begin = modeMatcher.start();
         final int end = Math.min( text.length(), modeMatcher.end() + 120 );
         final String window = text.substring( begin, end );
         final VentSupportMention mention = new VentSupportMention( jCas, modeMatcher.start(), modeMatcher.end() );
         mention.setMode( normalizeMode( modeMatcher.group() ) );
         fillSettings( mention, window );
         mention.addToIndexes();
      }

      // Standalone settings sentence without explicit mode (e.g. "FiO2 30%")
      if ( !any ) {
         final Matcher fio2 = FIO2.matcher( text );
         if ( fio2.find() ) {
            final VentSupportMention mention = new VentSupportMention( jCas, fio2.start(), fio2.end() );
            mention.setMode( "unknown" );
            fillSettings( mention, text.substring( Math.max( 0, fio2.start() - 80 ),
                  Math.min( text.length(), fio2.end() + 80 ) ) );
            mention.addToIndexes();
         }
      }
      LOGGER.debug( "Vent support annotation complete." );
   }

   static private void fillSettings( final VentSupportMention mention, final String window ) {
      setFloat( RR.matcher( window ), mention::setRr );
      setFloat( TV.matcher( window ), mention::setTv );
      setFloat( PEEP.matcher( window ), mention::setPeep );
      setFloat( FLOW.matcher( window ), mention::setFlow );
      final Matcher fio2 = FIO2.matcher( window );
      if ( fio2.find() ) {
         float v = Float.parseFloat( fio2.group( 1 ) );
         if ( v > 1.0f ) {
            v = v / 100.0f;
         }
         mention.setFio2( v );
      }
   }

   static private void setFloat( final Matcher matcher, final FloatSetter setter ) {
      if ( matcher.find() ) {
         setter.set( Float.parseFloat( matcher.group( 1 ) ) );
      }
   }

   static private String normalizeMode( final String raw ) {
      final String t = raw.trim().toUpperCase().replaceAll( "\\s+", " " );
      if ( t.contains( "HIGH" ) || t.contains( "HFNC" ) ) {
         return "HFNC";
      }
      if ( t.contains( "ROOM AIR" ) ) {
         return "RA";
      }
      if ( t.equals( "NC" ) || t.contains( "NASAL" ) ) {
         return "NC";
      }
      if ( t.contains( "MECHANICAL" ) ) {
         return "MV";
      }
      if ( t.contains( "BIPAP" ) ) {
         return "BiPAP";
      }
      if ( t.equals( "A/C" ) || t.equals( "AC" ) ) {
         return "AC";
      }
      return t;
   }

   @FunctionalInterface
   private interface FloatSetter {
      void set( float value );
   }
}
