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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import ucar.nc2.constants.FeatureType;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;

public class ResultSizeAlgorithmHeuristicTest {

	private static GridDataset daymetGridDataset;
	private static GridDataset prismGridDataset;
	private static GridDataset ssebopetaGridDataset;
	private static FeatureCollection coloradoFeatureCollection;
	
	@BeforeClass
	public static void setUpAll() throws Exception {
		FeatureDataset daymetFeatureDataSet = FeatureDatasetFactoryManager.open(FeatureType.GRID,
				ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("nc/daymet.nc").toString(),
				null, new Formatter(System.err));
		if (daymetFeatureDataSet instanceof GridDataset) {
			daymetGridDataset = (GridDataset) daymetFeatureDataSet;
		}

		FeatureDataset prismFeatureDataSet = FeatureDatasetFactoryManager.open(FeatureType.GRID,
				ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("nc/prism.nc").toString(),
				null, new Formatter(System.err));
		if (prismFeatureDataSet instanceof GridDataset) {
			prismGridDataset = (GridDataset) prismFeatureDataSet;
		}

		FeatureDataset ssebopetaFeatureDataSet = FeatureDatasetFactoryManager.open(FeatureType.GRID,
				ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("nc/ssebopeta.nc").toString(),
				null, new Formatter(System.err));
		if (ssebopetaFeatureDataSet instanceof GridDataset) {
			ssebopetaGridDataset = (GridDataset) ssebopetaFeatureDataSet;
		}

		coloradoFeatureCollection = FileDataStoreFinder.getDataStore(
				ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("shp/colorado/CONUS_States.shp"))
				.getFeatureSource().getFeatures();
	}

	@AfterClass
	public static void tearDownAll() {
		try {
			if (daymetGridDataset != null) {
				daymetGridDataset.close();
			}
		} catch (IOException ignore) {
		}

		try {
			if (prismGridDataset != null) {
				prismGridDataset.close();
			}
		} catch (IOException ignore) {
		}

		try {
			if (ssebopetaGridDataset != null) {
				ssebopetaGridDataset.close();
			}
		} catch (IOException ignore) {
		}
	}

	@Test
	public void validateTestPreconditions() {
		assertThat(daymetGridDataset, is(notNullValue()));
		assertThat(prismGridDataset, is(notNullValue()));
		assertThat(ssebopetaGridDataset, is(notNullValue()));
	}

	@Test(expected = AlgorithmHeuristicException.class)
	public void daymetSizeTestFail() {
		List<String> gridVariableList = Arrays.asList("prcp", "srad", "swe");

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

		/*
		 * Try small size of 1MB (should fail)
		 */
		CoverageSizeAlgorithmHeuristic resultSizeHeuristic = new CoverageSizeAlgorithmHeuristic(daymetGridDataset, gridVariableList,
				coloradoFeatureCollection, startDate, endDate, true, 1024 * 1024);
		resultSizeHeuristic.traverseStart(daymetGridDataset.findGridDatatype(gridVariableList.get(0)));
	}
	
	@Test
	public void daymetSizeTestPass() {
		List<String> gridVariableList = Arrays.asList("prcp", "srad", "swe");

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

		/*
		 * Try new size of 2GB (should succeed)
		 */
		CoverageSizeAlgorithmHeuristic resultSizeHeuristic = new CoverageSizeAlgorithmHeuristic(daymetGridDataset, gridVariableList,
			coloradoFeatureCollection, startDate, endDate, true, 2000000000);
		resultSizeHeuristic.traverseStart(daymetGridDataset.findGridByName(gridVariableList.get(0)));
	}

	@Test(expected = AlgorithmHeuristicException.class)
	public void prismSizeTestFail() {
		List<String> gridVariableList = Arrays.asList("ppt");

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
		
		/*
		 * Try small size of 1MB (should succeed)
		 */
		CoverageSizeAlgorithmHeuristic resultSizeHeuristic = new CoverageSizeAlgorithmHeuristic(prismGridDataset, gridVariableList,
				coloradoFeatureCollection, startDate, endDate, true, 1024 * 1024);
		resultSizeHeuristic.traverseStart(prismGridDataset.findGridByName(gridVariableList.get(0)));
	}
	
	@Test
	public void prismSizeTestPass() {
		List<String> gridVariableList = Arrays.asList("ppt");

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
		
		/*
		 * Try new size of 100MB (should fail)
		 */
		CoverageSizeAlgorithmHeuristic resultSizeHeuristic = new CoverageSizeAlgorithmHeuristic(prismGridDataset, gridVariableList,
				coloradoFeatureCollection, startDate, endDate, true, 104857600);
		resultSizeHeuristic.traverseStart(prismGridDataset.findGridByName(gridVariableList.get(0)));
	}

	@Test
	public void ssebopetaSizeTestFail() {
		List<String> gridVariableList = Arrays.asList("et");

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


		/*
		 * Try default size of 500MB (should succeed)
		 */
		CoverageSizeAlgorithmHeuristic resultSizeHeuristic = new CoverageSizeAlgorithmHeuristic(ssebopetaGridDataset, gridVariableList,
				coloradoFeatureCollection, startDate, endDate, true);
		resultSizeHeuristic.traverseStart(ssebopetaGridDataset.findGridByName(gridVariableList.get(0)));
	}
	
	@Test
	public void ssebopetaSizeTestPass() {
		List<String> gridVariableList = Arrays.asList("et");

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


		/*
		 * Try new size of 100MB (should fail)
		 */
		CoverageSizeAlgorithmHeuristic resultSizeHeuristic = new CoverageSizeAlgorithmHeuristic(ssebopetaGridDataset, gridVariableList,
				coloradoFeatureCollection, startDate, endDate, true, 104857600);
		resultSizeHeuristic.traverseStart(ssebopetaGridDataset.findGridByName(gridVariableList.get(0)));
	}

}
