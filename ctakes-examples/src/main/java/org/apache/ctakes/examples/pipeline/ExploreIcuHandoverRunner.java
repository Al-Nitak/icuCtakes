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
 * ICU handover explore runner: clinical piper + DrugMentionAnnotator + writers.
 * Usage: ExploreIcuHandoverRunner &lt;inputDir&gt; &lt;outputDir&gt;
 */
final public class ExploreIcuHandoverRunner {

   static private final Logger LOGGER = LoggerFactory.getLogger( ExploreIcuHandoverRunner.class );

   private ExploreIcuHandoverRunner() {
   }

   public static void main( final String... args ) throws Exception {
      if ( args.length < 2 ) {
         LOGGER.error( "Usage: ExploreIcuHandoverRunner <inputDir> <outputDir>" );
         System.exit( 1 );
      }
      final String inputDir = args[ 0 ];
      final String outputDir = args[ 1 ];
      new File( outputDir ).mkdirs();

      final PiperFileReader reader = new PiperFileReader();
      final PipelineBuilder builder = reader.getBuilder();
      builder.set( ConfigParameterConstants.PARAM_INPUTDIR, inputDir );
      builder.set( ConfigParameterConstants.PARAM_OUTPUTDIR, outputDir );
      reader.loadPipelineFile(
            "org/apache/ctakes/examples/pipeline/explore/08_IcuHandoverBase.piper" );
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
            new String[]{ "SIMPLE_SEGMENT", "CNS", "CVS", "Respiratory", "Gastrointestinal",
                  "Genitourinary", "Hematologic", "Abx", "Medications", "20105", "20103" } );
      builder.addDescription( drugDesc );

      reader.loadPipelineFile(
            "org/apache/ctakes/examples/pipeline/explore/08_IcuHandoverWriters.piper" );

      LOGGER.info( "Running ICU handover explore: in={} out={}", inputDir, outputDir );
      builder.run();
      LOGGER.info( "ICU handover explore finished." );
   }
}
