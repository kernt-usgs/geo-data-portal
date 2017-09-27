package gov.usgs.cida.gdp.coreprocessing.analysis.grid;

import gov.usgs.cida.gdp.constants.AppConstant;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.dataset.CoordinateAxis;
import ucar.nc2.dataset.CoordinateAxis1DTime;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDatatype;

/**
 * Request more than one timestep (and all z data) at a time to improve
 * the efficiency of high latency connections.  This improvement is important
 * for both speed of processing as well as being a good client to servers that
 * aren't expecting to be pummeled with requests.
 * 
 * @author jiwalker
 */
public class MultiTimestepReader {
		
	private static final Logger LOGGER = LoggerFactory.getLogger(MultiTimestepReader.class);
	
	private final static int INVALID_INDEX = Integer.MAX_VALUE;
	
	private final Map<Integer, GridDatatype> splitGridDataType;
	private final GridType gridType;
	private final int splitSize;
	
	private int currentArrayPosition;
	private Array currentRequestData;
	
	public MultiTimestepReader(GridDatatype gridDataType) {
		this.gridType = GridType.findGridType(gridDataType);
		this.splitSize = calculateSplitSize(gridDataType);
		this.splitGridDataType = splitGridDatatype(gridDataType, splitSize);
		this.currentArrayPosition = -1;
		this.currentRequestData = null;
	}

	/**
	 * Read a single slice of x-y data at a given t, z index.
	 * 
	 * To be more efficient about requests it will use a cached version if it is
	 * available locally.  The access pattern to be used in order to be
	 * efficient should be to work through time sequentially and fully read all
	 * z-indices before moving along.  Coming back to a timestep after it has
	 * been processed will result in another server read which is expensive.
	 * 
	 * @param t_index time index to read x-y slice for
	 * @param z_index z index to read x-y slice for
	 * @return Array containing data for 2d slice
	 * @throws java.io.IOException 
	 */
	public Array readDataSlice(int t_index, int z_index) throws java.io.IOException {
		int failures = 0;
		
		Array slice = null;
		
		// INVALID_INDEX used when dimension shouldn't exist
		int tPassthru = (t_index == INVALID_INDEX) ? t_index : -1;
		int zPassthru = (z_index == INVALID_INDEX) ? z_index : -1;
		while (slice == null) {
			GridDatatype gridDataType = splitGridDataType.get(t_index);
			try {
				if (currentArrayPosition == -1 || splitGridDataType.get(t_index) != splitGridDataType.get(currentArrayPosition)) {
					LOGGER.debug("Requesting next chunk of data from server");
					currentArrayPosition = t_index;
					currentRequestData = gridDataType.readDataSlice(tPassthru, zPassthru, -1, -1);
				}
				switch(gridType) {
					case TYX:
						slice = currentRequestData.slice(0, t_index % splitSize);
						break;
					case TZYX:
						slice = currentRequestData.slice(0, t_index % splitSize);
						slice = slice.slice(0, z_index);
						break;
					case ZYX:
						slice = currentRequestData.slice(0, z_index);
						break;
					default:
						slice = currentRequestData;
				}
			} catch (IOException e) {
				if (failures++ < 3) {
					LOGGER.warn("Error reading slice [t={}, z={}] from {}: failure {}, reattempting.  Exception was {}",
							new Object[] {t_index, z_index, gridDataType.getDescription(), failures, e});
				} else {
					LOGGER.error("Unable to read slice [t={}, z={}] from {} after {} failures. Exception was {}",
							new Object[] {t_index, z_index, gridDataType.getDescription(), failures, e});
					throw e;
				}
			}
		}
		return slice;
	}
	
	private static int calculateSplitSize(GridDatatype gridDataType) {
		GridCoordSystem gridCoordSystem = gridDataType.getCoordinateSystem();
		CoordinateAxis zAxis = gridCoordSystem.getVerticalAxis();

		// storing as long to avoid overflow of int in calculation
		long xCellCount = GridUtility.getXAxisLength(gridCoordSystem);
		long yCellCount = GridUtility.getYAxisLength(gridCoordSystem);
		long zCellCount = (zAxis == null) ? 1l : zAxis.getShape(0);
		
		long maxSplit = Long.valueOf(AppConstant.MAX_DATA_CHUCK_REQUEST_SIZE.getValue());
		long singleTimestep = (long)gridDataType.getDataType().getSize() * xCellCount * yCellCount * zCellCount;
		int numTimesteps = (int)Math.floorDiv(maxSplit, singleTimestep);
		return numTimesteps;
	}

	private static Map<Integer, GridDatatype> splitGridDatatype(GridDatatype gridDataType, int splitSize) {
		Map<Integer, GridDatatype> splits = new HashMap<>();
		
		splits.put(INVALID_INDEX, gridDataType);
		
		GridCoordSystem gridCoordSystem = gridDataType.getCoordinateSystem();
		CoordinateAxis1DTime tAxis = gridCoordSystem.getTimeAxis1D();
		long tCellCount = (tAxis == null) ? 0 : tAxis.getShape(0);
		
		for (int i = 0; i < tCellCount; i += splitSize) {
			GridDatatype split;
			try {
				int maxRange = (i + splitSize < tCellCount) ? i + splitSize : (int)tCellCount - 1;
				Range timeRange = new Range(i, maxRange);
				split = gridDataType.makeSubset(null, null, timeRange, null, null, null);
			} catch (InvalidRangeException ex) {
				throw new RuntimeException("Data range is invalid", ex);
			}
			for (int j = i; j < i + splitSize && j < tCellCount; j++) {
				splits.put(j, split);
			}
		}
		return splits;
	}
}
