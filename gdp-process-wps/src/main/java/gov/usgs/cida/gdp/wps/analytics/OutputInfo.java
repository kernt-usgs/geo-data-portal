package gov.usgs.cida.gdp.wps.analytics;

import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Figure out the size of what was fetched from remote service
 * 
 * @author jiwalker
 */
public class OutputInfo implements IMetadataLogger {
	
	private static final Logger log = LoggerFactory.getLogger(OutputInfo.class);
	private static final ConnectionHandler connectionHandler = DatabaseUtil.getJNDIConnectionHandler();
	
	private long returnSize;
	
	public OutputInfo(long returnSize) {
		this.returnSize = returnSize;
	}
	
	@Override
	public void log(String requestId) {
		log.debug("Inserting data info for: {}", requestId);
		try (Connection connection = connectionHandler.getConnection()) {
			UUID pkey = UUID.randomUUID();
			PreparedStatement prepared = connection.prepareStatement(
					"INSERT INTO request_metadata (ID, REQUEST_ID, DATA_RETURNED)"
							+ " VALUES (?, ?, ?) ON CONFLICT (REQUEST_ID) DO UPDATE SET DATA_RETURNED = ?");
			prepared.setString(1, pkey.toString());
			prepared.setString(2, requestId);
			prepared.setLong(3, returnSize);
			prepared.setLong(4, returnSize);
			
			prepared.execute();
		} catch (SQLException ex) {
			log.debug("Problem logging output info", ex);
		}
	}
	
}
