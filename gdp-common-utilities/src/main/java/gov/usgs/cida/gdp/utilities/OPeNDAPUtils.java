package gov.usgs.cida.gdp.utilities;

import gov.usgs.cida.gdp.utilities.exception.OPeNDAPUtilException;
import gov.usgs.cida.gdp.utilities.exception.OPeNDAPUtilExceptionID;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ucar.ma2.Array;
import ucar.ma2.Range;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.nc2.dataset.VariableDS;

public class OPeNDAPUtils {
    public enum OPeNDAPContentType {
        ASCII, BINARY, NONE;

        public static OPeNDAPContentType getTypeFromString(String string) {
            if (string.equals("ASCII")) {
                return ASCII;
            }

            if (string.equals("BINARY")) {
                return BINARY;
            }

            return NONE;
        }

        public static String getStringFromType(OPeNDAPContentType type) {
            switch (type) {
                case ASCII: {
                    return "ASCII";
                }
    
                case BINARY: {
                    return "BINARY";
                }
    
                default: {
                    return "NONE";
                }
            }
        }

        public static String getContentTypeString(OPeNDAPContentType type) {
            switch (type) {
                case ASCII: {
                    return ".ascii";
                }
    
                case BINARY: {
                    return ".dods";
                }
    
                default: {
                    return "";
                }
            }
        }
    }

    public static final int RANGE_STRIDE = 1;
    public static final String PARAMETER_DECLARATOR = "?";
    public static final String RANGE_DELIMETER = ":";
    public static final String URI_DELIMETER = ",";
    public static final String OPENDAP_PROTO = "dods:";
    public static final String REQUEST_PROTO = "http:";
    public static final List<String> REQUIRED_ATTRIBUTES = Arrays
            .asList("grid_mapping");

    public static final String DESCRIPTION_HEADER = "Content-Description";

    /**
     * Will return an OPeNDAP URI based on the NetCDF information passed in.
     * 
     * @param datasetURI
     * @param requestedVariableList
     * @param gridVariableList
     * @param timeRange
     * @param yRange
     * @param xRange
     * @return
     * @throws OPeNDAPUtilException
     */
    public static String generateOpenDapURL(String datasetURI, List<String> requestedVariableList,
            List<?> gridVariableList, Range timeRange, Range yRange, Range xRange)
            throws OPeNDAPUtilException {
        return generateOpenDapURL(datasetURI, requestedVariableList,
                gridVariableList, timeRange, yRange, xRange,
                OPeNDAPContentType.NONE);
    }

