package org.apache.ctakes.icu.cc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders {@link HandoverDocument} as SBAR-style HTML.
 */
final public class HandoverHtmlRenderer {

   private HandoverHtmlRenderer() {
   }

   static public String render( final HandoverDocument doc ) {
      final StringBuilder sb = new StringBuilder( 8192 );
      sb.append( "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n" );
      sb.append( "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" );
      sb.append( "<title>ICU Handover — " ).append( esc( nullToEmpty( doc.documentId ) ) ).append( "</title>\n" );
      sb.append( "<style>\n" ).append( CSS ).append( "\n</style>\n</head>\n<body>\n" );
      sb.append( "<header class=\"page-header\">\n" );
      sb.append( "<h1>ICU Handover</h1>\n" );
      sb.append( "<p class=\"doc-id\">" ).append( esc( nullToEmpty( doc.documentId ) ) ).append( "</p>\n" );
      sb.append( "</header>\n" );

      section( sb, "S", "Situation", renderSituation( doc ) );
      section( sb, "B", "Background", renderBackground( doc ) );
      section( sb, "A", "Assessment", renderAssessment( doc ) );
      section( sb, "R", "Recommendation / Plan", renderPlan( doc ) );

      sb.append( "<h2 class=\"systems-title\">Systems review</h2>\n" );
      section( sb, null, "CNS", renderCns( doc.systems.cns ) );
      section( sb, null, "CVS", renderCvs( doc.systems.cvs ) );
      section( sb, null, "Respiratory", renderRespiratory( doc.systems.respiratory ) );
      section( sb, null, "GI", renderGi( doc.systems.gi ) );
      section( sb, null, "GU", renderGu( doc.systems.gu ) );
      section( sb, null, "Hematology / Culture", renderHeme( doc.systems.hematology ) );
      section( sb, null, "Labs", renderLabs( doc.systems.labs ) );
      section( sb, null, "LINE / Access", renderAccess( doc.access ) );
      section( sb, null, "Medications", renderAllMeds( doc.access.medications ) );
      section( sb, null, "Abx", renderMeds( doc.access.antibiotics ) );
      section( sb, null, "Imaging", renderImaging( doc.imaging ) );

      sb.append( "</body>\n</html>\n" );
      return sb.toString();
   }

   static private void section( final StringBuilder sb, final String letter, final String title,
                                final String body ) {
      sb.append( "<section class=\"handover-section\">\n" );
      sb.append( "<h2>" );
      if ( letter != null ) {
         sb.append( "<span class=\"sbar-letter\">" ).append( esc( letter ) ).append( "</span> " );
      }
      sb.append( esc( title ) ).append( "</h2>\n" );
      if ( body == null || body.isBlank() ) {
         sb.append( "<p class=\"empty\">No extracted content</p>\n" );
      } else {
         sb.append( body );
      }
      sb.append( "</section>\n" );
   }

   static private String renderBackground( final HandoverDocument doc ) {
      final StringBuilder sb = new StringBuilder();
      appendConceptList( sb, "Conditions / comorbidities", doc.background.conditions, doc.sentences );
      appendConceptList( sb, "Prior procedures", doc.background.priorProcedures, doc.sentences );
      appendConceptList( sb, "Functional status", doc.background.functionalStatus, doc.sentences );
      return sb.toString();
   }

