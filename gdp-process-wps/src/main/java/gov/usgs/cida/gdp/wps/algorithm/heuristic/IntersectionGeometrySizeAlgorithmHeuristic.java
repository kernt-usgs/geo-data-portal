package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import java.util.List;

import org.geotools.feature.FeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridCellCoverageFactory;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridCellCoverageFactory.GridCellCoverageByIndex;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridType;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridUtility;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicException;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicExceptionID;
import ucar.ma2.Range;
import ucar.nc2.dt.GridDatatype;

public class IntersectionGeometrySizeAlgorithmHeuristic implements AlgorithmHeuristic {
static private org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(IntersectionGeometrySizeAlgorithmHeuristic.class);
    
    public static final long MAXIMUM_GRID_SIZE = 2147483648L;     // 2GB
    public static final int GRID_MULTIPLIER = 4;
    private long maximumSizeConfigured = MAXIMUM_GRID_SIZE;
    
    private GridDatatype gridDataType;
    private FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection;
    private String attributeName;
    private boolean requireFullCoverage;
    private String result = "";
    private long resultingSize = 0;
    
    public IntersectionGeometrySizeAlgorithmHeuristic() {
        this.gridDataType = null;
        this.featureCollection = null;
        this.attributeName = "";
        this.requireFullCoverage = false;
    }
    
    public IntersectionGeometrySizeAlgorithmHeuristic(FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection,
    		String attributeName, boolean requireFullCoverage) {
        this.gridDataType = null;
        this.featureCollection = featureCollection;
        this.attributeName = attributeName;
        this.requireFullCoverage = requireFullCoverage;
    }
    
    public IntersectionGeometrySizeAlgorithmHeuristic(GridDatatype gridDataType, FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection,
    		String attributeName, boolean requireFullCoverage) {
        this.gridDataType = gridDataType;
        this.featureCollection = featureCollection;
        this.attributeName = attributeName;
        this.requireFullCoverage = requireFullCoverage;
    }

	@Override
	/*
	 * ((grid cells)*4 + (polygon nodes)) * (data type volume). If that exceeds 2GB its too big.
	 */
	public boolean validated() throws AlgorithmHeuristicException {
		isInitialized();
		
		GridType gt = GridType.findGridType(gridDataType.getCoordinateSystem());
        
        if( !(gt == GridType.ZYX || gt == GridType.TZYX || gt == GridType.YX || gt == GridType.TYX) ) {
        	this.result = "General Exception thrown";
            String msg = this.result + ":\nincompatible grid dimensions";
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.GENERAL_EXCEPTION, "IntersectionGeometrySizeAlgorithmHeuristic", "validated", msg);
        }

        Range[] ranges = null;
		try {
			ranges = GridUtility.getXYRangesFromBoundingBox(
			        featureCollection.getBounds(),
			        gridDataType.getCoordinateSystem(),
			        requireFullCoverage);
		} catch (Exception e) {
            this.result = "General Exception thrown";
            String msg = this.result + ":\n" + e.getMessage();
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.GENERAL_EXCEPTION, "IntersectionGeometrySizeAlgorithmHeuristic", "validated", msg);
        }
		
        try {
			gridDataType = gridDataType.makeSubset(null, null, null, null, ranges[1], ranges[0]);
		} catch (Exception e) {
            this.result = "General Exception thrown";
            String msg = this.result + ":\n" + e.getMessage();
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.GENERAL_EXCEPTION, "IntersectionGeometrySizeAlgorithmHeuristic", "validated", msg);
        }

        GridCellCoverageByIndex coverageByIndex = null;
		try {
			coverageByIndex = GridCellCoverageFactory.generateFeatureAttributeCoverageByIndex(
			    featureCollection,
			    attributeName,
			    gridDataType.getCoordinateSystem());
		} catch (Exception e) {
            this.result = "General Exception thrown";
            String msg = this.result + ":\n" + e.getMessage();
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.GENERAL_EXCEPTION, "IntersectionGeometrySizeAlgorithmHeuristic", "validated", msg);
        }

        List<Object> attributeList = coverageByIndex.getAttributeValueList();
        
        /*
         * (((X cell count) * (Y cell count)) * GRID_MULTIPLIER + attributeList.size()) * gridDataType.getVariable().getDataType().getSize()
         */
        int xCellCount = gridDataType.getXDimension().getLength();
        int yCellCount = gridDataType.getYDimension().getLength();
        int nodeSize = attributeList.size();
        int dataTypeSize = gridDataType.getVariable().getDataType().getSize();
        
        long gridSize = xCellCount * yCellCount;
        
        resultingSize = ((gridSize * GRID_MULTIPLIER) + nodeSize) * dataTypeSize;
        
        if(resultingSize >= maximumSizeConfigured) {
        	this.result = "One or more of the polygons in the submitted set is too large for the gridded dataset's resolution.";
        	return false;
        }
		
		return true;
	}

    @Override
    public String getError() {
        return this.result;
    }
    
    public long getResultingSize() {
        return resultingSize;
    }
    
    public long getMaximumSizeConfigured() {
        return maximumSizeConfigured;
    }

    public void setMaximumSizeConfigured(long maximumSizeConfigured) {
        this.maximumSizeConfigured = maximumSizeConfigured;
    }

    public void setGridDataType(GridDatatype gridDataType) {
        this.gridDataType = gridDataType;
    }

    public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}

	public void setFeatureCollection(FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection) {
        this.featureCollection = featureCollection;
    }

    public void setRequireFullCoverage(boolean requireFullCoverage) {
        this.requireFullCoverage = requireFullCoverage;
    }

    private boolean isInitialized() throws AlgorithmHeuristicException {
        if (this.gridDataType == null) {
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.UNINITIALIZED_EXCEPTION, "IntersectionGeometrySizeAlgorithmHeuristic", "validated", "GridDataType has not been initialized!");
        }
        if (this.featureCollection == null) {
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.UNINITIALIZED_EXCEPTION, "IntersectionGeometrySizeAlgorithmHeuristic", "validated", "FeatureCollection has not been initialized!");
        }
        
        return true;
    }

}
