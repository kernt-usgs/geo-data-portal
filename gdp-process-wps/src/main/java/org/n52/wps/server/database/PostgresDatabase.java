package org.n52.wps.server.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.naming.NamingException;


import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.n52.wps.ServerDocument;
import org.n52.wps.commons.PropertyUtil;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.n52.wps.server.database.connection.DefaultConnectionHandler;
import org.n52.wps.server.database.connection.JNDIConnectionHandler;
import org.n52.wps.server.database.domain.WpsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import java.io.ByteArrayInputStream;

import java.sql.Savepoint;
import java.sql.Timestamp;
import java.util.Calendar;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import net.opengis.ows.x11.CodeType;
import net.opengis.ows.x11.ExceptionReportDocument.ExceptionReport;
import net.opengis.ows.x11.LanguageStringType;
import net.opengis.wps.x100.ExecuteResponseDocument;
import net.opengis.wps.x100.OutputDataType;
import net.opengis.wps.x100.OutputDescriptionType;
import net.opengis.wps.x100.OutputReferenceType;
import net.opengis.wps.x100.ProcessBriefType;
import net.opengis.wps.x100.ProcessDescriptionType.ProcessOutputs;
import net.opengis.wps.x100.ProcessFailedType;
import net.opengis.wps.x100.ProcessStartedType;
import net.opengis.wps.x100.StatusType;
import org.apache.xmlbeans.XmlCursor;
import org.n52.wps.server.CapabilitiesConfiguration;
import org.n52.wps.server.RepositoryManager;
import org.n52.wps.server.WebProcessingService;

import org.n52.wps.server.database.domain.WpsInput;
import org.n52.wps.server.database.domain.WpsOutput;
import org.n52.wps.server.database.domain.WpsOutputDefinition;
import org.n52.wps.server.database.domain.WpsResponse;
import org.n52.wps.server.database.domain.WpsStatus;
import org.n52.wps.server.request.Request;
import org.n52.wps.util.XMLBeansHelper;


/**
 *
 * @author isuftin
 */
public class PostgresDatabase extends AbstractDatabase {

	private static final Logger LOGGER = LoggerFactory.getLogger(PostgresDatabase.class);

	private static final String KEY_DATABASE_ROOT = "org.n52.wps.server.database";
	private static final String KEY_DATABASE_PATH = "path";
	private static final String KEY_DATABASE_WIPE_ENABLED = "wipe.enabled";
	private static final String KEY_DATABASE_WIPE_PERIOD = "wipe.period";
	private static final String KEY_DATABASE_WIPE_THRESHOLD = "wipe.threshold";
	private static final boolean DEFAULT_DATABASE_WIPE_ENABLED = true;
	private static final long DEFAULT_DATABASE_WIPE_PERIOD = 1000 * 60 * 60; // default to running once an hour
	private static final long DEFAULT_DATABASE_WIPE_THRESHOLD = 1000 * 60 * 60 * 24 * 7; // default to wipe things over a week old

	private static final String FILE_URI_PREFIX = "file://";
	private static final String SUFFIX_GZIP = "gz";
	private static final String DEFAULT_BASE_DIRECTORY
		= Joiner.on(File.separator).join(System.getProperty("java.io.tmpdir", "."), "Database", "Results");
	private static final ServerDocument.Server server = WPSConfig.getInstance().getWPSConfig().getServer();
	private static final String baseResultURL = String.format("%s://%s:%s/%s/RetrieveResultServlet?id=",
			server.getProtocol(), server.getHostname(), server.getHostport(), server.getWebappPath());

	private static String connectionURL;
	private static Path BASE_DIRECTORY;
	private static PostgresDatabase instance;
	private static ConnectionHandler connectionHandler;
	private final static  boolean SAVE_RESULTS_TO_DB = Boolean.parseBoolean(getDatabaseProperties("saveResultsToDB"));
	protected final Object storeResponseLock = new Object();

	private static Timer wipeTimer;
	private final String DATABASE_NAME;

	// SQL DATABASE CREATION
	private static final String REQUEST_TABLE_NAME = "request";
	private static final String INPUT_TABLE_NAME = "input";
	private static final String OUTPUT_DEF_TABLE_NAME = "output_definition";
	private static final String OUTPUT_TABLE_NAME = "output";
	private static final String RESPONSE_TABLE_NAME = "response";
	
