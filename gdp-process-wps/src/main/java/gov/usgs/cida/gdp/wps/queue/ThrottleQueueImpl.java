package gov.usgs.cida.gdp.wps.queue;

import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.request.ExecuteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.n52.wps.server.database.connection.ConnectionHandler;

/**
 * Implements the pre and post logic required for the processing threads
 * (ExecuteRequest) meant to protect the data resource by preventing the
 * re-running of previously finished or running requests and not running
 * multiple requests that would require the same data source to process. This is
 * not a 'real' queue in the sense that it is not inheriting from the
 * concurrency package nor using callable/runnable directly. Instead, it relies
 * heavily on the database status. ie request will go into 'waiting' status if
 * the data source is currently in use.
 *
 * @author smlarson
 */
public class ThrottleQueueImpl implements ThrottleQueue {

    final Lock lock = new ReentrantLock();
    private final HashMap requestQueue = new HashMap();  // requestID is the key, ExecuteRequest is the value
    private final HashSet dataSetInUse = new HashSet();
    private static final Logger LOGGER = LoggerFactory.getLogger(ThrottleQueueImpl.class);
    private static final ConnectionHandler CONNECTION_HANDLER = DatabaseUtil.getJNDIConnectionHandler();
    private static final int TIME_OUT_SECONDS = 20;

    /**
     * Ensures the same request is not processed again (either finished or
     * executing). As a pre-processing check, first check to see if it has ran
     * or is currently running and hand back the status url if it is. Add the
     * request to the hashmap and set the status to ACCEPTED
     *
     *
     * @param req
     * @throws org.n52.wps.server.ExceptionReport
     */
    @Override
    public void putRequest(ExecuteRequest req) throws ExceptionReport {

        try {
            lock.tryLock(TIME_OUT_SECONDS, TimeUnit.SECONDS);   //#TODO# review timeout duration 
            UUID requestId = req.getUniqueId();

            if (isRedundantRequestEnabled()) {  //can toggle this logic on and off with the setting in the DB - throttle_queue_toggle table
                if (hasRan(req)) {
                    LOGGER.info("Current request has ran previously. Getting file response URI...");
                    String responseId = getPreviousResponseId(requestId);
                    LOGGER.info("Response ID, " + responseId + " found for request with ID..." + requestId.toString());
                } else {
                    addRequest(req);
                }
            } else {
                // move this to a an add request to easily manage the put of the datasets too
                LOGGER.info("Adding request to Throttle Queue with ID:" + requestId.toString());
                addRequest(req);
            }

            /// record status in DB (separate status from the wps status)  ACCEPTED
        } catch (InterruptedException e) {
            LOGGER.info("Attempt to acquire lock to add request to the throttle timed out: " + e.getMessage());

        } finally {
            lock.unlock();
        }
    }

    /**
     * Ensures the same dataset is currently not in use before updating the
     * status and giving the worker thread the doc to execute upon
     * 'requestQueue'. 1) Removes all the previous completed requests data
     * resources from the set, updates status from STARTED to PROCESSED for all
     * the requests that have 'wps' completed.  2) Finds
     * the next request with data resources that are available, updates that
     * request to STARTED, removes it from the Hashmap, Adds the data resources
     * to the set
     *
     * @return nulls allowed
     * @throws org.n52.wps.server.ExceptionReport
     */
    @Override
    public ExecuteRequest takeRequest() throws ExceptionReport {
        ExecuteRequest result = null;

        try {
            lock.tryLock(TIME_OUT_SECONDS, TimeUnit.SECONDS);   //#TODO# review timeout duration 
            // update the inUse datasets based upon any completed wps requests (complete the work of the previous requests)
            updateInUse();
            updateStatusToProcessed(); //for previously completed requests

            // get the next request that has a status of accepted...FIFO
            String requestId = getNextAvailable();

            if (null == requestId) {
                LOGGER.info("There are no requests that are available for processing at this time....");
                return result;
            }

            //get the request from the map to return to the worker thread for processing
            result = (ExecuteRequest) this.requestQueue.get(requestId);

            //update the throttle_queue table status from ACCEPTED to STARTED
            updateQueueStatus(requestId, ThrottleStatus.STARTED);

            //add the dataresources needed to process this request to the inUse set
            addDataResources(requestId);

            //remove the request from the Hashmap
            this.requestQueue.remove(requestId);
            LOGGER.info("Removed request from queue:" + requestId);

        } catch (InterruptedException e) {
            LOGGER.info("Attempt to acquire lock to remove the request from the throttle timed out: " + e.getMessage());

        } finally {
            lock.unlock();
        }

        return result; //this could be null 
    }

