package org.apache.ctakes.icu.cc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A medication carries only the dose / route / frequency said about that drug.
 * <p>
 * Each case is a sentence as dictated, with the values Drug NER attached to the mention when
 * the sentence was run through the ICU pipeline (it attaches by sentence, with no offsets, so
 * they can belong to any item in it). A blank field is acceptable; a value from another item
 * is not.
 * </p>
 */
public class HandoverAssemblerMedAttributesTest {

   /** A mention of {@code name} in {@code text}, as toMed hands it over before any attribute is settled. */
   static private HandoverDocument.MedDto mention( final String text, final String name ) {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      dto.text = name;
      dto.begin = text.indexOf( name );
      dto.end = dto.begin + name.length();
      return dto;
   }

   /** The whole text is one sentence — also what the sentence detector does when it fuses two. */
   static private void attach( final HandoverDocument.MedDto dto, final String text ) {
      HandoverAssembler.attachSpokenAttributes( dto, text, 0, text.length(), -1 );
   }

   // ---- 1. Nothing is read across the end of a sentence --------------------------------

   static private final String FENTANYL
         = "She is sedated on fentanyl 100 mcg per hour. She is on levetiracetam 500 mg BID for seizure prophylaxis.";

   @Test
   public void frequencyOfTheNextSentenceIsNotTaken() {
      final HandoverDocument.MedDto fentanyl = mention( FENTANYL, "fentanyl" );
      fentanyl.strength = "100 mcg";
      // Sentence annotation [0,44].
      HandoverAssembler.attachSpokenAttributes( fentanyl, FENTANYL, 0, 44, -1 );
      assertEquals( "100 mcg per hour", fentanyl.dose );
      assertEquals( "100 mcg", fentanyl.strength );
      assertNull( fentanyl.frequency );
      assertNull( fentanyl.route );
   }

   @Test
   public void fullStopEndsThePhraseEvenWhenTheSentenceDetectorMissesIt() {
      final HandoverDocument.MedDto fentanyl = mention( FENTANYL, "fentanyl" );
      fentanyl.strength = "100 mcg";
      attach( fentanyl, FENTANYL );
      assertEquals( "100 mcg per hour", fentanyl.dose );
      assertNull( fentanyl.frequency );
   }

   @Test
   public void conditionAndNextSentenceGiveNoradrenalineNothing() {
      final String text = "Start noradrenaline if MAP is below 65. She is on salbutamol nebulizer every 4 hours.";
      final HandoverDocument.MedDto noradrenaline = mention( text, "noradrenaline" );
      // Sentence annotation [0,39].
      HandoverAssembler.attachSpokenAttributes( noradrenaline, text, 0, 39, -1 );
      assertNull( noradrenaline.dose );
      assertNull( noradrenaline.frequency );
      assertNull( noradrenaline.route );

      final HandoverDocument.MedDto fused = mention( text, "noradrenaline" );
      attach( fused, text );
      assertNull( fused.dose );
      assertNull( fused.frequency );
      assertNull( fused.route );
   }

   @Test
   public void routeOfThePreviousSentenceIsNotTaken() {
      // The sentence detector returns this as one sentence, [0,59].
      final String text = "She is kept NPO (nil by mouth). She is on pantoprazole BID.";
      final HandoverDocument.MedDto pantoprazole = mention( text, "pantoprazole" );
      pantoprazole.frequency = HandoverAssembler.normalizeNerFrequency( "2.0 BID" );
      attach( pantoprazole, text );
      assertEquals( "bid", pantoprazole.frequency );
      assertNull( pantoprazole.route );
      assertNull( pantoprazole.dose );
   }

   // ---- 2. Nothing is read across to the next item of the same sentence ----------------

   @Test
   public void lactuloseKeepsItsOwnDoseNotTheEnemas() {
      final String text = "She is also receiving lactulose 30 ml every 8 hours and a 300 ml enema every 12 hours.";
      final HandoverDocument.MedDto lactulose = mention( text, "lactulose" );
      lactulose.dose = "300";
      attach( lactulose, text );
      assertEquals( "30 ml", lactulose.dose );
      assertEquals( "q8h", lactulose.frequency );
      assertNull( lactulose.route );
   }

