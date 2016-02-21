package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicException;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ucar.nc2.constants.FeatureType;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;

public class ResultSizeAlgorithmHeuristicTest {

	@Before
	public void setUp() throws Exception {


		FeatureDataset prismFeatureDataSet = FeatureDatasetFactoryManager.open(FeatureType.GRID,
				ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("nc/prism.nc").toString(),
				null, new Formatter(System.err));
		if (prismFeatureDataSet instanceof GridDataset) {
			prismGridDataSet = (GridDataset) prismFeatureDataSet;
		}

		FeatureDataset ssebopetaFeatureDataSet = FeatureDatasetFactoryManager.open(FeatureType.GRID,
				ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("nc/ssebopeta.nc").toString(),
				null, new Formatter(System.err));
		if (ssebopetaFeatureDataSet instanceof GridDataset) {
			ssebopetaGridDataSet = (GridDataset) ssebopetaFeatureDataSet;
		}

		coloradoFeatureCollection = FileDataStoreFinder.getDataStore(ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("shp/colorado/CONUS_States.shp"));
	}

	@After
	public void tearDown() {
		try {
			if (daymetGridDataSet != null) {
				daymetGridDataSet.close();
			}
		} catch (IOException ignore) {
		}

		try {
			if (prismGridDataSet != null) {
				prismGridDataSet.close();
			}
		} catch (IOException ignore) {
		}

		try {
			if (ssebopetaGridDataSet != null) {
				ssebopetaGridDataSet.close();
			}
		} catch (IOException ignore) {
		}
	}

	@Test
	public void validateTestPreconditions() {
		assertThat(daymetGridDataSet, is(notNullValue()));
		assertThat(prismGridDataSet, is(notNullValue()));
		assertThat(ssebopetaGridDataSet, is(notNullValue()));
	}

	@Test
	public void testResultSizeAlgorithmHeuristicConstructor() {
		CoverageSizeAlgorithmHeuristic resultSizeHeuristic = new CoverageSizeAlgorithmHeuristic();

		assert (resultSizeHeuristic.getError().equals(""));

		try {
			resultSizeHeuristic.validated();
			fail("AlgorithmHeuristic.validated() did not throw expected exception.");
		} catch (AlgorithmHeuristicException e) {
			assert (e.getExceptionid().value() == AlgorithmHeuristicExceptionID.UNINITIALIZED_EXCEPTION.value());
		}
	}

	@Test
	public void daymetSizeTest() {
		GridDataset daymetGridDataset;
		FeatureDataset daymetFeatureDataSet = FeatureDatasetFactoryManager.open(FeatureType.GRID,
				ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("nc/daymet.nc").toString(),
				null, new Formatter(System.err));
		if (daymetFeatureDataSet instanceof GridDataset) {
			daymetGridDataset = (GridDataset) daymetFeatureDataSet;
		}
		List<String> gridVariableList = Arrays.asList("prcp", "srad", "swe");

		SimpleFeatureCollection featureCollection = null;
		try {
			featureCollection = coloradoFeatureDataStore.getFeatureSource().getFeatures();
		} catch (IOException ignore) {
		}
		assertNotNull(featureCollection);

		DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
		Date startDate = null;
		try {
			startDate = format.parse("2006-10-01");
		} catch (ParseException e) {
		}
		assertNotNull(startDate);

		Date endDate = null;
		try {
			endDate = format.parse("2007-10-01");
		} catch (ParseException e) {
		}
		assertNotNull(endDate);

		CoverageSizeAlgorithmHeuristic resultSizeHeuristic = new CoverageSizeAlgorithmHeuristic(daymetGridDataSet,
				gridVariableList, featureCollection, startDate, endDate, true);

		/*
		 * Try default size of 500MB (should fail)
		 */
		try {
			if (!resultSizeHeuristic.validated()) {
				String error = resultSizeHeuristic.getError();

				assertTrue(error.equals("Estimated Data Size [1255251168 bytes] is greater than allowed maximum [524288000 bytes].  The following URI can be used with the nccopy tool to create a local copy of the data in the NetCDF4 format. See the Geo Data Portal documentation for more information: file:/Users/prusso/Development/Projects/GDP/git/geo-data-portal/gdp-process-wps/target/test-classes/ncml/daymet.ncml?time[0:1:365],y[0:1:466],x[0:1:611],lambert_conformal_conic,prcp[0:1:365][0:1:466][0:1:611],srad[0:1:365][0:1:466][0:1:611],swe[0:1:365][0:1:466][0:1:611]"));
			}
		} catch (AlgorithmHeuristicException e) {
			fail();
		}

		/*
		 * Try new size of 2GB (should succeed)
		 */
		resultSizeHeuristic.setMaximumSizeConfigured(2000000000);
		try {
			assertTrue(resultSizeHeuristic.validated());
		} catch (AlgorithmHeuristicException e) {
			fail();
		}
	}

