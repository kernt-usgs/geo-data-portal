package gov.usgs.cida.gdp.wps.algorithm;

import gov.usgs.cida.gdp.constants.AppConstant;
import gov.usgs.cida.gdp.utilities.GridUtils;
import gov.usgs.cida.gdp.wps.util.WCSUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Formatter;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dataset.CoordinateAxis1DTime;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;

/**
 *
 * @author tkunicki
 */
public abstract class GDPAlgorithmUtil {

    private static final Logger log = LoggerFactory.getLogger(GDPAlgorithmUtil.class);

    private GDPAlgorithmUtil() { }

    public static GridDataset generateGridDataSet(URI datasetURI) {
        int tries = 0;
        GridDataset gridDataset = null;
        while (null == gridDataset) {
            try {
                FeatureDataset featureDataset = null;
                String featureDatasetScheme = datasetURI.getScheme();
                if ("dods".equals(featureDatasetScheme)) {
                    featureDataset = FeatureDatasetFactoryManager.open(
                            FeatureType.GRID,
                            datasetURI.toString(),
                            null,
                            new Formatter(System.err));
                    if (featureDataset instanceof GridDataset) {
                        gridDataset = (GridDataset) featureDataset;
                    } else {
                        throw new RuntimeException("Unable to open gridded dataset at " + datasetURI);
                    }
                } else {
                    throw new RuntimeException("Unable to open gridded dataset at " + datasetURI);
                }
            } catch (IOException ex) {
                if (tries++ < 3) {
                    log.warn("Caught exception trying to generate grid data set, retrying", ex);
                } else {
                    throw new RuntimeException(ex);
                }
            }
        }
        return gridDataset;
    }

    public static GridDatatype generateGridDataType(URI datasetURI, String datasetId, ReferencedEnvelope featureBounds, boolean requireFullCoverage) {
        int tries = 0;
        GridDatatype gridDatatype = null;
        while (null == gridDatatype) {
            try {
                FeatureDataset featureDataset = null;
                String featureDatasetScheme = datasetURI.getScheme();
                if ("dods".equals(featureDatasetScheme)) {
                    GridDataset gridDataSet = generateGridDataSet(datasetURI);
                    gridDatatype = gridDataSet.findGridDatatype(datasetId);
                    if (gridDatatype == null) {
                        throw new RuntimeException("Unable to open dataset at " + datasetURI + " with identifier " + datasetId);
                    }
                    try {
                        Range[] ranges = GridUtils.getXYRangesFromBoundingBox(featureBounds, gridDatatype.getCoordinateSystem(), requireFullCoverage);
                        gridDatatype = gridDatatype.makeSubset(
                            null,       /* runtime */
                            null,       /* ensemble */
                            null,       /* time */
                            null,       /* z */
                            ranges[1]   /* y */ ,
                            ranges[0]   /* x */);
                    } catch (InvalidRangeException ex) {
                        log.error("Error generating grid data type", ex);
                    } catch (TransformException ex) {
                        log.error("Error generating grid data type", ex);
                    } catch (FactoryException ex) {
                        log.error("Error generating grid data type", ex);
                    }
                } else if ("http".equals(featureDatasetScheme)) {
                    File tiffFile = WCSUtil.generateTIFFFile(datasetURI, datasetId, featureBounds, requireFullCoverage, AppConstant.WORK_LOCATION.getValue());
                    featureDataset = FeatureDatasetFactoryManager.open(
                            FeatureType.GRID,
                            tiffFile.getCanonicalPath(),
                            null,
                            new Formatter(System.err));
                    if (featureDataset instanceof GridDataset) {
                        gridDatatype = ((GridDataset) featureDataset).findGridDatatype("I0B0");
                        if (gridDatatype == null) {
                            throw new RuntimeException("Unable to open dataset at " + datasetURI + " with identifier " + datasetId);
                        }
                    } else {
                        throw new RuntimeException("Unable to open dataset at " + datasetURI + " with identifier " + datasetId);
                    }
                }
            } catch (IOException ex) {
                if (tries++ < 3) {
                    log.warn("Caught exception trying to generate grid data set, retrying", ex);
                } else {
                    throw new RuntimeException("Unable to open dataset at " + datasetURI + " with identifier " + datasetId, ex);
                }
            }
        }
        return gridDatatype;
    }

    public static Range generateTimeRange(GridDatatype GridDatatype, Date timeStart, Date timeEnd) {
        CoordinateAxis1DTime timeAxis = GridDatatype.getCoordinateSystem().getTimeAxis1D();
        Range timeRange = null;
        if (timeAxis != null) {
            int timeStartIndex = timeStart != null
                    ? timeAxis.findTimeIndexFromDate(timeStart)
                    : 0;
            int timeEndIndex = timeEnd != null
                    ? timeAxis.findTimeIndexFromDate(timeEnd)
                    : timeAxis.getShape(0) - 1;
            try {
                timeRange = new Range(timeStartIndex, timeEndIndex);
            } catch (InvalidRangeException e) {
                throw new RuntimeException("Unable to generate time range.", e);
            }
        }
        return timeRange;
    }

}
