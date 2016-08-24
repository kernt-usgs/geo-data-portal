package gov.usgs.cida.gdp.wps.queue;

import java.util.Timer;
import java.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the timer task which is responsible for triggering the act of placing work on the queue.
 *
 * @author smlarson
 */
// #TODO# rename to ExecuteRequestManager ...
public class ExecuteRequestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecuteRequestManager.class);
    private final ThrottleQueue queue;
    private final ExecuteRequestQueue executeQueue;
    
    // private constructor prevents instantiation from external classes
    private ExecuteRequestManager() {
        this.queue = new ThrottleQueueImpl();
        this.executeQueue = new ExecuteRequestQueue();
        init();
    }
    
    /**
     * Singleton Holder is Loaded on the first execution of the
     * RequestManager.getInstance() or the first access to
     * SingletonHolder.INSTANCE and not before. It is thread-safe without
     * requiring synchronized or volatile as a result and prevents the
     * double-checked locking idiom. --see Bill Pugh write up for more info
     */
    private static class SingletonHolder {

        private static final ExecuteRequestManager INSTANCE = new ExecuteRequestManager();
    }

    public static ExecuteRequestManager getInstance() {
        return SingletonHolder.INSTANCE;
    }
    
    public ThrottleQueue getThrottleQueue() {
        return this.queue;
    }
   
    public ExecuteRequestQueue getExecuteRequestQueue() {
        return this.executeQueue;
    }
    
    // here for debug purposes. 
    private String getQueueStatus(){
        return this.getExecuteRequestQueue().getStatus();
    }
    
    private void init() { 
        LOGGER.debug("INIT Timer Task in RequestManager");
        long delay = 1000;  //milliseconds
        long period = 6000;
        
        
    	TimerTask task = new ThrottleQueueExecuter();

    	Timer timer = new Timer();
    	timer.schedule(task, delay, period);  //Frequency that work is checked to be added to the queue. -milliseconds so a 1 second delay with check every 6 seconds is 1000, 6000

    }
}