    private void addRequest(ExecuteRequest req) throws ExceptionReport {
        String requestId = req.getUniqueId().toString();

        this.requestQueue.put(requestId, req); //adds it to the internal hashmap for easy retrieval
        LOGGER.info("Added request:" + requestId + "to queue map.");

        insertQueueRequest(requestId);
    }

    /**
     * Used to protect the data resource: If something is currently utilizing
     * the data resource, this request will be blocked until the other request
     * completes and the data resource is available.
     *
     */
    //add the data resources to the internal hash set by fetching them from the DB
    private void addDataResources(String requestId) throws ExceptionReport {
        List<String> resources = getDataSources(requestId);
        this.dataSetInUse.addAll(resources);

        for (String resource : resources) {
            LOGGER.info("Added resources to inUse hashset:" + resource);
        }
    }

    // looks at the status of the previous requests to see if they have completed and removes the dataset from inuse set
    // Note that if the wps algo process throws an exception, the request will need to be updated with either a status of failed or succeeded. 
    // If the request is left in a started or accepted status, the dataset remains 'in-use' and prevents contention.
    // response table (SUCCEEDED or FAILED) and throttle_queue table (STARTED)
    private void updateInUse() throws ExceptionReport {

        try (Connection connection = CONNECTION_HANDLER.getConnection()) {

            String sql = SELECT_DATASETS_COMPLETED;
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(sql);
            while (rs.next()) {
                String dataResource = rs.getString("INPUT_VALUE");
                LOGGER.info("Removing resource from in-use set: " + dataResource);

                this.dataSetInUse.remove(dataResource);  // removes this from the internal set
            }
        } catch (SQLException ex) {
            String msg = "Query issue trying to select the data sources associated with the recently completed wps requests.";
            LOGGER.error(msg, ex);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
    }

    // looks at the wps status to determine if the request has completed (FAILED or SUCCEEDED) and updates the queue status from started to procesed
    private void updateStatusToProcessed() throws ExceptionReport {
        try (Connection connection = CONNECTION_HANDLER.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_TO_PROCESSED_STATUS);

            preparedStatement.executeUpdate();
            LOGGER.info("Updated status from STARTED to PROCESSED on the throttle_queue table for wps requests that recently completed.");
        } catch (Exception e) {
            String msg = "Failed to update status on throttle_queue for completed requests (STARTED -> PROCESSED).";
            LOGGER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
    }

    // #PRE_CHECK#  // 7/2016 blocked on requirement identifying what attributes should be compared to determine if it has ran before without comparing the results
    private boolean hasRan(ExecuteRequest req) {
        // "TBD: impl later";
        Document currentRequestDoc = req.getDocument();
        boolean result = false;
        
        // if hasRan or isRunning - result = true
        // determine this by comparing a checksum (MD5) on the doc (request_xml) found in the request (table).request_xml and this requests doc
        // String thisRequest = MD5(currentRequestDoc);
        // get the one from the DB that matches the DB size 
        // String compareTo = MD5(dbRequestDoc);
        // compare the two strings to see if they .equal, possibly looping thru them if more than one with a matched size are returned from the db
        
        LOGGER.info("Pre-check before placing on queue -hasRanBefore:" + result);
        return result; // update later after impl
    }

    // #PRE_CHECK#
    private String MD5(String md5) throws ExceptionReport {

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes(Charset.forName("UTF-8")));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            String msg = "Can not verify if request has been processed previously. MD5 not available.";
            LOGGER.info("MD5 algo not available.");
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
    }

