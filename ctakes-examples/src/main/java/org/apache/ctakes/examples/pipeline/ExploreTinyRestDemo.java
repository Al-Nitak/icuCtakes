package org.apache.ctakes.examples.pipeline;

import org.apache.ctakes.rest.service.RestPipelineRunner;
import org.apache.ctakes.rest.service.response.CuiListFormatter;
import org.apache.ctakes.rest.service.response.FhirJsonFormatter;
import org.apache.ctakes.rest.service.response.PrettyPrintFormatter;
import org.apache.ctakes.rest.service.response.ResponseFormatter;
import org.apache.ctakes.rest.service.response.UmlsJsonFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stage 7b — exercise the tiny-rest /process pipeline without a servlet container.
 * Uses the same RestPipelineRunner + formatters as TinyController.
 * Usage: ExploreTinyRestDemo &lt;inputNote.txt&gt; &lt;outputDir&gt;
 */
final public class ExploreTinyRestDemo {

   static private final Logger LOGGER = LoggerFactory.getLogger( ExploreTinyRestDemo.class );

   private ExploreTinyRestDemo() {
   }

   public static void main( final String... args ) throws Exception {
      if ( args.length < 2 ) {
         LOGGER.error( "Usage: ExploreTinyRestDemo <inputNote.txt> <outputDir>" );
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

      final String text = Files.readString( inputNote.toPath(), StandardCharsets.UTF_8 );

      // RestPipelineRunner constructs PiperFileReader with a filesystem path (not classpath).
      final File cwdPiper = new File( "TinyRestPipeline.piper" );
      if ( !cwdPiper.isFile() ) {
         final Path source = Path.of( "ctakes-tiny-rest/src/user/resources/TinyRestPipeline.piper" );
         if ( !Files.isRegularFile( source ) ) {
            throw new IllegalStateException( "Missing " + source.toAbsolutePath() );
         }
         Files.copy( source, cwdPiper.toPath() );
         LOGGER.info( "Copied {} -> {}", source, cwdPiper.getAbsolutePath() );
      }

      LOGGER.info( "Initializing RestPipelineRunner (TinyRestPipeline.piper) ..." );
      final RestPipelineRunner runner = RestPipelineRunner.getInstance();

      writeFormat( runner, text, outputDir, "pretty", new PrettyPrintFormatter(), "process_pretty.txt" );
      writeFormat( runner, text, outputDir, "cui", new CuiListFormatter(), "process_cui.txt" );
      writeFormat( runner, text, outputDir, "umls", new UmlsJsonFormatter(), "process_umls.json" );
      writeFormat( runner, text, outputDir, "fhir", new FhirJsonFormatter(), "process_fhir.json" );

      LOGGER.info( "Tiny REST demo wrote process_pretty.txt, process_cui.txt, process_umls.json, process_fhir.json" );
   }

   static private void writeFormat( final RestPipelineRunner runner,
                                    final String text,
                                    final File outputDir,
                                    final String formatLabel,
                                    final ResponseFormatter formatter,
                                    final String fileName ) throws Exception {
      LOGGER.info( "Processing format={} ...", formatLabel );
      final String result = runner.process( formatter, text );
      final File out = new File( outputDir, fileName );
      Files.writeString( out.toPath(), result == null ? "" : result, StandardCharsets.UTF_8 );
      LOGGER.info( "Wrote {}", out.getAbsolutePath() );
   }
}
