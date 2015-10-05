package gov.usgs.cida.gdp.wps.algorithm.discovery;

import gov.usgs.cida.gdp.urs.URSLoginProvider;
import gov.usgs.cida.gdp.utilities.OPeNDAPUtils;
import gov.usgs.cida.proxy.registration.HttpLoginProvider;
import gov.usgs.cida.proxy.registration.ProxyRegistrator;
import gov.usgs.cida.proxy.registration.ProxyRegistry;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.n52.wps.ServerDocument;
import org.n52.wps.algorithm.annotation.Algorithm;
import org.n52.wps.algorithm.annotation.Execute;
import org.n52.wps.algorithm.annotation.LiteralDataInput;
import org.n52.wps.algorithm.annotation.LiteralDataOutput;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.impl.WPSConfigurationDocumentImpl;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
@Algorithm(
		version = "1.0.0",
		title = "Interogate Dataset URI",
		abstrakt = "Given a dataset return information about it in plain text")
public class InterrogateDataset extends AbstractAnnotatedAlgorithm {
	
	private static final Logger log = LoggerFactory.getLogger(AbstractAnnotatedAlgorithm.class);
	
	private static final String REGISTRY_NAME = "proxy";
	
	static {
		ProxyRegistrator registrator = ProxyRegistrator.getInstance();
		ProxyRegistry proxyRegistry = registrator.getRegistry(REGISTRY_NAME);
		if (proxyRegistry == null) {
			registrator.newRegistry(REGISTRY_NAME);
		}
	}
	
	private String datasetUri;
	private StringBuilder response = new StringBuilder();
	
	@LiteralDataInput(
			identifier = "DATASET_URI",
			minOccurs = 1,
			maxOccurs = 1)
	public void setCatalogURL(String datasetUri) {
		this.datasetUri = datasetUri;
	}
	
	@LiteralDataOutput(identifier = "DESCRIPTION")
	public String getResult() {
		return response.toString();
	}

	@Execute
	public void process() {
		boolean isURS = false;
		HttpLoginProvider provider = null;
		
		try {
			URI uri = new URI(datasetUri);
			String host = uri.getHost();
			
			provider = new URSLoginProvider();
			URI checkUri = new URI(uri.toString() + ".info");
			isURS = provider.checkResource(uri.toURL());
			
			// other options could come here
			
			// do something with eventual provider
			ProxyRegistrator registrator = ProxyRegistrator.getInstance();
			ProxyRegistry proxyRegistry = registrator.getRegistry(REGISTRY_NAME);
			StringBuilder proxiedPath = new StringBuilder();
			if (proxyRegistry != null) {
				// simplifying by host for now (may need more complex rules in future)
				String path = proxyRegistry.getRegistryUrl(host);
				if (path == null) {
					String proxyTo = new URIBuilder().setScheme(uri.getScheme())
							.setHost(host).setPort(uri.getPort()).build().toString();
					proxyRegistry.setRegistryEntry(host, proxyTo, provider);
					path = host;
				}
				WPSConfigurationDocumentImpl.WPSConfigurationImpl wpsConfig = WPSConfig.getInstance().getWPSConfig();
				ServerDocument.Server server = wpsConfig.getServer();
				proxiedPath.append(server.getProtocol())
						.append("://")
						.append(server.getHostname())
						.append(":")
						.append(server.getHostport())
						.append("/")
						.append(server.getWebappPath())
						.append("/")
						.append(REGISTRY_NAME)
						.append("/")
						.append(path);
				if (StringUtils.isNotBlank(uri.getPath())) {
					proxiedPath.append("/")
						.append(uri.getPath());
				}
				if (StringUtils.isNotBlank(uri.getQuery())) {
					proxiedPath.append("?")
							.append(uri.getQuery());
				}
			} else {
				throw new RuntimeException("Proxy must be registered in order to use this algorithm");
			}
			
			
			if (OPeNDAPUtils.isOPeNDAP(new URI(proxiedPath.toString()))) {
				response.append("true");
			} else {
				response.append("false");
			}
		} catch (MalformedURLException | URISyntaxException ex) {
			throw new RuntimeException("DATASET_URI is invalid", ex);
		} finally {
			if (provider != null) {
				try {
					provider.close();
				} catch (Exception ex) {
					log.error("Problems closing http provider", ex);
				}
			}
		}
		// add others checks here
		
	}
}
