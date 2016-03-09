package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
	
	private String endpoint;
	private String observedProperty;
	private String startDate;
	private String endDate;
	
	private List<File> tempFiles;
	private Map<String, ObservationCollection> openCollections;
	
	private String units;
	private List<DateTime> timesteps;
	
	public TimeseriesDataset(String endpoint, String observedProperty, String startDate, String endDate) {
		this.endpoint = endpoint;
		this.observedProperty = observedProperty;
		this.startDate = startDate;
		this.endDate = endDate;
		
		this.openCollections = new ConcurrentHashMap<>();
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
		ObservationCollection obCol = parseData(station);
		while (obCol.hasNext()) {
			Observation next = obCol.next();
			if (null == units) {
				units = next.getMetadata().defaultUnits();
			}
			timesteps.add(next.getTime());
		}
	}
	
	private synchronized ObservationCollection parseData(String station) {
		ObservationCollection obs = null;
		if (openCollections.containsKey(station)) {
			return openCollections.get(station);
		} else {
			SOSClient sosClient = new SOSClient(endpoint, new DateTime(startDate),
					new DateTime(endDate), observedProperty, station);
			// this is goofy, but I'm taking an asynchronous process and making it synchronous
			sosClient.run();
			File fetched = sosClient.getFile();
			tempFiles.add(fetched);
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
		for (File tempFile : tempFiles) {
			boolean deleted = FileUtils.deleteQuietly(tempFile);
			if (!deleted) {
				tempFile.deleteOnExit();
			}
		}
	}

}
