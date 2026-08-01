package org.apache.ctakes.icu.cc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Drug class lexicon coverage for ICU handover assembly.
 */
public class HandoverAssemblerClassifyDrugTest {

   @Test
   public void classifiesAnalgesics() {
      assertEquals( "analgesic", HandoverAssembler.classifyDrug( "tramadol", null ) );
      assertEquals( "analgesic", HandoverAssembler.classifyDrug( "paracetamol", "Acetaminophen" ) );
      assertEquals( "analgesic", HandoverAssembler.classifyDrug( "lidocaine", null ) );
   }

   @Test
   public void classifiesSteroidAndFluid() {
      assertEquals( "steroid", HandoverAssembler.classifyDrug( "dexamethasone", null ) );
      assertEquals( "fluid", HandoverAssembler.classifyDrug( "normal saline", null ) );
   }

   @Test
   public void classifiesZivoAsGiOndansetronAlias() {
      assertEquals( "gi", HandoverAssembler.classifyDrug( "Zivo", "Ondansetron" ) );
   }

   @Test
   public void classifiesAntihypertensive() {
      assertEquals( "antihypertensive", HandoverAssembler.classifyDrug( "amlodipine", null ) );
   }

   @Test
   public void parsesEveryHoursFrequency() {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      HandoverAssembler.fillMedAttributesFromWindow( dto, " 50 mg every 8 hours, paracetamol" );
      assertEquals( "50 mg", dto.dose );
      assertEquals( "q8h", dto.frequency );
   }

   @Test
   public void preferEveryHoursOverridesBrokenDayFreq() {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      dto.frequency = "3.0 day";
      HandoverAssembler.preferEveryHoursFrequency( dto, " 50 mg every 8 hours, and Zivo" );
      assertEquals( "q8h", dto.frequency );
   }

   @Test
   public void truncateWindowAtNextDrug() {
      final String truncated = HandoverAssembler.truncateAtNextDrug(
            " 50 mg every 8 hours, paracetamol every 8 hours", "tramadol" );
      assertEquals( true, truncated.toLowerCase().contains( "every 8 hours" ) );
      assertEquals( false, truncated.toLowerCase().contains( "paracetamol" ) );
   }
}
