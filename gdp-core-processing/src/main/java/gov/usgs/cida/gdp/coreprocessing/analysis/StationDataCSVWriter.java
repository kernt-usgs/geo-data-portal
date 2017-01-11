package gov.usgs.cida.gdp.coreprocessing.analysis;

import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import org.geotools.data.crs.ReprojectFeatureResults;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.coverage.grid.InvalidRangeException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.ma2.StructureData;
import ucar.nc2.VariableSimpleIF;
import ucar.nc2.ft.StationTimeSeriesFeatureCollection;
import ucar.nc2.ft.point.StationFeature;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarDateRange;
import ucar.unidata.geoloc.LatLonRect;
import ucar.unidata.geoloc.Station;

/**
 *
 * @author tkunicki
 */
public class StationDataCSVWriter {

    private final static Logger log = LoggerFactory.getLogger(StationDataCSVWriter.class);

    public final static String DATE_FORMAT = "yyyy-MM-dd";

    public final static GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public static boolean write(
            FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection,
            StationTimeSeriesFeatureCollection stationTimeSeriesFeatureCollection,
            List<VariableSimpleIF> variableList,
            CalendarDateRange dateRange,
            BufferedWriter writer,
            boolean groupByVariable,
            String delimitter)
            throws IOException, InvalidRangeException, TransformException, FactoryException, SchemaException {

        Preconditions.checkNotNull(featureCollection, "featureCollection may not be null");
        Preconditions.checkNotNull(stationTimeSeriesFeatureCollection, "stationTimeSeriesFeatureCollection may not be null");
        Preconditions.checkNotNull(variableList, "variableList may not be null");
        Preconditions.checkNotNull(dateRange, "dateRange may not be null");
        Preconditions.checkNotNull(writer, "writer may not be null");

        if (delimitter == null || delimitter.length() == 0) {
            delimitter = ",";
        }

        TimeZone timeZone = TimeZone.getTimeZone("UTC");

        CalendarDate start = CalendarDate.parseISOformat(ucar.nc2.time.Calendar.gregorian.toString(), dateRange.getStart().toString());
        CalendarDate end = CalendarDate.parseISOformat(ucar.nc2.time.Calendar.gregorian.toString(), dateRange.getEnd().toString());

        LatLonRect featureCollectionLLR = GeoToolsNetCDFUtility.getLatLonRectFromEnvelope(
                featureCollection.getBounds(),
                DefaultGeographicCRS.WGS84);
        LatLonRect stationLLR = stationTimeSeriesFeatureCollection.getBoundingBox();
        if (stationLLR.containedIn(featureCollectionLLR)) {
            throw new RuntimeException("feature bounds (" + featureCollectionLLR + ") not contained in station time series dataset (" + stationLLR + ")");
        }

        featureCollection = new ReprojectFeatureResults(
                featureCollection,
                DefaultGeographicCRS.WGS84);

        List<PointFeatureCache> pointFeatureCacheList = new ArrayList<PointFeatureCache>();
        for (Station station : stationTimeSeriesFeatureCollection.getStationFeatures(featureCollectionLLR)) {
            Coordinate stationCoordinate = new Coordinate(station.getLongitude(), station.getLatitude());
            Geometry stationGeometry = GEOMETRY_FACTORY.createPoint(stationCoordinate);
            boolean stationContained = false;
            FeatureIterator<SimpleFeature> featureIterator = featureCollection.features();

            try {
                while (featureIterator.hasNext() && !stationContained) {
                    stationContained = ((Geometry) featureIterator.next().getDefaultGeometry()).contains(stationGeometry);
                }
            } finally {
                featureCollection.close(featureIterator);
            }
            if (stationContained) {
                List<String> stationNames = Arrays.asList(station.getName()); // single name in list
                List<StationFeature> stsfc = stationTimeSeriesFeatureCollection.subset(stationLLR, dateRange).getStationFeatures(stationNames);
                PointFeatureCache pointFeatureCache
                        = new PointFeatureCache(stsfc, variableList);
                if (pointFeatureCache.getFeatureCount() > 0) {
                    pointFeatureCacheList.add(pointFeatureCache);
                } else {
                    pointFeatureCache.finish();
                }
            }
        }

        try {

            int stationCount = pointFeatureCacheList.size();
            int variableCount = variableList.size();

            StringBuilder lineBuffer = new StringBuilder();
            // HEADER LINE 1
            if (groupByVariable) {
                for (int variableIndex = 0; variableIndex < variableCount; ++variableIndex) {
                    for (int stationIndex = 0; stationIndex < stationCount; ++stationIndex) {
                        lineBuffer.append(delimitter).append(pointFeatureCacheList.get(stationIndex).getStation().getName());
                    }
                }
            } else {
                for (int stationIndex = 0; stationIndex < stationCount; ++stationIndex) {
                    for (int variableIndex = 0; variableIndex < variableCount; ++variableIndex) {
                        lineBuffer.append(delimitter).append(pointFeatureCacheList.get(stationIndex).getStation().getName());
                    }
                }
            }
            writer.write(lineBuffer.toString());
            writer.newLine();

            // HEADER LINE 2
            lineBuffer.setLength(0);
            if (groupByVariable) {
                for (int variableIndex = 0; variableIndex < variableCount; ++variableIndex) {
                    for (int stationIndex = 0; stationIndex < stationCount; ++stationIndex) {
                        VariableSimpleIF variable = variableList.get(variableIndex);
                        String name = variable.getShortName();
                        String units = variable.getUnitsString();
                        lineBuffer.append(delimitter).append(name);
                        if (units != null && units.length() > 0) {
                            lineBuffer.append(" (").append(units).append(")");
                        }
                    }
                }
            } else {
                for (int stationIndex = 0; stationIndex < stationCount; ++stationIndex) {
                    for (int variableIndex = 0; variableIndex < variableCount; ++variableIndex) {
                        VariableSimpleIF variable = variableList.get(variableIndex);
                        String name = variable.getShortName();
                        String units = variable.getUnitsString();
                        lineBuffer.append(delimitter).append(name);
                        if (units != null && units.length() > 0) {
                            lineBuffer.append(" (").append(units).append(")");
                        }
                    }
                }
            }
            writer.write(lineBuffer.toString());
            writer.newLine();

            DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
            dateFormat.setTimeZone(timeZone);
            GregorianCalendar current = new GregorianCalendar(timeZone);
            current.setTime(start.toDate());

            if (groupByVariable) {
                float[][] rowData = new float[stationCount][];
                while (current.before(end)) {
                    lineBuffer.setLength(0);
                    lineBuffer.append(dateFormat.format(current.getTime()));
                    for (int stationIndex = 0; stationIndex < stationCount; ++stationIndex) {
                        rowData[stationIndex] = pointFeatureCacheList.get(stationIndex).getFeatureForDate(current.getTime());
                    }
                    for (int variableIndex = 0; variableIndex < variableCount; ++variableIndex) {
                        for (int stationIndex = 0; stationIndex < stationCount; ++stationIndex) {
                            float variableValue = rowData[stationIndex] != null
                                    ? rowData[stationIndex][variableIndex]
                                    : Float.NaN;
                            lineBuffer.append(delimitter).append(variableValue);
                        }
                    }
                    writer.write(lineBuffer.toString());
                    writer.newLine();
                    current.add(Calendar.DATE, 1);
                }
            } else {
                float[][] rowData = new float[stationCount][];
                while (current.before(end)) {
                    lineBuffer.setLength(0);
                    lineBuffer.append(dateFormat.format(current.getTime()));
                    for (int stationIndex = 0; stationIndex < stationCount; ++stationIndex) {
                        rowData[stationIndex] = pointFeatureCacheList.get(stationIndex).getFeatureForDate(current.getTime());
                    }
                    for (int stationIndex = 0; stationIndex < stationCount; ++stationIndex) {
                        for (int variableIndex = 0; variableIndex < variableCount; ++variableIndex) {
                            float variableValue = rowData[stationIndex] != null
                                    ? rowData[stationIndex][variableIndex]
                                    : Float.NaN;
                            lineBuffer.append(delimitter).append(variableValue);
                        }
                    }
                    writer.write(lineBuffer.toString());
                    writer.newLine();
                    current.add(Calendar.DATE, 1);
                }
            }
        } finally {
            if (pointFeatureCacheList != null) {
                for (PointFeatureCache cache : pointFeatureCacheList) {
                    if (cache != null) {
                        cache.finish();
                    }
                }
            }
        }

        return true;
    }

