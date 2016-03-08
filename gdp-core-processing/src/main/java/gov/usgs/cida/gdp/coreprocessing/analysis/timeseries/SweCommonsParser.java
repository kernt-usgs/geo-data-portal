package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import java.io.InputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class SweCommonsParser extends XMLTimeseriesParser {
	
	private static final Logger log = LoggerFactory.getLogger(SweCommonsParser.class);

	public static final String CLEAR_METADATA_ELEMENT = "Observation";
	
	public static final String PROCEDURE_ELEMENT = "procedure";
	public static final String OBSERVED_PROPERTY_ELEMENT = "field";
	public static final String FEATURE_OF_INTEREST_ELEMENT = "featureOfInterest";
	
	public static final String TEXT_BLOCK_ELEMENT = "TextBlock";
	
	public static final String UOM_ELEMENT = "uom";
	public static final String CODE_ATTRIBUTE = "code";
	public static final String NAME_ATTRIBUTE = "name";
	public static final String TIME_FIELD_NAME = "time";
	
	public static final String VALUES_ELEMENT = "values";
	
	private ObservationMetadata sharedMetadata;
	private SweBlockParser blockParser;
	
	@Override
	public void setInputStream(InputStream stream) {
		try {
			XMLStreamReader xmlReader = XMLInputFactory.newInstance().createXMLStreamReader(stream);
			this.reader = xmlReader;
		} catch (XMLStreamException ex) {
			throw new RuntimeException("Error parsing XML", ex);
		}
	}
	
	@Override
	public Observation parseNextObservation() {
		Observation observation = null;
		if (reader == null) {
			throw new IllegalStateException("Input stream must be set before parsing");
		}
		try {
			if (null == sharedMetadata || null == blockParser) {
				gatherEmptyObservation();
			}
			observation = new Observation();
			observation.setMetadata(sharedMetadata);
			String[] block = blockParser.getNextBlock();
			// TODO needs to be more robust to handle more than tvp
			if (null != block && block.length == 2) {
				String time = block[0];
				String value = block[1];
				observation.setTime(new DateTime(time));
				observation.setValue(value);
			}
		} catch (XMLStreamException ex) {
			log.trace("Exception reading xml stream");
		}
		return observation;
	}

	/**
	 * Block contains multiple "observations" so this just gets to the data
	 * 
	 * @throws XMLStreamException
	 */
	protected void gatherEmptyObservation() throws XMLStreamException {
		Observation ob = null;
		
		while (this.reader.hasNext() && (ob == null || !ob.isReady())) {
			switch (reader.next()) {
				case XMLStreamConstants.START_ELEMENT:
					// Start metadata collection
					if (isElement(CLEAR_METADATA_ELEMENT)) {
						this.sharedMetadata = new ObservationMetadata();
					}
					if (isElement(TIME_PERIOD_ELEMENT)) {
						if (this.sharedMetadata != null) {
							this.sharedMetadata.timePeriod(gatherDateRange());
						}
					}
					
					if (isElement(PROCEDURE_ELEMENT)) {
						if (this.sharedMetadata != null) {
							this.sharedMetadata.procedure(getHrefAttribute());
						}
					}
					if (isElement(OBSERVED_PROPERTY_ELEMENT)) {
						if (this.sharedMetadata != null) {
							String name = reader.getAttributeValue(null, NAME_ATTRIBUTE);
							if (!TIME_FIELD_NAME.equals(name)) {
								this.sharedMetadata.observedProperty(name);
							}
						}
					}
					if (isElement(FEATURE_OF_INTEREST_ELEMENT)) {
						if (this.sharedMetadata != null) {
							this.sharedMetadata.featureOfInterest(getHrefAttribute());
						}
					}
					if (isElement(UOM_ELEMENT)) {
						if (this.sharedMetadata != null) {
							this.sharedMetadata.defaultUnits(
									reader.getAttributeValue(null, ObservationMetadata.CODE_ATTRIBUTE));
						}
					}
					if (isElement(TEXT_BLOCK_ELEMENT)) {
						if (this.blockParser == null) {
							this.blockParser = new SweBlockParser();
							this.blockParser.readEncoding(reader);
						}
					}
					// End metadata collection
					if (isElement(VALUES_ELEMENT)) {
						ob = new Observation().metadata(this.sharedMetadata);
						if (this.blockParser != null) {
							this.blockParser.readValues(reader);
						}
					}
					break;
				case XMLStreamConstants.END_ELEMENT:
					if (isElement(CLEAR_METADATA_ELEMENT)) {
						this.sharedMetadata = null;
						this.blockParser = null;
					}
					
					if (isElement(VALUES_ELEMENT)) {
						// IMPORTANT: this is what triggers the return
						if (ob != null) {
							ob.setReady(true);
						}
					}
					break;
				case XMLStreamConstants.END_DOCUMENT:
					log.trace("Reached end of XML document");
			}
		}
	}
	
	/**
	 * Parses the Swe portion of the document
	 * Currently doesn't use the decimal separator, but it is available
	 */
	public class SweBlockParser {
		
		public static final String BLOCK_SEP_ATTR = "blockSeparator";
		public static final String DECIMAL_SEP_ATTR = "decimalSeparator";
		public static final String TOKEN_SEP_ATTR = "tokenSeparator";
		
		private String blockSeparator = "\n";
		private String decimalSeparator = ".";
		private String tokenSeparator = ",";
		
		private String[] blocks;
		private int blockPos = -1;
		
		public void readEncoding(XMLStreamReader reader) {
			String blockAttr = reader.getAttributeValue(null, BLOCK_SEP_ATTR);
			String decimalAttr = reader.getAttributeValue(null, DECIMAL_SEP_ATTR);
			String tokenAttr = reader.getAttributeValue(null, TOKEN_SEP_ATTR);
			if (blockAttr != null) {
				blockSeparator = blockAttr;
			}
			if (decimalAttr != null) {
				decimalSeparator = decimalAttr;
			}
			if (tokenAttr != null) {
				tokenSeparator = tokenAttr;
			}
		}
		
		public void readValues(XMLStreamReader reader) {
			try {
				String values = reader.getElementText();
				blocks = getBlocks(values);
			} catch (XMLStreamException ex) {
				log.error("Failure to read values from swe block", ex);
			}
		}
		
		private String[] getBlocks(String values) {
			String [] parsed = null;
			if (null != values) {
				parsed = values.split(blockSeparator);
			}
			return parsed;
		}
		
		public String[] getNextBlock() {
			String[] tokens = null;
			blockPos++;
			if (null != blocks && blockPos < blocks.length) {
				String block = blocks[blockPos];
				tokens = block.split(tokenSeparator);
			}
			return tokens;
		}
		
		public String getDecimalSeparator() {
			return decimalSeparator;
		}
	}
}
