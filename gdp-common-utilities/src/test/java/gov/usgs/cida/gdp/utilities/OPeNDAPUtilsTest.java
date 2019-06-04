package gov.usgs.cida.gdp.utilities;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;
import gov.usgs.cida.gdp.utilities.exception.OPeNDAPUtilException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;

public class OPeNDAPUtilsTest {
    static GridDataset daymetGridDataSet;
    static GridDataset prismGridDataSet;
    static GridDataset ssebopetaGridDataSet;

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
    public void daymetRawDimensionNameTest() {
        String timeRangeString = "[9763:1:10128]";
        String xRangeString = "[1241:1:1852]";
        String yRangeString = "[1903:1:2369]";
       
        
        Map<String, String> dimensionRawMapping = OPeNDAPUtils.getDimensionRawNameRangeMapping(daymetGridDataSet.getNetcdfFile().getVariables(),
                xRangeString, yRangeString, timeRangeString);
        
        
        assertNotNull(dimensionRawMapping);
        assertThat(dimensionRawMapping.size(), is(equalTo(3)));
        
        // {time=[9763:1:10128], y=[1903:1:2369], x=[1241:1:1852]}
        assertTrue(dimensionRawMapping.containsKey("time"));
        assertTrue(dimensionRawMapping.get("time").equals(timeRangeString));
        assertTrue(dimensionRawMapping.containsKey("y"));
        assertTrue(dimensionRawMapping.get("y").equals(yRangeString));
        assertTrue(dimensionRawMapping.containsKey("x"));
        assertTrue(dimensionRawMapping.get("x").equals(xRangeString));
    }
    
    @Test
    public void daymetVariableDimensionNameTest() {
        String timeRangeString = "[9763:1:10128]";
        String xRangeString = "[1241:1:1852]";
        String yRangeString = "[1903:1:2369]";
       
        
        Map<String, String> dimensionVariableMapping = OPeNDAPUtils.getDimensionVariableNameRangeMapping(daymetGridDataSet.getNetcdfFile().getVariables(),
                xRangeString, yRangeString, timeRangeString);
        
        
        assertNotNull(dimensionVariableMapping);
        assertThat(dimensionVariableMapping.size(), is(equalTo(3)));
        
        // {time=[9763:1:10128], y=[1903:1:2369], x=[1241:1:1852]}
        assertTrue(dimensionVariableMapping.containsKey("time"));
        assertTrue(dimensionVariableMapping.get("time").equals(timeRangeString));
        assertTrue(dimensionVariableMapping.containsKey("y"));
        assertTrue(dimensionVariableMapping.get("y").equals(yRangeString));
        assertTrue(dimensionVariableMapping.containsKey("x"));
        assertTrue(dimensionVariableMapping.get("x").equals(xRangeString));
    }
    
    @Test
    public void daymetOpendapUriTest() {
        String gridSetLocation = "dods://daymet.ornl.gov/thredds/dodsC/daymet-agg/daymet-agg.ncml";
        List<String> gridVariableList = Arrays.asList("prcp", "srad", "swe");
        
        Range timeRange = null;
        try {
            timeRange = new Range(9763, 10128);
        } catch (InvalidRangeException ignore) {}
        assertNotNull(timeRange);
        
        Range xLngRange = null;
        try {
            xLngRange = new Range(1241, 1852);
        } catch (InvalidRangeException ignore) {}
        assertNotNull(xLngRange);
        
        Range yLatRange = null;
        try {
            yLatRange = new Range(1903, 2369);
        } catch (InvalidRangeException ignore) {}
        assertNotNull(yLatRange);
        
        String openDapURL = null;
        try {
            openDapURL = OPeNDAPUtils.generateOpenDapURL(gridSetLocation, gridVariableList, daymetGridDataSet.getNetcdfFile().getVariables(), timeRange, yLatRange, xLngRange);
            
        } catch (OPeNDAPUtilException ignore) {}
        
        assertNotNull(openDapURL);    
        assertTrue(openDapURL.equals("http://daymet.ornl.gov/thredds/dodsC/daymet-agg/daymet-agg.ncml?x[1241:1:1852],y[1903:1:2369],time[9763:1:10128],lambert_conformal_conic,prcp[9763:1:10128][1903:1:2369][1241:1:1852],srad[9763:1:10128][1903:1:2369][1241:1:1852],swe[9763:1:10128][1903:1:2369][1241:1:1852]"));
    }
    
