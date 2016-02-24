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
import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.StringUtils;
import org.n52.wps.ServerDocument;
import org.n52.wps.commons.WPSConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author abramhall
 */
public class ProcessListService extends BaseProcessServlet {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProcessListService.class);
	private static final String DATA_QUERY = "select request_id, wps_algorithm_identifier, status, creation_time, start_time, end_time, exception_text from response where request_id like ?;";
	private static final int DATA_QUERY_REQUEST_ID_PARAM_INDEX = 1;
	private static final long serialVersionUID = 1L;
	private static final int NO_OFFSET = 0;
	
	private static String BASE_URL;

	@Override
	public void init() throws ServletException {
		super.init();
		ServerDocument.Server server = WPSConfig.getInstance().getWPSConfig().getServer();
		StringBuilder url = new StringBuilder();
		url.append(server.getProtocol())
				.append("://")
				.append(server.getHostname())
				.append(":")
				.append(server.getHostport())
				.append("/")
				.append(server.getWebappPath());
		BASE_URL = url.toString();
	}

	
	
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
		for (String request : getRequestIds(DEFAULT_LIMIT, offset)) {
			DashboardData dashboardData = buildDashboardData(request);
			dashboardData.setRequestId(request);
			dashboardData.setRequestLink(BASE_URL + "/request?id=" + request);
			dataset.add(dashboardData);
		}
		return dataset;
	}

	private DashboardData buildDashboardData(String baseRequestId) throws SQLException {
		DashboardData data = new DashboardData();
		long startTime = -1;
		long endTime = System.currentTimeMillis();
		try (Connection conn = getConnection(); PreparedStatement pst = conn.prepareStatement(DATA_QUERY)) {
			pst.setString(DATA_QUERY_REQUEST_ID_PARAM_INDEX, baseRequestId);
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					String requestId = rs.getString("request_id");
					Timestamp creationDate = rs.getTimestamp("creation_time");
					String wpsAlgorithmIdentifier = rs.getString("wps_algorithm_identifier");
					String status = rs.getString("status");
					Timestamp startDate = rs.getTimestamp("start_time");
					Timestamp endDate = rs.getTimestamp("end_time");
					String exceptionText = rs.getString("exception_text");
					if (endDate != null) {
						endTime = endDate.getTime();
					}
					if (startDate != null) {
						startTime = startDate.getTime();
					}
					data.setRequestId(requestId);
					data.setIdentifier(ClassUtils.getShortClassName(wpsAlgorithmIdentifier));
					data.setErrorMessage(exceptionText);
					data.setStatus(status);
					data.setCreationTime(creationDate.getTime());
				}
				if (startTime != -1) {
					data.setElapsedTime(endTime - startTime);
				}
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
