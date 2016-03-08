package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.geotools.xlink.XLINK;
import org.joda.time.DateTime;
import org.joda.time.Period;

import static gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.WaterML2Parser.BEGIN_POSITION_ELEMENT;
import static gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.WaterML2Parser.END_POSITION_ELEMENT;
import static gov.usgs.cida.gdp.coreprocessing.analysis.timeseries.WaterML2Parser.TIME_PERIOD_ELEMENT;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public abstract class XMLTimeseriesParser implements TimeseriesParser {

	protected XMLStreamReader reader;
	
	public static final String TIME_PERIOD_ELEMENT = "TimePeriod";
	public static final String BEGIN_POSITION_ELEMENT = "beginPosition";
	public static final String END_POSITION_ELEMENT = "endPosition";
	
	/**
	 * Returns true if current XML element is equal to that passed in
	 * @param elementName name to check for equality with element
	 * @return true if equal
	 */
	protected boolean isElement(String elementName) {
		boolean isElement = false;
		if (elementName != null) {
			isElement = elementName.equals(reader.getLocalName());
		} 
		return isElement;
	}
	
	protected String getHrefAttribute() {
		return reader.getAttributeValue(XLINK.HREF.getNamespaceURI(), XLINK.HREF.getLocalPart());
	}
	
	protected Period gatherDateRange() throws XMLStreamException {
		Period range = null;
		DateTime startTime = null;
		DateTime endTime = null;
		
		while (this.reader.hasNext() && range == null) {
			switch (reader.next()) {
				case XMLStreamConstants.START_ELEMENT:
					if (isElement(BEGIN_POSITION_ELEMENT)) {
						startTime = new DateTime(reader.getElementText());
					}
					if (isElement(END_POSITION_ELEMENT)) {
						endTime = new DateTime(reader.getElementText());
					}
					break;
				case XMLStreamConstants.END_ELEMENT:
					if (isElement(TIME_PERIOD_ELEMENT)) {
						range = new Period(startTime, endTime);
					}
					break;
			}
		}
		return range;
	}
}