    /**
     * Will return an OPeNDAP URI based on the NetCDF information passed in.
     * 
     * @param datasetURI
     * @param requestedVariableList
     * @param gridVariableList
     * @param timeRange
     * @param yRange
     * @param xRange
     * @param contentType
     * @return
     * @throws OPeNDAPUtilException
     */
    public static String generateOpenDapURL(String datasetURI, List<String> requestedVariableList,
            List<?> gridVariableList, Range timeRange, Range yRange, Range xRange,
            OPeNDAPContentType contentType) throws OPeNDAPUtilException {
        StringBuilder uriString = new StringBuilder();

        /*
         * Need Data Set URI (variable datasetURI) Example:
         * dods://cida.usgs.gov/thredds/dodsC/new_gmo
         * 
         * Need Set Variable List (variable datasetId) Example: [pr, tas,
         * tasmax, tasmin, wind]
         * 
         * Need Time Range Example: [0,22644]
         * 
         * Need Latitude Range Example: [0,221]
         * 
         * Need Longitude Range Example: [0,461]
         */

        /*
         * First, make sure we format the URI correctly. Get rid of the dods
         * protocol descriptor and replace with http.
         */
        String requestURL = datasetURI.replace(OPENDAP_PROTO, REQUEST_PROTO);
        uriString.append(requestURL);

        /*
         * Determine the response type for the data
         * 
         * Update 070115 Blodgett does not want a content type. Is inforcing an
         * OPeNDAP client URI. Keeping this in here in case we ever want to
         * offer a workable URL.
         */
        uriString.append(OPeNDAPContentType.getContentTypeString(contentType));

        // Append parameter declarator
        uriString.append(PARAMETER_DECLARATOR);

        /*
         * Create longitude range
         */
        String xRangeString = "[" + xRange.first() + RANGE_DELIMETER + RANGE_STRIDE + RANGE_DELIMETER + xRange.last() + "]";

        /*
         * Create latitude range
         */
        String yRangeString = "[" + yRange.first() + RANGE_DELIMETER + RANGE_STRIDE + RANGE_DELIMETER + yRange.last() + "]";

        /*
         * Create time range
         */
        String timeRangeString = "[" + timeRange.first() + RANGE_DELIMETER + RANGE_STRIDE + RANGE_DELIMETER + timeRange.last() + "]";

        /*
         * Now, retrieve the Dimension variable name->Range mapping from the
         * variable list
         */
        Map<String, String> dimensionVariableMapping = OPeNDAPUtils.getDimensionVariableNameRangeMapping(gridVariableList,
                        xRangeString, yRangeString, timeRangeString);

        /*
         * Loop through the dimensionMapping and place each on the URI string
         */
        for (Map.Entry<String, String> entry : dimensionVariableMapping
                .entrySet()) {
            String dimension = entry.getKey();
            String range = entry.getValue();
            uriString.append(dimension + range + URI_DELIMETER);
        }

        /*
         * We also need the raw dimension name to range mapping for our other
         * variables
         */
        Map<String, String> dimensionRawMapping = OPeNDAPUtils.getDimensionRawNameRangeMapping(gridVariableList,
                        xRangeString, yRangeString, timeRangeString);

        /*
         * Loop through the request variables and append them to the URL.
         * 
         * Variable ranges include all 3 dimension ranges:
         * variable[dim1][dim2][dim3] - must determine order!
         * 
         * 
         * 1. Loop through requestedVariableList 2. For each variable, look up
         * VariableDS in gridVariableList 3. If VariableDS has associated
         * variable, include that variable in URI string 4. Get dimensions for
         * variable and determine order 5. Include variable with dimensions in
         * URI string
         */
        List<String> attributeVariablesUsed = new ArrayList<String>();
        List<String> uriParameters = new ArrayList<String>();
        for (int i = 0; i < gridVariableList.size(); i++) {
            Object variable = gridVariableList.get(i);

            /*
             * First check to see if this is indeed a VariableDS object.
             */
            if (variable instanceof VariableDS) {
                StringBuilder varNameBuilder = new StringBuilder(((VariableDS) variable).getShortName());

                /*
                 * Now lets see if this variable name exists in the
                 * requestedVariableList
                 */
                if (requestedVariableList.contains(varNameBuilder.toString())) {
                    /*
                     * First, we have a variable that has been requested in the
                     * original post. Lets see if it has any dependent
                     * attributes associated with it.
                     */
                    for (Attribute attribute : ((VariableDS) variable).getAttributes()) {
                        if (REQUIRED_ATTRIBUTES.contains(attribute.getFullName())) {
                            Array values = attribute.getValues();

                            /*
                             * Lets loop through the attribute values. We only
                             * care about strings to put on the URI.
                             */
                            for (int j = 0; j < values.getSize(); j++) {
                                Object value = values.getObject(j);

                                if (value instanceof String) {
                                    String stringValue = (String) value;

                                    /*
                                     * Now lets check to see if we've seen this
                                     * attribute value. If we have, continue. If
                                     * not, we need to add it to our URI.
                                     */
                                    if ((!attributeVariablesUsed.contains(stringValue)) && (!stringValue.isEmpty())) {
                                        attributeVariablesUsed.add(stringValue);
                                        uriParameters.add(stringValue);
                                    }
                                }
                            }
                        }
                    }


                    /*
                     * We now need to figure out the order of the dimensions. As
                     * far as I can tell there is NO documentation that
                     * explicitly states how to retrieve the order. I can only
                     * go by the coincidence that the List returned by
                     * getDimensions() has so far been accurate in the order
                     * required. Again, nowhere is this order explicitly stated
                     * as THE order required.
                     */
                    for (Dimension dimension : ((VariableDS) variable).getDimensions()) {
                        /*
                         * The Dimension.getShortName() *should* match the
                         * dimension key we have in our dimensionMapping map.
                         */
                        String dimensionName = dimension.getShortName();
                        if (dimensionRawMapping.containsKey(dimensionName)) {
                            varNameBuilder.append(dimensionRawMapping.get(dimensionName));
                        } else {
                            /*
                             * We got an error... this variable's dimension does
                             * not exist in our mapping.
                             */
                            throw new OPeNDAPUtilException(OPeNDAPUtilExceptionID.NETCDF_UNKNOWN_DIMENSION_EXCEPTION,
                                    "OPeNDAPUtils", "generateOpenDapURL", "Variable dimension [" + dimensionName +
                                    "] has no corresponding dimension in mapping [" + dimensionRawMapping + "]");
                        }
                    }
                    
                    /*
                     * Append the main variable
                     */
                    if(varNameBuilder.length() > 0) {
                        uriParameters.add(varNameBuilder.toString());
                    }
                }
            }
        }
        
        /*
         * Lets add our parameter delimeters
         */
        for(int i = 0; i < uriParameters.size(); i++) {
            String param = uriParameters.get(i);
            uriString.append(param);
            
            if((i + 1) < uriParameters.size()) {
                uriString.append(URI_DELIMETER);
            }
        }

        return uriString.toString();
    }

