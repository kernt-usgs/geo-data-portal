package gov.usgs.cida.gdp.wps.service;

import com.google.common.collect.Maps;
import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import javax.naming.NamingException;
import javax.servlet.http.HttpServlet;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import net.opengis.wps.v_1_0_0.Execute;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.n52.wps.server.database.connection.JNDIConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static gov.usgs.cida.gdp.wps.util.DatabaseUtil.getDatabaseProperty;
import java.util.Map;

/**
 * @author abramhall (Arthur Bramhall), isuftin@usgs.gov (Ivan Suftin)
 */
public abstract class BaseProcessServlet extends HttpServlet {

	protected static final String WPS_NAMESPACE = "net.opengis.wps.v_1_0_0";
	private static final Logger LOGGER = LoggerFactory.getLogger(BaseProcessServlet.class);
	// FEFF because this is the Unicode char represented by the UTF-8 byte order mark (EF BB BF).
	protected static final int DEFAULT_OFFSET = 0;
	protected static final int DEFAULT_LIMIT = 50;
	private static final String REQUEST_ENTITY_QUERY = "SELECT request_xml FROM request WHERE REQUEST_ID = ?";
	private static final long serialVersionUID = -149568144765889031L;
	protected Unmarshaller wpsUnmarshaller;
	private ConnectionHandler connectionHandler;

	public BaseProcessServlet() {
		connectionHandler = DatabaseUtil.getJNDIConnectionHandler();

		try {
			wpsUnmarshaller = JAXBContext.newInstance(WPS_NAMESPACE).createUnmarshaller();
		} catch (JAXBException ex) {
			LOGGER.error("Error creating WPS parsing context.");
			throw new RuntimeException("JAXBContext for " + WPS_NAMESPACE + " could not be created", ex);
		}
	}

	protected InputStream getRequestEntityAsStream(String id) throws SQLException, IOException {
		InputStream requestEntity = null;
		try (Connection conn = getConnection(); PreparedStatement pst = conn.prepareStatement(REQUEST_ENTITY_QUERY)) {
			pst.setString(1, id);
			try (ResultSet rs = pst.executeQuery()) {
				if (rs.next()) {
					String entity = rs.getString(1);
					requestEntity = new ByteArrayInputStream(entity.getBytes());
				}
			}
		}
		return requestEntity;
	}
	
	protected final Connection getConnection() throws SQLException {
		return connectionHandler.getConnection();
	}

	/**
	 * @return The latest
	 * {@value gov.usgs.cida.gdp.wps.service.BaseProcessServlet#DEFAULT_LIMIT}
	 * ExecuteRequest request ids
	 * @throws SQLException
	 */
	protected final List<String> getRequestIds() throws SQLException {
		return getRequestIds(DEFAULT_LIMIT, DEFAULT_OFFSET, Maps.newTreeMap());
	}

	/**
	 *
	 * @param limit the max number of results to return
	 * @param offset which row of the query results to start returning at
	 * @param params parameter map for querying data
	 * @return a list of ExecuteRequest request ids
	 * @throws SQLException
	 */
	protected final List<String> getRequestIds(int limit, int offset, Map<String, String[]> params) throws SQLException {
		List<String> request_ids = new ArrayList<>();
		try (Connection conn = getConnection(); PreparedStatement pst = conn.prepareStatement(requestQueryBuilder(params))) {
			int paramPos = 1;
			if (params.containsKey("hash")) {
				pst.setString(paramPos++, params.get("hash")[0]);
			}
			if (params.containsKey("status")) {
				pst.setString(paramPos++, params.get("status")[0]);
			}
			pst.setInt(paramPos++, limit);
			pst.setInt(paramPos++, offset);
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					String id = rs.getString(1);
					request_ids.add(id);
				}
			}
		}
		return request_ids;
	}

	protected final String getIdentifier(String xml) throws JAXBException, IOException {
		StreamSource source;

		if (xml.toLowerCase().endsWith(".gz")) {
			source = new StreamSource(new GZIPInputStream(new FileInputStream(new File(xml))));
		} else {
			source = new StreamSource(new StringReader(xml));
		}
		JAXBElement<Execute> wpsExecuteElement = wpsUnmarshaller.unmarshal(source, Execute.class);
		Execute execute = wpsExecuteElement.getValue();
		String identifier = execute.getIdentifier().getValue();
		return identifier.substring(identifier.lastIndexOf(".") + 1);
	}
	
	private String requestQueryBuilder(Map<String, String[]> params) {
		StringBuilder builder = new StringBuilder();
		builder.append("SELECT")
				.append(" r.request_id, r.wps_algorithm_identifier, r.status, r.creation_time, r.start_time, r.end_time")
				.append(" FROM response r, request_metadata m WHERE m.request_id = r.request_id");
		if (params.containsKey("hash") || params.containsKey("status")) {
			if (params.containsKey("hash")) {
				builder.append(" AND m.user_hash = ?");
			}
			if (params.containsKey("status")) {
				builder.append(" AND r.status = ?");
			}
		}
		builder.append(" ORDER BY creation_time DESC LIMIT ? offset ?;");
		return builder.toString();
	}
}
