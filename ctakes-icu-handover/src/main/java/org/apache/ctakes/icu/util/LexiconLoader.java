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
            tokens.add( line.toLowerCase( Locale.ROOT ) );
         }
      } catch ( Exception e ) {
         LOGGER.warn( "Could not load lexicon {}: {}", resourcePath, e.getMessage() );
      }
      return Collections.unmodifiableSet( tokens );
   }

   /**
    * Loads pattern||label lines into an ordered map of compiled Pattern -> label.
    */
   static public Map<Pattern, String> loadPatternLabels( final String resourcePath ) {
      final Map<Pattern, String> map = new LinkedHashMap<>();
      try ( InputStream in = FileLocator.getAsStream( resourcePath );
            BufferedReader reader = new BufferedReader( new InputStreamReader( in, StandardCharsets.UTF_8 ) ) ) {
         String line;
         while ( (line = reader.readLine()) != null ) {
            line = line.trim();
            if ( line.isEmpty() || line.startsWith( "#" ) ) {
               continue;
            }
            final String[] parts = line.split( "\\|\\|", 2 );
            if ( parts.length < 2 ) {
               continue;
            }
            map.put( Pattern.compile( parts[ 0 ].trim(), Pattern.CASE_INSENSITIVE ), parts[ 1 ].trim() );
         }
      } catch ( Exception e ) {
         LOGGER.warn( "Could not load pattern lexicon {}: {}", resourcePath, e.getMessage() );
      }
      return Collections.unmodifiableMap( map );
   }

   static public List<Pattern> loadPatterns( final String resourcePath ) {
      final List<Pattern> patterns = new ArrayList<>();
      for ( String token : loadTokens( resourcePath ) ) {
         patterns.add( Pattern.compile( token, Pattern.CASE_INSENSITIVE ) );
      }
      return Collections.unmodifiableList( patterns );
   }

   static public boolean textContainsAny( final String text, final Set<String> tokens ) {
      if ( text == null || text.isEmpty() || tokens.isEmpty() ) {
         return false;
      }
      final String lower = text.toLowerCase( Locale.ROOT );
      for ( String token : tokens ) {
         if ( lower.contains( token ) ) {
            return true;
         }
      }
      return false;
   }
}
