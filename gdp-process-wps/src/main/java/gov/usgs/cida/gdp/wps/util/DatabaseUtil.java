package gov.usgs.cida.gdp.wps.util;

import javax.naming.NamingException;
import org.n52.wps.DatabaseDocument;
import org.n52.wps.PropertyDocument;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.server.database.connection.ConnectionHandler;
import org.n52.wps.server.database.connection.JNDIConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class DatabaseUtil {

	private static final Logger log = LoggerFactory.getLogger(DatabaseUtil.class);
	
	public static String getDatabaseProperty(String propertyName) {
		DatabaseDocument.Database database = WPSConfig.getInstance().getWPSConfig().getServer().getDatabase();
		String property = null;
		if (null != database) {
			PropertyDocument.Property[] dbProperties = database.getPropertyArray();
			for (PropertyDocument.Property dbProperty : dbProperties) {
				if (property == null && dbProperty.getName().equalsIgnoreCase(propertyName)) {
					property = dbProperty.getStringValue();
				}
			}
		}
		return property;
	}
	
	public static ConnectionHandler getJNDIConnectionHandler() {
		ConnectionHandler handler = null;
		String jndiName = getDatabaseProperty("jndiName");
		if (null != jndiName) {
			try {
				handler = new JNDIConnectionHandler(jndiName);
			} catch (NamingException e) {
				log.error("Error creating database connection handler", e);
				throw new RuntimeException("Error creating database connection handler", e);
			}
		} else {
			log.error("Error creating database connection handler. No jndiName provided.");
			throw new RuntimeException("Must configure a Postgres JNDI datasource");
		}
		return handler;
	}
}
