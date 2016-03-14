package gov.usgs.cida.gdp.coreprocessing.analysis.timeseries;

import java.io.InputStream;
import java.util.Iterator;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses a WaterML 2.0 timeseries into an observation Collection.
 * 
 * This is a single use iterator, if you want to use it again, create another
 * one.
 * 
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class ObservationCollection implements Iterator, AutoCloseable {
	
	private static final Logger log = LoggerFactory.getLogger(ObservationCollection.class);

	private InputStream stream;
	private TimeseriesParser parser;
	protected Observation currentObservation;
	private boolean observationRead = false;
	
	public ObservationCollection(InputStream stream, TimeseriesParser parser) {
		this.stream = stream;
		this.parser = parser;
		parser.setInputStream(stream);
	}

	@Override
	public boolean hasNext() {
		boolean hasNext = false;
		if (currentObservation != null && !observationRead) {
			hasNext = true;
		} else {
			currentObservation = parser.parseNextObservation();
			observationRead = false;
			hasNext = (currentObservation != null);
		}
		
		return hasNext;
	}

	@Override
	public Observation next() {
		
		if (hasNext()) {
			observationRead = true;
			return currentObservation;
		} else {
			throw new IllegalStateException("There is no next value, you should have called hasNext() first");
		}
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("This is a read-only iterator");
	}

	@Override
	public void close() throws Exception {
		IOUtils.closeQuietly(stream);
	}
	
}
