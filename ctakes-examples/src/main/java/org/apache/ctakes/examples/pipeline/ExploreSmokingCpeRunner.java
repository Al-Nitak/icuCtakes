package org.apache.ctakes.examples.pipeline;

import org.apache.ctakes.core.cc.FileTreeXmiWriter;
import org.apache.ctakes.core.config.ConfigParameterConstants;
import org.apache.ctakes.core.cr.FileTreeReader;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.util.XMLInputSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Runs the smoking-status SimulatedProdSmokingTAE aggregate on a directory of notes.
 * Usage: ExploreSmokingCpeRunner &lt;inputDir&gt; &lt;outputDir&gt;
 */
final public class ExploreSmokingCpeRunner {

   static private final Logger LOGGER = LoggerFactory.getLogger( ExploreSmokingCpeRunner.class );

   static private final String SMOKING_TAE = "desc/analysis_engine/SimulatedProdSmokingTAE.xml";

   private ExploreSmokingCpeRunner() {
   }

   public static void main( final String... args ) throws Exception {
      if ( args.length < 2 ) {
         LOGGER.error( "Usage: ExploreSmokingCpeRunner <inputDir> <outputDir>" );
         System.exit( 1 );
      }
      final File inputDir = new File( args[ 0 ] );
      final File outputDir = new File( args[ 1 ] );
      if ( !outputDir.exists() && !outputDir.mkdirs() ) {
         throw new IllegalStateException( "Cannot create " + outputDir );
      }

      File taeXml = new File( "ctakes-smoking-status/" + SMOKING_TAE );
      if ( !taeXml.isFile() ) {
         taeXml = new File( "../ctakes-smoking-status/" + SMOKING_TAE );
      }
      if ( !taeXml.isFile() ) {
         taeXml = new File( SMOKING_TAE );
      }
      if ( !taeXml.isFile() ) {
         throw new IllegalStateException( "Cannot find SimulatedProdSmokingTAE.xml" );
      }

      LOGGER.info( "Smoking TAE: {}", taeXml.getAbsolutePath() );
      LOGGER.info( "Input: {}  Output: {}", inputDir.getAbsolutePath(), outputDir.getAbsolutePath() );

      // Resolve relative file: URLs (descriptors under ctakes-smoking-status/, data under resources/).
      final String dataPath = new File( "." ).getAbsolutePath()
            + File.pathSeparator
            + new File( "resources" ).getAbsolutePath();
      System.setProperty( "uima.datapath", dataPath );
      LOGGER.info( "uima.datapath={}", dataPath );

      final AnalysisEngineDescription aeDesc = UIMAFramework
            .getXMLParser()
            .parseAnalysisEngineDescription( new XMLInputSource( taeXml ) );

      final CollectionReader reader = CollectionReaderFactory.createReader(
            FileTreeReader.class,
            ConfigParameterConstants.PARAM_INPUTDIR,
            inputDir.getAbsolutePath() );

      final AnalysisEngine engine = AnalysisEngineFactory.createEngine( aeDesc );
      final AnalysisEngine xmiWriter = AnalysisEngineFactory.createEngine(
            FileTreeXmiWriter.class,
            ConfigParameterConstants.PARAM_OUTPUTDIR,
            outputDir.getAbsolutePath() );

      SimplePipeline.runPipeline( reader, engine, xmiWriter );
      LOGGER.info( "Smoking explore run finished." );
   }
}
