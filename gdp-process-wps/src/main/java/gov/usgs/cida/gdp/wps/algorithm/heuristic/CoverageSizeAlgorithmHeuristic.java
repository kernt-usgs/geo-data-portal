package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import gov.usgs.cida.gdp.constants.AppConstant;
import gov.usgs.cida.gdp.utilities.GridUtils;
import gov.usgs.cida.gdp.utilities.OPeNDAPUtils;
import gov.usgs.cida.gdp.utilities.exception.OPeNDAPUtilException;
import gov.usgs.cida.gdp.wps.algorithm.GDPAlgorithmUtil;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicException;

import java.util.Date;
import java.util.List;
import org.apache.commons.io.FileUtils;

import org.geotools.feature.FeatureCollection;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.GridDatatype;

/**
 * This Heuristic provides a means in determining the resulting size of a NetCDF
 * request. Based on the dimensions, variables and filters requested, a NetCDF
 * result can be Gigabytes in size. This Heuristic will compute an estimate of
 * the resulting NetCDF result size and return true or false if it violates the
 * global response maximum size.
 * 
 * Currently assumes that all grid datatypes are the same. As this is a heuristic
 * this seems like a reasonable assumption.
 *
 * @author prusso
 * @author jiwalker
 *
 */
public class CoverageSizeAlgorithmHeuristic extends AlgorithmHeuristic {

	private static final Logger log = LoggerFactory.getLogger(CoverageSizeAlgorithmHeuristic.class);

	private static final int DEFAULT_DATATYPES_TO_ESTIMATE = 1;

	private GridDataset gridDataset;
	private List<String> gridVariableList;
	private FeatureCollection<?, ?> featureCollection;
	private Date dateTimeStart;
	private Date dateTimeEnd;
	private boolean requireFullCoverage;
	
	private long maximumSizeConfigured;
	private int dataTypesEstimated = 0;

	public CoverageSizeAlgorithmHeuristic(GridDataset gridDataset, List<String> gridVariableList, FeatureCollection<?, ?> featureCollection,
			Date dateTimeStart, Date dateTimeEnd, boolean requireFullCoverage) {
		this(gridDataset, gridVariableList, featureCollection, dateTimeStart, dateTimeEnd,
				requireFullCoverage, Long.parseLong(AppConstant.HEURISTIC_COVERAGE_OUTPUT_MAX.getValue()));
	}
	
	public CoverageSizeAlgorithmHeuristic(GridDataset gridDataset, List<String> gridVariableList, FeatureCollection<?, ?> featureCollection,
			Date dateTimeStart, Date dateTimeEnd, boolean requireFullCoverage, long maxSize) {
		this.gridDataset = gridDataset;
		this.gridVariableList = gridVariableList;
		this.featureCollection = featureCollection;
		this.dateTimeStart = dateTimeStart;
		this.dateTimeEnd = dateTimeEnd;
		this.requireFullCoverage = requireFullCoverage;
		this.maximumSizeConfigured = maxSize;
	}

	/**
	 * Throws exception if heuristic fails.
	 * @param gridDatatype 
	 */
	@Override
	public void traverseStart(GridDatatype gridDatatype) {
		if (dataTypesEstimated >= DEFAULT_DATATYPES_TO_ESTIMATE) {
			return;
		}
		dataTypesEstimated++;
		
		Range timeRange = null;
		Range yRange = null;
		Range xRange = null;

		int numVariables = gridVariableList.size();
		int dataTypeSize = gridDatatype.getDataType().getSize();
		String dataTypeName = gridDatatype.getDataType().name();
		
		GridDatatype subset = null;
		try {
			timeRange = GDPAlgorithmUtil.generateTimeRange(gridDatatype, dateTimeStart, dateTimeEnd);
			GridCoordSystem gridCoordSystem = gridDatatype.getCoordinateSystem();
			Range[] xyRanges = GridUtils.getXYRangesFromBoundingBox(featureCollection.getBounds(), gridCoordSystem, requireFullCoverage);
			yRange = new Range(xyRanges[1].first(), xyRanges[1].last());
			xRange = new Range(xyRanges[0].first(), xyRanges[0].last());
			subset = gridDatatype.makeSubset(null, null, timeRange, null, yRange, xRange);
		} catch (InvalidRangeException | TransformException | FactoryException e) {
			log.debug("User specified invalid request", e);
			// wrapping in Runtime to propogate to error handler, nothin we can do to recover
			throw new RuntimeException(e);
		}
		
		int xLength = subset.getXDimension().getLength();
		int yLength = subset.getYDimension().getLength();
		int tLength = subset.getTimeDimension().getLength();
		
		long totalSize = xLength * yLength * tLength * numVariables * dataTypeSize;
		log.debug("ResultSizeHeuristic on {}x{}x{} with {} variables of type {} has an estimated size of {} bytes",
				xLength, yLength, tLength, numVariables, dataTypeName, totalSize);

		if (totalSize >= maximumSizeConfigured) {
			StringBuilder message = new StringBuilder();
			message.append(String.format(
					"Estimated data size %s is greater than allowed maximum %s.",
					FileUtils.byteCountToDisplaySize(totalSize), FileUtils.byteCountToDisplaySize(maximumSizeConfigured)));
			
			/*
			 * Retrieve the OPeNDAP URL for this request
			 */
			try {
				String OPeNDAPurl = OPeNDAPUtils.generateOpenDapURL(gridDataset.getLocation(), gridVariableList, gridDataset.getNetcdfFile().getVariables(), timeRange, yRange, xRange);
				message.append("  The following URI can be used with the nccopy tool")
					.append("to create a local copy of the data in the NetCDF4 format. See the Geo Data Portal")
					.append("documentation for more information: ")
					.append(OPeNDAPurl);
			} catch (OPeNDAPUtilException e) {
				message.append(String.format("  Unable to generate OPeNDAP URI [%s]", e.getMessage()));
			}
			throw new AlgorithmHeuristicException(message.toString());
		}
	}
}
