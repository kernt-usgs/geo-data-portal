package gov.usgs.cida.gdp.wps.algorithm;

import static org.n52.wps.algorithm.annotation.LiteralDataInput.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.SchemaException;
import org.n52.wps.algorithm.annotation.Algorithm;
import org.n52.wps.algorithm.annotation.ComplexDataInput;
import org.n52.wps.algorithm.annotation.ComplexDataOutput;
import org.n52.wps.algorithm.annotation.Execute;
import org.n52.wps.algorithm.annotation.LiteralDataInput;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.usgs.cida.gdp.constants.AppConstant;
import gov.usgs.cida.gdp.coreprocessing.Delimiter;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.FeatureCoverageWeightedGridStatistics;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.GridCellVisitor;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.Statistics1DWriter.GroupBy;
import gov.usgs.cida.gdp.coreprocessing.analysis.grid.WeightedStatistic;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.DataInspectionAlgorithmHeuristic;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.GeometrySizeAlgorithmHeuristic;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.SummaryOutputSizeAlgorithmHeuristic;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.TotalTimeAlgorithmHeuristic;
import gov.usgs.cida.gdp.wps.algorithm.heuristic.UpdatePercentHeuristic;
import gov.usgs.cida.gdp.wps.binding.CSVFileBinding;
import gov.usgs.cida.gdp.wps.binding.GMLStreamingFeatureCollectionBinding;
import java.io.BufferedOutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.dt.GridDatatype;

/**
 *
 * @author tkunicki
 */
@Algorithm(
		version = "1.0.0",
		title = "Area Grid Statistics (weighted)",
		abstrakt = "This algorithm generates area weighted statistics of a gridded dataset for a set of vector polygon features. Using the bounding-box that encloses the feature data and the time range, if provided, a subset of the gridded dataset is requested from the remote gridded data server. Polygon representations are generated for cells in the retrieved grid. The polygon grid-cell representations are then projected to the feature data coordinate reference system. The grid-cells are used to calculate per grid-cell feature coverage fractions. Area-weighted statistics are then calculated for each feature using the grid values and fractions as weights. If the gridded dataset has a time range the last step is repeated for each time step within the time range or all time steps if a time range was not supplied.")
public class FeatureWeightedGridStatisticsAlgorithm extends AbstractAnnotatedAlgorithm {

	protected static final Logger log = LoggerFactory.getLogger(FeatureCategoricalGridCoverageAlgorithm.class);

	private FeatureCollection<SimpleFeatureType,SimpleFeature> featureCollection;
	private String featureAttributeName;
	private URI datasetURI;
	private List<String> datasetId;
	private boolean requireFullCoverage = true;
	private Date timeStart;
	private Date timeEnd;
	private List<WeightedStatistic> statistics;
	private GroupBy groupBy;
	private Delimiter delimiter;
	private boolean summarizeTimeStep = false;
	private boolean summarizeFeatureAttribute = false;

	private File output;

	@ComplexDataInput(
			identifier = GDPAlgorithmConstants.FEATURE_COLLECTION_IDENTIFIER,
			title = GDPAlgorithmConstants.FEATURE_COLLECTION_TITLE,
			abstrakt = GDPAlgorithmConstants.FEATURE_COLLECTION_ABSTRACT,
			binding = GMLStreamingFeatureCollectionBinding.class)
	public void setFeatureCollection(FeatureCollection<SimpleFeatureType,SimpleFeature> featureCollection) {
		this.featureCollection = featureCollection;
	}

	@LiteralDataInput(
			identifier = GDPAlgorithmConstants.FEATURE_ATTRIBUTE_NAME_IDENTIFIER,
			title = GDPAlgorithmConstants.FEATURE_ATTRIBUTE_NAME_TITLE,
			abstrakt = GDPAlgorithmConstants.FEATURE_ATTRIBUTE_NAME_ABSTRACT)
	public void setFeatureAttributeName(String featureAttributeName) {
		this.featureAttributeName = featureAttributeName;
	}

