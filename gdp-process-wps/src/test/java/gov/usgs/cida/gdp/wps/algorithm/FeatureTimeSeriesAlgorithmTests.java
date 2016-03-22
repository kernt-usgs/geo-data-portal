package gov.usgs.cida.gdp.wps.algorithm;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.usgs.cida.gdp.coreprocessing.Delimiter;
import gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.StationTimeseriesVisitor;
import jdk.nashorn.internal.ir.annotations.Ignore;


/**
 *
 * @author jiwalker
 */
public class FeatureTimeSeriesAlgorithmTests {

	private static final Logger log = LoggerFactory.getLogger(FeatureTimeSeriesAlgorithmTests.class);
	
	private static SimpleFeatureCollection featureCollection;

	@BeforeClass
	public static void setUpClass() throws Exception {
		
		Map<String, Object> map = new HashMap<>();
		// this file had to be copied from the gdp-core-processing in order to populate feature collection
		map.put("url", FeatureTimeSeriesAlgorithmTests.class.getClassLoader().getResource("Sample_files/huc08.shp"));
		DataStore store = DataStoreFinder.getDataStore(map);
		SimpleFeatureSource featureSource = store.getFeatureSource("huc08");
		featureCollection = featureSource.getFeatures();
	}
	
	
	@Test
	@Ignore
	public void testProcess() throws Exception {
		
		FeatureTimeSeriesAlgorithm process = new FeatureTimeSeriesAlgorithm();
		
		List<StationTimeseriesVisitor> additionalVisitors = new LinkedList<>();
		process.setAdditionalVisitors(additionalVisitors);
		process.setFeatureAttributeName("huc8");
		process.setFeatureCollection(featureCollection);
		process.setDatasetURI(new URI("http://cida-eros-wsdev.er.usgs.gov:8080/nwc/proxythredds/HUC08_data/HUC08_eta.nc"));
		process.setObservedProperty("et");
		process.setTimeStart(new DateTime("2001-01-01").toDate());
		process.setTimeEnd(new DateTime("2007-01-01").toDate());
		process.setDelimiter(Delimiter.COMMA);
		process.setWorkPath("/tmp");
		
		process.process();
		
		try (ZipInputStream zip = new ZipInputStream(new FileInputStream(process.getOutput()))) {
			zip.getNextEntry();
			BufferedReader reader = new BufferedReader( new InputStreamReader(zip));
			StringBuilder builder = new StringBuilder();
			while (reader.ready()) {
				try {
					builder.append(reader.readLine()).append("\n");
				} catch (Exception e) {
					break;
				}
			}
			System.out.println(builder);
			assertThat(builder.toString().contains("04150405"), is(true));
		}
	}
	
	


}