    // #PRE_CHECK#
    private String getPreviousResponseId(UUID currentRequestId) throws ExceptionReport {
        String resultURL = null;

        //return the result (table).response(column) where the response (table).status(column) is succeeded for the currentRequestId
        try (Connection connection = CONNECTION_HANDLER.getConnection();
                PreparedStatement selectRequestStatement = connection.prepareStatement(SELECT_RESPONSE_URL_STATEMENT)) {
            selectRequestStatement.setString(1, currentRequestId.toString());

            ResultSet rs = selectRequestStatement.executeQuery();

            if (rs != null && rs.next()) {
                resultURL = rs.getString(1);
                LOGGER.info("Got previous responsId for request:" + currentRequestId.toString());
            }
        } catch (Exception e) {
            String msg = "Failed to find previously ran response URL from database.";
            LOGGER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
        return resultURL;

    }

    private boolean isRedundantRequestEnabled() throws ExceptionReport {
        boolean result = true;
        try (Connection connection = CONNECTION_HANDLER.getConnection()) {

            String sql = "SELECT enabled FROM throttle_queue_toggle  WHERE toggle_type = " + "'" + ToggleType.REDUNDANT_REQUEST_CHECK.toString().toUpperCase() + "'"; // \"REDUNDANT_REQUEST_CHECK\"";
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(sql);
            while (rs.next()) {
                String boolString = rs.getString("ENABLED");
                result = Boolean.valueOf(boolString);
            }
        } catch (SQLException ex) {
            LOGGER.error("Problem selecting throttle_queue_toggle for pre-check redundant request enabled from the db.", ex);
            LOGGER.info("Throttle_queue reundant request enabled: " + result);
            throw new ExceptionReport("Problem selecting throttle_queue_toggle enabled from the db. Redundant request 'enabled' is currently:" + result, ExceptionReport.NO_APPLICABLE_CODE);
        }
        return result;
    }

    //gets the datasources from the input table instead of parsing the doc cuz its more convenient now that they are there for the dequeueing process
    private List getDataSources(String requestId) throws ExceptionReport {
        //select distinct(input_value) from input where input_identifier = 'DATASET_URI';  //list of all the potential datasources in use
        List<String> result = new ArrayList<>();  //must maintain order - FIFO

        try (Connection connection = CONNECTION_HANDLER.getConnection();
                PreparedStatement selectRequestStatement = connection.prepareStatement(SELECT_DATA_SOURCES_STATEMENT)) {
            selectRequestStatement.setString(1, requestId);

            ResultSet rs = selectRequestStatement.executeQuery();

            if (rs != null) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                    LOGGER.info("Fetched datasources from input for requestId:" + requestId + " datasource: " + rs.getString(1));
                }
            }
        } catch (Exception e) {
            String msg = "Failed to find previously ran response URL from database";
            LOGGER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
        return result;
    }

    private String getNextAvailable() throws ExceptionReport {
        String result = null;
        List ids = getNextFew();

        if ((null == ids) || (ids.size() < 1)) {
            LOGGER.info("No requests are currently available for processing.");
            return null; //went through all the active requests and not a one (or there are no reamining requests) qualified to run
        }

        // the ids are the request ids of the the candidate work requests
        for (Object id : ids) {
            String requestId = (String) id;

            if (isAvailable(requestId)) {
                return requestId;
            }
        }

        return result;
    }

    private boolean isAvailable(String requestId) throws ExceptionReport {
        boolean isAvailable = false;
        //get the datasets associated with the request from the input table
        List<String> datasets = getDataSources(requestId);
        for (String dataResource : datasets) {
            LOGGER.info("In use data resource: " + dataResource);
            if (!inUse(dataResource)) {
                //found the next request with an available data resource
                return true;
            }
            else
            {
                LOGGER.info("Data resource in use. Skipping this request for now:" + requestId);
            }
        }
        return isAvailable;
    }

    private boolean inUse(String dataResource) {
        boolean inUse = false;

        if (this.dataSetInUse.contains(dataResource)) {
            // dataset in use currently and this request will have to wait until it frees up- skip it for now
            inUse = true;
            LOGGER.info("Dataset needed to process this request is current IN USE:" + dataResource + ". Will check availability again later.");
        }

        return inUse;
    }