	@LiteralDataInput(
			identifier = GDPAlgorithmConstants.DATASET_URI_IDENTIFIER,
			title = GDPAlgorithmConstants.DATASET_URI_TITLE,
			abstrakt = GDPAlgorithmConstants.DATASET_URI_ABSTRACT)
	public void setDatasetURI(URI datasetURI) {
		this.datasetURI = datasetURI;
	}

	@LiteralDataInput(
			identifier = GDPAlgorithmConstants.DATASET_ID_IDENTIFIER,
			title = GDPAlgorithmConstants.DATASET_ID_TITLE,
			abstrakt = GDPAlgorithmConstants.DATASET_ID_ABSTRACT,
			maxOccurs = Integer.MAX_VALUE)
	public void setDatasetId(List<String> datasetId) {
		this.datasetId = datasetId;
	}

	@LiteralDataInput(
			identifier = GDPAlgorithmConstants.REQUIRE_FULL_COVERAGE_IDENTIFIER,
			title = GDPAlgorithmConstants.REQUIRE_FULL_COVERAGE_TITLE,
			abstrakt = GDPAlgorithmConstants.REQUIRE_FULL_COVERAGE_ABSTRACT,
			defaultValue = "true")
	public void setRequireFullCoverage(boolean requireFullCoverage) {
		this.requireFullCoverage = requireFullCoverage;
	}

	@LiteralDataInput(
			identifier = GDPAlgorithmConstants.TIME_START_IDENTIFIER,
			title = GDPAlgorithmConstants.TIME_START_TITLE,
			abstrakt = GDPAlgorithmConstants.TIME_START_ABSTRACT,
			minOccurs = 0)
	public void setTimeStart(Date timeStart) {
		this.timeStart = timeStart;
	}

	@LiteralDataInput(
			identifier = GDPAlgorithmConstants.TIME_END_IDENTIFIER,
			title = GDPAlgorithmConstants.TIME_END_TITLE,
			abstrakt = GDPAlgorithmConstants.TIME_END_ABSTRACT,
			minOccurs = 0)
	public void setTimeEnd(Date timeEnd) {
		this.timeEnd = timeEnd;
	}

	@LiteralDataInput(
			identifier = "STATISTICS",
			title = "Statistics",
			abstrakt = "Statistics that will be returned for each feature in the processing output.",
			maxOccurs = ENUM_COUNT)
	public void setStatistics(List<WeightedStatistic> statistics) {
		this.statistics = statistics;
	}

	@LiteralDataInput(
			identifier = "GROUP_BY",
			title = "Group By",
			abstrakt = "If multiple features and statistics are selected, this will change whether the processing output columns are sorted according to statistics or feature attributes.")
	public void setGroupBy(GroupBy groupBy) {
		this.groupBy = groupBy;
	}

	@LiteralDataInput(
			identifier = GDPAlgorithmConstants.DELIMITER_IDENTIFIER,
			title = GDPAlgorithmConstants.DELIMITER_TITLE,
			abstrakt = GDPAlgorithmConstants.DELIMITER_ABSTRACT,
			defaultValue = "COMMA")
	public void setDelimiter(Delimiter delimiter) {
		this.delimiter = delimiter;
	}

	@LiteralDataInput(
			identifier = "SUMMARIZE_TIMESTEP",
			title = "Summarize Timestep",
			abstrakt = "If selected, processing output will include columns with summarized statistics for all feature attribute values for each timestep",
			minOccurs = 0,
			defaultValue = "false")
	public void setSummarizeTimeStep(boolean summarizeTimeStep) {
		this.summarizeTimeStep = summarizeTimeStep;
	}

	@LiteralDataInput(
			identifier = "SUMMARIZE_FEATURE_ATTRIBUTE",
			title = "Summarize Feature Attribute",
			abstrakt = "If selected, processing output will include a final row of statistics summarizing all timesteps for each feature attribute value",
			minOccurs = 0,
			defaultValue = "false")
	public void setSummarizeFeatureAttribute(boolean summarizeFeatureAttribute) {
		this.summarizeFeatureAttribute = summarizeFeatureAttribute;
	}

