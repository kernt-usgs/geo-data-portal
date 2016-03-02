package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is set up to look like NetCDF-Java in that it represents the dataset
 * with minimal data.  It will later fetch the data and return the underlying
 * data.
 * 
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class TimeseriesDataset {

	private static final Logger log = LoggerFactory.getLogger(TimeseriesDataset.class);
	
	private String endpoint;
	
	public TimeseriesDataset(String endpoint) {
		this.endpoint = endpoint;
	}

	String getUnits() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

}