    private static class PointFeatureCache {

        public final static long MILLIS_PER_DAY = 1000 * 60 * 60 * 24;
        public final static int BUFFER_SIZE = 16 << 10;

        private Station station;

        private int featureCount;
        private int variableCount;

        private File cacheFile;
        private DataInputStream cacheInputStream;

        long nextTimeMillis;

        public PointFeatureCache(List<StationFeature> stationTimeSeriesFeature, List<VariableSimpleIF> variables) throws IOException {

            //this.station = stationTimeSeriesFeature;

            variableCount = variables.size();
            try {

                    cacheFile = File.createTempFile("tmp.", ".cache");

                    DataOutputStream cacheOutputStream = null;
                    try {
                        cacheOutputStream = new DataOutputStream(
                                new BufferedOutputStream(
                                        new FileOutputStream(cacheFile), BUFFER_SIZE));
                        for (StationFeature stf: stationTimeSeriesFeature) {
                            cacheOutputStream.writeLong(stf.hashCode()); // SCA - LOOK: Wrong, but what would be right?
                            //cacheOutputStream.writeLong(pf.getNominalTimeAsDate().getTime());
                            for (VariableSimpleIF variable : variables) {
                                String shortName = variable.getShortName();
                                cacheOutputStream.writeFloat(stf.getFeatureData().convertScalarFloat(shortName));
                            }
                            ++featureCount;
                        }
                    } finally {
                        if (cacheOutputStream != null) {
                            try {
                                cacheOutputStream.close();
                            } catch (IOException e) {
                                log.warn("Failed to close cacheOutputStream", e);
                            }
                        }
                    }

                    try {
                        if (featureCount > 0) {
                            cacheInputStream = new DataInputStream(
                                    new BufferedInputStream(
                                            new FileInputStream(cacheFile), BUFFER_SIZE));
                            nextTimeMillis = cacheInputStream.readLong();
                        }
                    } catch (IOException e) {
                        if (cacheInputStream != null) {
                            try {
                                cacheInputStream.close();
                            } catch (IOException ee) {
                                log.warn("Failed to close cacheInputStream", ee);
                            }
                        }
                        throw e;
                    }

            } finally {
                if (featureCount < 1 || cacheInputStream == null) {
                    if (cacheFile != null) {
                        cacheFile.delete();
                        cacheFile = null;
                    }
                    featureCount = 0;
                    nextTimeMillis = -1;
                }
            }
        }