	// SQL STATEMENTS
	private static final String INSERT_REQUEST_STATEMENT = "INSERT INTO " + REQUEST_TABLE_NAME + " VALUES(?, ?, ?)";
	private static final String INSERT_INPUT_STATEMENT = "INSERT INTO " + INPUT_TABLE_NAME + " VALUES (?, ?, ?, ?)";
	private static final String INSERT_OUTPUT_DEF_STATEMENT = "INSERT INTO " + OUTPUT_DEF_TABLE_NAME + " VALUES (?, ?, ?, ?)";
	private static final String INSERT_RESPONSE_STATEMENT = "INSERT INTO " + RESPONSE_TABLE_NAME + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
	private static final String INSERT_OUTPUT_STATEMENT = "INSERT INTO " + OUTPUT_TABLE_NAME + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
	
	private static final String UPDATE_RESPONSE_STATEMENT = "UPDATE " + RESPONSE_TABLE_NAME + " SET STATUS=?,PERCENT_COMPLETE=?,END_TIME=?,EXCEPTION_TEXT=? WHERE REQUEST_ID = ?";
	
	private static final String SELECT_REQUEST_STATEMENT = "SELECT * FROM " + REQUEST_TABLE_NAME + " WHERE REQUEST_ID = ?";
	private static final String SELECT_RESPONSE_STATEMENT = "SELECT * FROM " + RESPONSE_TABLE_NAME + " WHERE REQUEST_ID = ?";
	private static final String SELECT_OUTPUT_STATEMENT = "SELECT * FROM " + OUTPUT_TABLE_NAME + " WHERE OUTPUT_ID = ?";
	
	private static final String SELECT_ALL_OUTPUT_STATEMENT = "SELECT * FROM " + OUTPUT_TABLE_NAME + ", " + RESPONSE_TABLE_NAME
			+ " WHERE " + OUTPUT_TABLE_NAME + ".RESPONSE_ID=" + RESPONSE_TABLE_NAME + ".ID AND "
			+ RESPONSE_TABLE_NAME + ".REQUEST_ID=?";
	private static final String SELECT_OLD_OUTPUT_STATEMENT = "SELECT * FROM " +
			"(SELECT *, EXTRACT(EPOCH FROM INSERTED) * 1000 AS TIMESTAMP FROM " + OUTPUT_TABLE_NAME + ") output_time WHERE TIMESTAMP < ?";
	
	private PostgresDatabase() {
		PropertyUtil propertyUtil = new PropertyUtil(server.getDatabase().getPropertyArray(), KEY_DATABASE_ROOT);
		String baseDirectoryPath = propertyUtil.extractString(KEY_DATABASE_PATH, DEFAULT_BASE_DIRECTORY);
		String dbName = getDatabaseProperties(PROPERTY_NAME_DATABASE_NAME);
		DATABASE_NAME = (StringUtils.isBlank(dbName)) ? "wps" : dbName;
		try {
			Class.forName("org.postgresql.Driver");
			initializeBaseDirectory(baseDirectoryPath);
			initializeConnectionHandler();
			initializeDatabaseWiper(propertyUtil);
		} catch (IOException | SQLException | NamingException ex) {
			LOGGER.error("Error creating PostgresDatabase", ex);
			throw new RuntimeException("Error creating PostgresDatabase", ex);
		} catch (ClassNotFoundException ex) {
			LOGGER.error("The database class could not be loaded.", ex);
			throw new UnsupportedDatabaseException("The database class could not be loaded.", ex);
		}
	}

	private void initializeBaseDirectory(String baseDirectoryPath) throws IOException {
		BASE_DIRECTORY = Paths.get(baseDirectoryPath);
		LOGGER.info("Using \"{}\" as base directory for results database", baseDirectoryPath);
		Files.createDirectories(BASE_DIRECTORY);
	}

