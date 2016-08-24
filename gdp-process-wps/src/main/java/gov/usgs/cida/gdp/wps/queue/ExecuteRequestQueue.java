package gov.usgs.cida.gdp.wps.queue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.handler.RequestExecutor;
import org.n52.wps.server.request.ExecuteRequest;
import org.n52.wps.server.request.ExecuteRequestWrapper;
import org.n52.wps.server.response.Response;
import org.slf4j.LoggerFactory;

/**
 *
 * @author smlarson
 */
public class ExecuteRequestQueue { 

    private final RequestExecutor POOL = new RequestExecutor();  //manages all the ExecuteRequests
    private final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ExecuteRequestQueue.class);

    /**
     *
     * @param execReq
     * @return
     * @throws ExceptionReport
     */
    public Response put(ExecuteRequestWrapper execReq) throws ExceptionReport {
        Response response = null;
        ExceptionReport exceptionReport = null;
        LOGGER.info("There are currently: " + this.POOL.getActiveCount() + " active requests in the queue.");
        LOGGER.debug("Queue status: " + getStatus());
      

        if (!isRequestInQueue(execReq)) {
            if (execReq.isStoreResponse()) {
                try {
                    LOGGER.info("Putting request on queue: " + execReq.getUniqueId());
                    POOL.submit(execReq);
                    ExecuteRequestManager.getInstance().getThrottleQueue().updateStatus(execReq, ThrottleStatus.ENQUEUE); //updates to ENQUEUED
                } finally {
                    LOGGER.debug("Queue status: " + getStatus());
              //      LOGGER.info("and active queue count is " + this.POOL.getActiveCount());
                }

            } else {
                try {
                    LOGGER.info("Putting synch request on queue: " + execReq.getUniqueId());
                    response = POOL.submit(execReq).get();  //this will block     
                } catch (ExecutionException ee) {
                    LOGGER.warn("exception while handling ExecuteRequest.");
                    // the computation threw an error
                    // probably the client input is not valid
                    if (ee.getCause() instanceof ExceptionReport) {
                        exceptionReport = (ExceptionReport) ee
                                .getCause();
                    } else {
                        exceptionReport = new ExceptionReport(
                                "An error occurred in the computation: "
                                + ee.getMessage(),
                                ExceptionReport.NO_APPLICABLE_CODE);
                    }
                } catch (InterruptedException ex) {
                    LOGGER.warn("interrupted while handling ExecuteRequest.");
                    // interrupted while waiting in the queue
                    exceptionReport = new ExceptionReport(
                            "The computation in the process was interrupted. " + ex.getMessage(),
                            ExceptionReport.NO_APPLICABLE_CODE);
                } finally {
                    if (exceptionReport != null) {
                        LOGGER.debug("ExceptionReport not null attempting to add to ExecuteRequestQeue: " + exceptionReport.getMessage());
                        // NOT SURE, if this exceptionReport is also written to the DB, if required... test please!
                        throw exceptionReport;
                    }
                }
            }
        }
        return response;

    }

    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        String sep = System.lineSeparator();
        
        sb.append(sep);
        sb.append("_____________________________________________");        
        sb.append("Pool size: ");
        sb.append(POOL.getPoolSize());
        sb.append(sep);
        sb.append("_____________________________________________");
               
        sb.append("Active Count: ");
        sb.append(POOL.getActiveCount());
        sb.append(sep);
        sb.append("_____________________________________________");
        
        sb.append("Task count: ");
        sb.append(POOL.getTaskCount());
        sb.append(sep);
        sb.append("_____________________________________________");
        
        sb.append("Completed task count: ");
        sb.append(POOL.getCompletedTaskCount());
        sb.append(sep);
        sb.append("_____________________________________________");
        
        sb.append("Keep alive (seconds): ");
        sb.append(POOL.getKeepAliveTime(TimeUnit.SECONDS));
        sb.append(sep);
        sb.append("_____________________________________________");
        
        sb.append("Max pool size: ");
        sb.append(POOL.getMaximumPoolSize());
        sb.append(sep);
        sb.append("_____________________________________________");
        
        sb.append("Largest pool size: ");
        sb.append(POOL.getLargestPoolSize());
        sb.append(sep);
        
        return sb.toString();
    }
    
    private boolean isRequestInQueue(ExecuteRequest execRequest) throws ExceptionReport {

        boolean result = false;
        // run query that selects all the requests that are in ENQUEUE status on the throttle_queue table
         result = ExecuteRequestManager.getInstance().getThrottleQueue().isEnqueue(execRequest);
        return result;
    }
}