   static private final String CRRT
         = "Continue on CRRT, give pantoprazole BID, start IV fluid D5LR at 100 ml per hour, RBS Q2 hours, "
               + "lactulose enema 300 ml plus 700 ml normal saline Q12, and 30 ml oral Q8 hours.";

   @Test
   public void pantoprazoleDoesNotTakeTheFluidsRateAndRoute() {
      final HandoverDocument.MedDto pantoprazole = mention( CRRT, "pantoprazole" );
      pantoprazole.dose = "100";
      pantoprazole.route = "Parenteral_Intravenous";
      attach( pantoprazole, CRRT );
      assertEquals( "bid", pantoprazole.frequency );
      assertNull( pantoprazole.dose );
      assertNull( pantoprazole.route );
   }

   @Test
   public void normalSalineDoesNotTakeTheOralDoseAndRoute() {
      final HandoverDocument.MedDto saline = mention( CRRT, "normal saline" );
      saline.dose = "30";
      attach( saline, CRRT );
      // "plus 700 ml normal saline": the dose said ahead of the name.
      assertEquals( "700 ml", saline.dose );
      assertEquals( "q12", saline.frequency );
      assertNull( saline.route );
   }

   @Test
   public void lactuloseEnemaStopsAtPlus() {
      final HandoverDocument.MedDto lactulose = mention( CRRT, "lactulose" );
      attach( lactulose, CRRT );
      assertEquals( "300 ml", lactulose.dose );
      assertNull( lactulose.frequency );
      assertNull( lactulose.route );
   }

   @Test
   public void secondDoseStartsTheNextItemWithoutPunctuation() {
      // levetiracetam is not an ICU lexicon drug, and nothing separates the two items.
      final String text = "Fentanyl 100 mcg per hour levetiracetam 500 mg BID.";
      final HandoverDocument.MedDto fentanyl = mention( text, "Fentanyl" );
      fentanyl.strength = "100 mg";
      attach( fentanyl, text );
      assertEquals( "100 mcg per hour", fentanyl.dose );
      // Drug NER paired fentanyl's number with levetiracetam's unit.
      assertNull( fentanyl.strength );
      assertNull( fentanyl.frequency );
   }

   @Test
   public void strengthOfThePreviousItemIsNotTheDose() {
      final String text = "Propofol 20 mg per hour, midazolam stopped.";
      final HandoverDocument.MedDto midazolam = mention( text, "midazolam" );
      midazolam.strength = "20 mg";
      attach( midazolam, text );
      assertNull( midazolam.dose );
      assertNull( midazolam.strength );

      final HandoverDocument.MedDto propofol = mention( text, "Propofol" );
      propofol.strength = "20 mg";
      attach( propofol, text );
      assertEquals( "20 mg per hour", propofol.dose );
   }

   @Test
   public void diluentVolumeIsNotTheDrugsDose() {
      final String text = "Vancomycin 1 g in 250 ml normal saline over 2 hours q12h.";
      final HandoverDocument.MedDto vancomycin = mention( text, "Vancomycin" );
      vancomycin.dose = "250";
      vancomycin.strength = "1 g";
      vancomycin.frequency = HandoverAssembler.normalizeNerFrequency( "2.0 q12h" );
      attach( vancomycin, text );
      assertEquals( "1 g", vancomycin.dose );
      assertNull( vancomycin.frequency );

      final HandoverDocument.MedDto saline = mention( text, "normal saline" );
      saline.dose = "250";
      saline.strength = "1 g";
      attach( saline, text );
      assertNull( saline.dose );
      assertNull( saline.strength );
   }

