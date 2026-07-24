package org.apache.ctakes.rest.service.response;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.ctakes.core.util.doc.DocIdUtil;
import org.apache.ctakes.icu.cc.HandoverAssembler;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Single-pass information map: clinical handover structure + entity/coding layers
 * + human views + optional FHIR. Prefer this over calling each format= separately
 * (those re-run the full pipeline each time).
 *
 * <pre>
 * {
 *   "schema": "ctakes.information-map/v1",
 *   "documentId": "...",
 *   "text": "...",
 *   "handover": { ... },      // ICU SBAR structure (what Smart-Handover consumes)
 *   "entities": { ... },      // UMLS mention map by type
 *   "cuiCounts": { ... },     // CUI → frequency
 *   "fhir": { ... },          // FHIR Bundle
 *   "views": {
 *     "pretty": "...",        // ASCII span view
 *     "property": "..."       // readable property list
 *   },
 *   "meta": { "layers": [...], "omitted": ["xmi"] }
 * }
 * </pre>
 */
final public class InformationMapFormatter implements ResponseFormatter {

   static private final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

   @Override
   public String getResultText( final JCas jCas ) throws AnalysisEngineProcessException {
      final Map<String, Object> map = new LinkedHashMap<>();
      map.put( "schema", "ctakes.information-map/v1" );
      map.put( "documentId", DocIdUtil.getDocumentID( jCas ) );
      final String text = jCas.getDocumentText();
      map.put( "text", text );
      map.put( "textLength", text == null ? 0 : text.length() );

      // Clinical structure (primary for your app)
      map.put( "handover", parseJson( HandoverAssembler.createJson( jCas ) ) );

      // Span-level coded entities
      map.put( "entities", parseJson( new UmlsJsonFormatter().getResultText( jCas ) ) );

      // Vocabulary frequency
      map.put( "cuiCounts", parseCuiCounts( new CuiListFormatter().getResultText( jCas ) ) );

      // Interop layer
      map.put( "fhir", parseJson( new FhirJsonFormatter().getResultText( jCas ) ) );

      // Human-readable overlays (same spans, different presentation)
      final Map<String, Object> views = new LinkedHashMap<>();
      views.put( "pretty", new PrettyPrintFormatter().getResultText( jCas ) );
      views.put( "property", new PropertyListFormatter().getResultText( jCas ) );
      map.put( "views", views );

      final Map<String, Object> meta = new LinkedHashMap<>();
      meta.put( "layers", Arrays.asList( "handover", "entities", "cuiCounts", "fhir", "views" ) );
      meta.put( "omitted", Arrays.asList( "xmi" ) );
      meta.put( "note", "XMI omitted (full CAS dump). Use format=xmi when needed." );
      map.put( "meta", meta );

      return GSON.toJson( map );
   }

   static private JsonElement parseJson( final String json ) {
      if ( json == null || json.isBlank() ) {
         return null;
      }
      return JsonParser.parseString( json );
   }

   static private Map<String, Integer> parseCuiCounts( final String cuiList ) {
      final Map<String, Integer> counts = new TreeMap<>();
      if ( cuiList == null || cuiList.isBlank() ) {
         return counts;
      }
      for ( final String line : cuiList.split( "\\R" ) ) {
         final String trimmed = line.trim();
         if ( trimmed.isEmpty() ) {
            continue;
         }
         final int sep = trimmed.lastIndexOf( " : " );
         if ( sep <= 0 ) {
            continue;
         }
         final String cui = trimmed.substring( 0, sep ).trim();
         try {
            counts.put( cui, Integer.parseInt( trimmed.substring( sep + 3 ).trim() ) );
         } catch ( NumberFormatException ignored ) {
            // skip malformed lines
         }
      }
      return counts;
   }
}
