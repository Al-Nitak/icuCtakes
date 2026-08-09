package org.apache.ctakes.icu.util;

import org.apache.ctakes.icu.cc.HandoverDocument;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * SentenceIndex id lookup and header-prefix normalization.
 */
public class SentenceIndexTest {

   @Test
   public void idForFindsCoveringSentence() {
      final List<HandoverDocument.SentenceDto> list = new ArrayList<>();
      list.add( sentence( 0, 10, "First." ) );
      list.add( sentence( 11, 30, "Second sentence here." ) );
      final SentenceIndex index = new SentenceIndex( list );
      assertEquals( Integer.valueOf( 0 ), index.idFor( 0, 5 ) );
      assertEquals( Integer.valueOf( 1 ), index.idFor( 15, 20 ) );
      assertNull( index.idFor( 100, 110 ) );
   }

   @Test
   public void normalizeTextStripsSectionHeader() {
      assertEquals( "55-year-old with hypertension.",
            SentenceIndex.normalizeText( "Background:\n55-year-old with hypertension." ) );
      assertEquals( "Arrived at the ER.",
            SentenceIndex.normalizeText( "  Situation: Arrived at the ER.  " ) );
   }

   @Test
   public void contentBeginAccountsForHeaderStrip() {
      final String covered = "Background:\n55-year-old with hypertension.";
      final String normalized = SentenceIndex.normalizeText( covered );
      assertEquals( "55-year-old with hypertension.", normalized );
      assertEquals( 12, SentenceIndex.contentBegin( 0, covered, normalized ) );
   }

   @Test
   public void nextReturnsSuccessor() {
      final List<HandoverDocument.SentenceDto> list = new ArrayList<>();
      list.add( sentence( 0, 5, "A." ) );
      list.add( sentence( 6, 10, "B." ) );
      final SentenceIndex index = new SentenceIndex( list );
      assertEquals( "B.", index.next( 0 ).text );
      assertNull( index.next( 1 ) );
   }

   static private HandoverDocument.SentenceDto sentence( final int begin, final int end, final String text ) {
      final HandoverDocument.SentenceDto dto = new HandoverDocument.SentenceDto();
      dto.begin = begin;
      dto.end = end;
      dto.contentBegin = begin;
      dto.text = text;
      return dto;
   }
}
