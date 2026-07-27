package org.apache.ctakes.icu.cc;

import org.apache.ctakes.icu.ae.LabValueAnnotator;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Lab value pattern smoke tests (lexicon + value regex).
 */
public class LabValuePatternTest {

   static private final Pattern VALUE_AFTER = Pattern.compile(
         "(?i)^\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)(?:\\s*(mg/dl|mmol/l))?\\b" );

   @Test
   public void parsesCreatinineValue() {
      final Matcher m = VALUE_AFTER.matcher( " 3, and CRP" );
      assertNotNull( m );
      assertEquals( true, m.find() );
      assertEquals( "3", m.group( 1 ) );
   }

   @Test
   public void parsesCrpRisingTo() {
      final Matcher m = Pattern.compile( "(?i)^\\s*(?:was\\s+)?(?:rising\\s+to|up\\s+to)\\s*(\\d+(?:\\.\\d+)?)\\b" )
            .matcher( " was rising to 120." );
      assertEquals( true, m.find() );
      assertEquals( "120", m.group( 1 ) );
   }
}
