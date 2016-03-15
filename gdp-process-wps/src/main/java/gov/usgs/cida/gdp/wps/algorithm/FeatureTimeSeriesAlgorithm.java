package gov.usgs.cida.gdp.wps.algorithm;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.n52.wps.algorithm.annotation.ComplexDataInput;
import org.n52.wps.algorithm.annotation.ComplexDataOutput;
import org.n52.wps.algorithm.annotation.Execute;
import org.n52.wps.algorithm.annotation.LiteralDataInput;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.usgs.cida.gdp.coreprocessing.Delimiter;
import gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.StationTimeseriesVisitor;
import gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.TimeseriesDataset;
import gov.usgs.cida.gdp.wps.binding.CSVFileBinding;
import gov.usgs.cida.gdp.wps.binding.GMLStreamingFeatureCollectionBinding;

public class FeatureTimeSeriesAlgorithm extends AbstractAnnotatedAlgorithm {
	
	private static final Logger log = LoggerFactory.getLogger(FeatureTimeSeriesAlgorithm.class);


	private FeatureCollection<SimpleFeatureType,SimpleFeature> featureCollection;
    private String featureAttributeName;
//	private boolean requireFullCoverage;
//	private URI datasetURI;
//	private List<String> datasetId;
    private Delimiter delimiter;
	private TimeseriesDataset timeseriesDataset;
    private List<StationTimeseriesVisitor> additionalVisitors;
    
	private File output;




	private String timeSeriesVariableName;


	private boolean includeShapefile;

	@ComplexDataInput(
			identifier = GDPAlgorithmConstants.FEATURE_COLLECTION_IDENTIFIER,
			title = GDPAlgorithmConstants.FEATURE_COLLECTION_TITLE,
			abstrakt = GDPAlgorithmConstants.FEATURE_COLLECTION_ABSTRACT,
			binding = GMLStreamingFeatureCollectionBinding.class)
	public void setFeatureCollection(FeatureCollection<SimpleFeatureType,SimpleFeature> featureCollection) {
		this.featureCollection = featureCollection;
	}

    @LiteralDataInput(
            identifier=GDPAlgorithmConstants.FEATURE_ATTRIBUTE_NAME_IDENTIFIER,
            title=GDPAlgorithmConstants.FEATURE_ATTRIBUTE_NAME_TITLE,
            abstrakt=GDPAlgorithmConstants.FEATURE_ATTRIBUTE_NAME_ABSTRACT)
    public void setFeatureAttributeName(String featureAttributeName) {
        this.featureAttributeName = featureAttributeName;
    }
    @LiteralDataInput(
            identifier=GDPAlgorithmConstants.TIME_SERIES_VARIABLE_NAME_IDENTIFIER,
            title=GDPAlgorithmConstants.TIME_SERIES_VARIABLE_NAME_TITLE,
            abstrakt=GDPAlgorithmConstants.TIME_SERIES_VARIABLE_NAME_ABSTRACT)
    public void setTimeSeriesVariableName(String variableName) {
        this.timeSeriesVariableName = variableName;
    }
    @LiteralDataInput(
            identifier=GDPAlgorithmConstants.ADDITIONAL_VISITORS_IDENTIFIER,
            title=GDPAlgorithmConstants.ADDITIONAL_VISITORS_TITLE,
            abstrakt=GDPAlgorithmConstants.ADDITIONAL_VISITORS_ABSTRACT)
    public void setAdditionalVisitors(List<StationTimeseriesVisitor> additionalVisitors) {
        this.additionalVisitors = additionalVisitors;
    }

    @LiteralDataInput(
            identifier=GDPAlgorithmConstants.TIME_SERIES_DATASET_IDENTIFIER,
            title=GDPAlgorithmConstants.TIME_SERIES_DATASET_TITLE,
            abstrakt=GDPAlgorithmConstants.TIME_SERIES_DATASET_ABSTRACT)
    public void setTimeSeriesDataset(TimeseriesDataset timeseriesDataset) {
        this.timeseriesDataset = timeseriesDataset;
    }

    @LiteralDataInput(
            identifier=GDPAlgorithmConstants.TIME_SERIES_DATASET_IDENTIFIER,
            title=GDPAlgorithmConstants.TIME_SERIES_DATASET_TITLE,
            abstrakt=GDPAlgorithmConstants.TIME_SERIES_DATASET_ABSTRACT)
    public void setIncludeShapefile(boolean includeShapefile) {
        this.includeShapefile = includeShapefile;
    }
    

    @LiteralDataInput(
        identifier=GDPAlgorithmConstants.DELIMITER_IDENTIFIER,
        title=GDPAlgorithmConstants.DELIMITER_TITLE,
        abstrakt=GDPAlgorithmConstants.DELIMITER_ABSTRACT,
        defaultValue="COMMA")
    public void setDelimiter(Delimiter delimiter) {
        this.delimiter = delimiter;
    }
    
    @ComplexDataOutput(identifier="OUTPUT",
            title="Output File",
            abstrakt="A delimited text file containing requested process output.",
            binding=CSVFileBinding.class)
    public File getOutput() {
        return output;
    }

	@Execute
	public void process() {

			// sends a writer so need to wrap the zip file entry in writer
			
			try( FileOutputStream fos = new FileOutputStream(output);
				  ZipOutputStream zip = new ZipOutputStream(fos);) {

				// TODO saved for a later date
//				if (includeShapefile) {
//					zip.putNextEntry(new ZipEntry("shapefile.shp"));
//					renderShapeFile(featureCollection, zip);
//					zip.closeEntry();
//				}
				
				zip.putNextEntry(new ZipEntry("sos."+delimiter));
				
				OutputStreamWriter writer = new OutputStreamWriter(zip);
				gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.FeatureTimeseriesStatistics.execute(
						featureCollection,
						featureAttributeName,
						timeseriesDataset,
						timeSeriesVariableName,
//						timeRange,
						additionalVisitors,
						writer,
						delimiter);
				zip.closeEntry();
			
		} catch (Exception e) { 
			// TODO other specific exception handling?
			log.error("General Error: ", e);
			addError("General Error: " + e.getMessage());
		}	
	}

	
	protected void renderShapeFile(FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection, OutputStream output) throws Exception {

	    /*
         * We use the DataUtilities class to create a FeatureType that will describe the data in our
         * shapefile.
         * 
         * See also the createFeatureType method below for another, more flexible approach.
         */
        final SimpleFeatureType TYPE = DataUtilities.createType("Location",
                "location:Point:srid=4326," + // <- the geometry attribute: Point type
                        "name:String," + // <- a String attribute
                        "number:Integer" // a number attribute
        );
        
		File newFile = File.createTempFile("shapefile", "shp");
		/*
         * Get an output file name and create the new shapefile
         */

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put("url", newFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);

        ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
        newDataStore.createSchema(TYPE);

        /*
         * You can comment out this line if you are using the createFeatureType method (at end of
         * class file) rather than DataUtilities.createType
         */
        newDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84);
        
        /*
         * Write the features to the shapefile
         */
        Transaction transaction = new DefaultTransaction("create");

        String typeName = newDataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);

        if (featureSource instanceof SimpleFeatureStore) {
            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

            featureStore.setTransaction(transaction);
            try {
                featureStore.addFeatures(featureCollection);
                transaction.commit();
            } catch (Exception problem) {
                transaction.rollback();
            } finally {
                transaction.close();
            }
        }
        
        IOUtils.copyLarge(new FileInputStream(newFile), output);
    }      
	
    
}

