package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridCellVisitor;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicException;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicExceptionID;
import org.apache.commons.lang.time.StopWatch;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class TimingHeuristicGridCellVisitor extends GridCellVisitor {

	private static final int DEFAULT_EVALUATION_STEPS = 7;
	private static final long DEFAULT_MAX_ALLOWED_TIME = 24 * 60 * 60 * 1000;
	
	private int datasetCount;
	private int totalTimestepsPerDataset;
	private StopWatch stopwatch;
	
	private int stepsTimed;
	private int stepsToTime;
	private long maxTime;
	
	public TimingHeuristicGridCellVisitor(int datasetCount, int totalTimestepsPerDataset, int evalSteps, long maxTime) {
		this.datasetCount = datasetCount;
		this.totalTimestepsPerDataset = totalTimestepsPerDataset;
		this.stepsTimed = 0;
		this.stepsToTime = evalSteps;
		this.maxTime = maxTime;
		this.stopwatch = new StopWatch();
	}
	
	public TimingHeuristicGridCellVisitor() {
		this(0, 0, DEFAULT_EVALUATION_STEPS, DEFAULT_MAX_ALLOWED_TIME);
	}

	public void setDatasetCount(int datasetCount) {
		this.datasetCount = datasetCount;
	}

	public void setTotalTimesteps(int totalTimesteps) {
		this.totalTimestepsPerDataset = totalTimesteps;
	}
	
	@Override
	public void tEnd(int tIndex) {
		stepsTimed++;
		if (!traverseContinue()) {
			stopwatch.stop();
			long estimatedTime = estimateTotalTime();
			if (estimatedTime > maxTime) {
				throw new RuntimeException(new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.GDP_MAX_TIME_EXCEEDED_EXCEPTION, this.getClass().getSimpleName(), null, null));
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
	
	@Override
	public void processGridCell(int xCellIndex, int yCellIndex, double value) {
		// Don't actually do anything with grid cells, just looking for time
	}
	
	public long estimateTotalTime() {
		double percentComplete = (double)stepsTimed / (double)(datasetCount * totalTimestepsPerDataset);
		double totalTime = stopwatch.getTime() / percentComplete;
		return Math.round(totalTime);
	}

}
