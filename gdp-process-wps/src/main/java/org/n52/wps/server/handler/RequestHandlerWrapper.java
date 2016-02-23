package org.n52.wps.server.handler;

import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is very ugly and hacky, but I need access to protected field.
 * 
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class RequestHandlerWrapper extends RequestHandler {

	private static final Logger log = LoggerFactory.getLogger(RequestHandlerWrapper.class);
	private static ConnectionHandler connectionHandler = DatabaseUtil.getJNDIConnectionHandler();
	
	private RequestHandler parent;
	
	private String userAgent;
	
	public RequestHandlerWrapper(RequestHandler wrapThis) {
		parent = wrapThis;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	@Override
	public void handle() throws ExceptionReport {
		if (StringUtils.isNotBlank(userAgent)) {
			UUID uniqueId = parent.req.getUniqueId();
			try (Connection connection = connectionHandler.getConnection()) {
				UUID pkey = UUID.randomUUID();
				PreparedStatement prepared = connection.prepareStatement("INSERT INTO request_metadata (ID, REQUEST_ID, USER_AGENT) VALUES (?, ?, ?)");
				prepared.setString(1, pkey.toString());
				prepared.setString(2, uniqueId.toString());
				prepared.setString(3, userAgent);
				prepared.execute();
			} catch (SQLException ex) {
				log.debug("Problem logging user agent", ex);
				// don't rethrow, just keep going
			}
		}
		parent.handle();
	}
	
	
}