    @Test
    public void prismRawDimensionNameTest() {
        String timeRangeString = "[0:1:1417]";
        String xRangeString = "[382:1:552]";
        String yRangeString = "[213:1:312]";
       
        
        Map<String, String> dimensionRawMapping = OPeNDAPUtils.getDimensionRawNameRangeMapping(prismGridDataSet.getNetcdfFile().getVariables(),
                xRangeString, yRangeString, timeRangeString);
        
        
        assertNotNull(dimensionRawMapping);
        assertThat(dimensionRawMapping.size(), is(equalTo(3)));
        
        // {time=[0:1:1417], lon=[382:1:552], lat=[213:1:312]}
        assertTrue(dimensionRawMapping.containsKey("time"));
        assertTrue(dimensionRawMapping.get("time").equals(timeRangeString));
        assertTrue(dimensionRawMapping.containsKey("lat"));
        assertTrue(dimensionRawMapping.get("lat").equals(yRangeString));
        assertTrue(dimensionRawMapping.containsKey("lon"));
        assertTrue(dimensionRawMapping.get("lon").equals(xRangeString));
    }
    
    @Test
    public void prismVariableDimensionNameTest() {
        String timeRangeString = "[0:1:1417]";
        String xRangeString = "[382:1:552]";
        String yRangeString = "[213:1:312]";
       
        
        Map<String, String> dimensionVariableMapping = OPeNDAPUtils.getDimensionVariableNameRangeMapping(prismGridDataSet.getNetcdfFile().getVariables(),
                xRangeString, yRangeString, timeRangeString);
        
        
        assertNotNull(dimensionVariableMapping);
        assertThat(dimensionVariableMapping.size(), is(equalTo(3)));
        
        // {time=[0:1:1417], lon=[382:1:552], lat=[213:1:312]}
        assertTrue(dimensionVariableMapping.containsKey("time"));
        assertTrue(dimensionVariableMapping.get("time").equals(timeRangeString));
        assertTrue(dimensionVariableMapping.containsKey("lat"));
        assertTrue(dimensionVariableMapping.get("lat").equals(yRangeString));
        assertTrue(dimensionVariableMapping.containsKey("lon"));
        assertTrue(dimensionVariableMapping.get("lon").equals(xRangeString));
    }
    
    @Test
    public void prismOpendapUriTest() {
        String gridSetLocation = "dods://cida.usgs.gov/thredds/dodsC/prism";
        List<String> gridVariableList = Arrays.asList("ppt");
        
        Range timeRange = null;
        try {
            timeRange = new Range(0, 1417);
        } catch (InvalidRangeException ignore) {}
        assertNotNull(timeRange);
        
        Range xLngRange = null;
        try {
            xLngRange = new Range(382, 552);
        } catch (InvalidRangeException ignore) {}
        assertNotNull(xLngRange);
        
        Range yLatRange = null;
        try {
            yLatRange = new Range(213, 312);
        } catch (InvalidRangeException ignore) {}
        assertNotNull(yLatRange);
        
        String openDapURL = null;
        try {
            openDapURL = OPeNDAPUtils.generateOpenDapURL(gridSetLocation, gridVariableList, prismGridDataSet.getNetcdfFile().getVariables(), timeRange, yLatRange, xLngRange);
            
        } catch (OPeNDAPUtilException ignore) {}
        
        assertNotNull(openDapURL);
        assertTrue(openDapURL.equals("http://cida.usgs.gov/thredds/dodsC/prism?lon[382:1:552],time[0:1:1417],lat[213:1:312],ppt[0:1:1417][213:1:312][382:1:552]"));
    }
    