	private void initializeDatabaseWiper(PropertyUtil propertyUtil) {
		if (propertyUtil.extractBoolean(KEY_DATABASE_WIPE_ENABLED, DEFAULT_DATABASE_WIPE_ENABLED)) {
			long periodMillis = propertyUtil.extractPeriodAsMillis(KEY_DATABASE_WIPE_PERIOD, DEFAULT_DATABASE_WIPE_PERIOD);
			long thresholdMillis = propertyUtil.extractPeriodAsMillis(KEY_DATABASE_WIPE_THRESHOLD, DEFAULT_DATABASE_WIPE_THRESHOLD);
			wipeTimer = new Timer(PostgresDatabase.class.getSimpleName() + " Postgres Wiper", true);
			wipeTimer.scheduleAtFixedRate(new PostgresDatabase.WipeTimerTask(thresholdMillis), 15000, periodMillis);
			LOGGER.info("Started {} Postgres wiper timer; period {} ms, threshold {} ms",
					new Object[]{DATABASE_NAME, periodMillis, thresholdMillis});
		} else {
			wipeTimer = null;
		}
	}

	private void initializeConnectionHandler() throws SQLException, NamingException {
		String jndiName = getDatabaseProperties("jndiName");
		if (null != jndiName) {
			connectionHandler = new JNDIConnectionHandler(jndiName);
		} else {
			connectionURL = "jdbc:postgresql:" + getDatabasePath() + "/" + DATABASE_NAME;
			LOGGER.debug("Database connection URL is: " + connectionURL);
			String username = getDatabaseProperties("username");
			String password = getDatabaseProperties("password");
			Properties props = new Properties();
			props.setProperty("create", "true");
			props.setProperty("user", username);
			props.setProperty("password", password);
			connectionHandler = new DefaultConnectionHandler(connectionURL, props);
		}
	}

	public static synchronized PostgresDatabase getInstance() {
		if (instance == null) {
			instance = new PostgresDatabase();
		}
		return instance;
	}

	@Override
	public String getConnectionURL() {
		return connectionURL;
	}

	@Override
	public Connection getConnection() {
		try {
			return connectionHandler.getConnection();
		} catch (SQLException ex) {
			throw new RuntimeException("Unable to obtain connection to database!", ex);
		}
	}
	
	@Override
	public String generateRetrieveResultURL(String id) {
		return baseResultURL + id;
	}

	@Override
	public void insertRequest(String id, InputStream inputStream, boolean xml) {
		if (xml) {
			WpsRequest wpsReq = new WpsRequest(id, inputStream);
			insertWpsRequest(wpsReq);
		} else{
			//TODO eventually we may need to support KVP (non xml) execution
			String msg = "Postgres Database does not support persisting non xml";
			LOGGER.error(msg);
			throw new UnsupportedOperationException(msg);
		}
	}

	private void insertWpsRequest(WpsRequest wpsReq) {
		Savepoint transaction = null;
		try (Connection connection = getConnection()) {
			try (PreparedStatement insertRequestStatement = connection.prepareStatement(INSERT_REQUEST_STATEMENT);
					PreparedStatement insertInputStatement = connection.prepareStatement(INSERT_INPUT_STATEMENT);
					PreparedStatement insertOutputDefStatement = connection.prepareStatement(INSERT_OUTPUT_DEF_STATEMENT)) {

				connection.setAutoCommit(false);
				transaction = connection.setSavepoint();

				insertRequest(insertRequestStatement, wpsReq);
				insertInputs(wpsReq.getWpsInputs(), insertInputStatement);
				insertOutputDefs(wpsReq.getWpsRequestedOutputs(), insertOutputDefStatement);

				connection.commit();
			} catch (Exception e) {
				String msg = "Failed to insert request into database";
				try {
					if (connection != null) {
						connection.rollback(transaction);
					}
				} catch (Exception e2) {
					LOGGER.error("Unable to rollback changes", e2);
				}
				LOGGER.error(msg, e);
				throw new RuntimeException(msg, e);
			}
		} catch (Exception e) {
			String msg = "Failed to get database connection";
			LOGGER.error(msg, e);
			throw new RuntimeException(msg, e);
		}
	}

	private void insertRequest(PreparedStatement insertRequestStatement, WpsRequest wpsReq) throws SQLException {
		insertRequestStatement.setString(1, wpsReq.getId());
		insertRequestStatement.setString(2, wpsReq.getWpsAlgoIdentifer());
		insertRequestStatement.setString(3, wpsReq.getExecuteDoc().xmlText());
		insertRequestStatement.execute();
	}
	