   @Test
   public void adjacentItemsWithoutPunctuationKeepTheirOwnRoutes() {
      final String text = "Enoxaparin 40 mg SC paracetamol 1 g IV.";
      final HandoverDocument.MedDto enoxaparin = mention( text, "Enoxaparin" );
      enoxaparin.strength = "40 g";
      enoxaparin.route = "Parenteral_Intravenous";
      attach( enoxaparin, text );
      assertEquals( "40 mg", enoxaparin.dose );
      assertNull( enoxaparin.strength );
      assertEquals( "SC", enoxaparin.route );

      final HandoverDocument.MedDto paracetamol = mention( text, "paracetamol" );
      paracetamol.strength = "1 g";
      paracetamol.route = "Parenteral_Intravenous";
      HandoverAssembler.attachSpokenAttributes( paracetamol, text, 0, text.length(), enoxaparin.end );
      assertEquals( "1 g", paracetamol.dose );
      assertEquals( "IV", paracetamol.route );
   }

   @Test
   public void routeDirectlyAfterAnotherDrugIsThatDrugs() {
      final String text = "levetiracetam IV paracetamol was given.";
      final HandoverDocument.MedDto paracetamol = mention( text, "paracetamol" );
      HandoverAssembler.attachSpokenAttributes( paracetamol, text, 0, text.length(), "levetiracetam".length() );
      assertNull( paracetamol.route );
   }

   @Test
   public void commaInsideANumberIsNotABreak() {
      final String text = "She is on heparin 5,000 units SC TID.";
      final HandoverDocument.MedDto heparin = mention( text, "heparin" );
      heparin.strength = "5000 units";
      attach( heparin, text );
      assertEquals( "5,000 units", heparin.dose );
      assertEquals( "5000 units", heparin.strength );
      assertEquals( "SC", heparin.route );
      assertEquals( "tid", heparin.frequency );
   }

   @Test
   public void rangeIsKeptWhole() {
      final String text = "She is on morphine 2 to 4 mg IV as needed.";
      final HandoverDocument.MedDto morphine = mention( text, "morphine" );
      morphine.strength = "4 mg";
      morphine.route = "Parenteral_Intravenous";
      attach( morphine, text );
      assertEquals( "2 to 4 mg", morphine.dose );
      assertEquals( "IV", morphine.route );
      assertEquals( "PRN", morphine.frequency );
   }

   @Test
   public void levelIsNotADose() {
      final String text = "Vancomycin level 15 mg/L this morning.";
      final HandoverDocument.MedDto vancomycin = mention( text, "Vancomycin" );
      vancomycin.strength = "15 mg";
      attach( vancomycin, text );
      assertNull( vancomycin.dose );
      assertNull( vancomycin.strength );
   }

   @Test
   public void oxygenFlowIsNotADose() {
      // Golden example 03: the lexicon finds "asa" inside "nasal".
      final String text = "Respiratory: nasal cannula 2 litres. GI: regular diet.";
      final HandoverDocument.MedDto asa = mention( text, "asa" );
      attach( asa, text );
      assertNull( asa.dose );
   }

   @Test
   public void routeAndFrequencyAroundABareNumber() {
      // Golden example 01. The route used to come back IV, from the furosemide before it.
      final String text = "On furosemide 40 IV twice daily. Hematology: on heparin subcutaneous 5000 "
            + "three times a day for prophylaxis.";
      final HandoverDocument.MedDto heparin = mention( text, "heparin" );
      attach( heparin, text );
      assertEquals( "SC", heparin.route );
      assertEquals( "tid", heparin.frequency );
      assertNull( heparin.dose );
   }

   @Test
   public void doseInWordsHasToBeInTheDrugsOwnPhrase() {
      // spoken_headers example: Drug NER gave heparin the "two units" of the next sentence.
      final String text = "He is bleeding, so no heparin or DVT pharmacologic prophylaxis is given. "
            + "He received two units of packed red cells.";
      final HandoverDocument.MedDto heparin = mention( text, "heparin" );
      heparin.dose = "two units";
      heparin.strength = "two units";
      attach( heparin, text );
      assertNull( heparin.dose );
      assertNull( heparin.strength );

      final String own = "She was given insulin two units subcutaneously.";
      final HandoverDocument.MedDto insulin = mention( own, "insulin" );
      insulin.dose = "two units";
      attach( insulin, own );
      assertEquals( "two units", insulin.dose );
      assertEquals( "SC", insulin.route );
   }

