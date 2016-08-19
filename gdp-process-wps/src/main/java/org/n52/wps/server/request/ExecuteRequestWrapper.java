package org.n52.wps.server.request;

import gov.usgs.cida.gdp.wps.queue.RequestManager;
import gov.usgs.cida.gdp.wps.queue.ThrottleQueueToggle;
import gov.usgs.cida.gdp.wps.queue.ThrottleStatus;
import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import net.opengis.wps.x100.ExecuteDocument;
import net.opengis.wps.x100.InputType;
import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.n52.wps.commons.context.ExecutionContext;
import org.n52.wps.commons.context.ExecutionContextFactory;
import org.n52.wps.io.data.IComplexData;
import org.n52.wps.io.data.IData;
import org.n52.wps.server.AbstractTransactionalAlgorithm;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.IAlgorithm;
import org.n52.wps.server.RepositoryManager;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.n52.wps.server.observerpattern.ISubject;
import org.n52.wps.server.response.ExecuteResponse;
import org.n52.wps.server.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 *
 * @author smlarson
 */
public class ExecuteRequestWrapper extends ExecuteRequest {

    private static Logger LOGGER_WRAPPER = LoggerFactory.getLogger(ExecuteRequestWrapper.class);
    private ExecuteDocument execDom;
    // Map<String, IData> returnResults;
    private static final long SLEEPTIME = 10000; //milli seconds
    private static final ConnectionHandler CONNECTION_HANDLER = DatabaseUtil.getJNDIConnectionHandler();
    private static final String SELECT_IF_RESOURCE_INUSE = "Select DISTINCT(INPUT_VALUE) FROM input WHERE input.INPUT_IDENTIFIER = 'DATASET_URI' AND input.request_id = ? AND INPUT_VALUE IN (Select DISTINCT(INPUT_VALUE) FROM input input, response resp WHERE resp.request_id = input.request_id AND input.INPUT_IDENTIFIER = 'DATASET_URI' AND resp.status = 'STARTED')";
    private static final String INSERT_STATUS_REQUEST_STATEMENT = "INSERT INTO throttle_queue (REQUEST_ID, STATUS, ENQUEUED, DEQUEUED) VALUES (?, ?, ?, ?)";

    public ExecuteRequestWrapper(CaseInsensitiveMap ciMap) throws ExceptionReport {
        super(ciMap);
    }

    public ExecuteRequestWrapper(Document doc) throws ExceptionReport {
        super(doc);
    }

