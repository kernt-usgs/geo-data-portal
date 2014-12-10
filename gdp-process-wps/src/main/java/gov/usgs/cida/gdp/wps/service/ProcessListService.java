package gov.usgs.cida.gdp.wps.service;

import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.zip.GZIPInputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import net.opengis.wps.v_1_0_0.ExecuteResponse;
import net.opengis.wps.v_1_0_0.StatusType;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author abramhall
 */
public class ProcessListService extends BaseProcessServlet {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProcessListService.class);
	private static final String DATA_QUERY = "select request_id, request_date, response from results where request_id like ?;";
	private static final int DATA_QUERY_REQUEST_ID_PARAM_INDEX = 1;
	private static final int DATA_QUERY_REQUEST_ID_COLUMN_INDEX = 1;
	private static final int DATA_QUERY_REQUEST_DATE_COLUMN_INDEX = 2;
	private static final int DATA_QUERY_RESPONSE_COLUMN_INDEX = 3;
	private static final String REQUEST_PREFIX = "REQ_";
	private static final long serialVersionUID = 1L;
	private static final int NO_OFFSET = 0;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			int offset = NO_OFFSET;
			String reqPage = req.getParameter("page");
			
			if (StringUtils.isNotBlank(reqPage)) {
				try {
					offset = (Integer.parseInt(reqPage, 10) - 1) * DEFAULT_LIMIT;
				} catch (NumberFormatException nfe) {
					LOGGER.info("Parameter 'page' ({}) could not be parsed as an integer. Disabling page limit for request.", reqPage);
				}
			}
			
			String json = new GsonBuilder().disableHtmlEscaping().create().toJson(getDashboardData(offset, req));
			resp.setContentType("application/json");
			resp.getWriter().write(json);
			resp.flushBuffer();
		} catch (SQLException ex) {
			LOGGER.error("Failed to retrieve data", ex);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve data: " + ex);
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "This servlet is read only. Try using Get.");
	}

	private List<DashboardData> getDashboardData(int offset, HttpServletRequest req) throws SQLException {
		List<DashboardData> dataset = new ArrayList<>();
		String requestUrl = req.getRequestURL().toString();
		String cleanedUrl = requestUrl.substring(0, requestUrl.indexOf("/list"));
		for (String request : getRequestIds(DEFAULT_LIMIT, offset)) {
			String baseRequestId = request.substring(REQUEST_PREFIX.length());
			DashboardData dashboardData = buildDashboardData(baseRequestId);
			dashboardData.setRequestId(baseRequestId);
			dashboardData.setRequestLink(cleanedUrl + "/request?id=" + baseRequestId);
			dataset.add(dashboardData);
		}
		return dataset;
	}

	private DashboardData buildDashboardData(String baseRequestId) throws SQLException {
		DashboardData data = new DashboardData();
		long startTime = -1;
		long endTime = System.currentTimeMillis();
		try (Connection conn = getConnection(); PreparedStatement pst = conn.prepareStatement(DATA_QUERY)) {
			pst.setString(DATA_QUERY_REQUEST_ID_PARAM_INDEX, "%" + baseRequestId + "%");
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					String requestId = rs.getString(DATA_QUERY_REQUEST_ID_COLUMN_INDEX);
					String requestDate = rs.getString(DATA_QUERY_REQUEST_DATE_COLUMN_INDEX);
					String xml = rs.getString(DATA_QUERY_RESPONSE_COLUMN_INDEX);
					if (requestId.toUpperCase().endsWith("OUTPUT")) {
						endTime = Timestamp.valueOf(requestDate).getTime();
						data.setOutput(xml);
					} else if (requestId.startsWith(REQUEST_PREFIX)) {
						String identifier;
						identifier = getIdentifier(xml);
						data.setIdentifier(identifier);
					} else {
						String status = getStatus(xml);
						startTime = getStartTime(xml);
						data.setStatus(status);
						data.setCreationTime(startTime);
					}
				}
				if (startTime != -1) {
					data.setElapsedTime(endTime - startTime);
				}
			} catch (JAXBException | IOException ex) {
				data.setErrorMessage("Unmarshalling error for request [" + baseRequestId + "] " + ex.toString());
			}
		}
		return data;
	}

	private String getStatus(String xml) throws JAXBException, IOException {
		StringBuilder status = new StringBuilder();
		StatusType statusElement = getStatusElement(xml);
		if (statusElement.isSetProcessAccepted()) {
			status.append("Accepted");
		} else if (statusElement.isSetProcessFailed()) {
			status.append("Failed");
		} else if (statusElement.isSetProcessPaused()) {
			status.append("Paused");
		} else if (statusElement.isSetProcessStarted()) {
			status.append("Started");
		} else if (statusElement.isSetProcessSucceeded()) {
			status.append("Succeeded");
		}
		return status.toString();
	}

	private StatusType getStatusElement(String xml) throws JAXBException, IOException {
		StreamSource source;

		if (xml.toLowerCase().endsWith(".gz")) {
			source = new StreamSource(new GZIPInputStream(new FileInputStream(new File(xml))));
		} else {
			source = new StreamSource(new StringReader(xml));
		}
		JAXBElement<ExecuteResponse> executeResponseElement = wpsUnmarshaller.unmarshal(source, ExecuteResponse.class);
		ExecuteResponse executeResponse = executeResponseElement.getValue();
		return executeResponse.getStatus();
	}

	private long getStartTime(String xml) throws JAXBException, IOException {
		final StatusType statusElement = getStatusElement(xml);
		Calendar start = DatatypeConverter.parseDateTime(statusElement.getCreationTime().toString());
		return start.getTimeInMillis();
	}
}
