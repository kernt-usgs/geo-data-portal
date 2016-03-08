package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
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
	
	private Map<String, File> cachedResults;
	
	private String units;
	private List<String> timesteps;
	
	public TimeseriesDataset(String endpoint, String observedProperty, String startDate, String endDate) {
		this.endpoint = endpoint;
		this.observedProperty = observedProperty;
		this.startDate = startDate;
		this.endDate = endDate;
		
		this.cachedResults = new HashMap<>();
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
	
	public List<String> getTimesteps() {
		if (timesteps != null) {
			return timesteps;
		} else {
			throw new IllegalStateException("Metadata not populated");
		}
	}
	
	public void getValue(String station, String timestep) {
		
	}
	
	public void populateMetadata(String station) {
		File fetchData = fetchData(station);
	}
	
	private File fetchData(String station) {
		return null;
	}
	
	private ObservationCollection parseData(File file) {
		try {
			return new ObservationCollection(new FileInputStream(file), new SweCommonsParser());
		} catch (FileNotFoundException ex) {
			String message = "Could not read timeseries file";
			log.error(message);
			throw new RuntimeException(message, ex);
		}
	}

	@Override
	public void close() throws Exception {
		for (File cacheFile : cachedResults.values()) {
			boolean deleted = FileUtils.deleteQuietly(cacheFile);
			if (!deleted) {
				cacheFile.deleteOnExit();
			}
		}
	}

}
