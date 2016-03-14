package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import com.google.common.collect.Lists;
import gov.usgs.cida.gdp.coreprocessing.Delimiter;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.Statistics1DWriter;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ma2.Range;

import static gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.TimeseriesStatistic.VALUE;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class FeatureTimeseriesStatistics {

	private static final Logger log = LoggerFactory.getLogger(FeatureTimeseriesStatistics.class);

	public static void execute(
			FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection,
			String attributeName,
			TimeseriesDataset timeseriesDataset,
			String variableName,
			List<StationTimeseriesVisitor> additionalVisitors,
			Writer writer,
			Delimiter delimiter) {

		List<Object> featureNames = gatherFeatureAttributes(featureCollection, attributeName);
		if (featureNames == null || featureNames.isEmpty()) {
			throw new RuntimeException("No features specified");
		}
		timeseriesDataset.populateMetadata(featureNames.get(0).toString());
		String units = timeseriesDataset.getUnits();
		Statistics1DWriter statisticWriter = new Statistics1DWriter(
				featureNames,
				variableName,
				units,
				Lists.newArrayList(VALUE),
				false,
				delimiter.delimiter,
				null,
				false,
				false,
				writer);
		
		List<StationTimeseriesVisitor> visitors = new LinkedList<>();
		FeatureTimeseriesStatiticsVisitor ftsVisitor = new FeatureTimeseriesStatiticsVisitor(featureNames, statisticWriter);
		visitors.add(ftsVisitor);
		visitors.addAll(additionalVisitors);
		TimeseriesTraverser traverser = new TimeseriesTraverser(timeseriesDataset);
		
		traverser.traverse(visitors);
	}

	/**
	 * Coerce attribute into String to be used in SOS offering request
	 * @param featureCollection
	 * @param attributeName
	 * @return 
	 */
	private static List<Object> gatherFeatureAttributes(
			FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection, String attributeName) {
		Set<String> attributes = new LinkedHashSet<>();
		
		FeatureIterator<SimpleFeature> features = featureCollection.features();
		while (features.hasNext()) {
			SimpleFeature feature = features.next();
			Object attribute = feature.getAttribute(attributeName);
			if (null != attribute) {
				String strAttribute = attribute.toString();
				attributes.add(strAttribute);
			}
		}
		return Lists.newArrayList(attributes);
	}

}
