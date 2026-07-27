package org.apache.ctakes.icu.cc;

import org.apache.ctakes.icu.ae.CultureResultAnnotator;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Culture sensitivity parsing tests.
 */
public class CultureResultAnnotatorTest {

   @Test
   public void parsesMultisensitive() {
      final List<String> sens = CultureResultAnnotator.extractSensitivities(
            "Previously grew Pseudomonas but sensitive, multisensitive." );
      assertTrue( sens.stream().anyMatch( s -> s.toLowerCase().contains( "sensitive" ) ) );
      assertTrue( sens.stream().anyMatch( s -> s.toLowerCase().contains( "multisensitive" ) ) );
   }

   @Test
   public void parsesAntibioticTokenSens() {
      final List<String> sens = CultureResultAnnotator.extractSensitivities(
            "Blood culture growing E. coli sensitive CTX-S CIP-R." );
      assertTrue( sens.contains( "CTX-S" ) );
   }

   @Test
   public void noFalseSensOnNegativePhrase() {
      final List<String> sens = CultureResultAnnotator.extractSensitivities( "cultures were sent." );
      assertFalse( sens.stream().anyMatch( s -> s.equalsIgnoreCase( "sent" ) ) );
   }
}
