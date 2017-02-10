package gov.usgs.cida.gdp.dataaccess.helper;

import com.google.common.base.Preconditions;
import gov.usgs.cida.gdp.dataaccess.bean.DataTypeCollection;
import gov.usgs.cida.gdp.dataaccess.bean.Time;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.LoggerFactory;

import thredds.client.catalog.Access;
import thredds.client.catalog.Catalog;
import thredds.client.catalog.Dataset;
import thredds.client.catalog.ServiceType;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.VariableSimpleIF;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dt.grid.GeoGrid;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.ft.DsgFeatureCollection;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;
import ucar.nc2.ft.FeatureDatasetPoint;
import ucar.nc2.ft.StationTimeSeriesFeature;
import ucar.nc2.ft.StationTimeSeriesFeatureCollection;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.util.NamedObject;

public abstract class NetCDFUtility {
    // Private nullary ctor ensures non-instantiability.
    static org.slf4j.Logger log = LoggerFactory.getLogger(NetCDFUtility.class);

    private NetCDFUtility() {
    }

    /**
     * For every dataset discovered in a depth-first traversal of {@code catalog}, this method returns a handle to it
     * of type {@code serviceType}, if available.
     *
     * @param catalog       an object representing a THREDDS catalog.
     * @param serviceType   the type of service that the returned handles will use to access data.
     * @return  a list of dataset handles. The list will be empty if {@code catalog} or {@code serviceType} is null.
     */
    public static List<Access> getDatasetHandles(Catalog catalog, ServiceType serviceType) {
        if (catalog == null || serviceType == null) {
            return Collections.emptyList();     // Template parameter inferred from return type.
        }

        List<Access> handles = new LinkedList<Access>();
        for (Dataset dataset : catalog.getDatasets()) {
            List<Access> tmp = getDatasetHandles(dataset, serviceType);
            handles.addAll(tmp);
        }
        return handles;
    }

    /**
     * For every dataset discovered in a depth-first traversal of {@code dataset} and its nested datasets, this method
     * returns a handle to it of type {@code serviceType}, if available.
     *
     * @param dataset       a THREDDS dataset, which may have nested datasets.
     * @param serviceType   the type of service that the returned handles will use to access data.
     * @return  a list of dataset handles. The list will be empty if {@code dataset} or {@code serviceType} is null.
     */
    public static List<Access> getDatasetHandles(Dataset dataset, ServiceType serviceType) {
        if (dataset == null || serviceType == null) {
            return Collections.emptyList();     // Template parameter inferred from return type.
        }

        List<Access> handles = new LinkedList<Access>();
        List<Access> datasetAccess = dataset.getAccess();
        for (Access handle : datasetAccess) {
            if (handle.getService().getType() == serviceType) {
                handles.add(handle);
            }
        }

        List<Dataset> datasets = dataset.getDatasets();
        for (Dataset nestedDataset : datasets) {
            handles.addAll(getDatasetHandles(nestedDataset, serviceType));
        }

        return handles;
    }