	private void insertInputs(List<WpsInput> inputs, PreparedStatement preparedStatement) throws SQLException {
		for (WpsInput input : inputs) {
			preparedStatement.setString(1, input.getId());
			preparedStatement.setString(2, input.getWpsRequestId());
			preparedStatement.setString(3, input.getInputId());
			preparedStatement.setString(4, input.getValue());
			preparedStatement.addBatch();
		}
		preparedStatement.executeBatch();
	}
	
	private void insertOutputDefs(List<WpsOutputDefinition> outputs, PreparedStatement preparedStatement) throws SQLException {
		for (WpsOutputDefinition output : outputs) {
			preparedStatement.setString(1, output.getId());
			preparedStatement.setString(2, output.getWpsRequestId());
			preparedStatement.setString(3, output.getMimeType());
			preparedStatement.setString(4, output.getOutputIdentifier());
			preparedStatement.addBatch();
		}
		preparedStatement.executeBatch();
	}

	@Override
	public String insertResponse(String id, InputStream inputStream) {
		WpsResponse wpsResponse = new WpsResponse(id, inputStream);
		wpsResponse.setStartTime(new DateTime());
		insertWpsResponse(wpsResponse);
		return generateRetrieveResultURL(id);
	}
	

	private void insertWpsResponse(WpsResponse wpsResp) {
		Savepoint transaction = null;
		try (Connection connection = getConnection()) {
			try (PreparedStatement insertResponseStatement = connection.prepareStatement(INSERT_RESPONSE_STATEMENT)) {

				connection.setAutoCommit(false);
				transaction = connection.setSavepoint();

				insertResponseToDb(insertResponseStatement, wpsResp);

				connection.commit();
			} catch (Exception e) {
				String msg = "Failed to insert request into database";
				try {
					if (connection != null) {
						connection.rollback(transaction);
					}
				} catch (Exception e2) {
					LOGGER.error("Unable to rollback changes", e2);
				}
				LOGGER.error(msg, e);
				throw new RuntimeException(msg, e);
			}
		} catch (Exception e) {
			String msg = "Failed to get database connection";
			LOGGER.error(msg, e);
			throw new RuntimeException(msg, e);
		}
	}

	private void insertResponseToDb(PreparedStatement insertResponseStatement, WpsResponse wpsResp) throws SQLException {	
		insertResponseStatement.setString(1, wpsResp.getId());
		insertResponseStatement.setString(2, wpsResp.getWpsRequestId());
		insertResponseStatement.setString(3, wpsResp.getWpsAlgoIdentifer());
		insertResponseStatement.setString(4, wpsResp.getStatus().toString());
		insertResponseStatement.setInt(5, wpsResp.getPercentComplete() == null ? 0 : wpsResp.getPercentComplete());
		insertResponseStatement.setTimestamp(6, toSQLTimestamp(wpsResp.getCreationTime()));
		insertResponseStatement.setTimestamp(7, toSQLTimestamp(wpsResp.getStartTime()));
		insertResponseStatement.setTimestamp(8, toSQLTimestamp(wpsResp.getEndTime()));
		insertResponseStatement.setString(9, wpsResp.getExceptionText());
		insertResponseStatement.execute();
	}
	

	private void persistOutput(WpsOutput output, PreparedStatement insertOutputStatement) throws SQLException {
		if (output != null) {
			insertOutputStatement.setString(1, output.getId());
			insertOutputStatement.setString(2, output.getOutputId());
			insertOutputStatement.setString(3, output.getWpsResponseId());
			//NOTE either content or location is populated, based on type of output (inline or not)
			String content = output.getContent();
			insertOutputStatement.setString(4, content);
			insertOutputStatement.setString(5, output.getMimeType());
			insertOutputStatement.setLong(6, output.getResponseLength());
			insertOutputStatement.setString(7, output.getLocation());
			insertOutputStatement.setTimestamp(8, new Timestamp(Calendar.getInstance().getTimeInMillis()));
			insertOutputStatement.executeUpdate();
		}
	}
	
	private Timestamp toSQLTimestamp(DateTime dateTime) {
		return (dateTime == null) ? null : new Timestamp(dateTime.getMillis());
	}
	