	@ComplexDataOutput(
			identifier = "OUTPUT",
			title = "Output File",
			abstrakt = "A delimited text file containing requested process output.",
			binding = CSVFileBinding.class)
	public File getOutput() {
		return output;
	}

	@Execute
	public void process() {
		if (featureCollection.getSchema().getDescriptor(featureAttributeName) == null) {
			addError("Attribute " + featureAttributeName + " not found in feature collection");
			return;
		}

		/*
		 * Lets run our geometry size heuristic to see if we should go ahead and process
		 * this bounds.  All geometry size should be relative regardless of the dataset
		 * variable.
		 * 
		 * Lets get the first variable and create a GridDatatype
		 */
		if (datasetId.isEmpty()) {
			addError("Error subsetting gridded data.  Grid variable list is empty! ");
			return;
		}
		if (datasetURI == null || datasetURI.isAbsolute()) {
			addError("Error accessing gridded data.  Dataset URI is invalid.");
			return;
		}

		Writer writer = null;

		try {
			if (featureCollection.getSchema().getDescriptor(featureAttributeName) == null) {
				addError("Attribute " + featureAttributeName + " not found in feature collection");
				return;
			}
			output = File.createTempFile(getClass().getSimpleName(), delimiter.extension, new File(AppConstant.WORK_LOCATION.getValue()));
			CountingOutputStream cos = new CountingOutputStream(new BufferedOutputStream(new FileOutputStream(output)));
			writer = new PrintWriter(new OutputStreamWriter(cos), true);

			List<GridCellVisitor> heuristics = setupHeuristics(cos);
			
			for (String currentDatasetId : datasetId) {
				GridDatatype gridDatatype = GDPAlgorithmUtil.generateGridDataType(
						datasetURI,
						currentDatasetId,
						featureCollection.getBounds(),
						requireFullCoverage);

				Range timeRange = GDPAlgorithmUtil.generateTimeRange(
						gridDatatype,
						timeStart,
						timeEnd);

				writer.write("# " + currentDatasetId);
				writer.write("\n");
				FeatureCoverageWeightedGridStatistics.execute(
						featureCollection,
						featureAttributeName,
						gridDatatype.makeSubset(null, null, timeRange, null, null, null),
						heuristics,
						statistics == null || statistics.isEmpty() ? Arrays.asList(WeightedStatistic.values()) : statistics,
						writer,
						groupBy == null ? GroupBy.STATISTIC : groupBy,
						delimiter == null ? Delimiter.getDefault() : delimiter,
						requireFullCoverage,
						summarizeTimeStep,
						summarizeFeatureAttribute);
			}
		} catch (InvalidRangeException e) {
			addError("Error subsetting gridded data: " + e.getMessage());
		} catch (IOException e) {
			addError("IO Error :" + e.getMessage());
		} catch (FactoryException e) {
			addError("Error initializing CRS factory: " + e.getMessage());
		} catch (TransformException e) {
			addError("Error attempting CRS transform: " + e.getMessage());
		} catch (SchemaException e) {
			addError("Error subsetting gridded data : " + e.getMessage());
		} catch (Exception e) {
			addError("General Error: " + e.getMessage());
		} finally {
			IOUtils.closeQuietly(writer);
		}
	}

	private List<GridCellVisitor> setupHeuristics(CountingOutputStream cos) {
		List<GridCellVisitor> heuristics = new LinkedList<>();
		heuristics.add(new GeometrySizeAlgorithmHeuristic(featureCollection, requireFullCoverage));
		heuristics.add(new SummaryOutputSizeAlgorithmHeuristic(this, cos, datasetId.size()));
		heuristics.add(new TotalTimeAlgorithmHeuristic(datasetId.size()));
		heuristics.add(new UpdatePercentHeuristic(this, datasetId.size()));
		heuristics.add(new DataInspectionAlgorithmHeuristic(this, featureCollection, timeStart, timeEnd, requireFullCoverage));
		return heuristics;
	}
}
