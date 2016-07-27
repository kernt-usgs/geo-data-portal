package gov.usgs.cida.gdp.wps.queue;

import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
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
import net.opengis.wps.x100.DataInputsType;
import net.opengis.wps.x100.ExecuteDocument;
import net.opengis.wps.x100.ExecuteDocument.Execute;
import net.opengis.wps.x100.InputType;
import org.apache.xmlbeans.XmlException;
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
     * or is currently running and hand back the status url if it is. If it has
     * not ran yet, add the dataset to the dataSetInUse set.
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

            /// record status in DB (sep status from the wps status)  ACCEPTED
        } catch (InterruptedException e) {
            LOGGER.info("Attempt to acquire lock to add request to the throttle timed out: " + e.getMessage());

        } finally {
            lock.unlock();
        }
    }

    /**
     * Ensures the same dataset is currently not in use before updating the
     * status and giving the worker thread the doc to execute upon
     * 'requestQueue'
     *
     * @return nulls allowed
     * @throws org.n52.wps.server.ExceptionReport
     */
    @Override
    public ExecuteRequest takeRequest() throws ExceptionReport {
        ExecuteRequest result = null;

        try {
            lock.tryLock(TIME_OUT_SECONDS, TimeUnit.SECONDS);   //#TODO# review timeout duration 
            // get the next request that has a status of accepted
            String requestId = getNext(0);  //init the offset to 0 for the first request to get the oldest request (FIFO)

            //get the request from the map
            result = (ExecuteRequest) this.requestQueue.get(requestId);
            if (null == result) {
                LOGGER.info("There are no requests that are available for processing at this time....");
                // block, sleep and then retry?
            } else {
                // now remove it and its datasources from the internal map and set
                removeRequest(requestId);
            }

        } catch (InterruptedException e) {
            LOGGER.info("Attempt to acquire lock to remove the request from the throttle timed out: " + e.getMessage());  //#TODO# how many retrys ?

        } finally {
            lock.unlock();
        }

        return result; //this could be null 
    }

    private void addRequest(ExecuteRequest req) throws ExceptionReport {
        String requestId = req.getUniqueId().toString();
        addDataResources(req); // this is done before the postgres process has placed the resources in the input table...will need to parse

        this.requestQueue.put(requestId, req); //adds it to the internal hashmap for easy retrieval
        LOGGER.info("Added request:" + requestId + "to queue map.");

        insertQueueRequest(requestId);
    }

    /**
     * Used to protect the data resource: If something is currently utilizing
     * the resource, this request will be blocked until the previous request
     * completes.
     *
     * @return
     */
    private void addDataResources(ExecuteRequest req) throws ExceptionReport {
        // get the data resources from the request and add them to the hashmap. FYI: they don't exist in the input table yet.

        List<String> datasetUris = new ArrayList();
        Document doc = req.getDocument();
        try {
            InputStream in = new ByteArrayInputStream(doc.getTextContent().getBytes("UTF-8"));

            ExecuteDocument eDoc = constructExecuteFromStream(in);

            Execute execute = eDoc.getExecute();
            DataInputsType dataInputs = execute.getDataInputs();

            if (dataInputs != null) {
                for (InputType inputType : dataInputs.getInputArray()) {
                    if (inputType.getData() != null && inputType.getData().getLiteralData() != null) {
                        if (inputType.getIdentifier().getStringValue().equals("DATASET_URI")) {
                            datasetUris.add(inputType.getData().getLiteralData().getStringValue());
                        }
                    }
                }
            }

            this.dataSetInUse.addAll(datasetUris);
            for (String dataSetUri : datasetUris) {
                LOGGER.info("Added to datasets in use: " + dataSetUri);
            }

        } catch (UnsupportedEncodingException ex) {
            String msg = "UTF-8 is not supported. Unable to parse doc and identify inputs.";
            LOGGER.error(msg, ex);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);

        }
    }

    private static ExecuteDocument constructExecuteFromStream(InputStream stream) throws ExceptionReport {
        ExecuteDocument executeDoc;
        try {
            executeDoc = ExecuteDocument.Factory.parse(stream);
        } catch (XmlException | IOException e) {
            String msg = "Issue when constructing ExecuteDocument from xml request.";
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
        return executeDoc;
    }

    /**
     * Used to protect the data resource: If something is currently utilizing
     * the resource, this request will be blocked until the previous request
     * completes. Use a re-entrant lock to prevent issues when adding to the
     * set. Note that one request can have multiple data sets.
     *
     * @return
     */
    private void removeDataResources(String requestId) throws ExceptionReport {
        // get the data inputs from the db and remove them all from the set
        List<String> dataResources = getDataSources(requestId);
        this.dataSetInUse.removeAll(dataResources);
        for (String dataSource : dataResources) {
            LOGGER.info("Removed data source from in use set: " + dataSource);
        }
        //arrayOfUrls = Urls.toArray(new String[Urls.size()]);
    }

    // we make the assumption that, once the request is handled off to the worker thread, all will go well 
    // or the request will be added back into the queue by the end user
    private void removeRequest(String requestId) throws ExceptionReport {
        //remove the data resources from the internal set
        removeDataResources(requestId);

        //remove the request from the queue
        this.requestQueue.remove(requestId);
        LOGGER.info("Removed request from queue:" + requestId);

        //update the status from ACCEPTED to PROCESSED... #TODO# we will never get to a fail or succeeded ie completed state
        updateQueueStatus(requestId, ThrottleStatus.PROCESSED);

    }

    // #PRE_CHECK#
    private boolean hasRan(ExecuteRequest req) {
        // "TBD: impl later";
        Document currentRequestDoc = req.getDocument();
        boolean result = false;
        LOGGER.info("Pre-check before placing on queue -hasRanBefore:" + result);
        // if hasRan or isRunning - result = true
        // determine this by comparing a checksum (MD5) on the doc (request_xml) found in the request (table).request_xml and this requests doc
        // String thisRequest = MD5(currentRequestDoc);
        // get the one from the DB that matches the DB size 
        // String compareTo = MD5(dbRequestDoc);
        // compare the two strings to see if they .equal, possibly looping thru them if more than one with a matched size are returned from the db

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
            // UUID pkey = UUID.randomUUID();
            String sql = "SELECT enabled FROM throttle_queue_toggle  WHERE toggle_type = " + ToggleType.REDUNDANT_REQUEST_CHECK; // \"REDUNDANT_REQUEST_CHECK\"";
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

            if (rs != null && rs.next()) {
                result.add(rs.getString(1));
                LOGGER.info("Fetched datasources from input for requestId:" + requestId + " datasource: " + rs.getString(1));
            }
        } catch (Exception e) {
            String msg = "Failed to find previously ran response URL from database";
            LOGGER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
        return result;
    }

    //return the next available request to be worked on who's data set is not in use
    private String getNext(int offset) throws ExceptionReport {

        List ids = getNextFew(offset); //get the first three or quantity set by the offset

        for (Object id : ids) {
            String resultId = (String) id;

            //get the datasets associated with the request from the input table
            List datasets = getDataSources(resultId);
            for (Object dataset : datasets) {
                if (!this.dataSetInUse.contains(dataset)) {
                    //return the first one that is found and return to the worker thread
                    LOGGER.info("Found work for worker thread: " + resultId);
                    return resultId;
                }
            }
            // none of the first few met the criteria (a needed dataset was in use)...
            //if you try all 3, do another getNextFew with an offset of 3  ... may need to adjust the offset for performance (and the limit)
            int tryAgainOffset = offset + 3; // #TODO#test when only 2 remain in left in the db, will it still get/process them?
            LOGGER.info("Fetching next work candidates from DB using offset of: " + tryAgainOffset);
            getNext(tryAgainOffset);
        }

        return null; //went through all the active requests and not a one (or there are no reamining requests) qualified to run
    }

    private List getNextFew(int offset) throws ExceptionReport {
        List requestIds = new ArrayList();
        //   Map requestIdsMap = new LinkedHashMap(3);

        //return the next available request to be worked on that's data set is not in contention
        try (Connection connection = CONNECTION_HANDLER.getConnection();
                PreparedStatement selectRequestStatement = connection.prepareStatement(SELECT_NEXT_AVAILABLE)) {
            selectRequestStatement.setInt(1, offset);// set the first ? to 0 which will then bring back the top 3 rows

            ResultSet rs = selectRequestStatement.executeQuery();

            if (rs != null && rs.next()) {
                requestIds.add(rs.getString(1));
                //requestIdsMap.put(rs.getString(1), rs.getDate(2));
                LOGGER.info("Getting candidates for worker thread:" + rs.getString(1) + " with enqueue time of: " + rs.getDate(2).getTime());  //number of millis since Jan 1 1970
            }

        } catch (Exception e) {
            String msg = "Faild to select the next available for processing from the throttleQueue";
            LOGGER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
        return requestIds;
    }

    private void insertQueueRequest(String requestId) throws ExceptionReport {
        //ACCEPTED -> PROCESSED 

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
            //throw new RuntimeException(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
    }

    //update the requests status on the throttle_queue table for the dequeuing process
    private void updateQueueStatus(String requestId, ThrottleStatus status) throws ExceptionReport {
        //ACCEPTED -> PROCESSED 
        Timestamp now = new Timestamp(Calendar.getInstance().getTimeInMillis());

        try (Connection connection = CONNECTION_HANDLER.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_STATUS_DEQUEUE_STATEMENT);
            preparedStatement.setString(1, requestId);
            preparedStatement.setString(2, status.toString());

            if (status.equals(ThrottleStatus.PROCESSED)) {  //in place should we ever go with more than two status possibilities
                preparedStatement.setTimestamp(3, now);  //dequeue timestamp
            }

            preparedStatement.executeUpdate();
            LOGGER.info("Updated status in throttle_queue table of request:" + requestId + " to status of :" + status.toString());
        } catch (Exception e) {
            String msg = "Failed to insert request and status into throttle_queue table.";
            LOGGER.error(msg, e);
            throw new ExceptionReport(msg, ExceptionReport.NO_APPLICABLE_CODE);
        }
    }

    // throttle queue table 
    private static final String THROTTLE_QUEUE_TABLE = "throttle_queue";

    // sql prepared statements
    // #PRE_CHECK# used to determine if the request ran before    
    private static final String SELECT_RESPONSE_URL_STATEMENT = "SELECT response FROM results, response WHERE results.request_id = response.request_id and response.status = \"SUCCEEDED\" and response.request_id = ?";
    // used to determine which request should run next 
    private static final String SELECT_STATUS_REQUEST_STATEMENT = "SELECT STATUS FROM " + THROTTLE_QUEUE_TABLE + " WHERE REQUEST_ID = ?";
    private static final String INSERT_STATUS_REQUEST_STATEMENT = "INSERT INTO " + THROTTLE_QUEUE_TABLE + "(REQUEST_ID, STATUS, ENQUEUED, DEQUEUED) VALUES (?, ?, ?, ?)";
    private static final String UPDATE_STATUS_DEQUEUE_STATEMENT = "UPDATE " + THROTTLE_QUEUE_TABLE + " SET STATUS = ? WHERE REQUEST_ID = ?";
    private static final String SELECT_NEXT_AVAILABLE = "SELECT request_id, enqueued from " + THROTTLE_QUEUE_TABLE + " where status = 'ACCEPTED' ORDER BY enqueued LIMIT 3 OFFSET ?";
    private static final String SELECT_DATA_SOURCES_STATEMENT = "SELECT INPUT_VALUE FROM INPUT WHERE INPUT_IDENTIFIER = \"DATASET_URI\" AND REQUEST_ID = ?";

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
