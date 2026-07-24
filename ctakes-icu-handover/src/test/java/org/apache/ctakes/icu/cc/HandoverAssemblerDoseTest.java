package org.apache.ctakes.icu.cc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for ICU med dose / route / frequency window parsing.
 */
public class HandoverAssemblerDoseTest {

   @Test
   public void parsesInfusionRateAfterMidazolam() {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      HandoverAssembler.fillMedAttributesFromWindow( dto, " 5 mg/h and fentanyl" );
      assertEquals( "5 mg/h", dto.dose );
      assertNull( dto.route );
      assertNull( dto.frequency );
   }

   @Test
   public void parsesCeftriaxoneDoseRouteFrequency() {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      HandoverAssembler.fillMedAttributesFromWindow( dto, " 2 g IV q24h. Fluconazole" );
      assertEquals( "2 g", dto.dose );
      assertEquals( "IV", dto.route );
      assertEquals( "q24h", dto.frequency );
   }

   @Test
   public void parsesPantoprazoleDoseRouteDaily() {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      HandoverAssembler.fillMedAttributesFromWindow( dto, " 40 mg IV daily." );
      assertEquals( "40 mg", dto.dose );
      assertEquals( "IV", dto.route );
      assertEquals( "daily", dto.frequency );
   }

   @Test
   public void parsesAsNeededAsPrn() {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      HandoverAssembler.fillMedAttributesFromWindow( dto, " 20 mg IV as needed." );
      assertEquals( "20 mg", dto.dose );
      assertEquals( "IV", dto.route );
      assertEquals( "PRN", dto.frequency );
   }

   @Test
   public void parsesMcgKgMinRate() {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      HandoverAssembler.fillMedAttributesFromWindow( dto, " 0.1 mcg/kg/min" );
      assertEquals( "0.1 mcg/kg/min", dto.dose );
   }

   @Test
   public void doesNotFillWhenWindowHasNoDose() {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      HandoverAssembler.fillMedAttributesFromWindow( dto, " continued overnight." );
      assertNull( dto.dose );
      assertNull( dto.route );
      assertNull( dto.frequency );
   }

   @Test
   public void doesNotOverwriteExistingModifierValues() {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      dto.dose = "10 mg";
      dto.route = "PO";
      dto.frequency = "bid";
      HandoverAssembler.fillMedAttributesFromWindow( dto, " 2 g IV q24h" );
      assertEquals( "10 mg", dto.dose );
      assertEquals( "PO", dto.route );
      assertEquals( "bid", dto.frequency );
   }
}
