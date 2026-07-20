package org.apache.ctakes.icu.cc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for adjacent / overlapping situation-event merging.
 */
public class HandoverAssemblerMergeTest {

   @Test
   public void mergesWeakCoughAdjacentTokens() {
      final String doc = "Patient has weak cough today.";
      final List<HandoverDocument.EventDto> events = Arrays.asList(
            event( "weak", "C1", 12, 16 ),
            event( "cough", "C2", 17, 22 )
      );

      final List<HandoverDocument.EventDto> merged =
            HandoverAssembler.mergeAdjacentSituationEvents( doc, new ArrayList<>( events ) );

      assertEquals( 1, merged.size() );
      assertEquals( "weak cough", merged.get( 0 ).text );
      assertEquals( "C2", merged.get( 0 ).cui );
      assertEquals( 12, merged.get( 0 ).begin );
      assertEquals( 22, merged.get( 0 ).end );
   }

   @Test
   public void collapsesNestedOverlapPreferringLongest() {
      final String doc = "negative inspiratory force (NIF)";
      final List<HandoverDocument.EventDto> events = Arrays.asList(
            event( "inspiratory", "C_short", 9, 20 ),
            event( "inspiratory force", "C_long", 9, 26 )
      );

      final List<HandoverDocument.EventDto> merged =
            HandoverAssembler.mergeAdjacentSituationEvents( doc, new ArrayList<>( events ) );

      assertEquals( 1, merged.size() );
      assertEquals( "inspiratory force", merged.get( 0 ).text );
      assertEquals( "C_long", merged.get( 0 ).cui );
   }

   @Test
   public void mergesConsciousAlertAndOriented() {
      final String doc = "The patient is conscious, alert, and oriented. Hemodynamically";
      final int start = doc.indexOf( "conscious" );
      final List<HandoverDocument.EventDto> events = Arrays.asList(
            event( "conscious", "C1", start, start + "conscious".length() ),
            event( "alert", "C2", doc.indexOf( "alert" ), doc.indexOf( "alert" ) + "alert".length() ),
            event( "oriented", "C3", doc.indexOf( "oriented" ),
                  doc.indexOf( "oriented" ) + "oriented".length() )
      );

      final List<HandoverDocument.EventDto> merged =
            HandoverAssembler.mergeAdjacentSituationEvents( doc, new ArrayList<>( events ) );

      assertEquals( 1, merged.size() );
      assertEquals( "conscious, alert, and oriented", merged.get( 0 ).text );
   }

   @Test
   public void doesNotMergeAcrossContentWords() {
      final String doc = "mild hypertension and tachycardia";
      final List<HandoverDocument.EventDto> events = Arrays.asList(
            event( "mild", "C1", 0, 4 ),
            event( "tachycardia", "C2", 22, 33 )
      );

      final List<HandoverDocument.EventDto> merged =
            HandoverAssembler.mergeAdjacentSituationEvents( doc, new ArrayList<>( events ) );

      assertEquals( 2, merged.size() );
      assertEquals( "mild", merged.get( 0 ).text );
      assertEquals( "tachycardia", merged.get( 1 ).text );
   }

   @Test
   public void doesNotMergeAcrossWithLinker() {
      final String doc = "autonomic dysfunction with mild hypertension";
      final int dys = doc.indexOf( "dysfunction" );
      final int mild = doc.indexOf( "mild" );
      final List<HandoverDocument.EventDto> merged = HandoverAssembler.mergeAdjacentSituationEvents(
            doc,
            new ArrayList<>( Arrays.asList(
                  event( "dysfunction", "Cd", dys, dys + "dysfunction".length() ),
                  event( "mild", "Cm", mild, mild + "mild".length() )
            ) ) );
      assertEquals( 2, merged.size() );
      assertEquals( "dysfunction", merged.get( 0 ).text );
      assertEquals( "mild", merged.get( 1 ).text );
   }

   @Test
   public void mergesTouchingSpansWithOnlyWhitespace() {
      final String doc = "weak cough";
      assertTrue( doc.charAt( 4 ) == ' ' );
      final List<HandoverDocument.EventDto> merged = HandoverAssembler.mergeAdjacentSituationEvents(
            doc,
            new ArrayList<>( Arrays.asList(
                  event( "weak", "Cw", 0, 4 ),
                  event( "cough", "Cc", 5, 10 )
            ) ) );
      assertEquals( 1, merged.size() );
      assertEquals( "weak cough", merged.get( 0 ).text );
   }

   static private HandoverDocument.EventDto event( final String text, final String cui,
                                                   final int begin, final int end ) {
      final HandoverDocument.EventDto e = new HandoverDocument.EventDto();
      e.text = text;
      e.cui = cui;
      e.type = "SignSymptomMention";
      e.begin = begin;
      e.end = end;
      return e;
   }
}
