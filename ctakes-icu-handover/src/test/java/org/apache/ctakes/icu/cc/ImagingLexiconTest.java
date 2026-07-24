package org.apache.ctakes.icu.cc;

import org.apache.ctakes.icu.util.LexiconLoader;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Imaging lexicon coverage for sentence-level CT/MRI/CXR detection.
 */
public class ImagingLexiconTest {

   @Test
   public void matchesCtScanSentence() {
      final LexiconLoader.CodedEntry hit = LexiconLoader.matchCoded(
            "CT scan showed brain edema, no stroke or bleeding",
            LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/imaging.txt" ) );
      assertNotNull( hit );
      assertEquals( "CT", hit.preferredText );
   }

   @Test
   public void matchesCxrAndEcho() {
      final var lexicon = LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/imaging.txt" );
      assertEquals( "CXR", LexiconLoader.matchCoded( "CXR clear overnight", lexicon ).preferredText );
      assertEquals( "Echo", LexiconLoader.matchCoded( "Echo showed EF 45%", lexicon ).preferredText );
      assertEquals( "MRI", LexiconLoader.matchCoded( "MRI brain pending", lexicon ).preferredText );
   }

   @Test
   public void lexiconHasMultipleModalities() {
      final var lexicon = LexiconLoader.loadCodedEntries( "org/apache/ctakes/icu/data/imaging.txt" );
      assertTrue( lexicon.size() >= 10 );
   }
}
