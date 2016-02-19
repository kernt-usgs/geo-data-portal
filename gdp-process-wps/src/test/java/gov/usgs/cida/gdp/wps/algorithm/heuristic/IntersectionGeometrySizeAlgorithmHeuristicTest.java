package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;

import gov.usgs.cida.gdp.wps.algorithm.GDPAlgorithmUtil;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicException;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;

public class IntersectionGeometrySizeAlgorithmHeuristicTest {
    static GridDataset prismGridDataSet;
    
    static FileDataStore coloradoFeatureDataStore;

    @Before
    public void setUp() throws Exception {        
        FeatureDataset prismFeatureDataSet = FeatureDatasetFactoryManager.open(FeatureType.GRID,
                ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("nc/prism.nc").toString(),
                null, new Formatter(System.err));
        if (prismFeatureDataSet instanceof GridDataset) {
            prismGridDataSet = (GridDataset) prismFeatureDataSet;
        }
        
        coloradoFeatureDataStore = FileDataStoreFinder.getDataStore(ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("shp/colorado/CONUS_States.shp"));
    }

    @After
    public void tearDown() {
        try {
            if (prismGridDataSet != null) {
                prismGridDataSet.close();
            }
        } catch (IOException ignore) {}
    }
    
    @Test
    public void validateTestPreconditions() {
        assertThat(prismGridDataSet, is(notNullValue()));
    }
    
    @Test
    public void prismSizeTest() {
        List<String> gridVariableList = Arrays.asList("ppt");
        
        SimpleFeatureCollection featureCollection = null;
        try {
            featureCollection = coloradoFeatureDataStore.getFeatureSource().getFeatures();
        } catch (IOException ignore) {}
        assertNotNull(featureCollection);
        
        GridDatatype heuristicGridDatatype = prismGridDataSet.findGridDatatype(gridVariableList.get(0));
        
        GeometrySizeAlgorithmHeuristic geometrySizeHeuristic = new GeometrySizeAlgorithmHeuristic(heuristicGridDatatype, 
                featureCollection, "STATE", false);
        
        /*
         * Try default size of 2GB (should succeed)
         */
        try {
            assertTrue(geometrySizeHeuristic.validated());            
        } catch (AlgorithmHeuristicException e) {
            fail();
        }
        
        /*
         * Try new size of 1MB (should fail)
         */
        geometrySizeHeuristic.setMaximumSizeConfigured(1024);
        try {
            if(!geometrySizeHeuristic.validated()) {
                String error = geometrySizeHeuristic.getError();
                
                assertTrue(error.equals("One or more of the polygons in the submitted set is too large for the gridded dataset's resolution."));
            }
        } catch (AlgorithmHeuristicException e) {
            fail();
        }
        
    }
}
