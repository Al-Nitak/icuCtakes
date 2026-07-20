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
 * Writes ICU handover SBAR HTML assembled from the CAS.
 */
@PipeBitInfo(
      name = "ICU Handover HTML File Writer",
      description = "Writes SBAR-style ICU handover HTML from stock and custom ICU annotations.",
      role = PipeBitInfo.Role.WRITER,
      dependencies = { PipeBitInfo.TypeProduct.DOCUMENT_ID },
      usables = { PipeBitInfo.TypeProduct.DOCUMENT_ID_PREFIX }
)
public class HandoverHtmlFileWriter extends AbstractJCasFileWriter {

   static private final Logger LOGGER = LoggerFactory.getLogger( HandoverHtmlFileWriter.class );

   @Override
   public void writeFile( final JCas jCas, final String outputDir,
                          final String documentId, final String fileName ) throws IOException {
      final HandoverDocument doc = HandoverAssembler.createDocument( jCas );
      final String html = HandoverHtmlRenderer.render( doc );
      final File file = new File( outputDir, fileName + ".handover.html" );
      try ( Writer writer = new BufferedWriter( new FileWriter( file ) ) ) {
         writer.write( html );
      }
      LOGGER.info( "Wrote {}", file.getAbsolutePath() );
   }

   public static AnalysisEngine createEngine( final String outputDirectory )
         throws ResourceInitializationException {
      return AnalysisEngineFactory.createEngine(
            HandoverHtmlFileWriter.class,
            ConfigParameterConstants.PARAM_OUTPUTDIR, outputDirectory );
   }
}
