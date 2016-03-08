package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import gov.usgs.cida.nar.resultset.CachedResultSet;
import gov.usgs.cida.nar.service.SosTableRowComparator;
import gov.usgs.cida.nar.util.Profiler;
import gov.usgs.cida.sos.WaterML2Parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.util.UUID;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class SOSClient extends Thread implements AutoCloseable {
	
	private static final Logger log = LoggerFactory.getLogger(SOSClient.class);
	
	private static final int MAX_CONNECTIONS = 4;
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

	private synchronized void fetchData() {
		if (fetched) {
			return;
		}
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 10000);
		clientConfig.property(ClientProperties.READ_TIMEOUT, 180000);
		Client client = ClientBuilder.newClient(clientConfig);
		
		Response response = null;
		InputStream returnStream = null;
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
			String getUrl = buildGetObservationRequest(startTime, endTime, observedProperty, offering);
			response = client.target(getUrl)
				.request(new MediaType[]{MediaType.APPLICATION_XML_TYPE})
				.get();
			returnStream = response.readEntity(InputStream.class);
			WaterML2Parser parser = new WaterML2Parser(returnStream);
			long sosTime = Profiler.stopTimer(timerId);
			Profiler.log.debug("SOS GetObservations took {} milliseconds", sosTime);
			
			timerId = Profiler.startTimer();
			ResultSet parse = parser.parse();
			CachedResultSet.sortedSerialize(parse, new SosTableRowComparator(), this.file);
			long parseTime = Profiler.stopTimer(timerId);
			Profiler.log.debug("Parsing and sorting SOS took {} milliseconds", parseTime);
		} catch (IOException | XMLStreamException ex) {
			log.error("Unable to get data from service", ex);
		} finally {
			numConnections--;
			IOUtils.closeQuietly(returnStream);
			response.close();
			client.close();
			fetched = true;
		}
	}

	public String buildGetObservationRequest(DateTime startTime, DateTime endTime, String observedProperty, String offering) {
		return null;
	}
	
}
