package org.apache.ctakes.icu.cc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Imaging deduplication keeps the longer overlapping span.
 */
public class HandoverAssemblerImagingTest {

   @Test
   public void dedupeKeepsLongerMriBrainSpan() {
      final HandoverDocument.ImagingDto shortSpan = new HandoverDocument.ImagingDto();
      shortSpan.modality = "MRI";
      shortSpan.begin = 10;
      shortSpan.end = 13;
      shortSpan.procedureText = "MRI";

      final HandoverDocument.ImagingDto longSpan = new HandoverDocument.ImagingDto();
      longSpan.modality = "MRI";
      longSpan.begin = 10;
      longSpan.end = 19;
      longSpan.procedureText = "MRI brain";

      final List<HandoverDocument.ImagingDto> input = new ArrayList<>();
      input.add( shortSpan );
      input.add( longSpan );

      final List<HandoverDocument.ImagingDto> deduped = invokeDedupe( input );
      assertEquals( 1, deduped.size() );
      assertEquals( "MRI brain", deduped.get( 0 ).procedureText );
   }

   @Test
   public void parseBodySiteFromMriBrain() {
      assertEquals( "brain", invokeParseBodySite( "MRI brain on 30/6 showed infarction" ) );
   }

   @SuppressWarnings( "unchecked" )
   static private List<HandoverDocument.ImagingDto> invokeDedupe( final List<HandoverDocument.ImagingDto> list ) {
      try {
         final var method = HandoverAssembler.class.getDeclaredMethod( "dedupeImaging", List.class );
         method.setAccessible( true );
         return (List<HandoverDocument.ImagingDto>) method.invoke( null, list );
      } catch ( ReflectiveOperationException e ) {
         throw new RuntimeException( e );
      }
   }

   static private String invokeParseBodySite( final String text ) {
      try {
         final var method = HandoverAssembler.class.getDeclaredMethod( "parseBodySiteFromText", String.class );
         method.setAccessible( true );
         return (String) method.invoke( null, text );
      } catch ( ReflectiveOperationException e ) {
         throw new RuntimeException( e );
      }
   }
}
