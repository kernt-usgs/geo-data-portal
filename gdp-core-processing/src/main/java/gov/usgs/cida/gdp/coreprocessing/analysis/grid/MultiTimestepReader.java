/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.usgs.cida.gdp.coreprocessing.analysis.grid;

import gov.usgs.cida.gdp.constants.AppConstant;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 *
 * @author jiwalker
 */
public class MultiTimestepReader {
		
	private static final Logger LOGGER = LoggerFactory.getLogger(MultiTimestepReader.class);
	
	private final Map<Long, GridDatatype> splitGridDataType;
	private final GridType gridType;
	private final int splitSize;
	
	private Long currentArrayPosition;
	private Array currentRequestData;
	
	public MultiTimestepReader(GridDatatype gridDataType) {
		this.gridType = GridType.findGridType(gridDataType);
		this.splitSize = calculateSplitSize(gridDataType);
		this.splitGridDataType = splitGridDatatype(gridDataType, splitSize);
		this.currentArrayPosition = -1l;
		this.currentRequestData = null;
	}

	public Array readDataSlice(int t_index, int z_index) throws java.io.IOException {
		int failures = 0;
		
		Array slice = null;
		while (slice == null) {
			GridDatatype gridDataType = splitGridDataType.get((long)t_index);
			try {
				if (currentArrayPosition == -1 || splitGridDataType.get((long)t_index) != splitGridDataType.get(currentArrayPosition)) {
					currentArrayPosition = (long)t_index;
					currentRequestData = gridDataType.readDataSlice(-1, -1, -1, -1);
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

		
		long xCellCount = GridUtility.getXAxisLength(gridCoordSystem);
		long yCellCount = GridUtility.getYAxisLength(gridCoordSystem);
		long zCellCount = (zAxis == null) ? 1l : zAxis.getShape(0);
		
		
		long maxSplit = Long.valueOf(AppConstant.MAX_DATA_CHUCK_REQUEST_SIZE.getValue());
		long singleTimestep = (long)gridDataType.getDataType().getSize() * xCellCount * yCellCount * zCellCount;
		int numTimesteps = (int)Math.floorDiv(maxSplit, singleTimestep);
		return numTimesteps;
	}

	private static Map<Long, GridDatatype> splitGridDatatype(GridDatatype gridDataType, int splitSize) {
		Map<Long, GridDatatype> splits = new HashMap<>();
		
		GridCoordSystem gridCoordSystem = gridDataType.getCoordinateSystem();
		CoordinateAxis1DTime tAxis = gridCoordSystem.getTimeAxis1D();
		long tCellCount = (tAxis == null) ? 0 : tAxis.getShape(0);
		

		for (int i = 0; i < tCellCount; i += splitSize) {
			GridDatatype split;
			try {
				Range timeRange = new Range(i, i + splitSize);
				split = gridDataType.makeSubset(null, null, timeRange, null, null, null);
			} catch (InvalidRangeException ex) {
				throw new RuntimeException("this shouldn't happen");
			}
			for (long j = i; j < i + splitSize; j++) {
				splits.put(j, split);
			}
		}
		return splits;
	}
}
