package org.n52.wps.server.database;

import gov.usgs.cida.gdp.liquibase.LiquibaseUtility;

import static java.text.MessageFormat.format;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.slf4j.LoggerFactory;

import ru.yandex.qatools.embed.postgresql.PostgresExecutable;
import ru.yandex.qatools.embed.postgresql.PostgresProcess;
import ru.yandex.qatools.embed.postgresql.PostgresStarter;
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig;

@Ignore
public class AbstractPostgresDatabaseTest {
	
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(AbstractPostgresDatabaseTest.class);

	private static final String DB_NAME = "test";
	private static final String DB_USER = "wpsTest";
	private static final String DB_PASS = "abc12345";
	
	protected static PostgresProcess process = null;
	protected static DataSource ds = null;

	@BeforeClass
	public static void setupInMemoryDB() throws Exception {
		// starting Postgres
		PostgresStarter<PostgresExecutable, PostgresProcess> runtime = PostgresStarter.getDefaultInstance();
		final PostgresConfig config = PostgresConfig.defaultWithDbName(DB_NAME, DB_USER, DB_PASS);

		PostgresExecutable exec = runtime.prepare(config);
		process = exec.start();

		// connecting to a running Postgres
		final String url = format("jdbc:postgresql://{0}:{1,number,#}/{2}?user={3}&password={4}",
				config.net().host(),
				config.net().port(),
				config.storage().dbName(), config.credentials().username(), config.credentials().password()
		);

		ds = initializeDataSource(url);

		setupJNDI(ds);
		LiquibaseUtility.update(ds);
		PostgresDatabase.getInstance();
	}

	private static DataSource initializeDataSource(final String url) {
		return new DataSource() {
			@Override
			public Connection getConnection(String username, String password) throws SQLException {
				return DriverManager.getConnection(url);
			}

			@Override
			public Connection getConnection() throws SQLException {
				return DriverManager.getConnection(url);
			}

			@Override
			public <T> T unwrap(Class<T> iface) throws SQLException {
				return null;
			}

			@Override
			public boolean isWrapperFor(Class<?> iface) throws SQLException {
				return false;
			}

			@Override
			public void setLoginTimeout(int seconds) throws SQLException {
			}

			@Override
			public void setLogWriter(PrintWriter out) throws SQLException {
			}

			@Override
			public int getLoginTimeout() throws SQLException {
				return 0;
			}

			@Override
			public PrintWriter getLogWriter() throws SQLException {
				return null;
			}

			@Override
			public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
				return null;
			}
		};
	}

	private static void setupJNDI(DataSource ds) throws NamingException {
		// Create initial context
		System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.naming.java.javaURLContextFactory");
		System.setProperty(Context.URL_PKG_PREFIXES, "org.apache.naming");
		InitialContext ic = new InitialContext();

		ic.createSubcontext("java:");
		ic.createSubcontext("java:comp");
		ic.createSubcontext("java:comp/env");
		ic.createSubcontext("java:comp/env/jdbc");

		ic.bind("java:comp/env/jdbc/" + "gdp", ds);
	}
	
	@AfterClass
	public static void cleanupInMemoryDB() throws SQLException {
		process.stop();
	}

}
