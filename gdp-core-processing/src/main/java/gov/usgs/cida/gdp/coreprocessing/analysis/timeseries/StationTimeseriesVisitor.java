package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public abstract class StationTimeseriesVisitor {

	private static final Logger log = LoggerFactory.getLogger(StationTimeseriesVisitor.class);

	public void traverseStart(TimeseriesDataset dataset) {}
	public boolean traverseContinue() {return true;}
	public void traverseEnd() {}
	
	public void timeStart(DateTime timestep) {}
	public boolean timeContinue() {return true;}
	public void timeEnd() {}
	
	public void stationsStart() {}
	public boolean stationsContinue() {return true;}
	public void stationsEnd() {}
	
	public void processStations() {}
}
