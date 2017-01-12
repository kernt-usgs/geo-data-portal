package gov.usgs.cida.gdp.wps.algorithm.heuristic;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.exception.AlgorithmHeuristicException;
import org.geotools.feature.FeatureCollection;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.ft.FeatureDataset;
import ucar.nc2.ft.FeatureDatasetFactoryManager;

public class IntersectionGeometrySizeAlgorithmHeuristicTest {

	private GridDataset prismGridDataSet;
	private FeatureCollection coloradoFeatureCollection;

	@Before
	public void setUp() throws Exception {
		FeatureType ft = FeatureType.GRID;
		String file = ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("nc/prism.nc").toString();
		Formatter formatter = new Formatter(System.err);
		FeatureDataset prismFeatureDataSet = FeatureDatasetFactoryManager.open(ft,
				file,
				null, formatter);
		if (prismFeatureDataSet instanceof GridDataset) {
			prismGridDataSet = (GridDataset) prismFeatureDataSet;
		}

		coloradoFeatureCollection = FileDataStoreFinder.getDataStore(
				ResultSizeAlgorithmHeuristicTest.class.getClassLoader().getResource("shp/colorado/CONUS_States.shp"))
				.getFeatureSource().getFeatures();
	}

	@After
	public void tearDown() {
		try {
			if (prismGridDataSet != null) {
				prismGridDataSet.close();
			}
		} catch (IOException ignore) {
		}
	}

	@Test
	public void validateTestPreconditions() {
		assertThat(prismGridDataSet, is(notNullValue()));
		assertThat(coloradoFeatureCollection, is(notNullValue()));
	}

	/**
	 * Try default size of 2GB (should succeed)
	 */
	@Test
	public void prismHeuristicPasses() {
		GeometrySizeAlgorithmHeuristic geometrySizeHeuristic = new GeometrySizeAlgorithmHeuristic(coloradoFeatureCollection, false);
		//geometrySizeHeuristic.traverseStart(prismGridDataSet.getGrids().get(0));
		// if no exception thrown this works
	}
	
	/**
	 * Try new size of 1MB (should fail)
	 */
	@Test(expected = AlgorithmHeuristicException.class)
	public void prismHeuriticFails() {
		GeometrySizeAlgorithmHeuristic geometrySizeHeuristic = new GeometrySizeAlgorithmHeuristic(coloradoFeatureCollection, false, 100, 1024);
		//geometrySizeHeuristic.traverseStart(prismGridDataSet.getGrids().get(0));
	}
}
