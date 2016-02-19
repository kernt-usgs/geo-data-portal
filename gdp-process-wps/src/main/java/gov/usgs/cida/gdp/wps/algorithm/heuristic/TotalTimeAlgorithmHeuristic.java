package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicException;
import org.apache.commons.lang.time.StopWatch;
import org.joda.time.MutablePeriod;
import org.joda.time.ReadablePeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.Dimension;
import ucar.nc2.dt.GridDatatype;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class TotalTimeAlgorithmHeuristic extends AlgorithmHeuristic {
	
	private static final Logger log = LoggerFactory.getLogger(TotalTimeAlgorithmHeuristic.class);

	private static final int DEFAULT_EVALUATION_STEPS = 7;
	private static final long DEFAULT_MAX_ALLOWED_TIME = 24 * 60 * 60 * 1000;
	
	private int datasetCount;
	private int totalTimestepsPerDataset;
	private StopWatch stopwatch;
	
	private int stepsTimed;
	private int stepsToTime;
	private long maxTime;
	
	public TotalTimeAlgorithmHeuristic(int datasetCount, int evalSteps, long maxTime) {
		this.datasetCount = datasetCount;
		this.totalTimestepsPerDataset = -1;
		this.stepsTimed = 0;
		this.stepsToTime = evalSteps;
		this.maxTime = maxTime;
		this.stopwatch = new StopWatch();
	}
	
	public TotalTimeAlgorithmHeuristic(int datasetCount) {
		this(datasetCount, DEFAULT_EVALUATION_STEPS, DEFAULT_MAX_ALLOWED_TIME);
	}
	
	@Override
	public void traverseStart(GridDatatype gridDatatype) {
		Dimension timeDimension = gridDatatype.getTimeDimension();
		totalTimestepsPerDataset = timeDimension.getLength();
	}
	
	@Override
	public void tEnd(int tIndex) {
		stepsTimed++;
		if (!traverseContinue()) {
			stopwatch.stop();
			long estimatedTime = estimateTotalTime();
			if (estimatedTime > maxTime) {
				throw new AlgorithmHeuristicException("Max Time Exception : Estimated process time exceeds preset limit.");
			}
		}
	}

	@Override
	public boolean tStart(int tIndex) {
		if (stopwatch.getTime() == 0) {
			stopwatch.start();
		}
		return traverseContinue();
	}

	@Override
	public boolean traverseContinue() {
		return (stepsTimed < stepsToTime);
	}
	
	public long estimateTotalTime() {
		long totalTime = -1;
		if (totalTimestepsPerDataset <= 0) {
			throw new IllegalStateException("Traverse start must be called before time end");
		}
		double percentComplete = (double)stepsTimed / (double)(datasetCount * totalTimestepsPerDataset);
		totalTime = Math.round(stopwatch.getTime() / percentComplete);
		ReadablePeriod period = new MutablePeriod(totalTime);
		log.debug("Total estimated time: {}", period);
		return totalTime;
	}

}
