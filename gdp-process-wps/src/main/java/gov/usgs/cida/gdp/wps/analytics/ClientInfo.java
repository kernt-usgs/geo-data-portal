package gov.usgs.cida.gdp.wps.analytics;

import gov.usgs.cida.gdp.constants.AppConstant;
import gov.usgs.cida.gdp.wps.util.DatabaseUtil;
import gov.usgs.cida.gdp.wps.util.DocumentUtil;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This metadata logger is a bit special because it is executed outside of the algorithm
 * due to the user agent header no longer being available at the point of execution.
 * 
 * @author jiwalker
 */
public class ClientInfo implements IMetadataLogger {
	
	private static final Logger log = LoggerFactory.getLogger(ClientInfo.class);
	private static final ConnectionHandler connectionHandler = DatabaseUtil.getJNDIConnectionHandler();
	
	private static final Encoder encoder = Base64.getEncoder();
	
	private final String userAgent;
	private final String userIp;
	private String userHash;
	private String userGeo;
	
	public ClientInfo(String userAgent, String ip) {
		this(userAgent, ip, null, null);
	}
	
	public ClientInfo(String userAgent, String ip, String userHash, String userGeo) {
		this.userAgent = userAgent;
		this.userIp = ip;
		this.userHash = userHash;
		this.userGeo = userGeo;
	}
	
	@Override
	public void log(String requestId) {
		
		userHash = calculateHash();
		userGeo = lookupLocation();
		if (StringUtils.isNotBlank(userAgent)) {
			log.debug("Inserting Agent Logging with ID:" + requestId);
			try (Connection connection = connectionHandler.getConnection()) {
				UUID pkey = UUID.randomUUID();
				PreparedStatement prepared = connection.prepareStatement("INSERT INTO request_metadata (ID, REQUEST_ID, USER_AGENT, USER_HASH, USER_GEO)"
						+ " VALUES (?, ?, ?, ?, ?) ON CONFLICT (REQUEST_ID)"
						+ " DO UPDATE SET USER_AGENT = ?, USER_HASH = ?, USER_GEO = ?");
				prepared.setString(1, pkey.toString());
				prepared.setString(2, requestId);
				
				prepared.setString(3, userAgent);
				prepared.setString(4, userHash);
				prepared.setString(5, userGeo);
				
				prepared.setString(6, userAgent);
				prepared.setString(7, userHash);
				prepared.setString(8, userGeo);
				prepared.execute();
			} catch (SQLException ex) {
				log.debug("Problem logging user agent", ex);
			}
		}
	}

	private String lookupLocation() {
		String location = "";
		
		String url = AppConstant.ANALYTICS_GEOIP_ENDPOINT.getValue() + userIp;
		HttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet(url);
		try {
			HttpResponse response = client.execute(get);
			location = GeoIP.parse(response.getEntity().getContent());
		} catch (IOException | SAXException ex) {
			log.debug("Failed to get geoip information", ex);
		}
		
		return location;
	}
	
	private String calculateHash() {
		return encoder.encodeToString(DigestUtils.sha1(userIp));
	}
	
	public static class GeoIP {
		public static String parse(InputStream is) throws SAXException, IOException {
			StringBuilder location = new StringBuilder();
			String lon = "";
			String lat = "";
			Document doc = DocumentUtil.createDocument(is);
			NodeList childNodes = doc.getChildNodes();
			if ("Response".equals(childNodes.item(0).getNodeName())) {
				childNodes = childNodes.item(0).getChildNodes();
				for (int i=0; i < childNodes.getLength(); i++) {
					Node item = childNodes.item(i);
					if ("Latitude".equals(item.getNodeName())) {
						lat = item.getTextContent();
					} else if ("Longitude".equals(item.getNodeName())) {
						lon = item.getTextContent();
					}
				}
			}
			if (StringUtils.isNotBlank(lon) && StringUtils.isNotBlank(lat)) {
				location.append("POINT(")
						.append(lon)
						.append(" ")
						.append(lat)
						.append(")");
			}
			return location.toString();
		}
	}
}
