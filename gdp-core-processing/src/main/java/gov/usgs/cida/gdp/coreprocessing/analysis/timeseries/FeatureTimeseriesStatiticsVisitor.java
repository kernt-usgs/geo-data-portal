package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import java.util.List;
import org.geotools.feature.FeatureCollection;
import org.opengis.feature.Feature;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class FeatureTimeseriesStatiticsVisitor extends StationTimeseriesVisitor {

	private List<Object> featureNames;
	
	public FeatureTimeseriesStatiticsVisitor(List<Object> featureNames) {
		this.featureNames = featureNames;
	}
	
	@Override
	public void processStation(Feature station) {
		super.processStation(station); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void stationStart() {
		super.stationStart(); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void timeStart() {
		super.timeStart(); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void traverseStart(TimeseriesDataset dataset, FeatureCollection stations) {
		super.traverseStart(dataset, stations); //To change body of generated methods, choose Tools | Templates.
	}

	
	
}
