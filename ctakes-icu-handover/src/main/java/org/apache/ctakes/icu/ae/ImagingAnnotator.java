package org.apache.ctakes.icu.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.icu.type.ImagingStudyMention;
import org.apache.ctakes.icu.util.LexiconLoader;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects imaging study sentences (CT/MRI/CXR/US/echo) via lexicon and annotates
 * the full sentence so findings narrative is preserved for handover assembly.
 */
@PipeBitInfo(
      name = "Imaging Study Annotator",
      description = "Detects CT/MRI/CXR/US/echo phrases and spans the containing sentence.",
      role = PipeBitInfo.Role.ANNOTATOR
)
final public class ImagingAnnotator extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = LoggerFactory.getLogger( ImagingAnnotator.class );

   static private final List<LexiconLoader.CodedEntry> IMAGING_LEXICON
         = LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/imaging.txt" );

   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      final List<ImagingStudyMention> created = new ArrayList<>();
      for ( Sentence sentence : JCasUtil.select( jCas, Sentence.class ) ) {
         final String text = sentence.getCoveredText();
         if ( text == null || text.isBlank() ) {
            continue;
         }
         final LexiconLoader.CodedEntry hit = LexiconLoader.matchCoded( text, IMAGING_LEXICON );
         if ( hit == null ) {
            continue;
         }
         if ( overlapsExisting( created, sentence.getBegin(), sentence.getEnd() ) ) {
            continue;
         }
         final ImagingStudyMention mention
               = new ImagingStudyMention( jCas, sentence.getBegin(), sentence.getEnd() );
         mention.setModality( hit.preferredText );
         mention.setPreferredText( hit.preferredText );
         mention.setCui( hit.cui );
         mention.setCodingScheme( hit.codingScheme );
         mention.setCode( hit.code );
         mention.addToIndexes();
         created.add( mention );
      }
      LOGGER.debug( "Imaging study annotation complete ({}).", created.size() );
   }

   static private boolean overlapsExisting( final List<ImagingStudyMention> existing,
                                            final int begin, final int end ) {
      for ( ImagingStudyMention m : existing ) {
         if ( begin < m.getEnd() && end > m.getBegin() ) {
            return true;
         }
      }
      return false;
   }
}
