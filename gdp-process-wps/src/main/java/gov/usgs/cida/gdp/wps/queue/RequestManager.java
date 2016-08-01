package gov.usgs.cida.gdp.wps.queue;

/**
 * Holds onto the requestMap and the dataSets that are in use.
 *
 * @author smlarson
 */
public class RequestManager {
    
    private final ThrottleQueue queue;
    
    // private constructor prevents instantiation from external classes
    private RequestManager() {
        this.queue = new ThrottleQueueImpl();
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

}
