package org.apache.ctakes.rest.service;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.ctakes.core.pipeline.PipelineBuilder;
import org.apache.ctakes.core.pipeline.PiperFileReader;
import org.apache.ctakes.rest.service.response.ResponseFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.JCasPool;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author SPF , chip-nlp
 * @version %I%
 * @since 11/5/2019
 */
public enum RestPipelineRunner {
   INSTANCE;

   static public RestPipelineRunner getInstance() {
      return INSTANCE;
   }

   static private final Logger LOGGER = LoggerFactory.getLogger( "RestPipelineRunner" );

   /** Default (local/dev) piper — typically no-UMLS. */
   static private final String DEFAULT_REST_PIPER = "TinyRestPipeline.piper";
   static private final String PRE_ANESTHESIA_PIPER = "TinyRestPipeline.pre_anesthesia.piper";

   /**
    * Override with {@code -Dctakes.rest.piper=TinyRestPipeline.prod.piper}
    * or env {@code CTAKES_REST_PIPER} / {@code ctakes.rest.piper}.
    */
   static private final String REST_PIPER_PROP = "ctakes.rest.piper";
   static private final String REST_PIPER_ENV = "CTAKES_REST_PIPER";

   static private final Object PROCESS_LOCK = new Object();

   private final Map<String, EngineBundle> _bundles = new ConcurrentHashMap<>();
   private final String _defaultPiperPath;

   RestPipelineRunner() {
      _defaultPiperPath = resolvePiperPath();
      try {
         // Workaround https://github.com/apache/uima-uimaj/issues/234
         // https://github.com/ClearTK/cleartk/issues/470
         CasCreationUtils.createCas();
         LoggerFactory.getLogger( "RestPipelineRunner" )
               .info( "Loading default REST pipeline from piper: {}", _defaultPiperPath );
         _bundles.put( "icu", loadBundle( _defaultPiperPath ) );
      } catch ( IOException | UIMAException | RuntimeException multE ) {
         LoggerFactory.getLogger( "RestPipelineRunner" ).error( multE.getMessage() );
         throw new ExceptionInInitializerError( multE );
      }
   }

   static private String resolvePiperPath() {
      final String fromProp = System.getProperty( REST_PIPER_PROP );
      if ( fromProp != null && !fromProp.isBlank() ) {
         return fromProp.trim();
      }
      final String fromEnv = System.getenv( REST_PIPER_ENV );
      if ( fromEnv != null && !fromEnv.isBlank() ) {
         return fromEnv.trim();
      }
      final String fromEnvAlt = System.getenv( REST_PIPER_PROP );
      if ( fromEnvAlt != null && !fromEnvAlt.isBlank() ) {
         return fromEnvAlt.trim();
      }
      return DEFAULT_REST_PIPER;
   }

   static private String normalizePack( final String pack ) {
      if ( pack == null || pack.isBlank() ) {
         return "icu";
      }
      final String key = pack.trim().toLowerCase();
      if ( "pre_anesthesia".equals( key ) || "pre-anesthesia".equals( key ) || "anesthesia".equals( key ) ) {
         return "pre_anesthesia";
      }
      return "icu";
   }

   private EngineBundle bundleFor( final String pack ) throws AnalysisEngineProcessException {
      final String key = normalizePack( pack );
      try {
         return _bundles.computeIfAbsent( key, this::loadPackOrFallback );
      } catch ( RuntimeException e ) {
         final Throwable cause = e.getCause() != null ? e.getCause() : e;
         throw new AnalysisEngineProcessException( cause );
      }
   }

   private EngineBundle loadPackOrFallback( final String key ) {
      final String piper = "pre_anesthesia".equals( key ) ? PRE_ANESTHESIA_PIPER : _defaultPiperPath;
      try {
         LOGGER.info( "Loading REST pipeline pack={} from piper: {}", key, piper );
         return loadBundle( piper );
      } catch ( IOException | UIMAException | RuntimeException loadE ) {
         if ( "icu".equals( key ) ) {
            throw new RuntimeException( loadE );
         }
         LOGGER.error( "Failed to load pack={} ({}). Falling back to icu pack.", key, loadE.getMessage() );
         return _bundles.computeIfAbsent( "icu", k -> {
            try {
               return loadBundle( _defaultPiperPath );
            } catch ( IOException | UIMAException e ) {
               throw new RuntimeException( e );
            }
         } );
      }
   }

   static private EngineBundle loadBundle( final String piperPath ) throws IOException, UIMAException {
      final PiperFileReader reader = new PiperFileReader( piperPath );
      final PipelineBuilder builder = reader.getBuilder();
      final AnalysisEngineDescription pipeline = builder.getAnalysisEngineDesc();
      final AnalysisEngine engine = UIMAFramework.produceAnalysisEngine( pipeline );
      final JCasPool pool = new JCasPool( 2, engine );
      return new EngineBundle( engine, pool, piperPath );
   }

   public String process( final ResponseFormatter formatter, final String text )
         throws AnalysisEngineProcessException {
      return process( formatter, text, "icu" );
   }

   public String process( final ResponseFormatter formatter, final String text, final String pack )
         throws AnalysisEngineProcessException {
      if ( text == null || text.isBlank() ) {
         return "";
      }
      final String key = normalizePack( pack );
      EngineBundle bundle = bundleFor( key );
      try {
         return processWithBundle( formatter, text, bundle, key );
      } catch ( AnalysisEngineProcessException firstE ) {
         if ( "icu".equals( key ) ) {
            throw firstE;
         }
         // Broken specialty pack at runtime (e.g. stale piper) — retry on ICU NER.
         LOGGER.error( "Processing failed for pack={} ({}). Retrying with icu pack.",
               key, firstE.getMessage() );
         bundle = bundleFor( "icu" );
         return processWithBundle( formatter, text, bundle, "icu" );
      }
   }

   private String processWithBundle( final ResponseFormatter formatter,
                                     final String text,
                                     final EngineBundle bundle,
                                     final String packKey )
         throws AnalysisEngineProcessException {
      synchronized ( PROCESS_LOCK ) {
         // JCasPool.getJCas() uses timeout 0 (non-blocking); wait until a CAS is free.
         JCas jcas = bundle.pool.getJCas( 60_000 );
         if ( jcas == null ) {
            throw new AnalysisEngineProcessException( new Throwable( "Could not acquire JCas from pool." ) );
         }
         try {
            jcas.reset();
            jcas.setDocumentText( text );
            bundle.engine.process( jcas );
            return formatter.getResultText( jcas );
         } catch ( CASRuntimeException | AnalysisEngineProcessException multE ) {
            LOGGER.error( "Error processing text (pack={}).", packKey );
            throw new AnalysisEngineProcessException( multE );
         } finally {
            bundle.pool.releaseJCas( jcas );
         }
      }
   }

   private static final class EngineBundle {
      private final AnalysisEngine engine;
      private final JCasPool pool;
      private final String piperPath;

      private EngineBundle( final AnalysisEngine engine, final JCasPool pool, final String piperPath ) {
         this.engine = engine;
         this.pool = pool;
         this.piperPath = piperPath;
      }
   }
}