    public static Map<String, String> getDimensionRawNameRangeMapping(List<?> gridVariableList,
            String xRangeString, String yRangeString, String timeRangeString) {
        Map<String, String> results = new HashMap<String, String>();

        for (int i = 0; i < gridVariableList.size(); i++) {
            Object variable = gridVariableList.get(i);

            /*
             * Check to see if this is a dimension. If so, which axis
             */
            if (variable instanceof CoordinateAxis1D) {
                switch (((CoordinateAxis1D) variable).getAxisType()) {
                    case GeoX:
                    case Lon: {
                        /*
                         * This is the x axis which corresponds to the Longitude or
                         * GeoX dimension.
                         * 
                         * Since we need the raw name, we need to get the actual
                         * dimension object.
                         * 
                         * If the dimension list for this variable is greater than 1
                         * then we have an issue and this is NOT a CordinateAxis1D
                         * variable (the test is for sanity's sake)
                         */
                        if (((CoordinateAxis1D) variable).getDimensions().size() == 1) {
                            Dimension dimension = ((CoordinateAxis1D) variable).getDimensions().get(0);
                            results.put(dimension.getShortName(), xRangeString);
                        }
                        break;
                    }
                    case GeoY:
                    case Lat: {
                        /*
                         * This is the y axis which corresponds to the Latitude or
                         * GeoY dimension.
                         * 
                         * Since we need the raw name, we need to get the actual
                         * dimension object.
                         * 
                         * If the dimension list for this variable is greater than 1
                         * then we have an issue and this is NOT a CordinateAxis1D
                         * variable (the test is for sanity's sake)
                         */
                        if (((CoordinateAxis1D) variable).getDimensions().size() == 1) {
                            Dimension dimension = ((CoordinateAxis1D) variable).getDimensions().get(0);
                            results.put(dimension.getShortName(), yRangeString);
                        }
                        break;
                    }
                    case Time: {
                        /*
                         * This is the time axis
                         * 
                         * Since we need the raw name, we need to get the actual
                         * dimension object.
                         * 
                         * If the dimension list for this variable is greater than 1
                         * then we have an issue and this is NOT a CordinateAxis1D
                         * variable (the test is for sanity's sake)
                         */
                        if (((CoordinateAxis1D) variable).getDimensions().size() == 1) {
                            Dimension dimension = ((CoordinateAxis1D) variable).getDimensions().get(0);
                            results.put(dimension.getShortName(), timeRangeString);
                        }
                        break;
                    }
                    default: {
                        // do nothing
                    }
                }
            }
        }

        return results;
    }

    public static Map<String, String> getDimensionVariableNameRangeMapping(
            List<?> gridVariableList, String xRangeString, String yRangeString,
            String timeRangeString) {
        Map<String, String> results = new HashMap<String, String>();

        for (int i = 0; i < gridVariableList.size(); i++) {
            Object variable = gridVariableList.get(i);

            /*
             * Check to see if this is a dimension. If so, which axis
             */
            if (variable instanceof CoordinateAxis1D) {
                String varName = ((VariableDS) variable).getShortName();

                switch (((CoordinateAxis1D) variable).getAxisType()) {
                    case GeoX:
                    case Lon: {
                        /*
                         * This is the x axis which corresponds to the Longitude or
                         * GeoX dimension.
                         */
                        results.put(varName, xRangeString);
                        break;
                    }
                    case GeoY:
                    case Lat: {
                        /*
                         * This is the y axis which corresponds to the Latitude or
                         * GeoY dimension.
                         */
                        results.put(varName, yRangeString);
                        break;
                    }
                    case Time: {
                        /*
                         * This is the time axis
                         */
                        results.put(varName, timeRangeString);
                        break;
                    }
                    default: {
                        // do nothing
                    }
                }
            }
        }

        return results;
    }

    public static boolean isOPeNDAP(URI uri) {
        boolean result = false;
        if (uri != null) {
            try {
                URI dodsInfo = new URI(uri.toString() + ".info");
                HttpURLConnection conn = HTTPUtils.openHttpConnection(dodsInfo.toURL(), "HEAD");
                Map<String, List<String>> headers = conn.getHeaderFields();
                if (headers.containsKey(DESCRIPTION_HEADER)) {
                    List<String> dods = headers.get(DESCRIPTION_HEADER);
                    if (dods.size() > 0) {
                        String description = dods.get(0);
                        if ("dods-info".equals(description) || "dods_info".equals(description)) {
                            result = true;
                        }
                    }
                }
            } catch (IOException | URISyntaxException ex) {
                throw new IllegalArgumentException("URI is not a valid dataset", ex);
            }
        }
        return result;
    }

}
