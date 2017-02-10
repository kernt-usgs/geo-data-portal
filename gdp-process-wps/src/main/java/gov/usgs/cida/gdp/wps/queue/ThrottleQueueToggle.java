package gov.usgs.cida.gdp.wps.queue;

import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author smlarson
 */
public class ThrottleQueueToggle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThrottleQueueToggle.class);
    private static final ConnectionHandler CONNECTION_HANDLER = DatabaseUtil.getJNDIConnectionHandler();

    public static boolean isThrottleOn() throws ExceptionReport {
        //Via the db, a toggle switch for the throttle_queue can be set to true to enable the throttle and false to disable the throttle.
        //This is an immediate disablement meaning if there is work remaining in the queue, it won't get processed.
        boolean result = false;
        try (Connection connection = CONNECTION_HANDLER.getConnection()) {

            String sql = "SELECT enabled FROM throttle_queue_toggle  WHERE toggle_type = 'THROTTLE'";
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(sql);
            while (rs.next()) {
                String boolString = rs.getString("ENABLED");
                if (boolString.equalsIgnoreCase("t") || boolString.equalsIgnoreCase("true") || boolString.equalsIgnoreCase("y") || boolString.equalsIgnoreCase("yes") || boolString.equalsIgnoreCase("1") || boolString.equalsIgnoreCase("on")) {
                    result = true;
                    LOGGER.info("DATA SOURCE THROTTLE is ENABLED");
                }
                
            }
        } catch (SQLException ex) {
            LOGGER.error("Problem selecting throttle_queue status from the db.", ex);
            throw new ExceptionReport("Problem selecting throttle_queue enabled from the db. Throttle 'enabled' is currently:" + result, ExceptionReport.NO_APPLICABLE_CODE);
        }
        return result;
    }

}
