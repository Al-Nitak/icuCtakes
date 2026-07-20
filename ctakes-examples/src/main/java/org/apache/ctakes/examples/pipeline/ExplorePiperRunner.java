package org.apache.ctakes.examples.pipeline;

import org.apache.ctakes.core.pipeline.PiperFileRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a single explore-tour piper with input/output directories.
 * Usage: ExplorePiperRunner &lt;piperPath&gt; &lt;inputDir&gt; &lt;outputDir&gt;
 */
final public class ExplorePiperRunner {

   static private final Logger LOGGER = LoggerFactory.getLogger( ExplorePiperRunner.class );

   private ExplorePiperRunner() {
   }

   public static void main( final String... args ) {
      if ( args.length < 3 ) {
         LOGGER.error( "Usage: ExplorePiperRunner <piperPath> <inputDir> <outputDir>" );
         System.exit( 1 );
      }
      final String piper = args[ 0 ];
      final String input = args[ 1 ];
      final String output = args[ 2 ];
      LOGGER.info( "Explore run: piper={} input={} output={}", piper, input, output );
      final boolean ok = PiperFileRunner.run(
            "-p", piper,
            "-i", input,
            "-o", output,
            "--xmiOut", output );
      System.exit( ok ? 0 : 1 );
   }
}
