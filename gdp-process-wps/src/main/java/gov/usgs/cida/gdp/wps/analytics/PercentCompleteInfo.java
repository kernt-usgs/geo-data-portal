package gov.usgs.cida.gdp.wps.analytics;

import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a weird one as well because this is currently handled by ExecuteRequest observer
 * 
 * @author jiwalker
 */
public class PercentCompleteInfo implements IMetadataLogger {
	
	private static final Logger log = LoggerFactory.getLogger(PercentCompleteInfo.class);
	private static final ConnectionHandler connectionHandler = DatabaseUtil.getJNDIConnectionHandler();
	
	private final int percent;
	
	public PercentCompleteInfo(int percent) {
		this.percent = percent;
	}
	
	@Override
	public void log(String requestId) {
		try (Connection connection = connectionHandler.getConnection()) {
			PreparedStatement prepared = connection.prepareStatement("UPDATE response SET percent_complete = ? WHERE request_id = ?");
			prepared.setInt(1, percent);
			prepared.setString(2, requestId);
			prepared.execute();
		} catch (SQLException ex) {
			log.debug("Problem logging user agent", ex);
		}
	}

}
