package org.apache.ctakes.icu.ae;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Negation lookbehind tests for {@link VentSupportAnnotator}.
 */
public class VentSupportAnnotatorTest {

   @Test
   public void detectsNegatedMechanicalVentilation() {
      final String text = "The patient is not on mechanical ventilation, so we did not have sedation.";
      final int idx = text.toLowerCase().indexOf( "mechanical" );
      assertTrue( idx > 0 );
      assertTrue( VentSupportAnnotator.isNegatedSupport( text, idx ) );
   }

   @Test
   public void allowsAffirmedMechanicalVentilation() {
      final String text = "The patient is now on mechanical ventilation, SIMV mode.";
      final int idx = text.toLowerCase().indexOf( "mechanical" );
      assertTrue( idx > 0 );
      assertFalse( VentSupportAnnotator.isNegatedSupport( text, idx ) );
   }

   @Test
   public void negationDoesNotCrossSentenceBoundary() {
      final String text = "He is not on pressors. Now on mechanical ventilation, SIMV mode.";
      final int idx = text.toLowerCase().indexOf( "mechanical" );
      assertTrue( idx > 0 );
      assertFalse( VentSupportAnnotator.isNegatedSupport( text, idx ) );
   }

   @Test
   public void detectsIsNotOnVariant() {
      final String text = "He is not on high-flow oxygen currently.";
      final int idx = text.toLowerCase().indexOf( "high-flow" );
      assertTrue( VentSupportAnnotator.isNegatedSupport( text, idx ) );
   }
}
