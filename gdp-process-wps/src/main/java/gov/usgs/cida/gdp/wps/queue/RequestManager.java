package gov.usgs.cida.gdp.wps.queue;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Holds onto the requestMap and the dataSets that are in use.
 *
 * @author smlarson
 */
// #TODO# rename to ExecuteRequestManager ...
public class RequestManager {
    
    private final ThrottleQueue queue;
    private final ExecuteRequestQueue executeQueue;
    
    // private constructor prevents instantiation from external classes
    private RequestManager() {
        this.queue = new ThrottleQueueImpl();
        this.executeQueue = new ExecuteRequestQueue();
    }
    
    /**
     * Singleton Holder is Loaded on the first execution of the
     * RequestManager.getInstance() or the first access to
     * SingletonHolder.INSTANCE and not before. It is thread-safe without
     * requiring synchronized or volatile as a result and prevents the
     * double-checked locking idiom. --see Bill Pugh write up for more info
     */
    private static class SingletonHolder {

        private static final RequestManager INSTANCE = new RequestManager();
    }

    public static RequestManager getInstance() {
        return SingletonHolder.INSTANCE;
    }
    
    public ThrottleQueue getThrottleQueue() {
        return this.queue;
    }
   
    public ExecuteRequestQueue getExecuteRequestQueue() {
        return this.executeQueue;
    }
    
    private void init() { 
        
    	TimerTask task = new ThrottleQueueExecuter();

    	Timer timer = new Timer();
    	timer.schedule(task, 1000, 6000);  //milliseconds so a 1 second delay with check every 6 seconds is 1000, 6000

    }
}