	@Override
	public synchronized String storeComplexValue(String requestid, String outputIdentifier, InputStream stream, String type, String mimeType) {
		String wpsResponseId = readWpsResponseFromDB(requestid).getId();
		// For now id will just be requestId + wps identifier which is unique as long as requests can only run once
		String outputId = requestid + outputIdentifier;
		WpsOutput output = new WpsOutput(wpsResponseId, outputId, mimeType);
		
		if (SAVE_RESULTS_TO_DB) {
			output.setInline(stream);
		} else{
			try {
				// The result contents won't be saved to the database, only a pointer to the file system. I am therefore
				// going to GZip the data to save space
				FileReferenceInfo info = writeInputStreamToDisk(requestid, stream, true);
				output.setLocation(info.getFileLocation());
				output.setResponseLength(info.getFileSize());
			} catch (IOException ex) {
				LOGGER.error("Failed to write output data to disk", ex);
			}
		}
		try (Connection connection = getConnection();
				PreparedStatement statement = connection.prepareStatement(INSERT_OUTPUT_STATEMENT)) {
			persistOutput(output, statement);
		} catch (Exception e) {
			throw new RuntimeException("issue writing output", e);
		}
		return generateRetrieveResultURL(outputId);
	}
	
	/**
	 * Writes an input stream to disk
	 * @param filename base filename
	 * @param data String of data to write to disk, compressed using gzip
	 * @param compress true to GZip results
	 * @return FileReferenceInfo of the file URI pointing where the data was written and length of data
	 * @throws IOException
	 */
	private FileReferenceInfo writeInputStreamToDisk(String filename, InputStream data, boolean compress) throws IOException {
		FileReferenceInfo info = null;
		Path filePath = BASE_DIRECTORY.resolve(Joiner.on(".").join(filename, SUFFIX_GZIP));
		Files.deleteIfExists(filePath);
		Path createdFilePath = Files.createFile(filePath);

		OutputStream os = new FileOutputStream(createdFilePath.toFile());

		if (compress) {
			os = new GZIPOutputStream(os);
		}

		long bytesCopied = IOUtils.copyLarge(data, os);
		IOUtils.closeQuietly(os);
		info = new FileReferenceInfo(createdFilePath.toUri().toString().replaceFirst(FILE_URI_PREFIX, ""), bytesCopied);
		
		return info;
	}
	
	private WpsRequest readWpsRequestFromDB(String requestId) {
		WpsRequest ret = null;
		try (Connection connection = getConnection();
				PreparedStatement selectRequestStatement = connection.prepareStatement(SELECT_REQUEST_STATEMENT)) {
			selectRequestStatement.setString(1, requestId);
			
			ResultSet rs = selectRequestStatement.executeQuery();
			
			if (rs != null && rs.next()) {
				ret = constructRequestFromRs(rs);
			}
		} catch (Exception e) {
			String msg = "Faild to get request from database";
			LOGGER.error(msg, e);
			throw new RuntimeException(msg, e);
		}
		return ret;
	}
	
	private WpsRequest constructRequestFromRs(ResultSet rs) {
		WpsRequest ret = null;
		if (rs != null) {
			try {
				InputStream xml = new ByteArrayInputStream(rs.getString("REQUEST_XML").getBytes());
				ret = new WpsRequest(rs.getString("REQUEST_ID"), xml);
			} catch (SQLException e) {
				String msg = "Failed to build response from database";
				LOGGER.error(msg, e);
				throw new RuntimeException(msg, e);
			}
		}
		return ret;
	}
	
	private WpsResponse readWpsResponseFromDB(String requestId) {
		WpsResponse ret = null;
		try (Connection connection = getConnection();
				PreparedStatement selectResponseStatement = connection.prepareStatement(SELECT_RESPONSE_STATEMENT)) {
			
			selectResponseStatement.setString(1, requestId);
			
			ResultSet rs = selectResponseStatement.executeQuery();
			
			if (rs != null && rs.next()) {
				ret = constructResponseFromRs(rs);
			}
		} catch (Exception e) {
			String msg = "Failed to select request from database";
			LOGGER.error(msg, e);
			throw new RuntimeException(msg, e);
		}
		return ret;
	}
	