    public static List<VariableSimpleIF> getDataVariableNames(String url) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL can't be null");
        }

        FeatureDataset dataset = null;

        List<VariableSimpleIF> variables;

        try {
            dataset = FeatureDatasetFactoryManager.open(null, url, null, new Formatter());
            variables = getDataVariableNames(dataset);
        } finally {
            if (dataset != null) {
                dataset.close();
            }
        }

        return variables;
    }

    public static List<VariableSimpleIF> getDataVariableNames(FeatureDataset dataset) throws IOException {

        List<VariableSimpleIF> variableList = null;

        switch (dataset.getFeatureType()) {
            case POINT:
            case PROFILE:
            case STATION:
            case STATION_PROFILE:
            case STATION_RADIAL:
            case TRAJECTORY:

                variableList = new ArrayList<VariableSimpleIF>();

                // Try Unidata Observation Dataset convention where observation
                // dimension is declared as global attribute...
                Attribute convAtt = dataset.findGlobalAttributeIgnoreCase("Conventions");
                if (convAtt != null && convAtt.isString()) {
                    String convName = convAtt.getStringValue();

                    //// Unidata Observation Dataset Convention
                    //   http://www.unidata.ucar.edu/software/netcdf-java/formats/UnidataObsConvention.html
                    if (convName.contains("Unidata Observation Dataset")) {
                        Attribute obsDimAtt = dataset.findGlobalAttributeIgnoreCase("observationDimension");
                        String obsDimName = (obsDimAtt != null && obsDimAtt.isString())
                                ? obsDimAtt.getStringValue() : null;
                        if (obsDimName != null && obsDimName.length() > 0) {
                            String psuedoRecordPrefix = obsDimName + '.';
                            for (VariableSimpleIF var : dataset.getDataVariables()) {
                                if (var.findAttributeIgnoreCase("_CoordinateAxisType") == null) {
                                    if (var.getName().startsWith(psuedoRecordPrefix)) {
                                        // doesn't appear to be documented, this
                                        // is observed behavior...
                                        variableList.add(var);
                                    } else {
                                        List<Dimension> dims = var.getDimensions();
                                        for (Dimension dim : dims) {
                                            if (obsDimName.equalsIgnoreCase(dim.getName())) {
                                                variableList.add(var);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (variableList.isEmpty()) {
                            // no explicit observation dimension found? look for
                            // variables with unlimited dimension
                            for (VariableSimpleIF var : dataset.getDataVariables()) {
                                List<Dimension> dims = var.getDimensions();
                                for (Dimension dim : dims) {
                                    if (dim.isUnlimited()) {
                                        variableList.add(var);
                                    }
                                }
                            }
                        }
                    }
                }

                //// CF Conventions
                //   https://cf-pcmdi.llnl.gov/trac/wiki/PointObservationConventions
                //
                //  Don't try explicit :Conventions attribute check since this
                //  doesnt seem to be coming through TDS with cdmremote when
                //  CF conventions are used (?!)
                if (variableList.isEmpty()) {
                    // Try CF convention where range variable has coordinate attribute
                    for (VariableSimpleIF variable : dataset.getDataVariables()) {
                        if (variable.findAttributeIgnoreCase("coordinates") != null) {
                            variableList.add(variable);
                        }
                    }
                }
                break;
            default:
                variableList = dataset.getDataVariables();
                break;
        }

        if (variableList == null) {
            variableList = Collections.emptyList();
        }
        return variableList;
    }

    public static DataTypeCollection getDataTypeCollection(String datasetUrl) throws IOException {

        if (datasetUrl == null) {
            throw new IllegalArgumentException("URL can't be null");
        }

        FeatureDataset dataset = null;

        String type;
        List<VariableSimpleIF> variables;

        try {
            dataset = FeatureDatasetFactoryManager.open(null, datasetUrl, null, new Formatter());

            type = dataset.getFeatureType().toString();
            variables = getDataVariableNames(dataset);
        } finally {
            if (dataset != null) {
                dataset.close();
            }
        }

        DataTypeCollection dtcb = new DataTypeCollection(type, variables.toArray(new VariableSimpleIF[0]));
        return dtcb;
    }

    public static boolean hasTimeCoordinate(String location) throws IOException {
        FeatureDataset featureDataset = null;
        boolean result = false;
        try {
            featureDataset = FeatureDatasetFactoryManager.open(null, location, null, new Formatter());
            result = hasTimeCoordinate(featureDataset);
        } finally {
            featureDataset.close();
        }
        return result;
    }

    public static boolean hasTimeCoordinate(FeatureDataset featureDataset) throws IOException {
//        boolean hasTime = false;
//        if (featureDataset.getFeatureType() == FeatureType.STATION) {
//            Iterator<VariableSimpleIF> variableIterator = featureDataset.getDataVariables().iterator();
//            while (!hasTime && variableIterator.hasNext()) {
//                VariableSimpleIF vairable = variableIterator.next();
//                Iterator<Attribute> attIterator = vairable.getAttributes().iterator();
//                while (!hasTime && attIterator.hasNext()) {
//                    Attribute att = attIterator.next();
//                    hasTime = "_CoordinateAxisType".equalsIgnoreCase(att.getName()) && "Time".equals(att.getStringValue());
//                }
//            }
//        }
//        return hasTime;
        return featureDataset.getFeatureType() == FeatureType.STATION;
    }

    /**
     * Retrieves a List of type String which has a date range from the beginning to the end of a FeatureDataSet
     * 
     * @param threddsURL URL for a THREDDS dataset
     * @param variableName name of a Grid or Station variable contained in that dataset
     * @return
     * @throws IOException
     * @throws IllegalArgumentException
     */
    public static List<String> getDateRange(String threddsURL, String variableName) throws IOException, IllegalArgumentException {
        Preconditions.checkNotNull(threddsURL, "location cannot be null");
        Preconditions.checkNotNull(variableName, "variable cannot be null");
        log.debug(new StringBuilder("Attempting to get date range from: ").append(threddsURL).append(" using grid or station ").append(variableName).toString());

        FeatureDataset dataset = FeatureDatasetFactoryManager.open(null, threddsURL, null, new Formatter());
        List<String> dateRange = new ArrayList<String>(2);
        try {
            if (dataset.getFeatureType() == FeatureType.GRID) {
                GeoGrid grid = ((GridDataset) dataset).findGridByName(variableName);
                if (grid == null) {
                    return dateRange;
                }
                List<NamedObject> times = grid.getTimes();
                if (times.isEmpty()) {
                    return dateRange;
                }

                NamedObject startTimeNamedObject = times.get(0);
                String startTime = startTimeNamedObject.getName();
                dateRange.add(0, startTime);

                NamedObject endTimeNamedObject = times.get(times.size() - 1);
                String endTime = endTimeNamedObject.getName();
                dateRange.add(1, endTime);
            } else if (dataset.getFeatureType() == FeatureType.STATION) {
                CalendarDateRange dr = dataset.getCalendarDateRange();

                if (dr == null) {
                    List<DsgFeatureCollection> list =
                            ((FeatureDatasetPoint) dataset).getPointFeatureCollectionList();
                    for (DsgFeatureCollection fc : list) {
                        if (fc instanceof StationTimeSeriesFeatureCollection) {
                            StationTimeSeriesFeatureCollection stsfc =
                                    (StationTimeSeriesFeatureCollection) fc;
                            while (dr == null && stsfc.hasNext()) {
                                StationTimeSeriesFeature stsf = stsfc.next();
                                dr = stsf.getCalendarDateRange();
                            }
                        }
                    }
                }
                if (dr != null) {
                    dateRange.set(0, dr.getStart().toString());
                    dateRange.set(1, dr.getEnd().toString());
                }
            }
        } finally {
            dataset.close();
        }
        log.trace(new StringBuilder("Attempt to get date range from: ").append(threddsURL).append(" successful. Date range").append(dateRange).toString());
        return dateRange;
    }

    public static Time getTimeBean(String location, String gridSelection) throws IOException, ParseException, IllegalArgumentException {
        List<String> dateRange = NetCDFUtility.getDateRange(location, gridSelection);
        if (dateRange.isEmpty()) {
            boolean hasTimeCoord = NetCDFUtility.hasTimeCoordinate(location);
            if (hasTimeCoord) { // This occurs when there is no date range in the file but has time coords
                // We want the user to pick dates but don't have a range to give
                dateRange.add("1800-01-01 00:00:00Z");
                dateRange.add("2100-12-31 00:00:00Z");
            }
        }
        Time result = new Time(dateRange.toArray(new String[0]));
        return result;
    }
}