   // ---- Said ahead of the name -----------------------------------------------------------

   @Test
   public void doseSaidAheadOfTheDrug() {
      final String text = "We gave 40 mg of furosemide IV.";
      final HandoverDocument.MedDto furosemide = mention( text, "furosemide" );
      furosemide.route = "Parenteral_Intravenous";
      attach( furosemide, text );
      assertEquals( "40 mg", furosemide.dose );
      assertEquals( "IV", furosemide.route );
   }

   @Test
   public void routeSaidAheadOfTheDrug() {
      final String text = "Start IV paracetamol 1 g q6h.";
      final HandoverDocument.MedDto paracetamol = mention( text, "paracetamol" );
      paracetamol.strength = "1 g";
      attach( paracetamol, text );
      assertEquals( "1 g", paracetamol.dose );
      assertEquals( "IV", paracetamol.route );
      assertEquals( "q6h", paracetamol.frequency );
   }

   // ---- 3. Worded frequencies ------------------------------------------------------------

   @Test
   public void twiceDailyIsNotDaily() {
      final String text = "She is on pantoprazole 40 IV twice daily and furosemide 40 IV twice daily.";
      final HandoverDocument.MedDto pantoprazole = mention( text, "pantoprazole" );
      pantoprazole.route = "Parenteral_Intravenous";
      attach( pantoprazole, text );
      assertEquals( "bid", pantoprazole.frequency );
      assertEquals( "IV", pantoprazole.route );
      assertNull( pantoprazole.dose );

      final HandoverDocument.MedDto furosemide = mention( text, "furosemide" );
      furosemide.route = "Parenteral_Intravenous";
      attach( furosemide, text );
      assertEquals( "bid", furosemide.frequency );
      assertEquals( "IV", furosemide.route );
      assertNull( furosemide.dose );
   }

   @Test
   public void wordedFrequenciesMapToTheirAbbreviation() {
      assertEquals( "daily", frequencyOf( " 40 mg IV once daily." ) );
      assertEquals( "daily", frequencyOf( " 40 mg once a day." ) );
      assertEquals( "bid", frequencyOf( " 40 mg twice daily." ) );
      assertEquals( "bid", frequencyOf( " 40 mg twice a day." ) );
      assertEquals( "bid", frequencyOf( " 40 mg two times daily." ) );
      assertEquals( "tid", frequencyOf( " 1 g IV three times daily." ) );
      assertEquals( "tid", frequencyOf( " 1 g IV three times a day." ) );
      assertEquals( "tid", frequencyOf( " 1 g IV 3 times per day." ) );
      assertEquals( "qid", frequencyOf( " 125 mg four times a day." ) );
      assertEquals( "daily", frequencyOf( " 40 mg IV daily." ) );
   }

   @Test
   public void countWithNoAbbreviationIsKeptAsSaid() {
      assertEquals( "5 times daily", frequencyOf( " 2 puffs 5 times daily." ) );
      // Never the bare "daily" inside it.
      assertNull( frequencyOf( " 2 puffs several times daily." ) );
   }

   static private String frequencyOf( final String window ) {
      final HandoverDocument.MedDto dto = new HandoverDocument.MedDto();
      HandoverAssembler.fillMedAttributesFromWindow( dto, window );
      return dto.frequency;
   }

   // ---- 4. Drug NER frequency strings ----------------------------------------------------

