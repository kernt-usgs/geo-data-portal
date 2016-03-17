package gov.usgs.cida.gdp.wps.algorithm;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;
import org.n52.wps.algorithm.annotation.ComplexDataInput;
import org.n52.wps.algorithm.annotation.ComplexDataOutput;
import org.n52.wps.algorithm.annotation.Execute;
import org.n52.wps.algorithm.annotation.LiteralDataInput;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.usgs.cida.gdp.constants.AppConstant;
import gov.usgs.cida.gdp.coreprocessing.Delimiter;
import gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.StationTimeseriesVisitor;
import gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.TimeseriesDataset;
import gov.usgs.cida.gdp.wps.binding.GMLStreamingFeatureCollectionBinding;
import gov.usgs.cida.gdp.wps.binding.ZipFileBinding;

public class FeatureTimeSeriesAlgorithm extends AbstractAnnotatedAlgorithm {
	
	private static final Logger log = LoggerFactory.getLogger(FeatureTimeSeriesAlgorithm.class);


	private FeatureCollection<SimpleFeatureType,SimpleFeature> featureCollection;
    private String featureAttributeName;
    private String observedProperty;
	private URI datasetURI;
	private Date timeStart;
	private Date timeEnd;
    private Delimiter delimiter;
    private List<StationTimeseriesVisitor> additionalVisitors;
    
	private File output;

	private String timeSeriesVariableName;


	private boolean includeShapefile;

	
	@LiteralDataInput(
			identifier = GDPAlgorithmConstants.DATASET_URI_IDENTIFIER,
			title = GDPAlgorithmConstants.DATASET_URI_TITLE,
			abstrakt = GDPAlgorithmConstants.DATASET_URI_ABSTRACT + " The data web service must adhere to the OPeNDAP protocol.")
	public void setDatasetURI(URI datasetURI) {
		this.datasetURI = datasetURI;
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
	
	@ComplexDataInput(
			identifier = GDPAlgorithmConstants.FEATURE_COLLECTION_IDENTIFIER,
			title = GDPAlgorithmConstants.FEATURE_COLLECTION_TITLE,
			abstrakt = GDPAlgorithmConstants.FEATURE_COLLECTION_ABSTRACT,
			binding = GMLStreamingFeatureCollectionBinding.class)
	public void setFeatureCollection(FeatureCollection<SimpleFeatureType,SimpleFeature> featureCollection) {
		this.featureCollection = featureCollection;
	}

    @LiteralDataInput(
            identifier=GDPAlgorithmConstants.OBSERVED_PROPERTY_IDENTIFIER,
            title=GDPAlgorithmConstants.OBSERVED_PROPERTY_TITLE,
            abstrakt=GDPAlgorithmConstants.OBSERVED_PROPERTY_ABSTRACT)
    public void setObservedProperty(String observedProperty) {
        this.observedProperty = observedProperty;
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
            binding=ZipFileBinding.class)
    public File getOutput() {
        return output;
    }
    
// TODO this will be included later
//  @LiteralDataInput(
//          identifier=GDPAlgorithmConstants.,
//          title=GDPAlgorithmConstants.,
//          abstrakt=GDPAlgorithmConstants.)
//  public void setIncludeShapefile(boolean includeShapefile) {
//      this.includeShapefile = includeShapefile;
//  }

    public void setAdditionalVisitors(List<StationTimeseriesVisitor> additionalVisitors) {
        this.additionalVisitors = additionalVisitors;
    }

    
	@Execute
	public void process() {
		
		if (null==datasetURI) {
			new IllegalArgumentException("The timeseries dataset URI is required.");
		}
		if (null==timeStart) {
			new IllegalArgumentException("The timeseries dataset start date or date/time is required.");
		}
		if (null==timeEnd) {
			new IllegalArgumentException("The timeseries dataset end date or date/time is required.");
		}
		
		if (null==additionalVisitors) {
			additionalVisitors = new LinkedList<StationTimeseriesVisitor>();
		}
		
        String extension = (delimiter == null) ? Delimiter.getDefault().extension : delimiter.extension;
        try {
        	output = File.createTempFile(getClass().getSimpleName(), extension, new File(AppConstant.WORK_LOCATION.getValue()));
        	
			try( FileOutputStream   fos    = new FileOutputStream(output);
				 ZipOutputStream    zip    = new ZipOutputStream(fos);
				 OutputStreamWriter buffer = new OutputStreamWriter(zip);
				 BufferedWriter     writer = new BufferedWriter(buffer);
				) {
				
				// TODO saved for a later date
				includeShapefile = false;
				if (includeShapefile) {
					renderShapeFile(featureCollection, zip);
				}
				
				TimeseriesDataset timeseriesDataset = new TimeseriesDataset(
						datasetURI, observedProperty, new DateTime(timeStart), new DateTime(timeEnd));
				
				
				zip.putNextEntry(new ZipEntry("sos."+delimiter));
				
				gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.FeatureTimeseriesStatistics.execute(
						featureCollection,
						featureAttributeName,
						timeseriesDataset,
						timeSeriesVariableName,
						additionalVisitors,
						writer,
						delimiter);
				zip.closeEntry();
			
			} catch (Exception e) { 
				// TODO other specific exception handling?
				log.error("General Error: ", e);
				addError("General Error: " + e.getMessage());
			}	
		} catch (Exception e) { 
			// TODO other specific exception handling?
			log.error("General Error: ", e);
			addError("General Error: " + e.getMessage());
		}	
	}


	// TODO this is more of a place holder and a possible rough implementation found 
	protected void renderShapeFile(FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection, ZipOutputStream zip) throws Exception {

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
        
        
        // TODO create a temp dir to hold all rendered files
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
        
        // TODO for each shape file make an appropriate file entry and stream
		zip.putNextEntry(new ZipEntry("shape/shapefile.shp"));
        IOUtils.copyLarge(new FileInputStream(newFile), zip);
        zip.closeEntry();
    }      
	
    
}

