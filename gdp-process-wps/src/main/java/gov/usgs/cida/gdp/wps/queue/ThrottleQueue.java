package gov.usgs.cida.gdp.wps.queue;

import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.request.ExecuteRequest;


/**
 *
 * The queue's purpose is to process all ExecuteRequests to ensure that
 * 1 - re-processing of the same request does not occur (really a pre-processing request)
 * 2 - the data source used to fulfill the request is not over taxed 
 * 
 * @author smlarson
 */
public interface ThrottleQueue  {
 
    public void putRequest(ExecuteRequest req) throws ExceptionReport;
    
    public void removeRequest(String requestId) throws ExceptionReport;
    
    public void enqueueRemainingWork() throws ExceptionReport;
    
    public void updateStatus(ExecuteRequest req, ThrottleStatus status) throws ExceptionReport;
    
    public boolean isEnqueue(ExecuteRequest req) throws ExceptionReport;
      
}
