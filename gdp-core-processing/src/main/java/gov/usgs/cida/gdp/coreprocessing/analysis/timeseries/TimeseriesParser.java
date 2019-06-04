package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import java.io.InputStream;

/**
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public interface TimeseriesParser {

	/**
	 * InputStream to parse, must be called prior to parsing
	 * @param stream 
	 */
	public void setInputStream(InputStream stream);
	
	/**
	 * Continues parsing, will return null if done parsing
	 * @return Observation resulting from continued parsing
	 */
	public Observation parseNextObservation();
}
