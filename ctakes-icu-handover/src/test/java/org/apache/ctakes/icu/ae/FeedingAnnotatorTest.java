package org.apache.ctakes.icu.ae;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Word-boundary formula matching — "vitally" must not hit Abbott Vital.
 */
public class FeedingAnnotatorTest {

   @Test
   public void vitalFormulaMatchesWholeWord() {
      assertTrue( FeedingAnnotator.matchesFormulaToken( "Vital 40 via NGT", "vital" ) );
      assertTrue( FeedingAnnotator.matchesFormulaToken( "started on vital feeds", "vital" ) );
      assertTrue( FeedingAnnotator.matchesFormulaToken( "VITAL AF", "vital" ) );
   }

   @Test
   public void vitalFormulaRejectsVitally() {
      assertFalse( FeedingAnnotator.matchesFormulaToken(
            "Cardiovascular, he was vitally stable toward hypertension.", "vital" ) );
      assertFalse( FeedingAnnotator.matchesFormulaToken( "vitally stable", "vital" ) );
      assertFalse( FeedingAnnotator.matchesFormulaToken( "revitalize", "vital" ) );
   }

   @Test
   public void otherFormulasStillMatch() {
      assertTrue( FeedingAnnotator.matchesFormulaToken( "Nepro 40", "nepro" ) );
      assertTrue( FeedingAnnotator.matchesFormulaToken( "ensure 65 ml/h", "ensure" ) );
   }
}
