package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.ReadableInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class SOSClient extends Thread implements AutoCloseable {
	
	private static final Logger log = LoggerFactory.getLogger(SOSClient.class);
	
	private static final int MAX_CONNECTIONS = 4;
	private static final int CLIENT_CONNECTION_TIMEOUT = 10000;
	private static final int CLIENT_SOCKET_TIMEOUT = 180000;
	private static int numConnections = 0;
	
	private File file;
	private String sosEndpoint;
	private DateTime startTime;
	private DateTime endTime;
	private String observedProperty;
	private String offering;
	private boolean fetched = false;

	public SOSClient(String sosEndpoint, DateTime startTime, DateTime endTime, String observedProperty,
			String offering) {
		UUID randomUUID = UUID.randomUUID();
		this.file = new File(FileUtils.getTempDirectory(), randomUUID.toString() + ".xml");
		this.sosEndpoint = sosEndpoint;
		this.startTime = startTime;
		this.endTime = endTime;
		this.observedProperty = observedProperty;
		this.offering = offering;
	}

	@Override
	public void run() {
		this.fetchData();
	}
	
	@Override
	public void close() {
		// nothing to close anymore
	}
	
	public File getFile() {
		File doneFile = null;
		if (fetched) {
			doneFile = file;
		}
		return doneFile;
	}

	private synchronized void fetchData() {
		if (fetched) {
			return;
		}
		
		HttpParams httpParams = new BasicHttpParams();
		HttpConnectionParams.setSoTimeout(httpParams, CLIENT_SOCKET_TIMEOUT);
		HttpConnectionParams.setConnectionTimeout(httpParams, CLIENT_CONNECTION_TIMEOUT);
		DefaultHttpClient client = new DefaultHttpClient(httpParams);
		
		HttpResponse response = null;
		try {
			while (numConnections >= MAX_CONNECTIONS) {
				try {
					sleep(250);
				}
				catch (InterruptedException ex) {
					log.debug("interrupted", ex);
				}
			}
			numConnections++;
			URI getURI = buildGetObservationRequest(startTime, endTime, observedProperty, offering);
			HttpGet httpGet = new HttpGet(getURI);
			httpGet.setHeader(HttpHeaders.ACCEPT, "application/xml");
			response = client.execute(httpGet);
			try (FileOutputStream fos = new FileOutputStream(file)) {
				response.getEntity().writeTo(fos);
			}
		} catch (IOException ex) {
			log.error("Unable to get data from service", ex);
		} finally {
			numConnections--;
			fetched = true;
		}
	}

	public URI buildGetObservationRequest(DateTime startTime, DateTime endTime, String observedProperty, String offering) {
		URI uri = null;
		ReadableInterval interval = new Interval(startTime, endTime);
		try {
			uri = new URIBuilder(sosEndpoint).setParameter("service", "SOS").setParameter("request", "GetObservation")
				.setParameter("version", "1.0.0").setParameter("observedProperty", observedProperty)
				.addParameter("offering", offering).addParameter("eventTime", interval.toString()).build();
		} catch (URISyntaxException ex) {
			throw new RuntimeException("SOS endpoint is invalid", ex);
		}
		return uri;
	}
	
}