    /**
     * Actually serves the Request.
     *
     * @return
     * @throws ExceptionReport
     */
    @Override
    public Response call() throws ExceptionReport {
        IAlgorithm algorithm = null;
        Map<String, List<IData>> inputMap = null;
        boolean wasInUse = false;  //#USGS override
        LOGGER_WRAPPER.info("PROCESSING in call of ExecuteRequestWrapper. reqId: " + this.getUniqueId());
        try {
            if (!isDataSourceInUse()) { //#USGS override code - check to see if throttle is on, then check to see if any of the requests data resources are presently in use.
                ExecutionContext context;
                if (getExecute().isSetResponseForm()) {
                    context = getExecute().getResponseForm().isSetRawDataOutput()
                            ? new ExecutionContext(getExecute().getResponseForm().getRawDataOutput())
                            : new ExecutionContext(Arrays.asList(getExecute().getResponseForm().getResponseDocument().getOutputArray()));
                } else {
                    context = new ExecutionContext();
                }
                // register so that any function that calls ExecuteContextFactory.getContext() gets the instance registered with this thread
                ExecutionContextFactory.registerContext(context);

                LOGGER_WRAPPER.debug("started with execution");

                updateStatusStarted();
                // #USGS want to maintain an internal hashmap or DB representation of everything that is on this VMs queue 
                RequestManager.getInstance().getThrottleQueue().updateStatus(this, ThrottleStatus.STARTED);
                //insertThrottleQueueStatus(this.getUniqueId().toString()); //#USGS override code  - do I need this anymore?

                // parse the input
                InputType[] inputs = new InputType[0];
                if (getExecute().getDataInputs() != null) {
                    inputs = getExecute().getDataInputs().getInputArray();
                }
                InputHandler parser = new InputHandler.Builder(inputs, getAlgorithmIdentifier()).build();

                // we got so far:
                // get the algorithm, and run it with the clients input
                /*
			 * IAlgorithm algorithm =
			 * RepositoryManager.getInstance().getAlgorithm(getAlgorithmIdentifier());
			 * returnResults = algorithm.run((Map)parser.getParsedInputLayers(),
			 * (Map)parser.getParsedInputParameters());
                 */
                algorithm = RepositoryManager.getInstance().getAlgorithm(getAlgorithmIdentifier());

                if (algorithm instanceof ISubject) {
                    ISubject subject = (ISubject) algorithm;
                    subject.addObserver(this);

                }

                if (algorithm instanceof AbstractTransactionalAlgorithm) {
                    returnResults = ((AbstractTransactionalAlgorithm) algorithm).run(execDom);
                } else {
                    inputMap = parser.getParsedInputData();
                    returnResults = algorithm.run(inputMap);
                }

                List<String> errorList = algorithm.getErrors();
                if (errorList != null && !errorList.isEmpty()) {
                    String errorMessage = errorList.get(0);
                    LOGGER_WRAPPER.error("Error reported while handling ExecuteRequest for " + getAlgorithmIdentifier() + ": " + errorMessage + " with requestId " + this.getUniqueId());
                    updateStatusError(errorMessage);
                    // #USGS# remove the request from the hashmap / DB throttle_queue with status of processed
                    RequestManager.getInstance().getThrottleQueue().removeRequest(this.getUniqueId().toString());
                } else {
                    updateStatusSuccess();
                    LOGGER_WRAPPER.info("Update was successful" );
                    // #USGS# remove the request from the hashmap / DB throttle_queue with status of processed
                    RequestManager.getInstance().getThrottleQueue().removeRequest(this.getUniqueId().toString());
                }
            }  //close if isInUse()  //#USGS override code
            else {
                wasInUse = true;
                LOGGER_WRAPPER.info("Dataset was in use. Request will wait:" + this.getUniqueId().toString());
                //update the status to WAITING
                //place in waiting status
                RequestManager.getInstance().getThrottleQueue().updateStatus(this, ThrottleStatus.WAITING);
                Thread.sleep(SLEEPTIME);  //do this or the DB will get overly taxed checking to see if its inUse
            }
        } catch (Throwable e) {
            String errorMessage = null;
            if (algorithm != null && algorithm.getErrors() != null && !algorithm.getErrors().isEmpty()) {
                errorMessage = algorithm.getErrors().get(0);
            }
            if (errorMessage == null) {
                errorMessage = e.toString();
            }
            if (errorMessage == null) {
                errorMessage = "UNKNOWN ERROR";
            }
            LOGGER_WRAPPER.error("Exception/Error while executing ExecuteRequest for " + getAlgorithmIdentifier() + ": " + errorMessage);
            updateStatusError(errorMessage);
            if (e instanceof Error) {
                // This is required when catching Error
                throw (Error) e;
            }
            if (e instanceof ExceptionReport) {
                throw (ExceptionReport) e;
            } else {
                throw new ExceptionReport("Error while executing the embedded process for: " + getAlgorithmIdentifier(), ExceptionReport.NO_APPLICABLE_CODE, e);
            }
        } finally {
            //  you ***MUST*** call this or else you will have a PermGen ClassLoader memory leak due to ThreadLocal use
            ExecutionContextFactory.unregisterContext();
            if (algorithm instanceof ISubject) {
                ((ISubject) algorithm).removeObserver(this);
            }
            if (inputMap != null) {
                for (List<IData> l : inputMap.values()) {
                    for (IData d : l) {
                        if (d instanceof IComplexData) {
                            ((IComplexData) d).dispose();
                        }
                    }
                }
            }
            if (returnResults != null) {
                for (IData d : returnResults.values()) {
                    if (d instanceof IComplexData) {
                        ((IComplexData) d).dispose();
                    }
                }
            }
            // #USGS:override code
            //places this request on the queue if the datasource was inUse to try again later, will need to sleep for a bit to allow the data source to release
            if (wasInUse) { 
                LOGGER_WRAPPER.info("Dataset was in use. Request will be placed back on the queue:" + this.getUniqueId().toString());
                RequestManager.getInstance().getExecuteRequestQueue().put(this);
            }
        }

        ExecuteResponse response = new ExecuteResponse(this);
        return response;
    }

    //determines if another request is using the data source of this request
    private boolean isDataSourceInUse() throws ExceptionReport {
        boolean result = false;

        if (ThrottleQueueToggle.isThrottleOn()) {
            //check to see if any of the requests data resources are in use

            try (Connection connection = CONNECTION_HANDLER.getConnection();
                    PreparedStatement selectRequestStatement = connection.prepareStatement(SELECT_IF_RESOURCE_INUSE)) {
                selectRequestStatement.setString(1, this.getUniqueId().toString());
                ResultSet rs = selectRequestStatement.executeQuery();

                if (rs != null) {
                    while (rs.next()) {
                        String input = rs.getString("INPUT_VALUE");
                        if (null != input) {
                            result = true;
                        }

                        String msg = "Request id: " + this.getUniqueId() + " with data source of: " + input + " -Determined that data resource is " + (result == true ? " in use " : " not in use ");
                        LOGGER_WRAPPER.info("In Use Query returned: " + result);
                        LOGGER_WRAPPER.info(msg);
                    }
                }
            } catch (Exception e) {
                String msg = "Failed to execute query to determine if data resource is in use.";
                LOGGER_WRAPPER.error(msg, e);
                throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
            }

        }
        return result;
    }
    
    // #TODO I'll probably remove this after the refacto
    private void insertThrottleQueueStatus(String requestId) throws ExceptionReport {
        //ACCEPTED status upon insert

        Timestamp now = new Timestamp(Calendar.getInstance().getTimeInMillis());

        try (Connection connection = CONNECTION_HANDLER.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(INSERT_STATUS_REQUEST_STATEMENT);
            preparedStatement.setString(1, requestId);
            preparedStatement.setString(2, ThrottleStatus.ACCEPTED.toString());
            preparedStatement.setTimestamp(3, now);
            preparedStatement.setTimestamp(4, null); //dont want to set the dequeue time now

            preparedStatement.execute();
            LOGGER_WRAPPER.info("Inserted request in throttle_queue table with ID:" + requestId);
        } catch (Exception e) {
            String msg = "Failed to insert request and status into throttle_queue table.";
            LOGGER_WRAPPER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
    }

}