	private WpsResponse constructResponseFromRs(ResultSet rs) {
		WpsResponse ret = null;
		if (rs != null) {
			try {
				ret = new WpsResponse(rs.getString("ID"), rs.getString("REQUEST_ID"),
						rs.getString("WPS_ALGORITHM_IDENTIFIER"), WpsStatus.valueOf(rs.getString("STATUS")),
						rs.getInt("PERCENT_COMPLETE"), new DateTime(rs.getTimestamp("CREATION_TIME")), rs.getString("EXCEPTION_TEXT"));
			} catch (SQLException e) {
				String msg = "Failed to build response from database";
				LOGGER.error(msg, e);
				throw new RuntimeException(msg, e);
			}
		}
		return ret;
	}

	private WpsOutput readOutputFromDB(String outputId) {
		WpsOutput ret = null;
		
		try (Connection connection = getConnection();
				PreparedStatement selectOutputStatement = connection.prepareStatement(SELECT_OUTPUT_STATEMENT)) {
			selectOutputStatement.setString(1, outputId);
			
			ResultSet rs = selectOutputStatement.executeQuery();
			
			if (rs != null && rs.next()) {
				ret = constructOutputFromRs(rs);
			}
		} catch (Exception e) {
			String msg = "Failed to select output from database";
			LOGGER.error(msg, e);
			throw new RuntimeException(msg, e);
		}
		return ret;
	}
	
	private List<WpsOutput> readOutputsByRequestFromDB(String requestId) {
		List<WpsOutput> ret = new ArrayList<>();
		
		try (Connection connection = getConnection();
				PreparedStatement selectOutputStatement = connection.prepareStatement(SELECT_ALL_OUTPUT_STATEMENT)) {
			selectOutputStatement.setString(1, requestId);
			
			ResultSet rs = selectOutputStatement.executeQuery();
			
			while (rs != null && rs.next()) {
				ret.add(constructOutputFromRs(rs));
			}
		} catch (Exception e) {
			String msg = "Failed to select output list from database";
			LOGGER.error(msg, e);
			throw new RuntimeException(msg, e);
		}
		return ret;
	}

	private WpsOutput constructOutputFromRs(ResultSet rs) {
		WpsOutput ret = null;
		if (rs != null) {
			try {
				/*
				 * OUTPUT_ID is request_id concattenated with wps output identifier (ex. OUTPUT)
				 * Not to be confused with OUTPUT_IDENTIFER which is just the identifier
				 */
				ret = new WpsOutput(rs.getString("ID"), rs.getString("OUTPUT_ID"),
						rs.getString("RESPONSE_ID"), rs.getString("INLINE_RESPONSE"),
						rs.getString("MIME_TYPE"), rs.getLong("RESPONSE_LENGTH"), rs.getString("LOCATION"));
			} catch (SQLException e) {
				String msg = "Failed to build output from database";
				LOGGER.error(msg, e);
				throw new RuntimeException(msg, e);
			}
		}
		return ret;
	}
	

	
	private InputStream buildExecuteResponse(String id) {
		InputStream is = null;
		
		WpsResponse responseObj = readWpsResponseFromDB(id);
		WpsRequest request = readWpsRequestFromDB(id);
		List<WpsOutput> outputList = readOutputsByRequestFromDB(id);
		
		ExecuteResponseDocument doc = ExecuteResponseDocument.Factory.newInstance();
		ExecuteResponseDocument.ExecuteResponse response = doc.addNewExecuteResponse();
		
		addResponseHeaders(doc);
		
		response.setStatusLocation(generateRetrieveResultURL(id));
		
		ProcessBriefType process = response.addNewProcess();
		process.addNewIdentifier().setStringValue(responseObj.getWpsAlgoIdentifer());
		LanguageStringType title = RepositoryManager.getInstance().getProcessDescription(responseObj.getWpsAlgoIdentifer()).getTitle();
		process.addNewTitle().setStringValue(title.getStringValue());
		
		StatusType status = response.addNewStatus();
		status.setCreationTime(responseObj.getCreationTime().toCalendar(Locale.getDefault()));
		switch (responseObj.getStatus()) {
			case ACCEPTED:
				status.setProcessAccepted("Process Accepted");
				break;
			case PAUSED:
				ProcessStartedType paused = ProcessStartedType.Factory.newInstance();
				paused.setPercentCompleted(responseObj.getPercentComplete());
				paused.setStringValue("Process Paused");
				status.setProcessPaused(paused);
				break;
			case STARTED:
				ProcessStartedType started = ProcessStartedType.Factory.newInstance();
				started.setPercentCompleted(responseObj.getPercentComplete());
				started.setStringValue("Process Started");
				status.setProcessStarted(started);
				break;
			case SUCCEEDED:
				status.setProcessSucceeded("Process successful");
				break;
			case FAILED:
			default:
				ProcessFailedType failed = ProcessFailedType.Factory.newInstance();
				ExceptionReport report = failed.addNewExceptionReport();
				report.addNewException().addExceptionText(responseObj.getExceptionText());
				status.setProcessFailed(failed);
				break;
		}
				
		if (responseObj.getStatus() == WpsStatus.SUCCEEDED) {
			response.addNewDataInputs().setInputArray(request.getExecuteDoc().getExecute().getDataInputs().getInputArray());
			ExecuteResponseDocument.ExecuteResponse.ProcessOutputs processOutputs = response.addNewProcessOutputs();
			for (WpsOutput output : outputList) {
				OutputDataType outputType = processOutputs.addNewOutput();

				// This is why the ids should be split up, HACK
				String outputId = output.getOutputId().replace(id, "");
				outputType.addNewIdentifier().setStringValue(outputId);
				String titleString = getOutputTitle(output, responseObj, id);
				if (titleString != null) {
					outputType.addNewTitle().setStringValue(titleString);
				}
				OutputReferenceType reference = outputType.addNewReference();
				reference.setMimeType(output.getMimeType());
				reference.setHref(generateRetrieveResultURL(output.getOutputId()));
			}
		}
		return doc.newInputStream(XMLBeansHelper.getXmlOptions());
	}
	
