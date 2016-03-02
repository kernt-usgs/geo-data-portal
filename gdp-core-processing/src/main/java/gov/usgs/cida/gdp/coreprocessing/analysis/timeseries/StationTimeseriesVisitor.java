package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import org.geotools.feature.FeatureCollection;
import org.opengis.feature.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public abstract class StationTimeseriesVisitor {

	private static final Logger log = LoggerFactory.getLogger(StationTimeseriesVisitor.class);

	public void traverseStart(TimeseriesDataset dataset, FeatureCollection stations) {}
	public void traverseContinue() {}
	public void traverseEnd() {}
	
	public void timeStart() {}
	public void timeContinue() {}
	public void timeEnd() {}
	
	public void stationStart() {}
	public void stationContinue() {}
	public void stationEnd() {}
	
	public void processStation(Feature station) {}
}