   static private String renderSituation( final HandoverDocument doc ) {
      if ( doc.situation.events == null || doc.situation.events.isEmpty() ) {
         return "";
      }
      final StringBuilder sb = new StringBuilder();
      sb.append( "<ol class=\"timeline\">\n" );
      final Map<Integer, List<HandoverDocument.EventDto>> grouped = new LinkedHashMap<>();
      final List<HandoverDocument.EventDto> ungrouped = new ArrayList<>();
      for ( HandoverDocument.EventDto e : doc.situation.events ) {
         if ( e.sentenceId != null ) {
            grouped.computeIfAbsent( e.sentenceId, k -> new ArrayList<>() ).add( e );
         } else {
            ungrouped.add( e );
         }
      }
      for ( Map.Entry<Integer, List<HandoverDocument.EventDto>> entry : grouped.entrySet() ) {
         final HandoverDocument.SentenceDto sentence = sentenceById( doc.sentences, entry.getKey() );
         final List<SpanMark> marks = new ArrayList<>();
         String badge = null;
         for ( HandoverDocument.EventDto e : entry.getValue() ) {
            marks.add( new SpanMark( e.begin, e.end, e.text ) );
            if ( badge == null && e.docTimeRel != null && !e.docTimeRel.isEmpty() ) {
               badge = e.docTimeRel;
            }
         }
         sb.append( "<li>" );
         if ( sentence != null && sentence.text != null && !sentence.text.isBlank() ) {
            sb.append( "<span class=\"sentence\">" );
            appendMarkedSentence( sb, sentence, marks );
            sb.append( "</span>" );
         } else {
            sb.append( entry.getValue().stream()
                  .map( e -> esc( e.text ) )
                  .collect( Collectors.joining( "; " ) ) );
         }
         if ( badge != null ) {
            sb.append( " <span class=\"badge\">" ).append( esc( badge ) ).append( "</span>" );
         }
         sb.append( "</li>\n" );
      }
      for ( HandoverDocument.EventDto e : ungrouped ) {
         sb.append( "<li><span class=\"event-text\">" ).append( esc( e.text ) ).append( "</span>" );
         if ( e.docTimeRel != null && !e.docTimeRel.isEmpty() ) {
            sb.append( " <span class=\"badge\">" ).append( esc( e.docTimeRel ) ).append( "</span>" );
         }
         if ( e.type != null ) {
            sb.append( " <span class=\"muted\">" ).append( esc( e.type ) ).append( "</span>" );
         }
         sb.append( "</li>\n" );
      }
      sb.append( "</ol>\n" );
      return sb.toString();
   }

   static private String renderAssessment( final HandoverDocument doc ) {
      final StringBuilder sb = new StringBuilder();
      appendConceptList( sb, "Working diagnosis / problem list", doc.assessment.problems, doc.sentences );
      return sb.toString();
   }

   static private String renderPlan( final HandoverDocument doc ) {
      final StringBuilder sb = new StringBuilder();
      if ( doc.plan.sectionText != null && !doc.plan.sectionText.isBlank() ) {
         sb.append( "<p class=\"section-text\">" ).append( esc( doc.plan.sectionText.trim() ) ).append( "</p>\n" );
      }
      if ( doc.plan.items != null && !doc.plan.items.isEmpty() ) {
         sb.append( "<ul>\n" );
         for ( HandoverDocument.PlanItemDto item : doc.plan.items ) {
            sb.append( "<li>" ).append( esc( item.text ) ).append( "</li>\n" );
         }
         sb.append( "</ul>\n" );
      }
      return sb.toString();
   }

   static private String renderCns( final HandoverDocument.CnsDto cns ) {
      final StringBuilder sb = new StringBuilder();
      if ( cns.gcs != null ) {
         sb.append( "<p><strong>GCS:</strong> " ).append( cns.gcs.value );
         if ( Boolean.TRUE.equals( cns.gcs.underSedation ) ) {
            sb.append( " <span class=\"badge\">under sedation</span>" );
         }
         if ( cns.gcs.text != null ) {
            sb.append( " <span class=\"muted\">(" ).append( esc( cns.gcs.text ) ).append( ")</span>" );
         }
         sb.append( "</p>\n" );
      }
      appendConceptList( sb, "Findings", cns.findings, null );
      appendMedTable( sb, cns.medications );
      return sb.toString();
   }

   static private String renderCvs( final HandoverDocument.CvsDto cvs ) {
      final StringBuilder sb = new StringBuilder();
      if ( cvs.status != null && !cvs.status.isEmpty() ) {
         final String label = cvs.status.get( "label" );
         final String evidence = cvs.status.get( "evidence" );
         sb.append( "<p><strong>Status:</strong> " ).append( esc( nullToEmpty( label ) ) );
         if ( evidence != null && !evidence.isBlank() ) {
            sb.append( " <span class=\"muted\">(" ).append( esc( evidence ) ).append( ")</span>" );
         }
         sb.append( "</p>\n" );
      }
      appendMedTable( sb, cvs.medications );
      return sb.toString();
   }

