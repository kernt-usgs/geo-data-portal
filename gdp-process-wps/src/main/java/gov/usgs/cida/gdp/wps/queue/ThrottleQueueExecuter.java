package gov.usgs.cida.gdp.wps.queue;

import java.util.TimerTask;
import org.n52.wps.server.ExceptionReport;
import org.slf4j.LoggerFactory;

/**
 * This class monitors the DB for candidate work to be placed on the queue. Work
 * is identified on the throttle_queue table with a status of 'ACCEPTED who's
 * data resource is currently not in-use. The singleton RequestManager schedules
 * the task.
 *
 * @author smlarson
 */
public class ThrottleQueueExecuter extends TimerTask {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ThrottleQueueExecuter.class);

    @Override
    public void run() {

        try {
            ExecuteRequestManager.getInstance().getThrottleQueue().enqueueRemainingWork();
        } catch (ExceptionReport ex) {
            String msg = "Error in attempting to get work for queue.";
            LOGGER.error(msg, ex);
        }
    }

}
