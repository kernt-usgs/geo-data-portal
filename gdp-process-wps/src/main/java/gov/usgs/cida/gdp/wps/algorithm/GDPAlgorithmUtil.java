package gov.usgs.cida.gdp.wps.algorithm;

import gov.usgs.cida.gdp.constants.AppConstant;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridUtility;
import gov.usgs.cida.gdp.wps.util.WCSUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import org.geotools.feature.FeatureCollection;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dataset.CoordinateAxis1DTime;
import ucar.nc2.dt.GridCoordSystem;
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
                        Range[] ranges = GridUtility.getXYRangesFromBoundingBox(featureBounds, gridDatatype.getCoordinateSystem(), requireFullCoverage);
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

    public static DataCube calculateDataCube(GridDatatype gridDatatype,
            FeatureCollection featureCollection,
            Date dateTimeStart, Date dateTimeEnd,
            boolean requireFullCoverage) {

        GridDatatype subset = null;
        Range timeRange = null;
        Range yRange = null;
        Range xRange = null;

        try {
            timeRange = GDPAlgorithmUtil.generateTimeRange(gridDatatype, dateTimeStart, dateTimeEnd);
            GridCoordSystem gridCoordSystem = gridDatatype.getCoordinateSystem();
            Range[] xyRanges = GridUtility.getXYRangesFromBoundingBox(featureCollection.getBounds(), gridCoordSystem, requireFullCoverage);
            yRange = new Range(xyRanges[1].first(), xyRanges[1].last());
            xRange = new Range(xyRanges[0].first(), xyRanges[0].last());
            subset = gridDatatype.makeSubset(null, null, timeRange, null, yRange, xRange);
        } catch (TransformException | FactoryException | InvalidRangeException e) {
            log.debug("User specified invalid request", e);
            // wrapping in Runtime to propogate to error handler, nothin we can do to recover
            throw new RuntimeException(e);
        }

        int xLength = subset.getXDimension().getLength();
        int yLength = subset.getYDimension().getLength();
        int tLength = subset.getTimeDimension().getLength();
        int dataTypeSize = gridDatatype.getDataType().getSize();

        DataCube info = new DataCube(xLength, yLength, tLength, dataTypeSize,
                xRange, yRange, timeRange);

        return info;
    }

    public static class DataCube {
        public final int xLength;
        public final int yLength;
        public final int tLength;
        public final int dataTypeSize;
        public final long totalSize;
        public final Range xRange;
        public final Range yRange;
        public final Range timeRange;

        public DataCube(int xLength, int yLength, int tLength, int dataTypeSize,
                Range xRange, Range yRange, Range timeRange) {
            this.xLength = xLength;
            this.yLength = yLength;
            this.tLength = tLength;
            this.dataTypeSize = dataTypeSize;
            this.totalSize = (long)xLength * (long)yLength * (long)tLength * (long)dataTypeSize;

            // just need these to pass through
            this.xRange = xRange;
            this.yRange = yRange;
            this.timeRange = timeRange;
        }
    }
}
