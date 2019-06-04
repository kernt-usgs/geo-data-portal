package org.n52.wps.server.database;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import org.junit.Ignore;

import org.junit.Test;

@Ignore
public class PostgresDatabaseTest extends AbstractPostgresDatabaseTest {
	
	@Test
	@Ignore
	public void testConnection() throws Exception {
		PostgresDatabase.getInstance().insertRequest("test", PostgresDatabaseTest.class.getClassLoader().getResourceAsStream("request.xml"), true);
		// performing some assertions
		Connection conn = ds.getConnection();
		final Statement statement = conn.createStatement();
		assertThat(statement.execute("SELECT * FROM REQUEST;"), is(true));
		assertThat(statement.getResultSet().next(), is(true));
		assertThat(statement.getResultSet().getString("REQUEST_ID"), is("test"));
		
		
		final Statement statement2 = conn.createStatement();
		assertThat(statement2.execute("SELECT * FROM input;"), is(true));
		assertThat("at least one input", statement2.getResultSet().next(), is(true));
		assertThat("input matches request id", statement2.getResultSet().getString("REQUEST_ID"), is("test"));
		assertThat("more than one input", statement2.getResultSet().next(), is(true));
		assertThat("input matches 2nd request id", statement2.getResultSet().getString("REQUEST_ID"), is("test"));
	}
	
	@Test
	@Ignore
	public void testInsertResponse() throws Exception {
		PostgresDatabase.getInstance().insertResponse("test", PostgresDatabaseTest.class.getClassLoader().getResourceAsStream("succeeded.xml"));
		// performing some assertions
		Connection conn = ds.getConnection();
		final Statement statement = conn.createStatement();
		assertThat(statement.execute("SELECT * FROM RESPONSE;"), is(true));
		assertThat(statement.getResultSet().next(), is(true));
		assertThat(statement.getResultSet().getString("REQUEST_ID"), is("test"));
	}

}
