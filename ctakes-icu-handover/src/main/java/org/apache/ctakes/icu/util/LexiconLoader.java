package org.apache.ctakes.icu.util;

import org.apache.ctakes.core.resource.FileLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads simple lexicon files from the classpath under org/apache/ctakes/icu/data/.
 */
final public class LexiconLoader {

   static private final Logger LOGGER = LoggerFactory.getLogger( LexiconLoader.class );

   private LexiconLoader() {
   }

   static public Set<String> loadTokens( final String resourcePath ) {
      final Set<String> tokens = new LinkedHashSet<>();
      try ( InputStream in = FileLocator.getAsStream( resourcePath );
            BufferedReader reader = new BufferedReader( new InputStreamReader( in, StandardCharsets.UTF_8 ) ) ) {
         String line;
         while ( (line = reader.readLine()) != null ) {
            line = line.trim();
            if ( line.isEmpty() || line.startsWith( "#" ) ) {
               continue;
            }
            // Bare token or first field of coded row.
            final String token = line.contains( "||" ) ? line.split( "\\|\\|", 2 )[ 0 ].trim() : line;
            if ( !token.isEmpty() ) {
               tokens.add( stripRegexMeta( token ).toLowerCase( Locale.ROOT ) );
            }
         }
      } catch ( Exception e ) {
         LOGGER.warn( "Could not load lexicon {}: {}", resourcePath, e.getMessage() );
      }
      return Collections.unmodifiableSet( tokens );
   }

   /**
    * Token set for classifyDrug-style contains matching from coded lexicons.
    * Includes pattern fragments and preferred labels.
    */
   static public Set<String> loadCodedTokenSet( final String resourcePath ) {
      final Set<String> tokens = new LinkedHashSet<>();
      for ( CodedEntry entry : loadCodedEntries( resourcePath ) ) {
         final String fromPattern = stripRegexMeta( entry.pattern.pattern() );
         if ( fromPattern.length() >= 3 ) {
            tokens.add( fromPattern.toLowerCase( Locale.ROOT ) );
         }
         if ( entry.preferredText != null && !entry.preferredText.isEmpty() ) {
            tokens.add( entry.preferredText.toLowerCase( Locale.ROOT ) );
            for ( String part : entry.preferredText.toLowerCase( Locale.ROOT ).split( "[\\s\\-]+" ) ) {
               if ( part.length() >= 4 ) {
                  tokens.add( part );
               }
            }
         }
      }
      return Collections.unmodifiableSet( tokens );
   }

   /**
    * Loads pattern||label lines into an ordered map of compiled Pattern -> label.
    * Extra || fields (CUI/scheme/code) are ignored for the label value.
    */
   static public Map<Pattern, String> loadPatternLabels( final String resourcePath ) {
      final Map<Pattern, String> map = new LinkedHashMap<>();
      for ( CodedEntry entry : loadCodedEntries( resourcePath ) ) {
         map.put( entry.pattern, entry.preferredText );
      }
      return Collections.unmodifiableMap( map );
   }

   /**
    * Loads pattern||preferred||cui||codingScheme||code rows.
    * Missing trailing fields are allowed; pattern + preferred are required.
    */
   static public List<CodedEntry> loadCodedEntries( final String resourcePath ) {
      final List<CodedEntry> entries = new ArrayList<>();
      try ( InputStream in = FileLocator.getAsStream( resourcePath );
            BufferedReader reader = new BufferedReader( new InputStreamReader( in, StandardCharsets.UTF_8 ) ) ) {
         String line;
         while ( (line = reader.readLine()) != null ) {
            line = line.trim();
            if ( line.isEmpty() || line.startsWith( "#" ) ) {
               continue;
            }
            final String[] parts = line.split( "\\|\\|", -1 );
            if ( parts.length < 2 || parts[ 0 ].trim().isEmpty() ) {
               // Bare token line → pattern == preferred.
               if ( parts.length == 1 && !parts[ 0 ].trim().isEmpty() ) {
                  final String tok = parts[ 0 ].trim();
                  entries.add( new CodedEntry(
                        Pattern.compile( Pattern.quote( tok ), Pattern.CASE_INSENSITIVE ),
                        tok, null, null, null ) );
               }
               continue;
            }
            final Pattern pattern = Pattern.compile( parts[ 0 ].trim(), Pattern.CASE_INSENSITIVE );
            final String preferred = parts[ 1 ].trim();
            final String cui = parts.length > 2 ? emptyToNull( parts[ 2 ].trim() ) : null;
            final String scheme = parts.length > 3 ? emptyToNull( parts[ 3 ].trim() ) : null;
            final String code = parts.length > 4 ? emptyToNull( parts[ 4 ].trim() ) : null;
            entries.add( new CodedEntry( pattern, preferred, cui, scheme, code ) );
         }
      } catch ( Exception e ) {
         LOGGER.warn( "Could not load coded lexicon {}: {}", resourcePath, e.getMessage() );
      }
      return Collections.unmodifiableList( entries );
   }

   /** Best lexicon hit whose pattern matches text (first match in file order). */
   static public CodedEntry matchCoded( final String text, final List<CodedEntry> entries ) {
      if ( text == null || text.isEmpty() || entries == null || entries.isEmpty() ) {
         return null;
      }
      for ( CodedEntry entry : entries ) {
         if ( entry.pattern.matcher( text ).find() ) {
            return entry;
         }
      }
      return null;
   }

   static private String emptyToNull( final String value ) {
      return value == null || value.isEmpty() ? null : value;
   }

   static private String stripRegexMeta( final String pattern ) {
      return pattern
            .replaceAll( "\\\\[bsSwWdD]", " " )
            .replaceAll( "[\\[\\](){}|+*?^$\\\\]", " " )
            .replaceAll( "\\s+", " " )
            .trim();
   }

   /** Lexicon row with optional UMLS/SNOMED/RxNorm coding. */
   public static final class CodedEntry {
      public final Pattern pattern;
      public final String preferredText;
      public final String cui;
      public final String codingScheme;
      public final String code;

      public CodedEntry( final Pattern pattern, final String preferredText,
                         final String cui, final String codingScheme, final String code ) {
         this.pattern = pattern;
         this.preferredText = preferredText;
         this.cui = cui;
         this.codingScheme = codingScheme;
         this.code = code;
      }
   }

   static public List<Pattern> loadPatterns( final String resourcePath ) {
      final List<Pattern> patterns = new ArrayList<>();
      for ( CodedEntry entry : loadCodedEntries( resourcePath ) ) {
         patterns.add( entry.pattern );
      }
      // Fallback for bare-token files with no ||
      if ( patterns.isEmpty() ) {
         for ( String token : loadTokens( resourcePath ) ) {
            patterns.add( Pattern.compile( Pattern.quote( token ), Pattern.CASE_INSENSITIVE ) );
         }
      }
      return Collections.unmodifiableList( patterns );
   }

   static public boolean textContainsAny( final String text, final Set<String> tokens ) {
      if ( text == null || text.isEmpty() || tokens.isEmpty() ) {
         return false;
      }
      final String lower = text.toLowerCase( Locale.ROOT );
      for ( String token : tokens ) {
         if ( token.length() >= 2 && lower.contains( token ) ) {
            return true;
         }
      }
      return false;
   }
}
