package gov.usgs.cida.gdp.liquibase;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.FileSystemResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class LiquibaseUtility {

	private static final Logger log = LoggerFactory.getLogger(LiquibaseUtility.class);
	private static final URL CHANGELOG_PATH = LiquibaseUtility.class.getClassLoader().getResource("changeLog.xml");
	
	public static boolean update(DataSource ds) {
		Connection connection = null;
		try {
			connection = ds.getConnection();
			File file;
			try {
				file = new File(CHANGELOG_PATH.toURI());
			} catch (URISyntaxException ex) {
				log.error("Changelog path incorrect", ex);
				return false;
			}
			
			Database db;
			try {
				db = DatabaseFactory.getInstance()
						.findCorrectDatabaseImplementation(new JdbcConnection(connection));
			} catch (DatabaseException ex) {
				log.error("Cannot connect to database", ex);
				return false;
			}
			Liquibase liquibase;
			try {
				liquibase = new Liquibase(file.getAbsolutePath(), new FileSystemResourceAccessor(), db);
			} catch (LiquibaseException ex) {
				log.error("Problem creating liquibase object", ex);
				return false;
			}
			try {
				liquibase.update((Contexts)null);
			} catch (LiquibaseException ex) {
				log.error("Cannot run update", ex);
				return false;
			}
		} catch (SQLException ex) {

		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ex2) {
					// whatever
				}
			}
		}
		return true;
	}
}
