package org.apache.ctakes.examples.pipeline;

import org.apache.ctakes.core.config.ConfigParameterConstants;
import org.apache.ctakes.core.pipeline.PipelineBuilder;
import org.apache.ctakes.core.pipeline.PiperFileReader;
import org.apache.ctakes.core.resource.FileResourceImpl;
import org.apache.ctakes.sideeffect.ae.SideEffectAnnotator;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Side-effect explore runner with a small local dictionary resource.
 * Usage: ExploreSideEffectRunner &lt;inputDir&gt; &lt;outputDir&gt;
 */
final public class ExploreSideEffectRunner {

   static private final Logger LOGGER = LoggerFactory.getLogger( ExploreSideEffectRunner.class );

   static private final String BASE_PIPER
         = "org/apache/ctakes/examples/pipeline/explore/06_SideEffectBase.piper";
   static private final String DICT_RESOURCE
         = "org/apache/ctakes/examples/pipeline/explore/sideEffect_dictionary.txt";

   private ExploreSideEffectRunner() {
   }

   /**
    * Assigns Segment.id onto covered IdentifiedAnnotations (needed by SideEffectAnnotator).
    */
   @TypeCapability( inputs = "org.apache.ctakes.typesystem.type.textspan.Segment",
         outputs = "org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation" )
   public static class SegmentIdAssigner extends JCasAnnotator_ImplBase {
      @Override
      public void process( final JCas jCas ) throws AnalysisEngineProcessException {
         final Map<Segment, List<IdentifiedAnnotation>> covered
               = JCasUtil.indexCovered( jCas, Segment.class, IdentifiedAnnotation.class );
         for ( Map.Entry<Segment, List<IdentifiedAnnotation>> entry : covered.entrySet() ) {
            final String segmentId = entry.getKey().getId();
            for ( IdentifiedAnnotation annotation : entry.getValue() ) {
               if ( annotation.getSegmentID() == null ) {
                  annotation.setSegmentID( segmentId != null ? segmentId : "SIMPLE_SEGMENT" );
               }
            }
         }
         for ( IdentifiedAnnotation annotation : JCasUtil.select( jCas, IdentifiedAnnotation.class ) ) {
            if ( annotation.getSegmentID() == null ) {
               annotation.setSegmentID( "SIMPLE_SEGMENT" );
            }
         }
      }
   }

   public static void main( final String... args ) throws Exception {
      if ( args.length < 2 ) {
         LOGGER.error( "Usage: ExploreSideEffectRunner <inputDir> <outputDir>" );
         System.exit( 1 );
      }
      final String inputDir = args[ 0 ];
      final String outputDir = args[ 1 ];
      new File( outputDir ).mkdirs();

      // FileResourceImpl cannot load from a jar; materialize the dict on disk.
      final File dictFile = new File( outputDir, "sideEffect_dictionary.txt" );
      try ( InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream( DICT_RESOURCE ) ) {
         if ( in == null ) {
            throw new IllegalStateException( "Missing classpath resource " + DICT_RESOURCE );
         }
         Files.copy( in, dictFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
      }

      final PiperFileReader reader = new PiperFileReader();
      final PipelineBuilder builder = reader.getBuilder();
      builder.set( ConfigParameterConstants.PARAM_INPUTDIR, inputDir );
      builder.set( ConfigParameterConstants.PARAM_OUTPUTDIR, outputDir );
      reader.loadPipelineFile( BASE_PIPER );
      builder.readFiles( inputDir );

      final TypeSystemDescription tsd = TypeSystemDescriptionFactory.createTypeSystemDescription();
      final AnalysisEngineDescription prepDesc = AnalysisEngineFactory.createEngineDescription(
            SegmentIdAssigner.class, tsd );

      final AnalysisEngineDescription seDesc = AnalysisEngineFactory.createEngineDescription(
            SideEffectAnnotator.class,
            tsd,
            "sectionsToIgnore", new String[]{ "20101" },
            "hasPatternOfDrugCauseVerbPse", new String[]{ "caused", "induced" },
            "hasPatternOfPseDueToDrug", new String[]{ "due to", "because of", "secondary to" },
            "hasPatternOfDrugDueToPse", new String[]{ "due to", "because of" },
            "hasPatternOfDiscontDrugBecausePse", new String[]{ "discontinued", "stopped" },
            "sideEffectWord", new String[]{ "side effect", "adverse effect" },
            "hasPatternOfNotePseWithDrug", new String[]{ "noted", "reported" },
            "hasPatternOfDrugMadePse", new String[]{ "made", "makes" },
            "hasPatternOfPseAfterDrug", new String[]{ "after taking", "after starting" } );

      final ExternalResourceDescription dictRes = ExternalResourceFactory.createSharedResourceDescription(
            dictFile.toURI().toURL(), FileResourceImpl.class );
      ExternalResourceFactory.createDependency( seDesc, "sideEffectTable", FileResourceImpl.class );
      ExternalResourceFactory.bindResource( seDesc, "sideEffectTable", dictRes );

      builder.addDescription( prepDesc );
      builder.addDescription( seDesc );
      builder.writeHtml( outputDir );
      builder.writeXMIs( outputDir );

      LOGGER.info( "Running side-effect explore: in={} out={}", inputDir, outputDir );
      builder.run();
      LOGGER.info( "Side-effect explore finished." );
   }
}
