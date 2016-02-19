package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridCellVisitor;

/**
 * Heuristics can implement the GridCellVisitor pattern to work inline with process.
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public abstract class AlgorithmHeuristic extends GridCellVisitor {

	@Override
	public void processGridCell(int xCellIndex, int yCellIndex, double value) {
		// This is the only abstract handler above
	}
	
}
