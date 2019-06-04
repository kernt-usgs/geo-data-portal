package gov.usgs.cida.gdp.utilities;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import gov.usgs.cida.gdp.utilities.exception.GeoTiffUtilException;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;

public class GeoTiffUtilsTest {
    static GridDataset daymetGridDataSet;
    static GridDataset prismGridDataSet;
    static GridDataset ssebopetaGridDataSet;
    
    SimpleFeatureCollection featureCollection;
    
    @Before
    public void setUp() throws Exception {
        FeatureDataset daymetFeatureDataSet = FeatureDatasetFactoryManager.open(FeatureType.GRID,
                OPeNDAPUtilsTest.class.getClassLoader().getResource("netcdf/daymet.nc").toString(),
                null, new Formatter(System.err));
        if (daymetFeatureDataSet instanceof GridDataset) {
            daymetGridDataSet = (GridDataset) daymetFeatureDataSet;
        }
        
        FeatureDataset prismFeatureDataSet = FeatureDatasetFactoryManager.open(FeatureType.GRID,
                OPeNDAPUtilsTest.class.getClassLoader().getResource("netcdf/prism.nc").toString(),
                null, new Formatter(System.err));
        if (prismFeatureDataSet instanceof GridDataset) {
            prismGridDataSet = (GridDataset) prismFeatureDataSet;
        }
        
        FeatureDataset ssebopetaFeatureDataSet = FeatureDatasetFactoryManager.open(FeatureType.GRID,
                OPeNDAPUtilsTest.class.getClassLoader().getResource("netcdf/ssebopeta.nc").toString(),
                null, new Formatter(System.err));
        if (ssebopetaFeatureDataSet instanceof GridDataset) {
            ssebopetaGridDataSet = (GridDataset) ssebopetaFeatureDataSet;
        }
        
        URL featurePath =  GeoTiffUtilsTest.class.getClassLoader().getResource("shp/Colorado/CONUS_States.shp");
        FileDataStore featureStore = FileDataStoreFinder.getDataStore(featurePath);
        featureCollection = featureStore.getFeatureSource().getFeatures();
    }

    @After
    public void tearDown() {
        try {
            if (daymetGridDataSet != null) {
                daymetGridDataSet.close();
            }
        } catch (IOException ignore) {}
        
        try {
            if (prismGridDataSet != null) {
                prismGridDataSet.close();
            }
        } catch (IOException ignore) {}
        
        try {
            if (ssebopetaGridDataSet != null) {
                ssebopetaGridDataSet.close();
            }
        } catch (IOException ignore) {}
    }
    
    @Test
    public void validateTestPreconditions() {
        assertThat(daymetGridDataSet, is(notNullValue()));
        assertThat(prismGridDataSet, is(notNullValue()));
        assertThat(ssebopetaGridDataSet, is(notNullValue()));
    }
    
    @Test
    public void writeSsebopetaGridToGeoTiffTest() {
        List<String> gridVariableList = Arrays.asList("et");     // SSEBOPETA
        
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        Date startDate = null;
        try {
            startDate = format.parse("2000-01-01");   // SSEBOPETA
        } catch (ParseException e) {}
        assertNotNull(startDate);
        
        Date endDate = null;
        try {
            endDate = format.parse("2014-12-01");   // SSEBOPETA
        } catch (ParseException e) {}
        assertNotNull(endDate);
        
        File testFile = null;
        try {
            testFile = GeoTiffUtils.generateGeoTiffZipFromGrid(ssebopetaGridDataSet, gridVariableList, featureCollection, true, startDate, endDate, ".");
        } catch (GeoTiffUtilException e) {
            e.printStackTrace();
            fail();
        }
        
        assertTrue(testFile.exists());
        
        testFile.delete();
    }
    
    
    @Test
    public void writePrismGridToGeoTiffTest() {
        List<String> gridVariableList = Arrays.asList("ppt");   // PRISM SET
        
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        Date startDate = null;
        try {
            startDate = format.parse("2000-02-01");   // PRISM TEST SET
        } catch (ParseException e) {}
        assertNotNull(startDate);
        
        Date endDate = null;
        try {
            endDate = format.parse("2013-02-01");   // PRISM SET
        } catch (ParseException e) {}
        assertNotNull(endDate);
        
        File testFile = null;
        try {
            testFile = GeoTiffUtils.generateGeoTiffZipFromGrid(prismGridDataSet, gridVariableList, featureCollection, true, startDate, endDate, ".");
        } catch (GeoTiffUtilException e) {
            e.printStackTrace();
            fail();
        }
        
        assertTrue(testFile.exists());
        
        testFile.delete();
    }
    
    @Test
	public void writeDaymetGridToGeoTiffTest() {
        List<String> gridVariableList = Arrays.asList("prcp", "srad", "swe");   // DAYMET SET
        
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        Date startDate = null;
        try {
            startDate = format.parse("2006-10-01");   // DAYMET TEST SET
        } catch (ParseException e) {}
        assertNotNull(startDate);
        
        Date endDate = null;
        try {
            endDate = format.parse("2007-10-01");   // DAYMET TEST SET
        } catch (ParseException e) {}
        assertNotNull(endDate);
        
        File testFile = null;
        try {
            testFile = GeoTiffUtils.generateGeoTiffZipFromGrid(daymetGridDataSet, gridVariableList, featureCollection, false, startDate, endDate, ".");
            testFile.delete();
            fail();
        } catch (GeoTiffUtilException e) {
            assertTrue(e.getMessage().contains("Exception: Unsupported projection"));
        }
    }
}




