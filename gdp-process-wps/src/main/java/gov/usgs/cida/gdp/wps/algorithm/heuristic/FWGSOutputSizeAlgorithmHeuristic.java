package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import java.util.List;

import org.geotools.feature.FeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridCellCoverageFactory;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridType;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridUtility;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridCellCoverageFactory.GridCellCoverageByIndex;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicException;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicExceptionID;
import ucar.ma2.Range;
import ucar.nc2.dt.GridDatatype;

public class FWGSOutputSizeAlgorithmHeuristic implements AlgorithmHeuristic {
static private org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(FWGSOutputSizeAlgorithmHeuristic.class);
    
    public static final long MAXIMUM_OUTPUT_SIZE = 5368709120L;     // 5GB
    private long maximumSizeConfigured = MAXIMUM_OUTPUT_SIZE;
    
    private GridDatatype gridDataType;
    private FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection;
    private Range outputTimeRange; 
    private String attributeName;
    private boolean requireFullCoverage;
    private String result = "";
    private long resultingSize = 0;
    
    public FWGSOutputSizeAlgorithmHeuristic() {
        this.gridDataType = null;
        this.featureCollection = null;
        this.outputTimeRange = null;
        this.attributeName = "";
        this.requireFullCoverage = false;
    }
    
    public FWGSOutputSizeAlgorithmHeuristic(FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection,
    		Range timeRange, String attributeName, boolean requireFullCoverage) {
        this.gridDataType = null;
        this.featureCollection = featureCollection;
        this.outputTimeRange = timeRange;
        this.attributeName = attributeName;
        this.requireFullCoverage = requireFullCoverage;
    }
    
    public FWGSOutputSizeAlgorithmHeuristic(GridDatatype gridDataType, FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection,
    		Range timeRange, String attributeName, boolean requireFullCoverage) {
        this.gridDataType = gridDataType;
        this.featureCollection = featureCollection;
        this.outputTimeRange = timeRange;
        this.attributeName = attributeName;
        this.requireFullCoverage = requireFullCoverage;
    }

	@Override
	/*
	 * (number of features in the polygon geometry) * (the number of time steps in the intersecting grid) * (the volume of each ascii entry) adds up to more than 5GB it's too big
	 */
	public boolean validated() throws AlgorithmHeuristicException {
		isInitialized();
		
		GridType gt = GridType.findGridType(gridDataType.getCoordinateSystem());
        
        if( !(gt == GridType.ZYX || gt == GridType.TZYX || gt == GridType.YX || gt == GridType.TYX) ) {
        	this.result = "General Exception thrown";
            String msg = this.result + ":\nincompatible grid dimensions";
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.GENERAL_EXCEPTION, "FWGSOutputSizeAlgorithmHeuristic", "validated", msg);
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
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.GENERAL_EXCEPTION, "FWGSOutputSizeAlgorithmHeuristic", "validated", msg);
        }
		
        try {
			gridDataType = gridDataType.makeSubset(null, null, null, null, ranges[1], ranges[0]);
		} catch (Exception e) {
            this.result = "General Exception thrown";
            String msg = this.result + ":\n" + e.getMessage();
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.GENERAL_EXCEPTION, "FWGSOutputSizeAlgorithmHeuristic", "validated", msg);
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
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.GENERAL_EXCEPTION, "FWGSOutputSizeAlgorithmHeuristic", "validated", msg);
        }

        List<Object> attributeList = coverageByIndex.getAttributeValueList();
        
        /*
         * (number of features in the polygon geometry) * (the number of time steps in the intersecting grid) * (the volume of each ascii entry)
         */
        int featureSize = attributeList.size();
        int dataTypeSize = gridDataType.getVariable().getDataType().getSize();
        
        // THIS IS NOT WORKING DUE TO TIME RANGE == 1???
        resultingSize = featureSize * outputTimeRange.length() * dataTypeSize;
        
        
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

    public void setGridDataType(final GridDatatype gridDataType) {
        this.gridDataType = gridDataType;
    }

    public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}

	public void setFeatureCollection(final FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection) {
        this.featureCollection = featureCollection;
    }

    public void setOutputTimeRange(Range outputTimeRange) {
		this.outputTimeRange = outputTimeRange;
	}

	public void setRequireFullCoverage(boolean requireFullCoverage) {
        this.requireFullCoverage = requireFullCoverage;
    }

    private boolean isInitialized() throws AlgorithmHeuristicException {
        if (this.gridDataType == null) {
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.UNINITIALIZED_EXCEPTION, "FWGSOutputSizeAlgorithmHeuristic", "validated", "GridDataType has not been initialized!");
        }
        if (this.featureCollection == null) {
            throw new AlgorithmHeuristicException(AlgorithmHeuristicExceptionID.UNINITIALIZED_EXCEPTION, "FWGSOutputSizeAlgorithmHeuristic", "validated", "FeatureCollection has not been initialized!");
        }
        
        return true;
    }

}
