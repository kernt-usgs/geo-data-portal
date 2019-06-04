package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import java.io.InputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.lang.StringEscapeUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class WaterML2Parser extends XMLTimeseriesParser {
	
	private static final Logger log = LoggerFactory.getLogger(WaterML2Parser.class);
	
	public static final String CLEAR_METADATA_ELEMENT = "OM_Observation";
	
	public static final String PROCEDURE_ELEMENT = "procedure";
	public static final String OBSERVED_PROPERTY_ELEMENT = "observedProperty";
	public static final String FEATURE_OF_INTEREST_ELEMENT = "featureOfInterest";
	
	public static final String UOM_ELEMENT = "uom";
	public static final String CODE_ATTRIBUTE = "code";
	
	public static final String POINT_ELEMENT = "point";
	public static final String TIME_ELEMENT = "time";
	public static final String VALUE_ELEMENT = "value";
	
	private ObservationMetadata sharedMetadata;
	
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
			observation = gatherObservation();
		} catch (XMLStreamException ex) {
			log.trace("Exception reading stream");
		}
		return observation;
	}
	
	protected Observation gatherObservation() throws XMLStreamException {
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
							this.sharedMetadata.observedProperty(getHrefAttribute());
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
									reader.getAttributeValue(null, CODE_ATTRIBUTE));
						}
					}
					// End metadata collection
					if (isElement(POINT_ELEMENT)) {
						ob = new Observation().metadata(this.sharedMetadata);
					}
					if (isElement(TIME_ELEMENT)) {
						if (ob != null) {
							ob.time(new DateTime(reader.getElementText()));
						}
					}
					if (isElement(VALUE_ELEMENT)) {
						if (ob != null) {
							String elementText = reader.getElementText();
							ob.value(StringEscapeUtils.unescapeXml(elementText));
						}
					}
					break;
				case XMLStreamConstants.END_ELEMENT:
					if (isElement(CLEAR_METADATA_ELEMENT)) {
						this.sharedMetadata = null;
					}
					
					if (isElement(POINT_ELEMENT)) {
						// IMPORTANT: this is what triggers the return
						if (ob != null) {
							ob.setReady(true);
						}
					}
					break;
				case XMLStreamConstants.END_DOCUMENT:
					log.trace("Reached end of document");
			}
		}
		return ob;
	}

}
