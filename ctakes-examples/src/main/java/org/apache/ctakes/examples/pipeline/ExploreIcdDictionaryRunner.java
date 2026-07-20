package org.apache.ctakes.examples.pipeline;

import org.apache.ctakes.core.pipeline.PiperFileReader;
import org.apache.ctakes.rest.service.response.UmlsJsonFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test ctakesicd2015 dictionary (loads IcdRestPipeline.piper directly).
 * Usage: ExploreIcdDictionaryRunner &lt;inputNote.txt&gt; &lt;outputDir&gt;
 */
final public class ExploreIcdDictionaryRunner {

   static private final Logger LOGGER = LoggerFactory.getLogger( ExploreIcdDictionaryRunner.class );

   static private final Path PIPER = Path.of(
         "ctakes-examples/src/user/resources/org/apache/ctakes/examples/pipeline/explore/IcdRestPipeline.piper" );

   private ExploreIcdDictionaryRunner() {
   }

   public static void main( final String... args ) throws Exception {
      if ( args.length < 2 ) {
         LOGGER.error( "Usage: ExploreIcdDictionaryRunner <inputNote.txt> <outputDir>" );
         System.exit( 1 );
      }
      final File inputNote = new File( args[ 0 ] );
      final File outputDir = new File( args[ 1 ] );
      if ( !inputNote.isFile() ) {
         throw new IllegalStateException( "Missing input note: " + inputNote.getAbsolutePath() );
      }
      if ( !outputDir.exists() && !outputDir.mkdirs() ) {
         throw new IllegalStateException( "Cannot create " + outputDir );
      }

      final Path dbScript = Path.of(
            "resources/org/apache/ctakes/dictionary/lookup/fast/ctakesicd2015/ctakesicd2015.script" );
      if ( !Files.isRegularFile( dbScript ) ) {
         throw new IllegalStateException(
               "ctakesicd2015 HSQLDB not installed. Run: ./scripts/install-icd-dictionary.sh" );
      }
      if ( !Files.isRegularFile( PIPER ) ) {
         throw new IllegalStateException( "Missing " + PIPER.toAbsolutePath() );
      }

      final String text = Files.readString( inputNote.toPath(), StandardCharsets.UTF_8 );
      LOGGER.info( "Loading pipeline {} ...", PIPER );
      final PiperFileReader reader = new PiperFileReader( PIPER.toString() );
      final AnalysisEngine engine = UIMAFramework.produceAnalysisEngine(
            reader.getBuilder().getAnalysisEngineDesc() );

      LOGGER.info( "Processing umls JSON (shows CUI + vocabulary codes incl. ICD) ..." );
      final JCas jcas = JCasFactory.createJCas();
      try {
         jcas.setDocumentText( text );
         engine.process( jcas );
         final String umlsJson = new UmlsJsonFormatter().getResultText( jcas );
         final File umlsOut = new File( outputDir, "icd_dictionary_umls.json" );
         Files.writeString( umlsOut.toPath(), umlsJson == null ? "" : umlsJson, StandardCharsets.UTF_8 );
         LOGGER.info( "Wrote {}", umlsOut.getAbsolutePath() );
      } finally {
         jcas.release();
         engine.destroy();
      }
   }
}
