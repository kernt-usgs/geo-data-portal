package gov.usgs.cida.gdp.wps.queue.cleanup;

import gov.usgs.cida.gdp.wps.queue.ExecuteRequestManager;
import gov.usgs.cida.gdp.wps.queue.ThrottleQueue;
import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jiwalker
 */
public class CleanupProcess {
	
	private static final Logger log = LoggerFactory.getLogger(CleanupProcess.class);
	
	private final ThrottleQueue queue;
	private final ConnectionHandler connectionHandler;
	
	private static final String REASON = "Server restart";
	
	private static final String REMOVE_ZOMBIE_QUEUE = "DELETE throttle_queue WHERE status = 'STARTED' OR status = 'PROCESSED'";
	private static final String UPDATE_ZOMBIE_RESPONSE = "UPDATE response SET status = 'FAILED', exception_text = ? WHERE status = 'STARTED'";
	
	public CleanupProcess() {
		this.queue = ExecuteRequestManager.getInstance().getThrottleQueue();
		this.connectionHandler = DatabaseUtil.getJNDIConnectionHandler();
	}

	public void cleanup() {
		try (Connection connection = connectionHandler.getConnection()) {
			try (PreparedStatement removeZombies = connection.prepareStatement(REMOVE_ZOMBIE_QUEUE)) {
				int removed = removeZombies.executeUpdate();
				log.debug("Removed {} items from throttle queue", removed);
			} catch (SQLException ex) {
				String message = "Unable to clear process queue";
				log.error(message, ex);
			}
			
			try (PreparedStatement updateResponse = connection.prepareStatement(UPDATE_ZOMBIE_RESPONSE)) {
				updateResponse.setString(1, REASON);
				int updated = updateResponse.executeUpdate();
				log.debug("Updated {} processes to failed", updated);
			} catch (SQLException ex) {
				String message = "Unable to update process to failed";
				log.error(message, ex);
			}
		} catch (SQLException ex) {
			String message = "Unable to get connection to database";
			log.error(message, ex);
		}
	}
	
}
