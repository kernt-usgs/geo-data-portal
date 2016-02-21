package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import org.geotools.feature.FeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridUtility;
import static gov.usgs.cida.gdp.wps.algorithm.heuristic.GeometrySizeAlgorithmHeuristic.SIDES_PER_GRID_CELL;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.dt.GridDatatype;

public class GeometrySizeAlgorithmHeuristic extends AlgorithmHeuristic {

	private static Logger log = LoggerFactory.getLogger(GeometrySizeAlgorithmHeuristic.class);

	public static final long DEFAULT_MAXIMUM_GRID_SIZE = 1024 * 1024 * 1024 * 2; // 2GB
	public static final int DEFAULT_FEATURE_SIZE_HEURISTIC = 100; // Very rough heuristic
	public static final int SIDES_PER_GRID_CELL = 4;

	private FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection;
	private boolean requireFullCoverage;
	private long maximumSizeConfigured;
	private int featureSizeHeuristic;

	public GeometrySizeAlgorithmHeuristic(FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection,
			boolean requireFullCoverage) {
		this(featureCollection, requireFullCoverage, DEFAULT_FEATURE_SIZE_HEURISTIC, DEFAULT_MAXIMUM_GRID_SIZE);
	}

	public GeometrySizeAlgorithmHeuristic(FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection,
			boolean requireFullCoverage, int featureSizeHeuristic, long maxSize) {
		this.featureCollection = featureCollection;
		this.requireFullCoverage = requireFullCoverage;
		this.featureSizeHeuristic = featureSizeHeuristic;
		this.maximumSizeConfigured = maxSize;
	}

	/*
	 * ((grid cells)*4 + (polygon nodes)) * (data type volume). If that exceeds 2GB its too big.
	 */
	@Override
	public void traverseStart(GridDatatype gridDatatype) {
		int xCellCount = -1;
		int yCellCount = -1;
		int dataTypeSize = -1;
		
		try {
			Range[] ranges = GridUtility.getXYRangesFromBoundingBox(
					featureCollection.getBounds(),
					gridDatatype.getCoordinateSystem(),
					requireFullCoverage);
			GridDatatype subset = gridDatatype.makeSubset(null, null, null, null, ranges[1], ranges[0]);
			xCellCount = subset.getXDimension().getLength();
			yCellCount = subset.getYDimension().getLength();
			dataTypeSize = subset.getVariable().getDataType().getSize();
		} catch (InvalidRangeException | FactoryException | TransformException ex) {
			log.debug("Error extracting heuristic info", ex);
			throw new RuntimeException(ex);
		}
		
		long gridSize = xCellCount * yCellCount;
		long gridCellEdges = gridSize * SIDES_PER_GRID_CELL;
		long gridMemoryUsage = gridSize * dataTypeSize;

		long featureMemoryUsage = featureCollection.size() * featureSizeHeuristic;
		
		long totalMemoryEstimate = gridCellEdges + gridMemoryUsage + featureMemoryUsage;

		if (totalMemoryEstimate >= maximumSizeConfigured) {
			throw new AlgorithmHeuristicException("One or more of the polygons in the submitted set is too large for the gridded dataset's resolution.");
		}
	}
}