    private List getNextFew() throws ExceptionReport {
        List requestIds = new ArrayList();

        //return the next available request to be worked on that's data set is not in contention (Status = ACCEPTED)
        try (Connection connection = CONNECTION_HANDLER.getConnection();
                PreparedStatement selectRequestStatement = connection.prepareStatement(SELECT_NEXT_AVAILABLE)) {

            ResultSet rs = selectRequestStatement.executeQuery();

            if (rs != null) {
                while (rs.next()) {
                    requestIds.add(rs.getString("request_id"));

                    LOGGER.info("Getting candidates for worker thread:" + rs.getString(1) + " with enqueue time of: " + rs.getDate(2).getTime());  //number of millis since Jan 1 1970
                }
            }

        } catch (Exception e) {
            String msg = "Faild to select the next available for processing from the throttleQueue";
            LOGGER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
        return requestIds;
    }

    private void insertQueueRequest(String requestId) throws ExceptionReport {
        //ACCEPTED status upon insert

        Timestamp now = new Timestamp(Calendar.getInstance().getTimeInMillis());

        try (Connection connection = CONNECTION_HANDLER.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(INSERT_STATUS_REQUEST_STATEMENT);
            preparedStatement.setString(1, requestId);
            preparedStatement.setString(2, ThrottleStatus.ACCEPTED.toString());
            preparedStatement.setTimestamp(3, now);
            preparedStatement.setTimestamp(4, null); //dont want to set the dequeue time now

            preparedStatement.execute();
            LOGGER.info("Inserted request in throttle_queue table with ID:" + requestId);
        } catch (Exception e) {
            String msg = "Failed to insert request and status into throttle_queue table.";
            LOGGER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
    }

    //update the requests status on the throttle_queue table for the dequeuing process
    private void updateQueueStatus(String requestId, ThrottleStatus status) throws ExceptionReport {
        //ACCEPTED -> STARTED -> PROCESSED 
        Timestamp now = new Timestamp(Calendar.getInstance().getTimeInMillis());

        try (Connection connection = CONNECTION_HANDLER.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_STATUS_DEQUEUE_STATEMENT);

            preparedStatement.setString(1, status.toString());
            preparedStatement.setTimestamp(2, now);
            preparedStatement.setString(3, requestId);

            preparedStatement.executeUpdate();
            LOGGER.info("Updated status in throttle_queue table of request:" + requestId + " to status of :" + status.toString());
        } catch (Exception e) {
            String msg = "Failed to update status to STARTED on throttle_queue table. :" + UPDATE_STATUS_DEQUEUE_STATEMENT;
            LOGGER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
    }

    // throttle queue table 
    private static final String THROTTLE_QUEUE_TABLE = "throttle_queue";

    // sql prepared statements
    // #PRE_CHECK# used to determine if the request ran before    
    private static final String SELECT_RESPONSE_URL_STATEMENT = "SELECT response FROM results, response WHERE results.request_id = response.request_id and response.status = 'SUCCEEDED' and response.request_id = ?";
    // used to determine which request should run next 
    private static final String SELECT_STATUS_REQUEST_STATEMENT = "SELECT STATUS FROM " + THROTTLE_QUEUE_TABLE + " WHERE REQUEST_ID = ?";
    private static final String INSERT_STATUS_REQUEST_STATEMENT = "INSERT INTO " + THROTTLE_QUEUE_TABLE + "(REQUEST_ID, STATUS, ENQUEUED, DEQUEUED) VALUES (?, ?, ?, ?)";
    private static final String UPDATE_STATUS_DEQUEUE_STATEMENT = "UPDATE " + THROTTLE_QUEUE_TABLE + " SET (STATUS, DEQUEUED) = (?, ?) WHERE REQUEST_ID = ?";
    private static final String SELECT_NEXT_AVAILABLE = "SELECT request_id, enqueued from " + THROTTLE_QUEUE_TABLE + " where status = 'ACCEPTED' ORDER BY enqueued";
    private static final String SELECT_DATA_SOURCES_STATEMENT = "SELECT INPUT_VALUE FROM INPUT WHERE INPUT_IDENTIFIER = 'DATASET_URI' AND REQUEST_ID = ?";
    private static final String UPDATE_TO_PROCESSED_STATUS = "UPDATE " + THROTTLE_QUEUE_TABLE + " SET status = 'PROCESSED' WHERE request_id in (SELECT resp.request_id from response resp, throttle_queue queue where resp.request_id = queue.request_id and queue.status = 'STARTED' and resp.status in ('SUCCEEDED', 'FAILED'))";
    private static final String SELECT_DATASETS_COMPLETED = "SELECT DISTINCT(INPUT_VALUE) FROM INPUT WHERE INPUT_IDENTIFIER = 'DATASET_URI' AND REQUEST_ID in (SELECT resp.request_id from response resp, throttle_queue queue where resp.request_id = queue.request_id and queue.status = 'STARTED' and resp.status in ('SUCCEEDED','FAILED'))";
// --- for restart after hard down below : To Be Implmented ---

    private ThrottleStatus getStatus(String requestId) throws ExceptionReport {

        //get the throttle_queue status for the restart logic upon system failure
        try (Connection connection = CONNECTION_HANDLER.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(SELECT_STATUS_REQUEST_STATEMENT);
            preparedStatement.setString(1, requestId);

            ResultSet rs = preparedStatement.executeQuery();

            if (rs != null && rs.next()) {
                String status = rs.getString(1);
                LOGGER.info("Got status in throttle_queue table for request:" + requestId + " status: " + status);
                return (ThrottleStatus.valueOf(status));
            }

        } catch (Exception e) {
            String msg = "Faild to select the next available for processing from the throttleQueue";
            LOGGER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }

        return ThrottleStatus.UNKNOWN;
    }
}
