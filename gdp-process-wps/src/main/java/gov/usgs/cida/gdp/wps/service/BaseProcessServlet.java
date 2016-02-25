package gov.usgs.cida.gdp.wps.service;

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
import org.n52.wps.DatabaseDocument.Database;
import org.n52.wps.PropertyDocument.Property;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.n52.wps.server.database.connection.JNDIConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author abramhall (Arthur Bramhall), isuftin@usgs.gov (Ivan Suftin)
 */
public abstract class BaseProcessServlet extends HttpServlet {

	protected static final String WPS_NAMESPACE = "net.opengis.wps.v_1_0_0";
	private static final Logger LOGGER = LoggerFactory.getLogger(BaseProcessServlet.class);
	// FEFF because this is the Unicode char represented by the UTF-8 byte order mark (EF BB BF).
	protected static final int DEFAULT_OFFSET = 0;
	protected static final int DEFAULT_LIMIT = 50;
	private static final int LIMIT_PARAM_INDEX = 1;
	private static final int OFFSET_PARAM_INDEX = 2;
	private static final String REQUESTS_QUERY = "select request_id, wps_algorithm_identifier, status, creation_time, start_time, end_time from response order by creation_time desc limit ? offset ?;";
	private static final String REQUEST_ENTITY_QUERY = "SELECT request_xml FROM request WHERE REQUEST_ID = ?";
	private static final long serialVersionUID = -149568144765889031L;
	protected Unmarshaller wpsUnmarshaller;
	private ConnectionHandler connectionHandler;

	public BaseProcessServlet() {
		String jndiName = getDatabaseProperty("jndiName");
		if (null != jndiName) {
			try {
				connectionHandler = new JNDIConnectionHandler(jndiName);
			} catch (NamingException e) {
				LOGGER.error("Error creating database connection handler", e);
				throw new RuntimeException("Error creating database connection handler", e);
			}
		} else {
			LOGGER.error("Error creating database connection handler. No jndiName provided.");
			throw new RuntimeException("Must configure a Postgres JNDI datasource");
		}

		try {
			wpsUnmarshaller = JAXBContext.newInstance(WPS_NAMESPACE).createUnmarshaller();
		} catch (JAXBException ex) {
			LOGGER.error("Error creating WPS parsing context.");
			throw new RuntimeException("JAXBContext for " + WPS_NAMESPACE + " could not be created", ex);
		}
	}

	private String getDatabaseProperty(String propertyName) {
		Database database = WPSConfig.getInstance().getWPSConfig().getServer().getDatabase();
		String property = null;
		if (null != database) {
			Property[] dbProperties = database.getPropertyArray();
			for (Property dbProperty : dbProperties) {
				if (property == null && dbProperty.getName().equalsIgnoreCase(propertyName)) {
					property = dbProperty.getStringValue();
				}
			}
		}
		return property;
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
		return getRequestIds(DEFAULT_LIMIT, DEFAULT_OFFSET);
	}

	/**
	 *
	 * @param limit the max number of results to return
	 * @param offset which row of the query results to start returning at
	 * @return a list of ExecuteRequest request ids
	 * @throws SQLException
	 */
	protected final List<String> getRequestIds(int limit, int offset) throws SQLException {
		List<String> request_ids = new ArrayList<>();
		try (Connection conn = getConnection(); PreparedStatement pst = conn.prepareStatement(REQUESTS_QUERY)) {
			pst.setInt(LIMIT_PARAM_INDEX, limit);
			pst.setInt(OFFSET_PARAM_INDEX, offset);
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
}