   @Test
   public void nerFrequencyIsNormalisedOrDropped() {
      assertEquals( "bid", HandoverAssembler.normalizeNerFrequency( "2.0 BID" ) );
      assertEquals( "q12h", HandoverAssembler.normalizeNerFrequency( "2.0 q12h" ) );
      assertEquals( "daily", HandoverAssembler.normalizeNerFrequency( "day" ) );
      assertEquals( "weekly", HandoverAssembler.normalizeNerFrequency( "weekly" ) );
      assertEquals( "PRN", HandoverAssembler.normalizeNerFrequency( "prn" ) );
      assertNull( HandoverAssembler.normalizeNerFrequency( "3.0 day" ) );
      assertNull( HandoverAssembler.normalizeNerFrequency( "h" ) );
      assertNull( HandoverAssembler.normalizeNerFrequency( "hr" ) );
      assertNull( HandoverAssembler.normalizeNerFrequency( "1.0" ) );
      assertNull( HandoverAssembler.normalizeNerFrequency( " " ) );
      assertNull( HandoverAssembler.normalizeNerFrequency( null ) );
   }

   @Test
   public void nerFrequencyStandsOnlyWhenTheDrugsOwnWordsHoldIt() {
      final String text = "She is on amlodipine 5 mg weekly.";
      final HandoverDocument.MedDto amlodipine = mention( text, "amlodipine" );
      amlodipine.strength = "5 mg";
      amlodipine.frequency = "weekly";
      attach( amlodipine, text );
      assertEquals( "weekly", amlodipine.frequency );

      final String other = "She is on amlodipine 5 mg, and methotrexate weekly.";
      final HandoverDocument.MedDto bled = mention( other, "amlodipine" );
      bled.strength = "5 mg";
      bled.frequency = "weekly";
      attach( bled, other );
      assertEquals( "5 mg", bled.dose );
      assertNull( bled.frequency );
   }

   // ---- Dosing as it was before ------------------------------------------------------------

   @Test
   public void ordinaryDosingIsUnchanged() {
      final String text = "Tramadol 50 mg every 8 hours, paracetamol 1 g every 6 hours, and ondansetron 4 mg PRN.";
      final HandoverDocument.MedDto tramadol = mention( text, "Tramadol" );
      tramadol.strength = "50 mg";
      attach( tramadol, text );
      assertEquals( "50 mg", tramadol.dose );
      assertEquals( "q8h", tramadol.frequency );

      final HandoverDocument.MedDto paracetamol = mention( text, "paracetamol" );
      paracetamol.strength = "1 g";
      attach( paracetamol, text );
      assertEquals( "1 g", paracetamol.dose );
      assertEquals( "q6h", paracetamol.frequency );

      final HandoverDocument.MedDto ondansetron = mention( text, "ondansetron" );
      ondansetron.strength = "4 mg";
      attach( ondansetron, text );
      assertEquals( "4 mg", ondansetron.dose );
      assertEquals( "PRN", ondansetron.frequency );
   }

   @Test
   public void infusionRatesAreKeptWithTheirRate() {
      final String text = "Noradrenaline 0.1 mcg/kg/min and vasopressin 2 units per hour.";
      final HandoverDocument.MedDto noradrenaline = mention( text, "Noradrenaline" );
      noradrenaline.strength = "0.1 units";
      attach( noradrenaline, text );
      assertEquals( "0.1 mcg/kg/min", noradrenaline.dose );
      assertNull( noradrenaline.strength );

      final HandoverDocument.MedDto vasopressin = mention( text, "vasopressin" );
      vasopressin.strength = "2 units";
      attach( vasopressin, text );
      assertEquals( "2 units per hour", vasopressin.dose );

      final String fluid = "Normal saline at 80 ml per hour.";
      final HandoverDocument.MedDto saline = mention( fluid, "Normal saline" );
      saline.dose = "80";
      attach( saline, fluid );
      assertEquals( "80 ml per hour", saline.dose );
   }

   @Test
   public void durationIsNotASecondDose() {
      final String text = "Dexamethasone 4 mg IV q6h for 5 days.";
      final HandoverDocument.MedDto dexamethasone = mention( text, "Dexamethasone" );
      dexamethasone.strength = "4 mg";
      dexamethasone.route = "Parenteral_Intravenous";
      attach( dexamethasone, text );
      assertEquals( "4 mg", dexamethasone.dose );
      assertEquals( "IV", dexamethasone.route );
      assertEquals( "q6h", dexamethasone.frequency );
   }
}
