package org.apache.ctakes.icu.util;

import org.apache.ctakes.icu.cc.HandoverDocument;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Document-order sentence index with stable 0-based ids for joining concepts to narrative text.
 */
final public class SentenceIndex {

   /** Leading section header fused into the first sentence by the sentence detector. */
   static private final Pattern SECTION_HEADER_PREFIX = Pattern.compile(
         "^\\s*[A-Za-z][A-Za-z /]{0,30}:\\s*" );

   private final List<HandoverDocument.SentenceDto> sentences;

   /**
    * Package-visible / public for unit tests that supply prebuilt sentence DTOs without a JCas.
    */
   public SentenceIndex( final List<HandoverDocument.SentenceDto> sentences ) {
      final List<HandoverDocument.SentenceDto> copy = new ArrayList<>(
            sentences == null ? List.of() : sentences );
      copy.sort( Comparator.comparingInt( s -> s.begin ) );
      for ( int i = 0; i < copy.size(); i++ ) {
         copy.get( i ).id = i;
      }
      this.sentences = Collections.unmodifiableList( copy );
   }

   static public SentenceIndex of( final JCas jCas ) {
      final List<Segment> segments = new ArrayList<>( JCasUtil.select( jCas, Segment.class ) );
      final List<HandoverDocument.SentenceDto> dtos = new ArrayList<>();
      int id = 0;
      for ( Sentence sentence : JCasUtil.select( jCas, Sentence.class ) ) {
         final HandoverDocument.SentenceDto dto = new HandoverDocument.SentenceDto();
         dto.id = id++;
         dto.begin = sentence.getBegin();
         dto.end = sentence.getEnd();
         final String covered = sentence.getCoveredText();
         dto.text = normalizeText( covered );
         dto.contentBegin = contentBegin( sentence.getBegin(), covered, dto.text );
         dto.sectionId = coveringSectionId( segments, sentence.getBegin(), sentence.getEnd() );
         dtos.add( dto );
      }
      return new SentenceIndex( dtos );
   }

   public List<HandoverDocument.SentenceDto> sentences() {
      return sentences;
   }

   /**
    * Returns the sentence id covering {@code begin}, or null if none.
    * Annotations that extend past the sentence end still resolve to the sentence containing their start.
    */
   public Integer idFor( final int begin, final int end ) {
      if ( sentences.isEmpty() ) {
         return null;
      }
      int lo = 0;
      int hi = sentences.size() - 1;
      while ( lo <= hi ) {
         final int mid = ( lo + hi ) >>> 1;
         final HandoverDocument.SentenceDto s = sentences.get( mid );
         if ( begin < s.begin ) {
            hi = mid - 1;
         } else if ( begin >= s.end ) {
            lo = mid + 1;
         } else {
            return s.id;
         }
      }
      return null;
   }

   /**
    * Returns the sentence DTO for an id, or null if out of range.
    */
   public HandoverDocument.SentenceDto get( final Integer id ) {
      if ( id == null || id < 0 || id >= sentences.size() ) {
         return null;
      }
      return sentences.get( id );
   }

   /**
    * Returns the next sentence after {@code id}, or null.
    */
   public HandoverDocument.SentenceDto next( final Integer id ) {
      if ( id == null || id < 0 || id + 1 >= sentences.size() ) {
         return null;
      }
      return sentences.get( id + 1 );
   }

   static String normalizeText( final String raw ) {
      if ( raw == null ) {
         return "";
      }
      String text = raw.trim();
      text = SECTION_HEADER_PREFIX.matcher( text ).replaceFirst( "" ).trim();
      return text;
   }

   /**
    * CAS offset where {@code normalized} begins inside the original covered span.
    */
   static int contentBegin( final int sentenceBegin, final String covered, final String normalized ) {
      if ( covered == null || normalized == null || normalized.isEmpty() ) {
         return sentenceBegin;
      }
      final int idx = covered.indexOf( normalized );
      if ( idx >= 0 ) {
         return sentenceBegin + idx;
      }
      // Fallback: account for leading whitespace trim only
      int i = 0;
      while ( i < covered.length() && Character.isWhitespace( covered.charAt( i ) ) ) {
         i++;
      }
      return sentenceBegin + i;
   }

   static private String coveringSectionId( final List<Segment> segments, final int begin, final int end ) {
      for ( Segment segment : segments ) {
         if ( begin >= segment.getBegin() && begin < segment.getEnd() ) {
            if ( segment.getId() != null && !segment.getId().isBlank() ) {
               return segment.getId();
            }
            if ( segment.getPreferredText() != null && !segment.getPreferredText().isBlank() ) {
               return segment.getPreferredText();
            }
         }
      }
      return null;
   }
}
