package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import com.google.common.collect.Lists;
import java.util.List;
import org.joda.time.DateTime;

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
		List<DateTime> timesteps = dataset.getTimesteps();
		for (StationTimeseriesVisitor visitor : visitors) {
			visitor.traverseStart(dataset);
		}
		for (DateTime timestep : timesteps) {
			for (StationTimeseriesVisitor visitor : visitors) {
				if (visitor.timeContinue()) {
					visitor.timeStart(timestep);
				}
			}
			for (StationTimeseriesVisitor visitor : visitors) {
				visitor.stationsStart();
				if (visitor.stationsContinue()) {
					visitor.processStations();
				}
				visitor.stationsEnd();
			}
			for (StationTimeseriesVisitor visitor : visitors) {
				visitor.timeEnd();
			}
		}
		for (StationTimeseriesVisitor visitor : visitors) {
			visitor.traverseEnd();
		}
	}
}
