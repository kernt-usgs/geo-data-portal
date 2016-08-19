package gov.usgs.cida.gdp.wps.queue;

import java.util.concurrent.ExecutionException;
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
        LOGGER.info("There are: " + this.POOL.getQueue().size() + " queued requests in the queue.");

        if (!isRequestInQueue(execReq)) {
            if (execReq.isStoreResponse()) {
                try {
                    LOGGER.info("Putting request on queue: " + execReq.getUniqueId());
                    POOL.submit(execReq);
                    RequestManager.getInstance().getThrottleQueue().updateStatus(execReq, ThrottleStatus.ENQUEUE); //updates to ENQUEUED
                } finally {
                    LOGGER.info("Completed add to queue. Queue size is " + this.POOL.getQueue().size());
                    LOGGER.info("and active queue count is " + this.POOL.getActiveCount());
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

    private boolean isRequestInQueue(ExecuteRequest execRequest) throws ExceptionReport {

        boolean result = false;
        // run query that selects all the requests that are in ENQUEUE status on the throttle_queue table
         result = RequestManager.getInstance().getThrottleQueue().isEnqueue(execRequest);
        return result;
    }
}
