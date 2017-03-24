package gov.usgs.cida.gdp.wps.analytics;

import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import org.n52.wps.server.database.connection.ConnectionHandler;

/**
 *
 * @author jiwalker
 */
public interface IMetadataLogger {
	
	public void log(String requestId);
}
