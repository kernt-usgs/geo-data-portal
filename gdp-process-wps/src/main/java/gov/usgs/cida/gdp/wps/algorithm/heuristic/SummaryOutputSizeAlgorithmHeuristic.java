package gov.usgs.cida.gdp.wps.algorithm.heuristic;


import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicException;
import org.apache.commons.io.output.CountingOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.Dimension;
import ucar.nc2.dt.GridDatatype;

public class SummaryOutputSizeAlgorithmHeuristic extends AlgorithmHeuristic {

	static private Logger log = LoggerFactory.getLogger(SummaryOutputSizeAlgorithmHeuristic.class);

	private static final int DEFAULT_EVALUATION_STEPS = 7;
	public static final long DEFAULT_MAXIMUM_OUTPUT_SIZE = 1024 * 1024 * 1024 * 5; // 5 GB
	
	private CountingOutputStream cos;
	private int datasetCount;
	private int totalTimestepsPerDataset;
	private int evalSteps;
	private long maximumSizeConfigured;
	
	private int stepsCounted = 0;
	
	public SummaryOutputSizeAlgorithmHeuristic(CountingOutputStream cos, int datasetCount, int evalSteps, long maxSizeBytes) {
		this.cos = cos;
		this.datasetCount = datasetCount;
		this.totalTimestepsPerDataset = -1;
		this.evalSteps = evalSteps;
		this.maximumSizeConfigured = maxSizeBytes;
	}
	
	public SummaryOutputSizeAlgorithmHeuristic(CountingOutputStream cos, int datasetCount) {
		this(cos, datasetCount, DEFAULT_EVALUATION_STEPS, DEFAULT_MAXIMUM_OUTPUT_SIZE);
	}

	@Override
	public void traverseStart(GridDatatype gridDatatype) {
		Dimension timeDimension = gridDatatype.getTimeDimension();
		totalTimestepsPerDataset = timeDimension.getLength();
	}

	@Override
	public void tEnd(int tIndex) {
		stepsCounted++;
		if (!traverseContinue()) {
			long bytesSoFar = cos.getByteCount();
			long estimatedTime = estimateTotalBytes(bytesSoFar);
			if (estimatedTime > maximumSizeConfigured) {
				throw new AlgorithmHeuristicException("Maximum Output Size Exception : Estimated process output exceeds preset limit.");
			}
		}
	}

	@Override
	public boolean traverseContinue() {
		return (stepsCounted < evalSteps);
	}

	public long estimateTotalBytes(long bytesSoFar) {
		long totalBytes = -1l;
		if (totalTimestepsPerDataset <= 0) {
			throw new IllegalStateException("Traverse start must be called before time end")
		}
		double percentComplete = (double)stepsCounted / (double)(datasetCount * totalTimestepsPerDataset);
		totalBytes = Math.round(bytesSoFar / percentComplete);
		log.debug("Total estimated bytes: {}", totalBytes);
		return totalBytes;
	}
	
}
