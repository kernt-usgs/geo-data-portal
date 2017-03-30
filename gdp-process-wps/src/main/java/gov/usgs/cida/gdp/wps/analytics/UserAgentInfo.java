package gov.usgs.cida.gdp.wps.analytics;

import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This metadata logger is a bit special because it is executed outside of the algorithm
 * due to the user agent header no longer being available at the point of execution.
 * 
 * @author jiwalker
 */
public class UserAgentInfo implements IMetadataLogger {
	
	private static final Logger log = LoggerFactory.getLogger(UserAgentInfo.class);
	private static final ConnectionHandler connectionHandler = DatabaseUtil.getJNDIConnectionHandler();
	
	private final String userAgent;
	
	public UserAgentInfo(String userAgent) {
		this.userAgent = userAgent;
	}
	
	@Override
	public void log(String requestId) {
        if (StringUtils.isNotBlank(userAgent)) {
            log.debug("Inserting Agent Logging with ID:" + requestId);
            try (Connection connection = connectionHandler.getConnection()) {
                UUID pkey = UUID.randomUUID();
                PreparedStatement prepared = connection.prepareStatement("INSERT INTO request_metadata (ID, REQUEST_ID, USER_AGENT) VALUES (?, ?, ?) ON CONFLICT (REQUEST_ID)"
                        + " DO UPDATE SET USER_AGENT = ?");
                prepared.setString(1, pkey.toString());
                prepared.setString(2, requestId);
                prepared.setString(3, userAgent);
                prepared.setString(4, userAgent);
                prepared.execute();
            } catch (SQLException ex) {
                log.debug("Problem logging user agent", ex);
            }
        }
    }
}
