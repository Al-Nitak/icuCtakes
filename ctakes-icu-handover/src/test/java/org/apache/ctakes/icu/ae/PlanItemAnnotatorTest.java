package org.apache.ctakes.icu.ae;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Plan header + narrative cue tests for {@link PlanItemAnnotator}.
 */
public class PlanItemAnnotatorTest {

   private static final Pattern HEADER = Pattern.compile(
         PlanItemAnnotator.PLAN_HEADER_REGEX, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE );

   @Test
   public void planHeaderMatchesStandalonePlan() {
      assertTrue( HEADER.matcher( "Plan:" ).find() );
      assertTrue( HEADER.matcher( "PLAN" ).find() );
      assertTrue( HEADER.matcher( "Assessment and Plan:" ).find() );
      assertTrue( HEADER.matcher( "Recommendations:" ).find() );
      assertTrue( HEADER.matcher( "Next steps:" ).find() );
   }

   @Test
   public void planHeaderMatchesCarePlanForTodaySameLine() {
      assertTrue( PlanItemAnnotator.matchesPlanHeader(
            "Care plan for today: We will start the patient on a high-protein diet." ) );
      assertTrue( PlanItemAnnotator.matchesPlanHeader( "CARE PLAN FOR THE DAY:\nContinue fluids." ) );
      assertTrue( PlanItemAnnotator.matchesPlanHeader( "Care plan: initiate enteral feeding." ) );
   }

   @Test
   public void planHeaderRejectsOtherSections() {
      assertFalse( PlanItemAnnotator.matchesPlanHeader( "Situation:" ) );
      assertFalse( PlanItemAnnotator.matchesPlanHeader( "Background: COPD" ) );
      assertFalse( PlanItemAnnotator.matchesPlanHeader(
            "The patient was hyperglycemic with a range of 330 at admission." ) );
   }

   @Test
   public void planCueMatchesNarrativeAndImperatives() {
      assertTrue( PlanItemAnnotator.matchesPlanCue(
            "We will start the patient on a high-protein regular diet." ) );
      assertTrue( PlanItemAnnotator.matchesPlanCue(
            "We continue IV fluids at 200 mL/h with ultrafiltration at 150 mL/h." ) );
      assertTrue( PlanItemAnnotator.matchesPlanCue(
            "We are maintaining paracetamol 1 g Q8H." ) );
      assertTrue( PlanItemAnnotator.matchesPlanCue(
            "We have not initiated thromboembolic prophylaxis due to coagulopathy." ) );
      assertTrue( PlanItemAnnotator.matchesPlanCue(
            "For analgesia, we are maintaining paracetamol 1 g Q8H." ) );
      assertTrue( PlanItemAnnotator.matchesPlanCue( "Continue propofol infusion." ) );
      assertTrue( PlanItemAnnotator.matchesPlanCue( "Initiate enteral feeding." ) );
   }

   @Test
   public void planCueRejectsAssessmentProse() {
      assertFalse( PlanItemAnnotator.matchesPlanCue(
            "Regarding bowel function, as previously noted, he has diarrhea." ) );
      assertFalse( PlanItemAnnotator.matchesPlanCue(
            "The patient was hyperglycemic with a range of 330 at admission." ) );
      assertFalse( PlanItemAnnotator.matchesPlanCue(
            "The head of bed is elevated." ) );
   }
}
