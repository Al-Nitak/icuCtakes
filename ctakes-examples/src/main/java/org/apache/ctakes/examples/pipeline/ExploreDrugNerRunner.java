package org.apache.ctakes.examples.pipeline;

import org.apache.ctakes.core.config.ConfigParameterConstants;
import org.apache.ctakes.core.pipeline.PipelineBuilder;
import org.apache.ctakes.core.pipeline.PiperFileReader;
import org.apache.ctakes.drugner.ae.DrugMentionAnnotator;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Drug NER explore runner using uimaFIT + full classpath type system.
 * Usage: ExploreDrugNerRunner &lt;inputDir&gt; &lt;outputDir&gt;
 */
final public class ExploreDrugNerRunner {

   static private final Logger LOGGER = LoggerFactory.getLogger( ExploreDrugNerRunner.class );

   static private final String BASE_PIPER
         = "org/apache/ctakes/examples/pipeline/explore/06_DrugNerBase.piper";

   private ExploreDrugNerRunner() {
   }

   public static void main( final String... args ) throws Exception {
      if ( args.length < 2 ) {
         LOGGER.error( "Usage: ExploreDrugNerRunner <inputDir> <outputDir>" );
         System.exit( 1 );
      }
      final String inputDir = args[ 0 ];
      final String outputDir = args[ 1 ];
      new File( outputDir ).mkdirs();

      final PiperFileReader reader = new PiperFileReader();
      final PipelineBuilder builder = reader.getBuilder();
      builder.set( ConfigParameterConstants.PARAM_INPUTDIR, inputDir );
      builder.set( ConfigParameterConstants.PARAM_OUTPUTDIR, outputDir );
      reader.loadPipelineFile( BASE_PIPER );
      builder.readFiles( inputDir );

      final TypeSystemDescription tsd = TypeSystemDescriptionFactory.createTypeSystemDescription();
      final AnalysisEngineDescription drugDesc = AnalysisEngineFactory.createEngineDescription(
            DrugMentionAnnotator.class,
            tsd,
            DrugMentionAnnotator.DISTANCE, "1",
            DrugMentionAnnotator.DISTANCE_ANN_TYPE,
            "org.apache.ctakes.typesystem.type.textspan.Sentence",
            DrugMentionAnnotator.BOUNDARY_ANN_TYPE,
            "org.apache.ctakes.typesystem.type.textspan.Sentence",
            DrugMentionAnnotator.PARAM_SEGMENTS_MEDICATION_RELATED,
            new String[]{ "SIMPLE_SEGMENT", "20105", "20103" } );
      builder.addDescription( drugDesc );

      builder.writeHtml( outputDir );
      builder.writeXMIs( outputDir );

      LOGGER.info( "Running drug NER explore: in={} out={}", inputDir, outputDir );
      builder.run();
      LOGGER.info( "Drug NER explore finished." );
   }
}
