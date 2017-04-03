package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import gov.usgs.cida.gdp.wps.analytics.PercentCompleteInfo;
import org.n52.wps.server.observerpattern.ISubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.Dimension;
import ucar.nc2.dt.GridDatatype;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class UpdatePercentHeuristic extends AlgorithmHeuristic {
	
	private static final Logger log = LoggerFactory.getLogger(UpdatePercentHeuristic.class);
	
	private ISubject algorithm;
	
	private int datasetCount;
	private int totalTimestepsPerDataset;
	
	private int stepsSoFar;
	private int percentComplete;
	
	public UpdatePercentHeuristic(ISubject algorithm, int datasetCount) {
		this.algorithm = algorithm;
		this.datasetCount = datasetCount;
		this.totalTimestepsPerDataset = -1;
		this.stepsSoFar = 0;
		this.percentComplete = 0;
	}
	
	@Override
	public void traverseStart(GridDatatype gridDatatype) {
		Dimension timeDimension = gridDatatype.getTimeDimension();
		totalTimestepsPerDataset = timeDimension.getLength();
	}
	
	@Override
	public void tEnd(int tIndex) {
		stepsSoFar++;
		int newPercent = calcPercent();
		if (newPercent != percentComplete) {
			log.debug("Updating percent complete: {}", newPercent);
			percentComplete = newPercent;
			PercentCompleteInfo info = new PercentCompleteInfo(percentComplete);
			algorithm.update(info);
		}
	}
	
	private int calcPercent() {
		if (totalTimestepsPerDataset <= 0) {
			throw new IllegalStateException("Traverse start must be called before time end");
		}
		int calculated = (int)((double)stepsSoFar / (double)(datasetCount * totalTimestepsPerDataset) * 100);
		return calculated;
	}

}