   static private String renderRespiratory( final HandoverDocument.RespiratoryDto resp ) {
      final StringBuilder sb = new StringBuilder();
      if ( resp.support != null ) {
         sb.append( "<p><strong>Support:</strong> " ).append( esc( nullToEmpty( resp.support.mode ) ) );
         if ( resp.support.settings != null && !resp.support.settings.isEmpty() ) {
            sb.append( " — " );
            sb.append( resp.support.settings.entrySet().stream()
                  .map( e -> e.getKey() + "=" + e.getValue() )
                  .collect( Collectors.joining( ", " ) ) );
         }
         if ( resp.support.text != null ) {
            sb.append( " <span class=\"muted\">(" ).append( esc( resp.support.text ) ).append( ")</span>" );
         }
         sb.append( "</p>\n" );
      }
      appendMedTable( sb, resp.medications );
      return sb.toString();
   }

   static private String renderGi( final HandoverDocument.GiDto gi ) {
      final StringBuilder sb = new StringBuilder();
      if ( gi.feeding != null ) {
         sb.append( "<p><strong>Feeding:</strong> " );
         if ( gi.feeding.route != null ) {
            sb.append( esc( gi.feeding.route ) );
         }
         if ( gi.feeding.formula != null ) {
            if ( gi.feeding.route != null ) {
               sb.append( " / " );
            }
            sb.append( esc( gi.feeding.formula ) );
         }
         sb.append( "</p>\n" );
      }
      appendMedTable( sb, gi.medications );
      return sb.toString();
   }

   static private String renderGu( final HandoverDocument.GuDto gu ) {
      final StringBuilder sb = new StringBuilder();
      if ( gu.urineOutput != null ) {
         sb.append( "<p><strong>UOP:</strong> " );
         if ( gu.urineOutput.value != null ) {
            sb.append( gu.urineOutput.value );
         }
         if ( gu.urineOutput.unit != null ) {
            sb.append( ' ' ).append( esc( gu.urineOutput.unit ) );
         }
         sb.append( "</p>\n" );
      }
      appendMedTable( sb, gu.medications );
      return sb.toString();
   }

   static private String renderHeme( final HandoverDocument.HematologyDto heme ) {
      final StringBuilder sb = new StringBuilder();
      appendConceptList( sb, "Coagulation / labs", heme.coagStatus, null );
      appendMedTable( sb, heme.anticoagulants );
      if ( heme.cultures != null && !heme.cultures.isEmpty() ) {
         sb.append( "<h3>Cultures</h3>\n<table>\n<thead><tr>" );
         sb.append( "<th>Site</th><th>Organism</th><th>Status</th><th>Sensitivities</th></tr></thead>\n<tbody>\n" );
         for ( HandoverDocument.CultureDto c : heme.cultures ) {
            sb.append( "<tr><td>" ).append( esc( nullToEmpty( c.site ) ) ).append( "</td>" );
            sb.append( "<td>" ).append( esc( nullToEmpty( c.organism ) ) ).append( "</td>" );
            sb.append( "<td>" ).append( esc( nullToEmpty( c.status ) ) ).append( "</td>" );
            sb.append( "<td>" ).append( esc( join( c.sensitivities ) ) ).append( "</td></tr>\n" );
         }
         sb.append( "</tbody></table>\n" );
      }
      return sb.toString();
   }

