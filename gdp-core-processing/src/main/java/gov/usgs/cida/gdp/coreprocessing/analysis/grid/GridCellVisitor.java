package gov.usgs.cida.gdp.coreprocessing.analysis.grid;

import ucar.nc2.dt.GridDatatype;

/**
 * Utility to visit grid cells and perform calculations on them
 *
 * @author tkunicki
 */
public abstract class GridCellVisitor {

	/**
	 * Called at the start of grid traversal. Override to perform traversal
	 * setup, default is NOOP.
	 *
	 * @param gridDatatype GridDatatype to traverse
	 */
	public void traverseStart(GridDatatype gridDatatype) {}

	/**
	 * Called within loops to determine whether to continue
	 * @return whether or not to continue (default true)
	 */
	public boolean traverseContinue() {
		return true;
	}

	/**
	 * Called at the end of grid traversal. Override to perform traversal
	 * cleanup, default is NOOP.
	 */
	public void traverseEnd() {}

	
	/**
	 * Called before each index of the time range.
	 * @param tIndex time index about to be fetched
	 * @return true if traversal should be performed on this index (default true)
	 */
	public boolean tStart(int tIndex) {
		return true;
	}

	/**
	 * Called after each index of the time range.
	 * @param tIndex time index just finished
	 */
	public void tEnd(int tIndex) {}

	
	/**
	 * Called before each index of the vertical dimension
	 * @param zIndex z index about to be fetched
	 * @return true if traversal should be performed on this index (default true)
	 */
	public boolean zStart(int zIndex) {
		return true;
	}

	/**
	 * Called after traversing each z index.
	 * @param zIndex z index just finished
	 */
	public void zEnd(int zIndex) {}

	
	/**
	 * Called before each yx slice
	 */
	public void yxStart() {}

	/**
	 * Called after each yx slice
	 */
	public void yxEnd() {}

	/**
	 * Called for each grid cell
	 * @param xCellIndex x index to process
	 * @param yCellIndex y index to process
	 * @param value value contained in grid cell
	 */
	public abstract void processGridCell(int xCellIndex, int yCellIndex, double value);

}
