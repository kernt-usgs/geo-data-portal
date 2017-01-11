package gov.usgs.cida.gdp.utilities;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.geotools.feature.FeatureCollection;

import gov.usgs.cida.gdp.utilities.exception.GeoTiffUtilException;
import gov.usgs.cida.gdp.utilities.exception.GeoTiffUtilExceptionID;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.geotiff.GeotiffWriter;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.time.CalendarDateFormatter;

public class GeoTiffUtils {
    static private org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GeoTiffUtils.class);
    
    public static final String GEOTIFF_DIRECTORY_PREFIX = "GeoTiff_";
    public static final String FILE_DATE_FORMAT = "yyyy-MM-dd-HH-mm-ss";
    public static final String FILE_DELIMETER = "-";
    public static final String FILE_TIFF_EXTENSION = ".tiff";
    public static final String FILE_ZIP_EXTENSION = ".zip";
    
	public static File generateGeoTiffZipFromGrid(GridDataset gridDataset, List<String> gridVariableList,
			FeatureCollection<?, ?> featureCollection, boolean requireFullCoverage, Date dateTimeStart,
			Date dateTimeEnd, String destination) throws GeoTiffUtilException {
		Path destinationDirectory = Paths.get(destination);
        
        CalendarDateFormatter dateFormatter = new CalendarDateFormatter(FILE_DATE_FORMAT);
        
        /*
         * Get the dataset ID and format it accordingly
         *      QUOTE (JIRA GDP-947)
         *          dataId should be what comes after dodsC in the OPeNDAP URI with '/'s replaced with '-'s
         */
        gridDataset.getLocation();
        String datasetURI = gridDataset.getLocation();
        String dataId = datasetURI.replace(OPeNDAPUtils.OPENDAP_PROTO, "");
        dataId = dataId.replace("//", ""); // remove protocal //
        dataId = dataId.replace("/", "-");
        
        /*
         * Create a temp directory for this specific request as we could get name collisions
         */
        Path workingDirectory;
        try {
            workingDirectory = Files.createTempDirectory(destinationDirectory, GEOTIFF_DIRECTORY_PREFIX);
        } catch (IOException e) {
            throw new GeoTiffUtilException(GeoTiffUtilExceptionID.GENERAL_EXCEPTION,
                    "GeoTiffUtils", "generateGeoTiffZipFromGrid", "Unable to create GeoTiff working " +
                    "directory at [" + destinationDirectory + "]  Exception: " + e.getMessage());
        }
        
        File result = null;
        String resultingZipFileName = "";
        try {
            for (String gridVariable : gridVariableList) {
                GridDatatype parentGridDataType;
                
                try {
                    /*
                     * A null pointer exception can be thrown here due to errors in the
                     * request such as incorrect dimensions (GridVariableList) associated
                     * with a dataset that does not contain them.
                     */
                	parentGridDataType = gridDataset.findGridDatatype(gridVariable);
                } catch (Exception e) {
                    throw new GeoTiffUtilException(GeoTiffUtilExceptionID.GENERAL_EXCEPTION,
                            "GeoTiffUtils", "generateGeoTiffZipFromGrid", "Unable to generate Grid Data Type " +
                            "for dataset [" + gridDataset.getLocation() + "] and variable [" + gridVariable +
                            "].  Exception: " + e.getMessage());
                }
                
                /*
                 * Create the time range object for using in both a grid data type subset (with the feature collection)
                 * as well as iteration of the geolocation
                 */
                Range timeRange = TimeRangeUtil.generateTimeRange(parentGridDataType, dateTimeStart, dateTimeEnd);
                
                /*
            	 * Grab the grid coordinate system
            	 */
            	GridCoordSystem gridCoordSystem = parentGridDataType.getCoordinateSystem();
                
            	/*
            	 * Create an XY range for the feature collection requested
            	 */
                Range[] xyRanges;
				try {
					xyRanges = GridUtils.getXYRangesFromBoundingBox(featureCollection.getBounds(), gridCoordSystem, requireFullCoverage);
				} catch (Exception e) {
					throw new GeoTiffUtilException(GeoTiffUtilExceptionID.GENERAL_EXCEPTION,
                            "GeoTiffUtils", "generateGeoTiffZipFromGrid", "Unable to generate XY Range set from Grid Data Type " +
                            "for dataset [" + gridDataset.getLocation() + "] and variable [" + gridVariable +
                            "].  Exception: " + e.getMessage());
				}
				
				/*
            	 * Now create a grid subset so we only get what the user requested with regards to geo locations
            	 */
                GridDatatype gridDataType;
				try {
					gridDataType = parentGridDataType.makeSubset(null, null, timeRange, null, xyRanges[1], xyRanges[0]);
				} catch (Exception e) {
					throw new GeoTiffUtilException(GeoTiffUtilExceptionID.GENERAL_EXCEPTION,
                            "GeoTiffUtils", "generateGeoTiffZipFromGrid", "Unable to generate Grid Subset " +
                            "for dataset [" + gridDataset.getLocation() + "] and variable [" + gridVariable +
                            "].  Exception: " + e.getMessage());
				}
                
                /*
                 * We introduced an issue here when we created subsets via the feature collection.
                 * 
                 * What happens is the original parentGridDataType is the full gridded set for this
                 * datastore with regards to time, x and y.  When we create the subset for the
                 * requested feature collect, the TIME is reset to index 0.
                 * 
                 * While the start time requested for the parentGridDataType set might be at index 9000 
                 * and the end time at index 9005, when we create the subset, the start time is set to
                 * index 0 and the end time is set to 5. 
                 */
                int delta = timeRange.last() - timeRange.first();
                Range deltaRange = null;
                try {
					deltaRange = new Range(0, delta);
				} catch (InvalidRangeException e) {
					throw new GeoTiffUtilException(GeoTiffUtilExceptionID.GENERAL_EXCEPTION,
                            "GeoTiffUtils", "generateGeoTiffZipFromGrid", "Unable to generate Subset time range " +
                            "for dataset [" + gridDataset.getLocation() + "] and variable [" + gridVariable +
                            "].  Exception: " + e.getMessage());
				}

                for (int tRange : deltaRange) {
                    /*
                     * Create the filename with the dataId and time of this iteration
                     *      QUOTE (JIRA GDP-947)
                     *          Each GeoTIFF file should be named with the time stamp converted to a string. 
                     *          As long as its an unambiguous string that someone can parse later, the format
                     *          isn't very important. It would be nice if it was a format that sorts nicely 
                     *          like dataId-YYYY-MM-DD-hh-mm-ss.tiff
                     */
                    CalendarDate date = TimeRangeUtil.getTimeFromRangeIndex(gridDataType, tRange);
                    String dateString = dateFormatter.toString(date);
                    String datasetName = dataId + FILE_DELIMETER + dateString;
                    String absoluteFilePath = workingDirectory + File.separator + datasetName + FILE_TIFF_EXTENSION;
            
                    GeoTiffUtils.createGeoTiffForGrid(gridDataset, gridDataType, tRange, absoluteFilePath);
            
                    LOGGER.debug("testGeoTiffWriter(): Successfully wrote tiff file at: " + absoluteFilePath);
                }
            }
            
            /*
             * Now zip the contents of this geotiff directory
             */
            resultingZipFileName = workingDirectory.toString() + FILE_ZIP_EXTENSION;
            try {
                FileHelper.zipDirectory(workingDirectory.toString(), resultingZipFileName);
                LOGGER.debug("GeoTiffUtils.generateGeoTiffZipFromGrid(): Directory [" + workingDirectory.toString() + "] successfully zipped.");
            } catch (Exception e) {
                throw new GeoTiffUtilException(GeoTiffUtilExceptionID.ZIP_EXCEPTION,
                        "GeoTiffUtils", "generateGeoTiffZipFromGrid", "Unable to zip GeoTiff generated files for directory [" +
                        workingDirectory.toString() + "].  Exception: " + e.getMessage());
            }
            
            result = new File(resultingZipFileName);
        } finally {
            /*
             * Now delete the temporary directory
             */
            try {
                FileUtils.deleteDirectory(workingDirectory.toFile());
            } catch (IOException e) {
                LOGGER.error("Unable to delete GeoTiff directory [" + workingDirectory.toString() + "].");
            }
        }
        
        /*
         * Final check to make sure that the zip file actually exists and then return the
         * File handle to the caller.
         */
        if (result == null || !result.exists()) {
            throw new GeoTiffUtilException(GeoTiffUtilExceptionID.GENERAL_EXCEPTION,
                    "GeoTiffUtils", "generateGeoTiffZipFromGrid", "Unable to find resulting GeoTiff zipped file " +
                    resultingZipFileName + "].");
        }
        
        return result;
    }
    
    public static void createGeoTiffForGrid(GridDataset gridDataset, GridDatatype grid, int timeIndex, String filename) throws GeoTiffUtilException {
        GeotiffWriter writer = null;
        try {
            Array data = grid.readDataSlice(timeIndex, 0, -1, -1);
            writer = new GeotiffWriter(filename);
            writer.writeGrid(gridDataset, grid, data, false);
        } catch (Exception e) {
            throw new GeoTiffUtilException(GeoTiffUtilExceptionID.GEOTIFFWRITER_EXCEPTION,
                    "GeoTiffUtils", "generateGeoTiffZipFromGrid", "Unable to generate Tiff image from grid [" +
                    gridDataset.getLocation() + "].  Exception: " + e.getMessage());
        } finally {
            if(writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    LOGGER.error("GeoTiffUtils.createGeoTiffForGrid() Exception: Unable to close GeotiffWriter.  Exception: " + e.getMessage());
                }
            }
        }
    }
}
