import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/** Convert ctakesicd2015 from HSQLDB 1.8 to 2.7.x (cTAKES official steps). */
public final class UpgradeIcdDb {

   private UpgradeIcdDb() {
   }

   public static void main( final String[] args ) throws Exception {
      if ( args.length < 2 ) {
         System.err.println( "Usage: UpgradeIcdDb <db-without-extension> <18|234|27>" );
         System.exit( 1 );
      }
      final Path dbPath = Path.of( args[ 0 ] ).toAbsolutePath();
      final String phase = args[ 1 ];
      final Path props = Path.of( dbPath + ".properties" );

      if ( "18".equals( phase ) ) {
         stripReadOnly( props );
      }

      final String url = "jdbc:hsqldb:file:" + dbPath;
      Class.forName( "org.hsqldb.jdbcDriver" );
      try ( Connection conn = DriverManager.getConnection( url, "sa", "" );
            Statement st = conn.createStatement() ) {
         if ( "18".equals( phase ) ) {
            st.execute( "SET SCRIPTFORMAT TEXT" );
            try ( ResultSet rs = st.executeQuery( "SELECT COUNT(*) FROM icd9cm" ) ) {
               rs.next();
               System.out.println( "ICD9CM rows: " + rs.getInt( 1 ) );
            }
            st.execute( "SHUTDOWN COMPACT" );
         } else {
            try ( ResultSet rs = st.executeQuery( "SELECT COUNT(*) FROM icd9cm" ) ) {
               rs.next();
               System.out.println( "ICD9CM rows: " + rs.getInt( 1 ) );
            }
            st.execute( "SHUTDOWN COMPACT" );
         }
      }
      System.out.println( "Phase " + phase + " complete." );
   }

   private static void stripReadOnly( final Path props ) throws Exception {
      if ( !Files.isRegularFile( props ) ) {
         return;
      }
      final List<String> lines = Files.readAllLines( props );
      final List<String> cleaned = lines.stream()
            .filter( line -> !line.startsWith( "readonly=" ) )
            .filter( line -> !line.startsWith( "files_readonly=" ) )
            .toList();
      Files.write( props, cleaned );
   }
}
