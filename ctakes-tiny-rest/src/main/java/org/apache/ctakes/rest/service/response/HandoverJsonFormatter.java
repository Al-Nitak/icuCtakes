package org.apache.ctakes.rest.service.response;

import org.apache.ctakes.icu.cc.HandoverAssembler;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

/**
 * Formats the CAS as ICU handover JSON.
 */
final public class HandoverJsonFormatter implements ResponseFormatter {

   @Override
   public String getResultText( final JCas jCas ) throws AnalysisEngineProcessException {
      return HandoverAssembler.createJson( jCas );
   }
}
