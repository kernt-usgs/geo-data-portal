package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is set up to look like NetCDF-Java in that it represents the dataset
 * with minimal data.  It will later fetch the data and return the underlying
 * data.
 * 
 * Currently this is specific for our datasource (SOS) but this should be more
 * generic once we add other sources.
 * 
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class TimeseriesDataset implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(TimeseriesDataset.class);
	
	private URI endpoint;
	private String observedProperty;
	private DateTime startDate;
	private DateTime endDate;
	
	private Map<String, File> tempFiles;
	private Map<String, ObservationCollection> openCollections;
	
	private String units;
	private List<DateTime> timesteps;
	
	public TimeseriesDataset(URI endpoint, String observedProperty, DateTime startDate, DateTime endDate) {
		this.endpoint = endpoint;
		this.observedProperty = observedProperty;
		this.startDate = startDate;
		this.endDate = endDate;
		
		this.openCollections = new ConcurrentHashMap<>();
		this.tempFiles = new ConcurrentHashMap<>();
		this.units = null;
		this.timesteps = null;
	}

	public String getUnits() {
		if (units != null) {
			return units;
		} else {
			throw new IllegalStateException("Metadata not populated");
		}
	}
	
	public List<DateTime> getTimesteps() {
		if (timesteps != null) {
			return timesteps;
		} else {
			throw new IllegalStateException("Metadata not populated");
		}
	}

	public String getValue(String station, DateTime timestep) {
		String value = null;
		ObservationCollection obs = parseData(station);
		while (obs.hasNext() && value == null) {
			Observation next = obs.next();
			if (null != next && next.getTime().isEqual(timestep)) {
				value = next.getValue();
			}
		}
		return value;
	}
	
	public void populateMetadata(String station) {
		try (ObservationCollection obCol = parseData(station)) {
			timesteps = new ArrayList<>();
			while (obCol.hasNext()) {
				Observation next = obCol.next();
				if (null == units) {
					units = next.getMetadata().defaultUnits();
				}
				timesteps.add(next.getTime());
			}
		} catch (Exception ex) {
			log.error("Error populating metadata", ex);
			throw new RuntimeException(ex);
		} finally {
			openCollections.remove(station);
		}
	}
	
	private synchronized ObservationCollection parseData(String station) {
		ObservationCollection obs = null;
		File fetched = null;
		if (openCollections.containsKey(station)) {
			obs = openCollections.get(station);
		} else {
			if (tempFiles.containsKey(station)) {
				fetched = tempFiles.get(station);
			} else {
				try (SOSClient sosClient = new SOSClient(endpoint, startDate, endDate, observedProperty, station)) {
					// this is goofy, but I'm taking an asynchronous process and making it synchronous
					sosClient.run();
					fetched = sosClient.getFile();
					tempFiles.put(station, fetched);
				}
			}
			
			try {
				obs = new ObservationCollection(new FileInputStream(fetched), new SweCommonsParser());
				openCollections.put(station, obs);
			} catch (FileNotFoundException ex) {
				String message = "Could not read timeseries file";
				log.error(message);
				throw new RuntimeException(message, ex);
			}
		}
		return obs;
	}

	@Override
	public void close() throws Exception {
		for (ObservationCollection obs : openCollections.values()) {
			obs.close();
		}
		for (File tempFile : tempFiles.values()) {
			boolean deleted = FileUtils.deleteQuietly(tempFile);
			if (!deleted) {
				tempFile.deleteOnExit();
			}
		}
	}
	
	public String getObservedProperty() {
		return observedProperty;
	}

}
