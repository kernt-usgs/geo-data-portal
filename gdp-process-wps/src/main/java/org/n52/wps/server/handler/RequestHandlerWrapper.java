package org.n52.wps.server.handler;

import gov.usgs.cida.gdp.wps.queue.RequestManager;
import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.database.connection.ConnectionHandler;
import static org.n52.wps.server.handler.RequestHandler.pool;
import org.n52.wps.server.request.ExecuteRequest;
import org.n52.wps.server.response.ExecuteResponse;
import org.n52.wps.server.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is very ugly and hacky, but I need access to protected field.(The request id)
 * 
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class RequestHandlerWrapper extends RequestHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandlerWrapper.class);
	private static ConnectionHandler connectionHandler = DatabaseUtil.getJNDIConnectionHandler();
	
	private RequestHandler parent;
	
	private String userAgent;
        
	public RequestHandlerWrapper(RequestHandler wrapThis) {
		parent = wrapThis;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	
        
	/**
	 * Handle a request after its type is determined. The request is scheduled
	 * for execution. If the server has enough free resources, the client will
	 * be served immediately. If time runs out, the client will be asked to come
	 * back later with a reference to the result.
         * 
         * Override: Previously, the wrapper added the handle agent logging. The entire handle method is now overridden to:
         * 1 - prevent redundant requests from processing (ran or running)
         * 2 - avoid taxing the same data source the request requires for processing
	 * 
	 * @param req The request of the client.
	 * @throws ExceptionReport
	 */
        @Override
	public void handle() throws ExceptionReport {
		Response resp = null;
		if(req ==null){
			throw new ExceptionReport("Internal Error","");
		}
                handleAgentLogging();  //USGS override logic: inserts the user_agent into the meta-data table. Required the requestId for the insert.
                
		if (req instanceof ExecuteRequest) {
			// cast the request to an executerequest
			ExecuteRequest execReq = (ExecuteRequest) req;
			
                        if (isThrottleQueueEnabled()) {
                            RequestManager.getInstance().getThrottleQueue().putRequest(execReq); //USGS override logic: add all requests to the throttle queue
                            // above will allow us to block until the input data source is free from all requests (FIFO) in the pipeline
                        }
                        
			execReq.updateStatusAccepted();
			
			ExceptionReport exceptionReport = null;
			try {
				if (execReq.isStoreResponse()) {
					resp = new ExecuteResponse(execReq);
					InputStream is = resp.getAsStream();
					IOUtils.copy(is, os);
					is.close();    
                   // the takeRequest can return a null if there are no requests that can currently be processed. #TODO# Do we block until the data resource is available? how long? 
                   if (isThrottleQueueEnabled()) {
                    pool.submit(RequestManager.getInstance().getThrottleQueue().takeRequest());//USGS override logic: this will block until a request with a dataset that has no contention with other running requests is available
                   }
                   else {
                    pool.submit(execReq);
					return;      
                   }                
				}
				try {
					// retrieve status with timeout enabled
					try {
						resp = pool.submit(execReq).get();
					}
					catch (ExecutionException ee) {
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
					} catch (InterruptedException ie) {
						LOGGER.warn("interrupted while handling ExecuteRequest.");
						// interrupted while waiting in the queue
						exceptionReport = new ExceptionReport(
								"The computation in the process was interrupted.",
								ExceptionReport.NO_APPLICABLE_CODE);
					}
				} finally {
					if (exceptionReport != null) {
						LOGGER.debug("ExceptionReport not null: " + exceptionReport.getMessage());
						// NOT SURE, if this exceptionReport is also written to the DB, if required... test please!
						throw exceptionReport;
					}
					// send the result to the outputstream of the client.
				/*	if(((ExecuteRequest) req).isQuickStatus()) {
						resp = new ExecuteResponse(execReq);
					}*/
					else if(resp == null) {
						LOGGER.warn("null response handling ExecuteRequest.");
						throw new ExceptionReport("Problem with handling threads in RequestHandler", ExceptionReport.NO_APPLICABLE_CODE);
					}
					if(!execReq.isStoreResponse()) {
						InputStream is = resp.getAsStream();
						IOUtils.copy(is, os);
						is.close();
						LOGGER.info("Served ExecuteRequest.");
					}
				}
			} catch (RejectedExecutionException ree) {
                LOGGER.warn("exception handling ExecuteRequest.", ree);
				// server too busy?
				throw new ExceptionReport(
						"The requested process was rejected. Maybe the server is flooded with requests.",
						ExceptionReport.SERVER_BUSY);
			} catch (Exception e) {
                LOGGER.error("exception handling ExecuteRequest.", e);
                if (e instanceof ExceptionReport) {
                    throw (ExceptionReport)e;
                }
                throw new ExceptionReport("Could not read from response stream.", ExceptionReport.NO_APPLICABLE_CODE);
			}
                        finally { //USGS override logic: check to see if there are any remaining requests to work on in the throttle queue
                            if (isThrottleQueueEnabled()) {
                                LOGGER.info("Checking for remaining tasks in throttle queue.");
                                pool.submit(RequestManager.getInstance().getThrottleQueue().takeRequest());
                            }
                           
                        }
		} else {
			// for GetCapabilities and DescribeProcess:
			resp = req.call();
			try {
				InputStream is = resp.getAsStream();
				IOUtils.copy(is, os);
				is.close();
			} catch (IOException e) {
				throw new ExceptionReport("Could not read from response stream.", ExceptionReport.NO_APPLICABLE_CODE);
			}
			
		}
	}
        
        // below is all the USGS additional functionality that is sliced into the original RequestHandler

	//this was originally done first and then it was tossed back to the parent.handle(). Had to add another intercept at a different point so now override the entire handle method
	public void handleAgentLogging() throws ExceptionReport {
		if (StringUtils.isNotBlank(userAgent)) {
			UUID uniqueId = parent.req.getUniqueId();
			try (Connection connection = connectionHandler.getConnection()) {
				UUID pkey = UUID.randomUUID();
				PreparedStatement prepared = connection.prepareStatement("INSERT INTO request_metadata (ID, REQUEST_ID, USER_AGENT) VALUES (?, ?, ?)");
				prepared.setString(1, pkey.toString());
				prepared.setString(2, uniqueId.toString());
				prepared.setString(3, userAgent);
				prepared.execute();
			} catch (SQLException ex) {
				LOGGER.debug("Problem logging user agent", ex);
				// don't rethrow, just keep going
			}
		}
		parent.handle();
	}
        
	//Via the db, a toggle switch for the throttle_queue can be set to true to enable the throttle and false to disable the throttle.
        //This is an immediate disablement meaning if there is work remaining in the queue, it won't get processed.
	private boolean isThrottleQueueEnabled() throws ExceptionReport {
		boolean result = true;
			try (Connection connection = connectionHandler.getConnection()) {
				// UUID pkey = UUID.randomUUID();
                                String sql = "SELECT enabled FROM throttle_queue_toggle  WHERE toggle_type = \"THROTTLE\"";
                                Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(sql);
                                while (rs.next()){
                                    String boolString = rs.getString("ENABLED");
                                    result = Boolean.valueOf(boolString);
                                }
			} catch (SQLException ex) {
				LOGGER.error("Problem selecting throttle_queue enabled from the db.", ex);
				LOGGER.info("Throttle_queue enabled: "+ result);
                                throw new ExceptionReport("Problem selecting throttle_queue enabled from the db. Throttle 'enabled' is currently:" + result, ExceptionReport.NO_APPLICABLE_CODE);
			}
		return result;
	}

}