   static private String renderLabs( final List<HandoverDocument.LabDto> labs ) {
      if ( labs == null || labs.isEmpty() ) {
         return "";
      }
      final StringBuilder sb = new StringBuilder();
      sb.append( "<table>\n<thead><tr><th>Lab</th><th>Value</th><th>Unit</th></tr></thead>\n<tbody>\n" );
      for ( HandoverDocument.LabDto lab : labs ) {
         sb.append( "<tr><td>" ).append( esc( firstNonBlank( lab.preferredText, lab.name, lab.text ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( lab.value ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( lab.unit ) ) ).append( "</td></tr>\n" );
      }
      sb.append( "</tbody></table>\n" );
      return sb.toString();
   }

   static private String renderAccess( final HandoverDocument.AccessDto access ) {
      if ( access.lines == null || access.lines.isEmpty() ) {
         return "";
      }
      final StringBuilder sb = new StringBuilder();
      sb.append( "<table>\n<thead><tr><th>Type</th><th>Text</th><th>Insert date</th></tr></thead>\n<tbody>\n" );
      for ( HandoverDocument.LineDto line : access.lines ) {
         sb.append( "<tr><td>" ).append( esc( nullToEmpty( line.type ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( line.text ) ) ).append( "</td>" );
         final String date = line.insertDate != null ? line.insertDate.text : "";
         sb.append( "<td>" ).append( esc( nullToEmpty( date ) ) ).append( "</td></tr>\n" );
      }
      sb.append( "</tbody></table>\n" );
      return sb.toString();
   }

   static private String renderMeds( final List<HandoverDocument.MedDto> meds ) {
      final StringBuilder sb = new StringBuilder();
      appendMedTable( sb, meds );
      return sb.toString();
   }

   static private String renderAllMeds( final List<HandoverDocument.MedDto> meds ) {
      if ( meds == null || meds.isEmpty() ) {
         return "";
      }
      final StringBuilder sb = new StringBuilder();
      sb.append( "<table>\n<thead><tr><th>Drug</th><th>Class</th><th>Dose</th><th>Route</th><th>Freq</th></tr></thead>\n<tbody>\n" );
      for ( HandoverDocument.MedDto m : meds ) {
         sb.append( "<tr><td>" ).append( esc( firstNonBlank( m.preferredText, m.text ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( m.drugClass ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( firstNonBlank( m.dose, m.strength ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( m.route ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( m.frequency ) ) ).append( "</td></tr>\n" );
      }
      sb.append( "</tbody></table>\n" );
      return sb.toString();
   }

   static private String renderImaging( final List<HandoverDocument.ImagingDto> imaging ) {
      if ( imaging == null || imaging.isEmpty() ) {
         return "";
      }
      final StringBuilder sb = new StringBuilder();
      sb.append( "<table>\n<thead><tr><th>Modality</th><th>Study</th><th>Site</th><th>Findings</th></tr></thead>\n<tbody>\n" );
      for ( HandoverDocument.ImagingDto img : imaging ) {
         sb.append( "<tr><td>" ).append( esc( nullToEmpty( img.modality ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( img.procedureText ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( img.bodySite ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( conceptLabels( img.findings ) ) ).append( "</td></tr>\n" );
      }
      sb.append( "</tbody></table>\n" );
      return sb.toString();
   }

   static private void appendConceptList( final StringBuilder sb, final String heading,
                                          final List<HandoverDocument.ConceptDto> concepts,
                                          final List<HandoverDocument.SentenceDto> sentences ) {
      if ( concepts == null || concepts.isEmpty() ) {
         return;
      }
      sb.append( "<h3>" ).append( esc( heading ) ).append( "</h3>\n" );
      if ( sentences == null || sentences.isEmpty()
            || concepts.stream().noneMatch( c -> c.sentenceId != null ) ) {
         sb.append( "<ul>\n" );
         for ( HandoverDocument.ConceptDto c : concepts ) {
            appendConceptBullet( sb, c );
         }
         sb.append( "</ul>\n" );
         return;
      }

      final Map<Integer, List<HandoverDocument.ConceptDto>> grouped = new LinkedHashMap<>();
      final List<HandoverDocument.ConceptDto> ungrouped = new ArrayList<>();
      for ( HandoverDocument.ConceptDto c : concepts ) {
         if ( c.sentenceId != null ) {
            grouped.computeIfAbsent( c.sentenceId, k -> new ArrayList<>() ).add( c );
         } else {
            ungrouped.add( c );
         }
      }
      sb.append( "<ul class=\"sentence-list\">\n" );
      for ( Map.Entry<Integer, List<HandoverDocument.ConceptDto>> entry : grouped.entrySet() ) {
         final HandoverDocument.SentenceDto sentence = sentenceById( sentences, entry.getKey() );
         sb.append( "<li>" );
         if ( sentence != null && sentence.text != null && !sentence.text.isBlank() ) {
            final List<SpanMark> marks = new ArrayList<>();
            for ( HandoverDocument.ConceptDto c : entry.getValue() ) {
               marks.add( new SpanMark( c.begin, c.end, c.text ) );
            }
            sb.append( "<span class=\"sentence\">" );
            appendMarkedSentence( sb, sentence, marks );
            sb.append( "</span>" );
            for ( HandoverDocument.ConceptDto c : entry.getValue() ) {
               final String label = firstNonBlank( c.preferredText, c.text );
               if ( c.cui != null && !c.cui.isEmpty() ) {
                  sb.append( " <span class=\"muted\">" ).append( esc( label ) )
                        .append( " " ).append( esc( c.cui ) ).append( "</span>" );
               }
            }
         } else {
            for ( int i = 0; i < entry.getValue().size(); i++ ) {
               if ( i > 0 ) {
                  sb.append( "; " );
               }
               appendConceptInline( sb, entry.getValue().get( i ) );
            }
         }
         sb.append( "</li>\n" );
      }
      for ( HandoverDocument.ConceptDto c : ungrouped ) {
         appendConceptBullet( sb, c );
      }
      sb.append( "</ul>\n" );
   }

   static private void appendConceptBullet( final StringBuilder sb, final HandoverDocument.ConceptDto c ) {
      final String label = firstNonBlank( c.preferredText, c.text );
      sb.append( "<li>" ).append( esc( label ) );
      if ( c.cui != null && !c.cui.isEmpty() ) {
         sb.append( " <span class=\"muted\">" ).append( esc( c.cui ) ).append( "</span>" );
      }
      if ( c.locations != null && !c.locations.isEmpty() ) {
         sb.append( " <span class=\"badge\">@" ).append( esc( join( c.locations ) ) ).append( "</span>" );
      }
      sb.append( "</li>\n" );
   }

   static private void appendConceptInline( final StringBuilder sb, final HandoverDocument.ConceptDto c ) {
      final String label = firstNonBlank( c.preferredText, c.text );
      sb.append( esc( label ) );
      if ( c.cui != null && !c.cui.isEmpty() ) {
         sb.append( " <span class=\"muted\">" ).append( esc( c.cui ) ).append( "</span>" );
      }
   }

   /**
    * Escape slices around marked spans separately so HTML escaping cannot shift offsets.
    */
   static private void appendMarkedSentence( final StringBuilder sb,
                                             final HandoverDocument.SentenceDto sentence,
                                             final List<SpanMark> marks ) {
      final String raw = sentence.text;
      if ( raw == null || raw.isEmpty() ) {
         return;
      }
      final List<SpanMark> sorted = new ArrayList<>();
      for ( SpanMark mark : marks ) {
         final int relBegin = mark.begin - sentence.contentBegin;
         final int relEnd = mark.end - sentence.contentBegin;
         if ( relBegin < 0 || relEnd > raw.length() || relBegin >= relEnd ) {
            continue;
         }
         sorted.add( new SpanMark( relBegin, relEnd, mark.label ) );
      }
      sorted.sort( Comparator.comparingInt( m -> m.begin ) );
      final List<SpanMark> kept = new ArrayList<>();
      int cursor = 0;
      for ( SpanMark mark : sorted ) {
         if ( mark.begin < cursor ) {
            continue;
         }
         kept.add( mark );
         cursor = mark.end;
      }
      int pos = 0;
      for ( SpanMark mark : kept ) {
         if ( mark.begin > pos ) {
            sb.append( esc( raw.substring( pos, mark.begin ) ) );
         }
         sb.append( "<mark>" ).append( esc( raw.substring( mark.begin, mark.end ) ) ).append( "</mark>" );
         pos = mark.end;
      }
      if ( pos < raw.length() ) {
         sb.append( esc( raw.substring( pos ) ) );
      }
   }

   static private HandoverDocument.SentenceDto sentenceById(
         final List<HandoverDocument.SentenceDto> sentences, final Integer id ) {
      if ( sentences == null || id == null || id < 0 || id >= sentences.size() ) {
         return null;
      }
      final HandoverDocument.SentenceDto s = sentences.get( id );
      if ( s != null && s.id == id ) {
         return s;
      }
      return sentences.stream()
            .filter( x -> x.id == id )
            .findFirst()
            .orElse( null );
   }

   static private void appendMedTable( final StringBuilder sb, final List<HandoverDocument.MedDto> meds ) {
      if ( meds == null || meds.isEmpty() ) {
         return;
      }
      sb.append( "<table>\n<thead><tr>" );
      sb.append( "<th>Medication</th><th>Class</th><th>Dose</th><th>Route</th><th>Freq</th>" );
      sb.append( "</tr></thead>\n<tbody>\n" );
      for ( HandoverDocument.MedDto m : meds ) {
         sb.append( "<tr><td>" ).append( esc( firstNonBlank( m.preferredText, m.text ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( m.drugClass ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( firstNonBlank( m.dose, m.strength ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( m.route ) ) ).append( "</td>" );
         sb.append( "<td>" ).append( esc( nullToEmpty( m.frequency ) ) ).append( "</td></tr>\n" );
      }
      sb.append( "</tbody></table>\n" );
   }

   static private String conceptLabels( final List<HandoverDocument.ConceptDto> concepts ) {
      if ( concepts == null || concepts.isEmpty() ) {
         return "";
      }
      return concepts.stream()
            .map( c -> firstNonBlank( c.preferredText, c.text ) )
            .collect( Collectors.joining( "; " ) );
   }

   static private String join( final List<String> values ) {
      if ( values == null || values.isEmpty() ) {
         return "";
      }
      return String.join( ", ", values );
   }

   static private String firstNonBlank( final String a, final String b ) {
      if ( a != null && !a.isBlank() ) {
         return a;
      }
      return nullToEmpty( b );
   }

   static private String firstNonBlank( final String a, final String b, final String c ) {
      if ( a != null && !a.isBlank() ) {
         return a;
      }
      if ( b != null && !b.isBlank() ) {
         return b;
      }
      return nullToEmpty( c );
   }

   static private String nullToEmpty( final String value ) {
      return value == null ? "" : value;
   }

   static private String esc( final String raw ) {
      if ( raw == null || raw.isEmpty() ) {
         return "";
      }
      return raw.replace( "&", "&amp;" )
            .replace( "<", "&lt;" )
            .replace( ">", "&gt;" )
            .replace( "\"", "&quot;" );
   }

   static private final class SpanMark {
      final int begin;
      final int end;
      final String label;

      SpanMark( final int begin, final int end, final String label ) {
         this.begin = begin;
         this.end = end;
         this.label = label;
      }
   }

   static private final String CSS = """
         :root {
           --ink: #1a2332;
           --muted: #5c6b7a;
           --line: #d5dde5;
           --bg: #f4f6f8;
           --card: #ffffff;
           --accent: #0b6e4f;
           --sbar: #1e3a5f;
         }
         * { box-sizing: border-box; }
         body {
           margin: 0;
           font-family: "IBM Plex Sans", "Segoe UI", Helvetica, Arial, sans-serif;
           color: var(--ink);
           background: var(--bg);
           line-height: 1.45;
         }
         .page-header {
           background: var(--sbar);
           color: #fff;
           padding: 1.25rem 1.5rem;
         }
         .page-header h1 { margin: 0 0 0.25rem; font-size: 1.5rem; font-weight: 600; }
         .doc-id { margin: 0; opacity: 0.85; font-size: 0.95rem; }
         .handover-section, .systems-title {
           max-width: 920px;
           margin: 1rem auto;
           padding: 0 1rem;
         }
         .handover-section {
           background: var(--card);
           border: 1px solid var(--line);
           border-radius: 6px;
           padding: 1rem 1.25rem;
         }
         .handover-section h2 {
           margin: 0 0 0.75rem;
           font-size: 1.1rem;
           border-bottom: 2px solid var(--accent);
           padding-bottom: 0.35rem;
         }
         .handover-section h3 {
           margin: 0.75rem 0 0.35rem;
           font-size: 0.95rem;
           color: var(--muted);
           text-transform: uppercase;
           letter-spacing: 0.04em;
         }
         .sbar-letter {
           display: inline-block;
           min-width: 1.5rem;
           background: var(--accent);
           color: #fff;
           text-align: center;
           border-radius: 3px;
           font-weight: 700;
           margin-right: 0.25rem;
         }
         .systems-title {
           margin-top: 1.5rem;
           font-size: 1.15rem;
         }
         ul, ol { margin: 0.25rem 0 0.5rem 1.2rem; padding: 0; }
         li { margin: 0.2rem 0; }
         table {
           width: 100%;
           border-collapse: collapse;
           margin: 0.5rem 0;
           font-size: 0.92rem;
         }
         th, td {
           border: 1px solid var(--line);
           padding: 0.4rem 0.55rem;
           text-align: left;
           vertical-align: top;
         }
         th { background: #eef3f7; font-weight: 600; }
         .muted { color: var(--muted); font-size: 0.85em; }
         .badge {
           display: inline-block;
           background: #e7f3ee;
           color: var(--accent);
           border-radius: 3px;
           padding: 0.05rem 0.4rem;
           font-size: 0.8em;
           font-weight: 600;
         }
         .empty { color: var(--muted); font-style: italic; margin: 0; }
         .timeline { list-style: decimal; }
         .section-text { white-space: pre-wrap; }
         .sentence { display: inline; }
         mark {
           background: #fff3bf;
           padding: 0 0.1em;
           border-radius: 2px;
         }
         """;
}