	@Test
	public void prismSizeTest() {
		List<String> gridVariableList = Arrays.asList("ppt");

		SimpleFeatureCollection featureCollection = null;
		try {
			featureCollection = coloradoFeatureDataStore.getFeatureSource().getFeatures();
		} catch (IOException ignore) {
		}
		assertNotNull(featureCollection);

		DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
		Date startDate = null;
		try {
			startDate = format.parse("1895-01-01");
		} catch (ParseException e) {
		}
		assertNotNull(startDate);

		Date endDate = null;
		try {
			endDate = format.parse("2013-02-01");
		} catch (ParseException e) {
		}
		assertNotNull(endDate);

		CoverageSizeAlgorithmHeuristic resultSizeHeuristic = new CoverageSizeAlgorithmHeuristic(prismGridDataSet,
				gridVariableList, featureCollection, startDate, endDate, true);

		/*
		 * Try default size of 500MB (should succeed)
		 */
		try {
			assertTrue(resultSizeHeuristic.validated());
		} catch (AlgorithmHeuristicException e) {
			fail();
		}

		/*
		 * Try new size of 100MB (should fail)
		 */
		resultSizeHeuristic.setMaximumSizeConfigured(104857600);
		try {
			if (!resultSizeHeuristic.validated()) {
				String error = resultSizeHeuristic.getError();

				assertTrue(error.equals("Estimated Data Size [193982400 bytes] is greater than allowed maximum [104857600 bytes].  The following URI can be used with the nccopy tool to create a local copy of the data in the NetCDF4 format. See the Geo Data Portal documentation for more information: file:/Users/prusso/Development/Projects/GDP/git/geo-data-portal/gdp-process-wps/target/test-classes/ncml/prism.ncml?lon[0:1:170],time[0:1:1417],lat[0:1:99],ppt[0:1:1417][0:1:99][0:1:170]"));
			}
		} catch (AlgorithmHeuristicException e) {
			fail();
		}
	}

	@Test
	public void ssebopetaSizeTest() {
		List<String> gridVariableList = Arrays.asList("et");

		SimpleFeatureCollection featureCollection = null;
		try {
			featureCollection = coloradoFeatureDataStore.getFeatureSource().getFeatures();
		} catch (IOException ignore) {
		}
		assertNotNull(featureCollection);

		DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
		Date startDate = null;
		try {
			startDate = format.parse("2000-01-01");
		} catch (ParseException e) {
		}
		assertNotNull(startDate);

		Date endDate = null;
		try {
			endDate = format.parse("2014-12-01");
		} catch (ParseException e) {
		}
		assertNotNull(endDate);

		CoverageSizeAlgorithmHeuristic resultSizeHeuristic = new CoverageSizeAlgorithmHeuristic(ssebopetaGridDataSet,
				gridVariableList, featureCollection, startDate, endDate, true);

		/*
		 * Try default size of 500MB (should succeed)
		 */
		try {
			assertTrue(resultSizeHeuristic.validated());
		} catch (AlgorithmHeuristicException e) {
			fail();
		}

		/*
		 * Try new size of 100MB (should fail)
		 */
		resultSizeHeuristic.setMaximumSizeConfigured(104857600);
		try {
			if (!resultSizeHeuristic.validated()) {
				String error = resultSizeHeuristic.getError();

				assertTrue(error.equals("Estimated Data Size [126564120 bytes] is greater than allowed maximum [104857600 bytes].  The following URI can be used with the nccopy tool to create a local copy of the data in the NetCDF4 format. See the Geo Data Portal documentation for more information: file:/Users/prusso/Development/Projects/GDP/git/geo-data-portal/gdp-process-wps/target/test-classes/ncml/ssebopeta.ncml?time[0:1:179],lon[0:1:782],lat[0:1:448],crs,et[0:1:179][0:1:448][0:1:782]"));
			}
		} catch (AlgorithmHeuristicException e) {
			fail();
		}
	}

}