	private void addResponseHeaders(ExecuteResponseDocument doc) {
		XmlCursor c = doc.newCursor();
		c.toFirstChild();
		c.toLastAttribute();
		c.setAttributeText(new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation"), "http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsExecute_response.xsd");
		doc.getExecuteResponse().setServiceInstance(CapabilitiesConfiguration.ENDPOINT_URL+"?REQUEST=GetCapabilities&SERVICE=WPS");
		doc.getExecuteResponse().setLang(WebProcessingService.DEFAULT_LANGUAGE);
		doc.getExecuteResponse().setService("WPS");
		doc.getExecuteResponse().setVersion(Request.SUPPORTED_VERSION);
	}
	
	private String getOutputTitle(WpsOutput output, WpsResponse response, String id) {
		String title = null;
		ProcessOutputs outputs = RepositoryManager.getInstance()
				.getProcessDescription(response.getWpsAlgoIdentifer()).getProcessOutputs();
		for (OutputDescriptionType type : outputs.getOutputArray()) {
			CodeType name = type.getIdentifier();
			LanguageStringType titleType = type.getTitle();
			// Doing the same identifier 
			String outputId = output.getOutputId().replace(id, "");
			if (outputId.equals(name.getStringValue())) {
				title = titleType.getStringValue();
			}
		}
		return title;
	}

	/**
	 * This is called when response already exists and just needs status or other updates set
	 * @param id
	 * @param stream 
	 */
	@Override
	public void updateResponse(String id, InputStream stream) {
		WpsResponse wpsResponse = new WpsResponse(id, stream);
		if (wpsResponse.getStatus() == WpsStatus.SUCCEEDED || wpsResponse.getStatus() == WpsStatus.FAILED) {
			wpsResponse.setEndTime(new DateTime());
		}
				
		try (Connection connection = getConnection();
			PreparedStatement updateStatement = connection.prepareStatement(UPDATE_RESPONSE_STATEMENT)) {
			
			updateStatement.setString(1, wpsResponse.getStatus().toString());
			updateStatement.setInt(2, wpsResponse.getPercentComplete());
			updateStatement.setTimestamp(3, toSQLTimestamp(wpsResponse.getEndTime()));
			updateStatement.setString(4, wpsResponse.getExceptionText());
			updateStatement.setString(5, id);

			updateStatement.executeUpdate();

			LOGGER.debug("Updated response into database with id of:" + id);
		} catch (SQLException ex) {
			LOGGER.error(MessageFormat.format("Failed to update data in database with  id of:{0}", id), ex);
		}
	}
	
	/**
	 * @param id
	 * @return 
	 */
	@Override
	public InputStream lookupResponse(String id) {
		
		InputStream result = null;
		synchronized (storeResponseLock) {
			if (StringUtils.isNotBlank(id)) {
				
				//first select to see if what we are looking up is in the Response Table
				WpsResponse responseFromDb = readWpsResponseFromDB(id);
				//next select to see if what we are looking up is in the output Table
				WpsOutput outputFromDb = readOutputFromDB(id);
				
				if (responseFromDb != null) {
					result = buildExecuteResponse(id);
				} else if (outputFromDb != null) {
					// TODO switch to ascii stream
					// result = rs.getAsciiStream(SELECTION_STRING_RESPONSE_COLUMN_INDEX);
					String inDbContent = outputFromDb.getContent();
					String location = outputFromDb.getLocation();
					if (inDbContent != null) {
						result = new ByteArrayInputStream(inDbContent.getBytes());
					} else if (location != null) {
						LOGGER.debug("ID {} is output and saved to disk instead of database. Path = " + location);
						if (Files.exists(Paths.get(location))) {
							try {
								result = new GZIPInputStream(new FileInputStream(location));
							} catch (IOException e) {
								String msg = "Problem reading file";
								LOGGER.warn(msg + " at " + location);
								throw new RuntimeException(msg, e);
							}
						} else {
							String msg = "Response not found on disk for id " + id;
							LOGGER.warn(msg + " at " + location);
							throw new RuntimeException(msg);
						}
					} else {
						throw new RuntimeException("No content to return");
					}
				}
			} else {
				LOGGER.warn("tried to look up response for null id, returned null");
			}
		}
		return result;
	}
	
	@Override
	public String getMimeTypeForStoreResponse(String id) {
		String mimeType = null;
		WpsResponse response = readWpsResponseFromDB(id);
		if (response == null) {
			WpsOutput output = readOutputFromDB(id);
			if (output != null) {
				mimeType = output.getMimeType();
			}
		} else {
			mimeType = "text/xml";
		}
		return mimeType;
	}
	
	@Override
	public long getContentLengthForStoreResponse(String id) {
		long contentLength = -1;
		WpsResponse response = readWpsResponseFromDB(id);
		if (response == null) {
			WpsOutput output = readOutputFromDB(id);
			contentLength = output.getResponseLength();
		}
		return contentLength;
	}
	
	@Override
	public File lookupResponseAsFile(String id) {
		throw new UnsupportedOperationException("This is only supported in FlatFileDatabase");
	}

	private class WipeTimerTask extends TimerTask {

		private final long thresholdMillis;
		private final String databaseName = getDatabaseName();

		WipeTimerTask(long thresholdMillis) {
			this.thresholdMillis = thresholdMillis;
		}

		@Override
		public void run() {
			LOGGER.info(databaseName + " Postgres wiper, checking for records older than {} ms", thresholdMillis);
			try {

				int deletedRecordsCount = wipe();
				if (deletedRecordsCount > 0) {
					LOGGER.info(databaseName + " Postgres wiper, cleaned {} records from database", deletedRecordsCount);
				} else {
					LOGGER.debug(databaseName + " Postgres wiper, cleaned {} records from database", deletedRecordsCount);
				}
			} catch (SQLException | IOException ex) {
				LOGGER.warn(databaseName + " Postgres wiper, failed to deleted old records", ex);
			}
		}

		private int wipe() throws SQLException, IOException {
			LOGGER.debug(databaseName + " Postgres wiper, checking for records older than {} ms", thresholdMillis);
			int deletedFileCount = 0;
			List<String> locations = findOldRecords();
			for (String location : locations) {
				boolean deleted = Files.deleteIfExists(Paths.get(location));
				if (deleted) {
					deletedFileCount++;
				}
			}
			return deletedFileCount;
		}

		private List<String> findOldRecords() throws SQLException {
			List<String> locations = new ArrayList<>();
			try (Connection connection = getConnection(); 
					PreparedStatement lookupStatement = connection.prepareStatement(SELECT_OLD_OUTPUT_STATEMENT)) {
				long ageMillis = System.currentTimeMillis() - thresholdMillis;
				lookupStatement.setLong(1, ageMillis);
				try (ResultSet rs = lookupStatement.executeQuery()) {
					while (rs.next()) {
						WpsOutput output = constructOutputFromRs(rs);
						if (output.getLocation() != null) {
							locations.add(output.getLocation());
						}
					}
				}
			}
			return locations;
		}
	}
}
