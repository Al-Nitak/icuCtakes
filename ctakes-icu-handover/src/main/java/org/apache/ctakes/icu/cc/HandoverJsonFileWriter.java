package org.apache.ctakes.icu.cc;

import org.apache.ctakes.core.cc.AbstractJCasFileWriter;
import org.apache.ctakes.core.config.ConfigParameterConstants;
import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * Writes ICU handover JSON assembled from the CAS.
 */
@PipeBitInfo(
      name = "ICU Handover JSON File Writer",
      description = "Writes ICU handover schema JSON from stock and custom ICU annotations.",
      role = PipeBitInfo.Role.WRITER,
      dependencies = { PipeBitInfo.TypeProduct.DOCUMENT_ID },
      usables = { PipeBitInfo.TypeProduct.DOCUMENT_ID_PREFIX }
)
public class HandoverJsonFileWriter extends AbstractJCasFileWriter {

   static private final Logger LOGGER = LoggerFactory.getLogger( HandoverJsonFileWriter.class );

   @Override
   public void writeFile( final JCas jCas, final String outputDir,
                          final String documentId, final String fileName ) throws IOException {
      final String json = HandoverAssembler.createJson( jCas );
      final File file = new File( outputDir, fileName + ".handover.json" );
      try ( Writer writer = new BufferedWriter( new FileWriter( file ) ) ) {
         writer.write( json );
      }
      LOGGER.info( "Wrote {}", file.getAbsolutePath() );
   }

   public static AnalysisEngine createEngine( final String outputDirectory )
         throws ResourceInitializationException {
      return AnalysisEngineFactory.createEngine(
            HandoverJsonFileWriter.class,
            ConfigParameterConstants.PARAM_OUTPUTDIR, outputDirectory );
   }
}
