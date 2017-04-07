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
public class DataFetchInfo implements IMetadataLogger {
	
	private static final Logger log = LoggerFactory.getLogger(DataFetchInfo.class);
	private static final ConnectionHandler connectionHandler = DatabaseUtil.getJNDIConnectionHandler();
	
	private long fetchSize;
	private long gridCells;
	private int timesteps;
	private int cellSize;
	private int numVars;
	private String boundingRect;
	
	public DataFetchInfo(long fetchSize, long gridCells, int timesteps, int cellSize, int numVars, String boundingRect) {
		this.fetchSize = fetchSize;
		this.gridCells = gridCells;
		this.timesteps = timesteps;
		this.cellSize = cellSize;
		this.numVars = numVars;
		this.boundingRect = boundingRect;
	}
	
	@Override
	public void log(String requestId) {
		log.debug("Inserting data info for: {}", requestId);
		try (Connection connection = connectionHandler.getConnection()) {
			UUID pkey = UUID.randomUUID();
			PreparedStatement prepared = connection.prepareStatement(
					"INSERT INTO request_metadata (ID, REQUEST_ID, TIMESTEPS, GRIDCELLS, VARCOUNT, CELLSIZE_BYTES, BOUNDING_RECT, DATA_RETRIEVED)"
							+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (REQUEST_ID) DO UPDATE SET TIMESTEPS = ?, GRIDCELLS = ?, VARCOUNT = ?, "
							+ " CELLSIZE_BYTES = ?, BOUNDING_RECT = ?, DATA_RETRIEVED = ?");
			prepared.setString(1, pkey.toString());
			prepared.setString(2, requestId);
			prepared.setInt(3, timesteps);
			prepared.setLong(4, gridCells);
			prepared.setInt(5, numVars);
			prepared.setInt(6, cellSize);
			prepared.setString(7, boundingRect);
			prepared.setLong(8, fetchSize);
			prepared.setInt(9, timesteps);
			prepared.setLong(10, gridCells);
			prepared.setInt(11, numVars);
			prepared.setInt(12, cellSize);
			prepared.setString(13, boundingRect);
			prepared.setLong(14, fetchSize);
			
			prepared.execute();
		} catch (SQLException ex) {
			log.debug("Problem logging fetch info", ex);
		}
	}
	
}