    @Test
    public void ssebopetaRawDimensionNameTest() {
        String timeRangeString = "[0:1:179]";
        String xRangeString = "[1881:1:2663]";
        String yRangeString = "[943:1:1391]";
       
        
        Map<String, String> dimensionRawMapping = OPeNDAPUtils.getDimensionRawNameRangeMapping(ssebopetaGridDataSet.getNetcdfFile().getVariables(),
                xRangeString, yRangeString, timeRangeString);
        
        
        assertNotNull(dimensionRawMapping);
        assertThat(dimensionRawMapping.size(), is(equalTo(3)));
        
        // {time=[9763:1:10128], y=[1903:1:2369], x=[1241:1:1852]}
        assertTrue(dimensionRawMapping.containsKey("time"));
        assertTrue(dimensionRawMapping.get("time").equals(timeRangeString));
        assertTrue(dimensionRawMapping.containsKey("y"));
        assertTrue(dimensionRawMapping.get("y").equals(yRangeString));
        assertTrue(dimensionRawMapping.containsKey("x"));
        assertTrue(dimensionRawMapping.get("x").equals(xRangeString));
    }
    
    @Test
    public void ssebopetaVariableDimensionNameTest() {
        String timeRangeString = "[0:1:179]";
        String xRangeString = "[1881:1:2663]";
        String yRangeString = "[943:1:1391]";
       
        
        Map<String, String> dimensionVariableMapping = OPeNDAPUtils.getDimensionVariableNameRangeMapping(ssebopetaGridDataSet.getNetcdfFile().getVariables(),
                xRangeString, yRangeString, timeRangeString);
        
        
        assertNotNull(dimensionVariableMapping);
        assertThat(dimensionVariableMapping.size(), is(equalTo(3)));
        
        // {time=[0:1:179], lon=[1881:1:2663], lat=[943:1:1391]}
        assertTrue(dimensionVariableMapping.containsKey("time"));
        assertTrue(dimensionVariableMapping.get("time").equals(timeRangeString));
        assertTrue(dimensionVariableMapping.containsKey("lat"));
        assertTrue(dimensionVariableMapping.get("lat").equals(yRangeString));
        assertTrue(dimensionVariableMapping.containsKey("lon"));
        assertTrue(dimensionVariableMapping.get("lon").equals(xRangeString));
    }
    
    @Test
    public void ssebopetaOpendapUriTest() {
        String gridSetLocation = "dods://cida.usgs.gov/thredds/dodsC/ssebopeta/monthly";
        List<String> gridVariableList = Arrays.asList("et");
        
        Range timeRange = null;
        try {
            timeRange = new Range(0, 179);
        } catch (InvalidRangeException ignore) {}
        assertNotNull(timeRange);
        
        Range xLngRange = null;
        try {
            xLngRange = new Range(1881, 2663);
        } catch (InvalidRangeException ignore) {}
        assertNotNull(xLngRange);
        
        Range yLatRange = null;
        try {
            yLatRange = new Range(943, 1391);
        } catch (InvalidRangeException ignore) {}
        assertNotNull(yLatRange);
        
        String openDapURL = null;
        try {
            openDapURL = OPeNDAPUtils.generateOpenDapURL(gridSetLocation, gridVariableList, ssebopetaGridDataSet.getNetcdfFile().getVariables(), timeRange, yLatRange, xLngRange);
            
        } catch (OPeNDAPUtilException ignore) {}
        
        assertNotNull(openDapURL);
        assertTrue(openDapURL.equals("http://cida.usgs.gov/thredds/dodsC/ssebopeta/monthly?lon[1881:1:2663],time[0:1:179],lat[943:1:1391],crs,et[0:1:179][943:1:1391][1881:1:2663]"));
    }

}