        public Station getStation() {
            return station;
        }

        public int getFeatureCount() {
            return featureCount;
        }

        public float[] getFeatureForDate(Date date) throws IOException {
            float[] variableData = null;
            if (nextTimeMillis > 0) {
                long requesteTimedMillis = date.getTime();
                long delta = nextTimeMillis - requesteTimedMillis;
                if (delta < 0) {
                    delta = -delta;
                }
                if (delta < MILLIS_PER_DAY) {
                    variableData = new float[variableCount];
                    try {
                        for (int variableIndex = 0; variableIndex < variableCount; ++variableIndex) {
                            variableData[variableIndex] = cacheInputStream.readFloat();
                        }
                        nextTimeMillis = cacheInputStream.readLong();
                    } catch (EOFException e) {
                        log.warn("expected data points per variable, found EOF", e);
                        variableData = null;
                        nextTimeMillis = -1;
                    }
                }
            }
            return variableData;
        }

        public void finish() {
            if (cacheInputStream != null) {
                try {
                    cacheInputStream.close();
                } catch (IOException e) {
                    log.warn("failed to close cacheInputStream", e);
                }
                cacheInputStream = null;
            }
            if (cacheFile != null) {
                cacheFile.delete();
                cacheFile = null;
            }
            nextTimeMillis = -1;
        }
    }

}
