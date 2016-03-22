package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.filter.text.cql2.CQLException;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.usgs.cida.gdp.coreprocessing.Delimiter;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.FeatureCoverageWeightedGridStatisticsTest;


/**
 *
 * @author jiwalker
 */
public class FeatureTimeseriesStatisticsTest {

	private static final Logger log = LoggerFactory.getLogger(FeatureTimeseriesStatisticsTest.class);
	
	private static SimpleFeatureCollection featureCollection;

	@BeforeClass
	public static void setUpClass() throws IOException, CQLException {
		
		Map<String, Object> map = new HashMap<>();
		map.put("url", FeatureCoverageWeightedGridStatisticsTest.class.getClassLoader().getResource("Sample_files/huc08.shp"));
		DataStore store = DataStoreFinder.getDataStore(map);
		SimpleFeatureSource featureSource = store.getFeatureSource("huc08");
		featureCollection = featureSource.getFeatures();
	}

	/**
	 * Test of execute method, of class FeatureTimeseriesStatistics.
	 * Ignoring for now until I come up with a good way to abstract out SOS resource
	 * @throws java.io.IOException
	 */
	@Test
	@Ignore
	public void testExecute() throws IOException, Exception {
		
		String attributeName = "huc8";
		String variableName = "et";
		URI uri = new URI("http://cida-eros-wsdev.er.usgs.gov:8080/nwc/proxythredds/HUC08_data/HUC08_eta.nc");
		TimeseriesDataset dataset = new TimeseriesDataset(uri, variableName, new DateTime("2004-01-01"), new DateTime("2007-01-01"));
		List<StationTimeseriesVisitor> additionalVisitors = new LinkedList<>();
		Writer writer = new StringWriter();
		Delimiter delimiter = Delimiter.COMMA;
		FeatureTimeseriesStatistics.execute(featureCollection, attributeName, dataset, additionalVisitors, writer, delimiter);
		String output = writer.toString();
		dataset.close();
		assertThat(output.contains("04150405"), is(true));
	}

}
