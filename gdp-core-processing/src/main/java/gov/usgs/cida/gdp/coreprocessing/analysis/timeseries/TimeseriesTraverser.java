package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import com.google.common.collect.Lists;
import java.util.List;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class TimeseriesTraverser {

	private TimeseriesDataset dataset;
	
	public TimeseriesTraverser(TimeseriesDataset dataset) {
		this.dataset = dataset;
	}
	
	public void traverse(StationTimeseriesVisitor visitor) {
		traverse(Lists.newArrayList(visitor));
	}
	
	public void traverse(List<StationTimeseriesVisitor> visitors) {
		// do stuff
	}
}
